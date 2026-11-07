package com.callcatcher.core.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.util.Log

/** An initialised record session for one source. */
internal class OpenedRecord(
    val record: AudioRecord,
    val source: CaptureSource,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bufferBytes: Int,
)

/**
 * Opens an [AudioRecord] for a single source. The cascade itself lives in [CaptureEngine], because
 * deciding whether a source is usable can require reading from it (digital-silence probe).
 *
 * Voice sources need CAPTURE_AUDIO_OUTPUT, which the app only holds when installed as a privileged
 * system app (KernelSU/Magisk module). Without it they fail to initialise and the cascade moves on.
 */
internal object AudioRecordOpener {

    private const val TAG = "AudioRecordOpener"

    @SuppressLint("MissingPermission")
    fun open(source: CaptureSource, channelCount: Int): OpenedRecord? {
        val channelMask = if (channelCount == 1) AudioFormat.CHANNEL_IN_MONO else AudioFormat.CHANNEL_IN_STEREO
        val sampleRate = nativeRateFor(source)

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            Log.w(TAG, "getMinBufferSize failed for $source @ $sampleRate Hz ($minBuffer)")
            return null
        }
        // Oversize so a brief scheduler stall doesn't overrun the record buffer.
        val bufferBytes = minBuffer * 4

        val record = try {
            AudioRecord(source.androidValue, sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT, bufferBytes)
        } catch (t: Throwable) {
            Log.w(TAG, "AudioRecord constructor threw for $source", t)
            return null
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.w(TAG, "Source $source did not initialise (state=${record.state})")
            record.release()
            return null
        }

        Log.i(TAG, "Opened $source @ $sampleRate Hz, ${channelCount}ch, buffer=$bufferBytes B")
        return OpenedRecord(record, source, sampleRate, channelCount, bufferBytes)
    }

    /**
     * Voice paths are wideband (16 kHz); asking for more only adds empty spectrum. The microphone
     * can genuinely do 48 kHz. Both are native Opus rates, so no resampling is needed.
     */
    private fun nativeRateFor(source: CaptureSource): Int = when (source) {
        CaptureSource.VOICE_CALL,
        CaptureSource.VOICE_UPLINK,
        CaptureSource.VOICE_DOWNLINK,
        CaptureSource.VOICE_COMMUNICATION -> 16_000
        CaptureSource.MIC -> 48_000
    }
}
