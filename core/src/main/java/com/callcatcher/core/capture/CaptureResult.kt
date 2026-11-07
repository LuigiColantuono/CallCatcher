package com.callcatcher.core.capture

import com.callcatcher.core.model.CallMetadata

/** Outcome of a finished capture, handed to whoever persists recordings. */
data class CaptureResult(
    val metadata: CallMetadata,
    /** Absolute path of the closed file. */
    val outputPath: String,
    /** [CaptureStrategy.label] of the strategy that produced the file. */
    val sourceLabel: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val durationMillis: Long,
    /** Downsampled 0..1 peak envelope for the waveform. */
    val waveform: FloatArray,
    /** Fraction of the stream below the silence threshold (0..1). Near 1 means a dead source. */
    val silenceRatio: Float,
    /** Why earlier strategies were skipped, e.g. "VOICE_CALL: digital silence". */
    val notes: List<String>,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** What a running session is doing, for the live recording display. */
data class LiveRecording(
    val metadata: CallMetadata,
    val startedAtMillis: Long,
    /** Null while the session is still in its start window choosing a strategy. */
    val sourceLabel: String?,
    val sampleRateHz: Int,
)
