package com.callcatcher.core.capture

import android.media.MediaRecorder
import com.callcatcher.core.model.CallSource

/** System audio sources the engine can try. */
enum class CaptureSource(val androidValue: Int) {
    VOICE_CALL(MediaRecorder.AudioSource.VOICE_CALL),
    VOICE_COMMUNICATION(MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    VOICE_UPLINK(MediaRecorder.AudioSource.VOICE_UPLINK),
    VOICE_DOWNLINK(MediaRecorder.AudioSource.VOICE_DOWNLINK),
    MIC(MediaRecorder.AudioSource.MIC),
}

/**
 * How to capture one kind of call.
 *
 * @param cascade sources tried in order; the first that opens (and, if [skipDigitalSilence],
 *   delivers real samples) wins.
 * @param startDelayMillis audio read but not kept at the start (ringing/handshake). It doubles
 *   as the probe window for [skipDigitalSilence].
 * @param skipDigitalSilence if the probe window is exact zeros, the source is treated as dead and
 *   the next one is tried. This is how a VOICE_CALL source that opens fine during a VoIP call but
 *   carries nothing gets skipped, instead of producing a silent file.
 */
data class CaptureProfile(
    val cascade: List<CaptureSource>,
    val startDelayMillis: Long,
    val skipDigitalSilence: Boolean,
) {
    companion object {
        /**
         * Carrier calls. VOICE_CALL is confirmed working on the target device (BCR logs, 16 kHz
         * mono, both sides). No delay: the trigger fires when the call is already ACTIVE.
         */
        val PHONE = CaptureProfile(
            cascade = listOf(CaptureSource.VOICE_CALL, CaptureSource.VOICE_COMMUNICATION, CaptureSource.MIC),
            startDelayMillis = 0,
            skipDigitalSilence = false,
        )

        /**
         * VoIP calls (WhatsApp). WhatsApp audio never goes through the modem, so whether VOICE_CALL
         * carries it is device-specific; that is exactly what this profile tests. It is tried first
         * (we hold CAPTURE_AUDIO_OUTPUT as a system app, which WAE inside WhatsApp never does), and
         * skipped automatically if it is digitally silent. The 2 s window matches WAE's delayed
         * start after the sound port is created.
         */
        val VOIP = CaptureProfile(
            cascade = listOf(CaptureSource.VOICE_CALL, CaptureSource.VOICE_COMMUNICATION, CaptureSource.MIC),
            startDelayMillis = 2_000,
            skipDigitalSilence = true,
        )

        /** Manual test from the app: microphone only, to verify encoding and playback. */
        val TEST = CaptureProfile(
            cascade = listOf(CaptureSource.MIC),
            startDelayMillis = 0,
            skipDigitalSilence = false,
        )

        fun forSource(source: CallSource): CaptureProfile = when (source) {
            CallSource.PHONE -> PHONE
            CallSource.WHATSAPP -> VOIP
            CallSource.TEST -> TEST
            CallSource.UNKNOWN -> VOIP
        }
    }
}
