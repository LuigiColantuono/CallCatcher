package com.callcatcher.core.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log

/** A successfully opened record session plus the source that won the cascade. */
internal class OpenedRecord(
    val record: AudioRecord,
    val source: CaptureSource,
    val sampleRateHz: Int,
    val channelCount: Int,
    val minBufferBytes: Int,
)

/**
 * Opens an [AudioRecord] by walking a source cascade and keeping the first that initialises.
 * Requires RECORD_AUDIO (and, for the voice sources, CAPTURE_AUDIO_OUTPUT granted via root).
 */
internal object AudioRecordOpener {

    private const val TAG = "AudioRecordOpener"

    @SuppressLint("MissingPermission")
    fun open(cascade: List<CaptureSource>, config: CaptureConfig): OpenedRecord? {
        val channelMask = if (config.channelCount == 1) {
            AudioFormat.CHANNEL_IN_MONO
        } else {
            AudioFormat.CHANNEL_IN_STEREO
        }

        for (source in cascade) {
            val sampleRate = if (config.sampleRateHz == CaptureConfig.NATIVE_SAMPLE_RATE) {
                nativeRateFor(source)
            } else {
                config.sampleRateHz
            }

            val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) {
                Log.w(TAG, "getMinBufferSize failed for $source @ $sampleRate")
                continue
            }
            // Oversize the buffer so brief scheduler stalls don't drop frames.
            val bufferBytes = minBuffer * 4

            val record = try {
                AudioRecord(source.androidValue, sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
            } catch (t: Throwable) {
                Log.w(TAG, "AudioRecord ctor threw for $source", t)
                null
            }

            if (record == null) continue
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "Source $source did not initialise (state=${record.state})")
                record.release()
                continue
            }

            Log.i(TAG, "Opened source $source @ ${sampleRate}Hz, ${config.channelCount}ch")
            return OpenedRecord(record, source, sampleRate, config.channelCount, bufferBytes)
        }

        Log.e(TAG, "No capture source in the cascade could be opened")
        return null
    }

    /**
     * Carrier voice paths are narrow/wideband (8–16 kHz); MIC can do far more. We request a rate
     * that matches the likely content so we don't bloat files with empty high-frequency bins.
     */
    private fun nativeRateFor(source: CaptureSource): Int = when (source) {
        CaptureSource.VOICE_CALL,
        CaptureSource.VOICE_UPLINK,
        CaptureSource.VOICE_DOWNLINK -> 16_000
        CaptureSource.VOICE_COMMUNICATION -> 16_000
        CaptureSource.MIC -> 48_000
    }
}
