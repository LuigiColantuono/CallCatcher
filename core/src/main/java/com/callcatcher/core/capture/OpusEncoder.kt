package com.callcatcher.core.capture

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.FileDescriptor
import java.nio.ByteBuffer

/**
 * Wraps a MediaCodec Opus encoder feeding a MediaMuxer Ogg container.
 *
 * We deliberately do not hand-write Ogg pages (BCR does, but it has reasons we don't): since
 * API 29 MediaMuxer can mux Opus into OGG, which is well-tested and keeps this class small.
 *
 * Usage: [start] once, [encode] PCM chunks as they arrive, [finish] exactly once. Not thread-safe;
 * the engine drives it from a single recording thread.
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
    private val bufferInfo = MediaCodec.BufferInfo()
    private var presentationUs = 0L

    fun start() {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_OPUS, sampleRateHz, channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitrateBps)
            setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
        }
        codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_OPUS).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
        muxer = MediaMuxer(fd, MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG)
    }

    /** Feed one PCM buffer (16-bit LE). [bytesRead] may be less than [pcm] capacity. */
    fun encode(pcm: ByteArray, bytesRead: Int) {
        drainInput(pcm, bytesRead, endOfStream = false)
        drainOutput(endOfStream = false)
    }

    fun finish() {
        try {
            drainInput(ByteArray(0), 0, endOfStream = true)
            drainOutput(endOfStream = true)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
            if (muxerStarted) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    private fun drainInput(pcm: ByteArray, bytesRead: Int, endOfStream: Boolean) {
        val index = codec.dequeueInputBuffer(TIMEOUT_US)
        if (index < 0) return
        val input: ByteBuffer = codec.getInputBuffer(index) ?: return
        input.clear()
        if (bytesRead > 0) input.put(pcm, 0, bytesRead)

        if (endOfStream) {
            codec.queueInputBuffer(index, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } else {
            codec.queueInputBuffer(index, 0, bytesRead, presentationUs, 0)
            // 16-bit samples: bytes / 2 / channels = frames; advance PTS by their duration.
            val frames = bytesRead / 2 / channelCount
            presentationUs += frames * 1_000_000L / sampleRateHz
        }
    }

    private fun drainOutput(endOfStream: Boolean) {
        while (true) {
            val index = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    if (muxerStarted) error("Encoder format changed twice")
                    trackIndex = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!endOfStream) return // wait for more input
                    // on EOS keep polling until the flag arrives
                }
                index >= 0 -> {
                    val encoded = codec.getOutputBuffer(index)
                    if (encoded != null && bufferInfo.size > 0 && muxerStarted &&
                        bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        encoded.position(bufferInfo.offset)
                        encoded.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                    }
                    codec.releaseOutputBuffer(index, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    companion object {
        private const val TAG = "OpusEncoder"
        private const val TIMEOUT_US = 10_000L
    }
}
