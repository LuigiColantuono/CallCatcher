package com.callcatcher.core.capture

import com.callcatcher.core.model.CallMetadata

/** Outcome of a finished capture, handed to whoever persists recordings. */
data class CaptureResult(
    val metadata: CallMetadata,
    /** Logical path the audio was written to. */
    val outputPath: String,
    val source: CaptureSource,
    val sampleRateHz: Int,
    val channelCount: Int,
    val durationMillis: Long,
    val sizeBytes: Long,
    /** Downsampled 0..1 peak envelope for the player's waveform. May be empty on failure. */
    val waveform: FloatArray,
    /** Fraction of the stream that was silent (0..1). A value near 1 means a dead source. */
    val silenceRatio: Float,
) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
