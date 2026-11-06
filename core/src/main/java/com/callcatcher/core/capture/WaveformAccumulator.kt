package com.callcatcher.core.capture

/**
 * Collects a peak per read buffer, then downsamples to a fixed-length envelope for the player.
 * Keeping it bounded means a two-hour call and a ten-second one both store the same small array.
 */
internal class WaveformAccumulator(private val targetBuckets: Int = 480) {

    private val peaks = ArrayList<Float>(4096)

    fun add(peak: Float) {
        peaks.add(peak)
    }

    fun toArray(): FloatArray {
        if (peaks.isEmpty()) return FloatArray(0)
        if (peaks.size <= targetBuckets) return peaks.toFloatArray()

        val out = FloatArray(targetBuckets)
        val per = peaks.size.toFloat() / targetBuckets
        for (b in 0 until targetBuckets) {
            val start = (b * per).toInt()
            val end = ((b + 1) * per).toInt().coerceAtMost(peaks.size)
            var m = 0f
            for (i in start until end) if (peaks[i] > m) m = peaks[i]
            out[b] = m
        }
        return out
    }
}
