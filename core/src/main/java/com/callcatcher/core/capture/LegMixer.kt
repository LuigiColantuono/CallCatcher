package com.callcatcher.core.capture

import kotlin.math.sqrt

/**
 * Fixed-size PCM FIFO between the downlink reader thread and the session thread. When the reader
 * runs ahead the oldest samples are dropped; when it falls behind, [read] pads with silence. Both
 * legs come off the same audio clock, so drift over a call stays within a buffer or two.
 */
internal class ShortRing(capacity: Int) {
    private val data = ShortArray(capacity)
    private var head = 0 // next read
    private var size = 0
    private val lock = Object()

    fun write(src: ShortArray, count: Int) = synchronized(lock) {
        for (i in 0 until count) {
            val tail = (head + size) % data.size
            data[tail] = src[i]
            if (size < data.size) size++ else head = (head + 1) % data.size
        }
    }

    /** Fills [dst] with exactly [count] samples; returns how many were real (not padding). */
    fun read(dst: ShortArray, count: Int): Int = synchronized(lock) {
        val real = minOf(count, size)
        for (i in 0 until real) {
            dst[i] = data[head]
            head = (head + 1) % data.size
        }
        size -= real
        for (i in real until count) dst[i] = 0
        real
    }
}

/**
 * Slow automatic gain for one leg. It only adapts while there is speech-level signal, so it doesn't
 * pump noise up during pauses. This is what fixes "me loud, them faint": each side is brought to
 * the same loudness before mixing.
 */
internal class Agc(private val maxGain: Float) {
    var gain = 1f
        private set

    fun update(buf: ShortArray, count: Int) {
        if (count == 0) return
        var sum = 0.0
        for (i in 0 until count) {
            val s = buf[i].toDouble()
            sum += s * s
        }
        val rms = sqrt(sum / count).toFloat()
        if (rms < NOISE_FLOOR_RMS) return
        val desired = (TARGET_RMS / rms).coerceIn(MIN_GAIN, maxGain)
        // Fall fast (avoid clipping), rise slowly (avoid pumping).
        val rate = if (desired < gain) 0.3f else 0.05f
        gain += (desired - gain) * rate
    }

    private companion object {
        const val TARGET_RMS = 3_000f // about -21 dBFS
        const val NOISE_FLOOR_RMS = 250f
        const val MIN_GAIN = 0.5f
    }
}

/** Mixes the two legs to mono after levelling each one. */
internal class LegMixer {
    private val uplinkAgc = Agc(maxGain = 3f)
    private val downlinkAgc = Agc(maxGain = 10f)

    fun mix(uplink: ShortArray, downlink: ShortArray, count: Int, out: ShortArray) {
        uplinkAgc.update(uplink, count)
        downlinkAgc.update(downlink, count)
        val gu = uplinkAgc.gain * 0.8f
        val gd = downlinkAgc.gain * 0.8f
        for (i in 0 until count) {
            val s = uplink[i] * gu + downlink[i] * gd
            out[i] = s.coerceIn(-32768f, 32767f).toInt().toShort()
        }
    }
}
