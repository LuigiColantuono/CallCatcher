package com.callcatcher.core.capture

/**
 * Tunables for the capture engine. Defaults reflect what the pre-flight test showed:
 * a carrier call delivers ~16 kHz mono, so there is no quality to gain from forcing 48 kHz or a
 * fat AAC bitrate — the network, not the encoder, is the ceiling. We therefore default to Opus at
 * the source's native rate and a modest bitrate that is transparent for voice.
 *
 * [sampleRateHz] = 0 means "ask the source for its native rate" (recommended). A fixed value
 * forces resampling expectations and is only useful for debugging.
 */
data class CaptureConfig(
    val sampleRateHz: Int = NATIVE_SAMPLE_RATE,
    val channelCount: Int = 1,
    /** Opus bitrate. 32 kbps is already clean for 16 kHz voice; raise for music-grade sources. */
    val bitrateBps: Int = 32_000,
    /**
     * Delay before the first PCM frame is kept, to skip ringing/handshake.
     * BCR-style ~1s is enough on the modem path; VoIP apps may want more.
     */
    val startDelayMillis: Long = 1_000,
) {
    companion object {
        const val NATIVE_SAMPLE_RATE = 0
    }
}
