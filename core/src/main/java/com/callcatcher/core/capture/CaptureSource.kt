package com.callcatcher.core.capture

import android.media.MediaRecorder
import com.callcatcher.core.model.CallSource

/** System audio sources the engine can open with [AudioRecordOpener]. */
enum class CaptureSource(val androidValue: Int) {
    VOICE_CALL(MediaRecorder.AudioSource.VOICE_CALL),
    VOICE_COMMUNICATION(MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    VOICE_UPLINK(MediaRecorder.AudioSource.VOICE_UPLINK),
    VOICE_DOWNLINK(MediaRecorder.AudioSource.VOICE_DOWNLINK),
    MIC(MediaRecorder.AudioSource.MIC),
}

/** One way of getting call audio. A [CaptureProfile] tries them in order. */
sealed interface CaptureStrategy {
    /** Source for the device owner's side (or both sides, for a [Single] voice source). */
    val uplink: CaptureSource

    /** Stored with the recording and shown in the UI, e.g. "VOICE_CALL" or "MIC+PLAYBACK". */
    val label: String

    /**
     * A single source. With [skipIfSilent], digital silence during the start window means the
     * source is dead (typical for VOICE_CALL during a VoIP call) and the next strategy is tried.
     */
    data class Single(override val uplink: CaptureSource, val skipIfSilent: Boolean = false) : CaptureStrategy {
        override val label: String get() = uplink.name
    }

    /**
     * VoIP calls: the other party's voice exists only as audio the calling app *plays*, never in a
     * microphone source. So: your side from [uplink], their side from playback capture of
     * voice-communication audio ([PlaybackCapture]), each auto-levelled, mixed to mono.
     */
    data class TwoLeg(override val uplink: CaptureSource = CaptureSource.MIC) : CaptureStrategy {
        override val label: String get() = "${uplink.name}+PLAYBACK"
    }
}

/**
 * @param startDelayMillis audio read but not kept at the start (ringing/handshake); also the probe
 *   window for [CaptureStrategy.Single.skipIfSilent].
 */
data class CaptureProfile(
    val strategies: List<CaptureStrategy>,
    val startDelayMillis: Long,
) {
    companion object {
        /** Carrier calls: VOICE_CALL carries both sides (confirmed on the target device). */
        val PHONE = CaptureProfile(
            strategies = listOf(
                CaptureStrategy.Single(CaptureSource.VOICE_CALL),
                CaptureStrategy.Single(CaptureSource.VOICE_COMMUNICATION),
                CaptureStrategy.Single(CaptureSource.MIC),
            ),
            startDelayMillis = 0,
        )

        /**
         * WhatsApp. VOICE_CALL first in case this device mixes VoIP into it (skipped if silent);
         * then the two-leg capture; plain MIC only as the last resort. VOICE_COMMUNICATION is not
         * tried: while WhatsApp itself records with it, Android hands other apps silence.
         */
        val VOIP = CaptureProfile(
            strategies = listOf(
                CaptureStrategy.Single(CaptureSource.VOICE_CALL, skipIfSilent = true),
                CaptureStrategy.TwoLeg(CaptureSource.MIC),
                CaptureStrategy.Single(CaptureSource.MIC),
            ),
            startDelayMillis = 1_500,
        )

        /** Manual test: microphone only, to verify encoding and playback. */
        val TEST = CaptureProfile(
            strategies = listOf(CaptureStrategy.Single(CaptureSource.MIC)),
            startDelayMillis = 0,
        )

        fun forSource(source: CallSource): CaptureProfile = when (source) {
            CallSource.PHONE -> PHONE
            CallSource.WHATSAPP -> VOIP
            CallSource.TEST -> TEST
            CallSource.UNKNOWN -> VOIP
        }
    }
}
