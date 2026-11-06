package com.callcatcher.core.capture

import android.media.MediaRecorder

/**
 * Candidate system audio sources, in the cascade order the engine tries them.
 * The first one that opens and yields non-silent audio wins.
 *
 * Ordering rationale (confirmed on the target device via BCR logs — VOICE_CALL = source 4 works
 * for cellular calls): the voice-path sources carry both legs of a carrier call, so they come
 * first. MIC is the last resort and, for app calls like WhatsApp, usually the only one the OS
 * allows — which is exactly why WhatsApp recordings are one-sided and need the two-stream path
 * (see [TwoLegCaptureStrategy]) rather than this cascade.
 */
enum class CaptureSource(val androidValue: Int) {
    VOICE_CALL(MediaRecorder.AudioSource.VOICE_CALL),
    VOICE_COMMUNICATION(MediaRecorder.AudioSource.VOICE_COMMUNICATION),
    VOICE_UPLINK(MediaRecorder.AudioSource.VOICE_UPLINK),
    VOICE_DOWNLINK(MediaRecorder.AudioSource.VOICE_DOWNLINK),
    MIC(MediaRecorder.AudioSource.MIC);

    companion object {
        /** Default cascade for carrier phone calls. */
        val PHONE_CASCADE = listOf(VOICE_CALL, VOICE_COMMUNICATION, MIC)

        /**
         * WhatsApp (and other VoIP apps) never reach the modem voice path, so VOICE_CALL returns
         * silence. The realistic single-source fallback is VOICE_COMMUNICATION (same source the
         * app itself records from). For proper two-sided audio the trigger should prefer the
         * two-leg strategy instead of this cascade.
         */
        val VOIP_CASCADE = listOf(VOICE_COMMUNICATION, MIC)
    }
}

/**
 * Marker for the planned WhatsApp capture that records the two legs separately and mixes them:
 *  - uplink  (you)          -> VOICE_COMMUNICATION microphone path, already echo-cancelled
 *  - downlink (other party) -> playback capture of what WhatsApp renders
 *
 * Implemented in :capture-wa once the per-device playback-capture test passes; declared here so
 * the engine and storage layer already have a stable name to depend on.
 */
interface TwoLegCaptureStrategy
