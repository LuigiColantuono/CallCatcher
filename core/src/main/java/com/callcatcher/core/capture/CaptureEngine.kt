package com.callcatcher.core.capture

import android.media.AudioRecord
import android.util.Log
import com.callcatcher.core.model.CallMetadata
import com.callcatcher.core.model.CallSource
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * The shared capture engine. It is the only [AudioSource] implementation; every trigger drives
 * this same object. It knows nothing about phones vs. WhatsApp beyond which source cascade to try,
 * which it picks from [CallMetadata.source].
 *
 * Threading: [onStreamStart]/[onStreamEnd] are called from the trigger's thread. The actual
 * read/encode loop runs on its own thread so a slow encoder never blocks the trigger.
 */
class CaptureEngine(
    private val config: CaptureConfig = CaptureConfig(),
    private val sink: RecordingSink,
    private val cascadeSelector: (CallSource) -> List<CaptureSource> = ::defaultCascade,
) : AudioSource {

    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null

    override fun onStreamStart(metadata: CallMetadata): Boolean {
        if (!running.compareAndSet(false, true)) {
            Log.w(TAG, "onStreamStart ignored: already capturing")
            return false
        }

        val target = sink.open(metadata)
        if (target == null) {
            running.set(false)
            sink.onFailed(metadata, null, "Sink refused to open an output")
            return false
        }

        val opened = AudioRecordOpener.open(cascadeSelector(metadata.source), config)
        if (opened == null) {
            target.close()
            running.set(false)
            sink.onFailed(metadata, target, "No capture source could be opened")
            return false
        }

        worker = Thread({ runLoop(metadata, target, opened) }, "CaptureEngine").apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
        return true
    }

    override fun onStreamEnd() {
        if (!running.compareAndSet(true, false)) return
        worker?.let { t ->
            runCatching { t.join(JOIN_TIMEOUT_MS) }
            if (t.isAlive) Log.w(TAG, "Capture worker did not stop within ${JOIN_TIMEOUT_MS}ms")
        }
        worker = null
    }

    private fun runLoop(metadata: CallMetadata, target: RecordingSink.Target, opened: OpenedRecord) {
        val record = opened.record
        val encoder = OpusEncoder(target.fd, opened.sampleRateHz, opened.channelCount, config.bitrateBps)
        val waveform = WaveformAccumulator()

        var totalSamples = 0L
        var silentSamples = 0L
        var frames = 0L

        try {
            encoder.start()
            record.startRecording()

            val buffer = ByteArray(opened.minBufferBytes)
            val skipUntil = System.currentTimeMillis() + config.startDelayMillis

            while (running.get()) {
                val read = record.read(buffer, 0, buffer.size)
                if (read <= 0) {
                    if (read == AudioRecord.ERROR_INVALID_OPERATION || read == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "AudioRecord.read error $read")
                        break
                    }
                    continue
                }

                // Delayed start: drop the handshake/ring window so it isn't in the file.
                if (System.currentTimeMillis() < skipUntil) continue

                val (peak, silent, samples) = analyze(buffer, read)
                waveform.add(peak)
                totalSamples += samples
                silentSamples += silent
                frames += samples / opened.channelCount

                encoder.encode(buffer, read)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Capture loop failed", t)
            runCatching { encoder.finish() }
            runCatching { record.stop() }
            runCatching { record.release() }
            target.close()
            sink.onFailed(metadata, target, t.message ?: "capture loop exception")
            return
        }

        runCatching { record.stop() }
        runCatching { record.release() }
        encoder.finish()
        target.close()

        val durationMs = if (opened.sampleRateHz > 0) frames * 1000L / opened.sampleRateHz else 0L
        val silenceRatio = if (totalSamples > 0) silentSamples.toFloat() / totalSamples else 1f

        sink.onCompleted(
            CaptureResult(
                metadata = metadata,
                outputPath = target.path,
                source = opened.source,
                sampleRateHz = opened.sampleRateHz,
                channelCount = opened.channelCount,
                durationMillis = durationMs,
                sizeBytes = 0L, // filled in by the sink, which owns the file handle
                waveform = waveform.toArray(),
                silenceRatio = silenceRatio,
            )
        )
    }

    /** Returns (peak 0..1, silentSampleCount, totalSampleCount) for one 16-bit LE buffer. */
    private fun analyze(buffer: ByteArray, bytes: Int): Triple<Float, Long, Long> {
        var peak = 0
        var silent = 0L
        var count = 0L
        var i = 0
        while (i + 1 < bytes) {
            val sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            val a = abs(sample)
            peak = max(peak, a)
            if (a < SILENCE_THRESHOLD) silent++
            count++
            i += 2
        }
        return Triple(peak / 32768f, silent, count)
    }

    companion object {
        private const val TAG = "CaptureEngine"
        private const val JOIN_TIMEOUT_MS = 5_000L
        private const val SILENCE_THRESHOLD = 150 // ~ -46 dBFS

        fun defaultCascade(source: CallSource): List<CaptureSource> = when (source) {
            CallSource.PHONE -> CaptureSource.PHONE_CASCADE
            CallSource.WHATSAPP -> CaptureSource.VOIP_CASCADE
            CallSource.UNKNOWN -> CaptureSource.PHONE_CASCADE
        }
    }
}
