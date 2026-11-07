package com.callcatcher.core.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.SystemClock
import java.io.FileDescriptor
import kotlin.math.min

/**
 * MediaCodec Opus encoder feeding a MediaMuxer Ogg container (API 29+).
 *
 * [encode] never drops PCM: it keeps queueing until the whole chunk is in the codec, splitting it
 * across input buffers when the chunk is larger than one buffer. Single-threaded by design: the
 * engine drives it from the session thread only.
 */
internal class OpusEncoder(
    private val fd: FileDescriptor,
    private val sampleRateHz: Int,
    private val channelCount: Int,
    private val bitrateBps: Int,
) {
    private lateinit var codec: MediaCodec
    private lateinit var muxer: MediaMuxer
    private var trackIndex = -1
    private var muxerStarted = false
    private var samplesWritten = 0L
    private val bufferInfo = MediaCodec.BufferInfo()

    /** Frames queued so far; drives presentation timestamps. */
    private var framesQueued = 0L

    fun start() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRateHz, channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
    }

    /** Queue [length] bytes of 16-bit PCM. Blocks briefly if the codec is busy; never drops data. */
    fun encode(pcm: ByteArray, length: Int) {
        var offset = 0
        while (offset < length) {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index >= 0) {
                val input = codec.getInputBuffer(index) ?: error("Null input buffer $index")
                input.clear()
                // Keep whole frames so timestamps stay exact.
                val frameBytes = 2 * channelCount
                val room = input.remaining() - input.remaining() % frameBytes
                val count = min(room, length - offset)
                input.put(pcm, offset, count)
                codec.queueInputBuffer(index, 0, count, ptsUs(), 0)
                framesQueued += count / frameBytes
                offset += count
            }
            drainOutput(endOfStream = false)
        }
    }

    /** Flush, finalise the container and release everything. Safe to call once. */
    fun finish() {
        try {
            val deadline = SystemClock.elapsedRealtime() + EOS_TIMEOUT_MS
            while (SystemClock.elapsedRealtime() < deadline) {
                val index = codec.dequeueInputBuffer(TIMEOUT_US)
                if (index >= 0) {
                    codec.queueInputBuffer(index, 0, 0, ptsUs(), MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    break
                }
                drainOutput(endOfStream = false)
            }
            drainOutput(endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted && samplesWritten > 0) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun ptsUs(): Long = framesQueued * 1_000_000L / sampleRateHz

    private fun drainOutput(endOfStream: Boolean) {
        val deadline = SystemClock.elapsedRealtime() + EOS_TIMEOUT_MS
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    check(!muxerStarted) { "Encoder output format changed twice" }
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream || SystemClock.elapsedRealtime() > deadline) return
                }
                index >= 0 -> {
                    val isConfig = bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && bufferInfo.size > 0 && muxerStarted) {
                        val encoded = codec.getOutputBuffer(index)
                        if (encoded != null) {
                            encoded.position(bufferInfo.offset)
                            encoded.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                            samplesWritten++
                        }
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
                else -> Unit // deprecated INFO_OUTPUT_BUFFERS_CHANGED: nothing to do
            }
        }
    }

    companion object {
        private const val TIMEOUT_US = 10_000L
        private const val EOS_TIMEOUT_MS = 2_000L
    }
}
