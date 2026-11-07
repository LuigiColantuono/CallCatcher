package com.callcatcher.core.capture

/**
 * Encoder settings. The source decides the sample rate (see [AudioRecordOpener]): carrier audio
 * is ~16 kHz mono, so the network, not the encoder, sets the quality ceiling. Opus at 32 kbps is
 * transparent for voice at that rate.
 */
data class CaptureConfig(
    val channelCount: Int = 1,
    val bitrateBps: Int = 32_000,
)
