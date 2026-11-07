package com.callcatcher.core.capture

import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import com.callcatcher.core.model.CallMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max

/**
 * The shared capture engine. Every trigger (phone InCallService, WhatsApp hook, test button) ends
 * up here, in the app process, which is the only process holding CAPTURE_AUDIO_OUTPUT.
 *
 * One [Session] per call, each with its own thread and stop flag, so a call that ends while the
 * previous file is still being finalised can never share state with the next one.
 */
class CaptureEngine(
    private val config: CaptureConfig,
    private val sink: RecordingSink,
    /** Runs on the session thread before anything else, e.g. contact-name lookup. */
    private val enrich: (CallMetadata) -> CallMetadata = { it },
    private val profileFor: (CallMetadata) -> CaptureProfile = { CaptureProfile.forSource(it.source) },
) : AudioSource {

    private val lock = Any()
    private var session: Session? = null

    private val _recording = MutableStateFlow(false)

    /** True while a session is capturing or finalising. Observable by the UI. */
    val recording: StateFlow<Boolean> = _recording

    val isRecording: Boolean get() = synchronized(lock) { session != null }

    override fun onStreamStart(metadata: CallMetadata): Boolean = start(metadata, null)

    /**
     * Start capturing. Returns false if a session is already running (one recording at a time).
     * [onFinished] runs on the session thread once the file is closed and stored (or the attempt
     * failed); hosting services use it to leave the foreground.
     */
    fun start(metadata: CallMetadata, onFinished: (() -> Unit)?): Boolean {
        synchronized(lock) {
            if (session != null) {
                Log.i(TAG, "start ignored: a session is already running")
                return false
            }
            val s = Session(metadata, profileFor(metadata), onFinished)
            session = s
            _recording.value = true
            s.start()
            return true
        }
    }

    /** Ask the running session to stop. Non-blocking and idempotent. */
    override fun onStreamEnd() {
        synchronized(lock) { session }?.requestStop()
    }

    private enum class Outcome { SKIPPED, DONE, ABORTED }

    private inner class Session(
        private val initialMetadata: CallMetadata,
        private val profile: CaptureProfile,
        private val onFinished: (() -> Unit)?,
    ) : Thread("CaptureSession") {

        @Volatile private var stopRequested = false
        @Volatile private var activeRecord: AudioRecord? = null

        init {
            priority = MAX_PRIORITY
        }

        fun requestStop() {
            stopRequested = true
            // Unblock a read() that would otherwise wait on a source that stopped producing.
            activeRecord?.let { runCatching { it.stop() } }
        }

        override fun run() {
            try {
                capture(runCatching { enrich(initialMetadata) }.getOrDefault(initialMetadata))
            } catch (t: Throwable) {
                Log.e(TAG, "Session crashed", t)
                sink.onFailed(initialMetadata, null, "Capture crashed: ${t.javaClass.simpleName}: ${t.message}")
            } finally {
                synchronized(lock) {
                    if (session === this) session = null
                    _recording.value = session != null
                }
                runCatching { onFinished?.invoke() }
            }
        }

        private fun capture(metadata: CallMetadata) {
            val tried = ArrayList<String>()
            for ((i, source) in profile.cascade.withIndex()) {
                if (stopRequested) return
                val opened = AudioRecordOpener.open(source, config.channelCount)
                if (opened == null) {
                    tried += "$source: unavailable"
                    continue
                }
                activeRecord = opened.record
                val outcome = try {
                    record(metadata, opened, isLast = i == profile.cascade.lastIndex)
                } finally {
                    activeRecord = null
                    runCatching { opened.record.stop() }
                    opened.record.release()
                }
                when (outcome) {
                    Outcome.DONE, Outcome.ABORTED -> return
                    Outcome.SKIPPED -> tried += "$source: silent or failed to start"
                }
            }
            if (!stopRequested) {
                sink.onFailed(metadata, null, "No usable audio source (${tried.joinToString("; ")})")
            }
        }

        private fun record(metadata: CallMetadata, opened: OpenedRecord, isLast: Boolean): Outcome {
            val rec = opened.record
            try {
                rec.startRecording()
            } catch (t: Throwable) {
                Log.w(TAG, "startRecording failed for ${opened.source}", t)
                return Outcome.SKIPPED
            }
            if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) return Outcome.SKIPPED

            val buffer = ByteArray(opened.bufferBytes)

            // Start window: read and discard (ringing/handshake), checking for real samples.
            if (profile.startDelayMillis > 0) {
                val windowEnd = SystemClock.elapsedRealtime() + profile.startDelayMillis
                var sawSignal = false
                while (!stopRequested && SystemClock.elapsedRealtime() < windowEnd) {
                    val n = rec.read(buffer, 0, buffer.size)
                    if (n < 0) return Outcome.SKIPPED
                    if (!sawSignal && n > 0) sawSignal = hasNonZero(buffer, n)
                }
                if (stopRequested) {
                    Log.i(TAG, "Call ended during the start window; nothing recorded")
                    return Outcome.ABORTED
                }
                if (profile.skipDigitalSilence && !sawSignal && !isLast) {
                    Log.i(TAG, "${opened.source} delivered only digital silence; trying next source")
                    return Outcome.SKIPPED
                }
            }

            val target = sink.open(metadata) ?: run {
                sink.onFailed(metadata, null, "Could not create the output file")
                return Outcome.ABORTED
            }

            val encoder = OpusEncoder(target.fd, opened.sampleRateHz, opened.channelCount, config.bitrateBps)
            val waveform = WaveformAccumulator()
            var totalSamples = 0L
            var silentSamples = 0L
            var frames = 0L

            try {
                encoder.start()
                while (!stopRequested) {
                    val n = rec.read(buffer, 0, buffer.size)
                    if (n < 0) {
                        Log.w(TAG, "AudioRecord.read returned $n; ending session")
                        break
                    }
                    if (n == 0) continue
                    val stats = analyze(buffer, n)
                    waveform.add(stats.peak)
                    totalSamples += stats.samples
                    silentSamples += stats.silent
                    frames += stats.samples / opened.channelCount
                    encoder.encode(buffer, n)
                }
                encoder.finish()
            } catch (t: Throwable) {
                Log.e(TAG, "Encoding failed", t)
                runCatching { encoder.finish() }
                target.close()
                sink.onFailed(metadata, target, "Encoding failed: ${t.javaClass.simpleName}: ${t.message}")
                return Outcome.ABORTED
            }
            target.close()

            sink.onCompleted(
                CaptureResult(
                    metadata = metadata,
                    outputPath = target.path,
                    source = opened.source,
                    sampleRateHz = opened.sampleRateHz,
                    channelCount = opened.channelCount,
                    durationMillis = frames * 1000L / opened.sampleRateHz,
                    sizeBytes = 0L, // measured by the sink, which owns the file
                    waveform = waveform.toArray(),
                    silenceRatio = if (totalSamples > 0) silentSamples.toFloat() / totalSamples else 1f,
                )
            )
            return Outcome.DONE
        }
    }

    private class Stats(val peak: Float, val silent: Long, val samples: Long)

    /** Peak (0..1) and silence count for one 16-bit little-endian buffer. */
    private fun analyze(buffer: ByteArray, bytes: Int): Stats {
        var peak = 0
        var silent = 0L
        var count = 0L
        var i = 0
        while (i + 1 < bytes) {
            val sample = (buffer[i].toInt() and 0xff) or (buffer[i + 1].toInt() shl 8)
            val a = abs(sample.toShort().toInt())
            peak = max(peak, a)
            if (a < SILENCE_THRESHOLD) silent++
            count++
            i += 2
        }
        return Stats(peak / 32768f, silent, count)
    }

    private fun hasNonZero(buffer: ByteArray, bytes: Int): Boolean {
        for (i in 0 until bytes) if (buffer[i].toInt() != 0) return true
        return false
    }

    companion object {
        private const val TAG = "CaptureEngine"
        private const val SILENCE_THRESHOLD = 150 // ~ -46 dBFS
    }
}
