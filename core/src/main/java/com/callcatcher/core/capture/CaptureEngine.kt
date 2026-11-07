package com.callcatcher.core.capture

import android.media.AudioRecord
import android.os.SystemClock
import android.util.Log
import com.callcatcher.core.model.CallMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max

/**
 * The shared capture engine. Every trigger (phone InCallService, WhatsApp hook, test button) ends
 * up here, in the app process, which is the only process holding the privileged capture permissions.
 *
 * One [Session] per call, each with its own threads and stop flag, so a call that ends while the
 * previous file is still being finalised can never share state with the next one.
 */
class CaptureEngine(
    private val config: CaptureConfig,
    private val sink: RecordingSink,
    /** Runs on the session thread before anything else, e.g. contact-name lookup. */
    private val enrich: (CallMetadata) -> CallMetadata = { it },
    /** Opens playback capture at a sample rate, for [CaptureStrategy.TwoLeg]. Throws on refusal. */
    private val playbackCapture: ((Int) -> PlaybackCaptureHandle)? = null,
    private val profileFor: (CallMetadata) -> CaptureProfile = { CaptureProfile.forSource(it.source) },
) : AudioSource {

    private val lock = Any()
    private var session: Session? = null

    private val _recording = MutableStateFlow(false)
    private val _live = MutableStateFlow<LiveRecording?>(null)
    private val _level = MutableStateFlow(0f)

    /** True while a session is capturing or finalising. */
    val recording: StateFlow<Boolean> = _recording

    /** The running session, for the live display; null when idle. */
    val live: StateFlow<LiveRecording?> = _live

    /** Peak level (0..1) of the latest buffer while recording. */
    val level: StateFlow<Float> = _level

    val isRecording: Boolean get() = synchronized(lock) { session != null }

    override fun onStreamStart(metadata: CallMetadata): Boolean = start(metadata, null)

    /**
     * Start capturing. Returns false if a session is already running (one recording at a time).
     * [onFinished] runs on the session thread once the file is stored (or the attempt failed);
     * hosting services use it to leave the foreground.
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
            _live.value = LiveRecording(metadata, System.currentTimeMillis(), null, 0)
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
        @Volatile private var activeRecords: List<AudioRecord> = emptyList()
        private val notes = ArrayList<String>()
        private val startedAt = System.currentTimeMillis()

        init {
            priority = MAX_PRIORITY
        }

        fun requestStop() {
            stopRequested = true
            // Unblock read() calls on sources that stopped producing.
            activeRecords.forEach { runCatching { it.stop() } }
        }

        override fun run() {
            try {
                capture(runCatching { enrich(initialMetadata) }.getOrDefault(initialMetadata))
            } catch (t: Throwable) {
                Log.e(TAG, "Session crashed", t)
                sink.onFailed(initialMetadata, null, "Capture crashed: ${describe(t)}")
            } finally {
                synchronized(lock) {
                    if (session === this) session = null
                    _recording.value = session != null
                    if (session == null) {
                        _live.value = null
                        _level.value = 0f
                    }
                }
                runCatching { onFinished?.invoke() }
            }
        }

        private fun capture(metadata: CallMetadata) {
            _live.value = LiveRecording(metadata, startedAt, null, 0)
            for ((i, strategy) in profile.strategies.withIndex()) {
                if (stopRequested) return
                val outcome = runStrategy(metadata, strategy, isLast = i == profile.strategies.lastIndex)
                if (outcome != Outcome.SKIPPED) return
            }
            if (!stopRequested) {
                sink.onFailed(metadata, null, "No usable audio source (${notes.joinToString("; ")})")
            }
        }

        private fun runStrategy(metadata: CallMetadata, strategy: CaptureStrategy, isLast: Boolean): Outcome {
            val uplink = AudioRecordOpener.open(strategy.uplink, config.channelCount) ?: run {
                notes += "${strategy.label}: ${strategy.uplink} unavailable"
                return Outcome.SKIPPED
            }
            var downlink: PlaybackCaptureHandle? = null
            try {
                if (strategy is CaptureStrategy.TwoLeg) {
                    val factory = playbackCapture ?: run {
                        notes += "${strategy.label}: playback capture not configured"
                        return Outcome.SKIPPED
                    }
                    downlink = try {
                        factory(uplink.sampleRateHz)
                    } catch (t: Throwable) {
                        Log.w(TAG, "Playback capture refused", t)
                        notes += "${strategy.label}: playback capture refused (${describe(t)})"
                        return Outcome.SKIPPED
                    }
                }
                activeRecords = listOfNotNull(uplink.record, downlink?.record)
                return record(metadata, strategy, uplink, downlink, isLast)
            } finally {
                activeRecords = emptyList()
                runCatching { uplink.record.stop() }
                uplink.record.release()
                downlink?.close()
            }
        }

        private fun record(
            metadata: CallMetadata,
            strategy: CaptureStrategy,
            uplink: OpenedRecord,
            downlink: PlaybackCaptureHandle?,
            isLast: Boolean,
        ): Outcome {
            val up = uplink.record
            try {
                up.startRecording()
                downlink?.record?.startRecording()
            } catch (t: Throwable) {
                notes += "${strategy.label}: start failed (${describe(t)})"
                return Outcome.SKIPPED
            }
            if (up.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                notes += "${strategy.label}: did not start"
                return Outcome.SKIPPED
            }

            val frames = uplink.bufferBytes / 2
            val upBuf = ShortArray(frames)
            val downBuf = ShortArray(frames)
            val mixBuf = ShortArray(frames)
            val bytes = ByteArray(frames * 2)

            val ring = downlink?.let { ShortRing(uplink.sampleRateHz * 2) }
            val readerDone = AtomicBoolean(false)
            val reader = if (downlink != null && ring != null) {
                Thread({
                    val buf = ShortArray(frames)
                    while (!stopRequested && !readerDone.get()) {
                        val n = downlink.record.read(buf, 0, buf.size)
                        if (n < 0) break
                        if (n > 0) ring.write(buf, n)
                    }
                }, "CaptureDownlink").apply { priority = MAX_PRIORITY; start() }
            } else {
                null
            }

            try {
                // Start window: discard (ringing/handshake) and check which legs carry real audio.
                if (profile.startDelayMillis > 0) {
                    val windowEnd = SystemClock.elapsedRealtime() + profile.startDelayMillis
                    var upSignal = false
                    var downSignal = false
                    while (!stopRequested && SystemClock.elapsedRealtime() < windowEnd) {
                        val n = up.read(upBuf, 0, upBuf.size)
                        if (n < 0) {
                            notes += "${strategy.label}: read error $n"
                            return Outcome.SKIPPED
                        }
                        if (n == 0) continue
                        if (!upSignal) upSignal = hasNonZero(upBuf, n)
                        if (ring != null && ring.read(downBuf, n) > 0 && !downSignal) downSignal = hasNonZero(downBuf, n)
                    }
                    if (stopRequested) return Outcome.ABORTED
                    if (strategy is CaptureStrategy.Single && strategy.skipIfSilent && !upSignal && !isLast) {
                        notes += "${strategy.label}: digital silence"
                        Log.i(TAG, "${strategy.label} delivered only digital silence; trying next strategy")
                        return Outcome.SKIPPED
                    }
                    if (ring != null && !downSignal) {
                        notes += "PLAYBACK: silent in the first ${profile.startDelayMillis} ms"
                    }
                }

                _live.value = LiveRecording(metadata, startedAt, strategy.label, uplink.sampleRateHz)

                val target = sink.open(metadata) ?: run {
                    sink.onFailed(metadata, null, "Could not create the output file")
                    return Outcome.ABORTED
                }

                val encoder = OpusEncoder(target.fd, uplink.sampleRateHz, uplink.channelCount, config.bitrateBps)
                val mixer = LegMixer()
                val waveform = WaveformAccumulator()
                var totalSamples = 0L
                var silentSamples = 0L
                var keptFrames = 0L

                try {
                    encoder.start()
                    while (!stopRequested) {
                        val n = up.read(upBuf, 0, upBuf.size)
                        if (n < 0) {
                            Log.w(TAG, "AudioRecord.read returned $n; ending session")
                            break
                        }
                        if (n == 0) continue
                        val out = if (ring != null) {
                            ring.read(downBuf, n)
                            mixer.mix(upBuf, downBuf, n, mixBuf)
                            mixBuf
                        } else {
                            upBuf
                        }
                        val stats = analyze(out, n)
                        waveform.add(stats.peak)
                        _level.value = stats.peak
                        totalSamples += n
                        silentSamples += stats.silent
                        keptFrames += n / uplink.channelCount
                        toLittleEndian(out, n, bytes)
                        encoder.encode(bytes, n * 2)
                    }
                    encoder.finish()
                } catch (t: Throwable) {
                    Log.e(TAG, "Encoding failed", t)
                    runCatching { encoder.finish() }
                    target.close()
                    sink.onFailed(metadata, target, "Encoding failed: ${describe(t)}")
                    return Outcome.ABORTED
                }
                target.close()

                sink.onCompleted(
                    CaptureResult(
                        metadata = metadata,
                        outputPath = target.path,
                        sourceLabel = strategy.label,
                        sampleRateHz = uplink.sampleRateHz,
                        channelCount = uplink.channelCount,
                        durationMillis = keptFrames * 1000L / uplink.sampleRateHz,
                        waveform = waveform.toArray(),
                        silenceRatio = if (totalSamples > 0) silentSamples.toFloat() / totalSamples else 1f,
                        notes = notes.toList(),
                    )
                )
                return Outcome.DONE
            } finally {
                readerDone.set(true)
                downlink?.record?.let { runCatching { it.stop() } }
                reader?.join(READER_JOIN_MS)
            }
        }
    }

    private class Stats(val peak: Float, val silent: Long)

    private fun analyze(buf: ShortArray, count: Int): Stats {
        var peak = 0
        var silent = 0L
        for (i in 0 until count) {
            val a = abs(buf[i].toInt())
            peak = max(peak, a)
            if (a < SILENCE_THRESHOLD) silent++
        }
        return Stats(peak / 32768f, silent)
    }

    private fun hasNonZero(buf: ShortArray, count: Int): Boolean {
        for (i in 0 until count) if (buf[i].toInt() != 0) return true
        return false
    }

    private fun toLittleEndian(src: ShortArray, count: Int, dst: ByteArray) {
        for (i in 0 until count) {
            val s = src[i].toInt()
            dst[2 * i] = (s and 0xff).toByte()
            dst[2 * i + 1] = (s shr 8 and 0xff).toByte()
        }
    }

    private fun describe(t: Throwable): String {
        val root = (t as? InvocationTargetException)?.targetException ?: t
        return "${root.javaClass.simpleName}: ${root.message}"
    }

    companion object {
        private const val TAG = "CaptureEngine"
        private const val SILENCE_THRESHOLD = 150 // ~ -46 dBFS
        private const val READER_JOIN_MS = 1_000L
    }
}
