package com.callcatcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Static peak-envelope waveform. [progress] (0..1) tints the played portion with [playedColor].
 * Purely presentational — it reads the precomputed envelope stored at capture time.
 */
@Composable
fun Waveform(
    peaks: FloatArray,
    progress: Float,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF2A2A2D),
    playedColor: Color = Color(0xFF5E9EFF),
) {
    Canvas(modifier = modifier.fillMaxWidth().height(36.dp)) {
        if (peaks.isEmpty()) return@Canvas
        val barWidth = size.width / peaks.size
        val gap = barWidth * 0.3f
        val playedUntil = size.width * progress.coerceIn(0f, 1f)
        peaks.forEachIndexed { i, peak ->
            val barHeight = max(size.height * peak, 1.5f)
            val x = i * barWidth
            drawLineBar(
                x = x + gap / 2,
                width = barWidth - gap,
                height = barHeight,
                fullHeight = size.height,
                color = if (x < playedUntil) playedColor else barColor,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLineBar(
    x: Float,
    width: Float,
    height: Float,
    fullHeight: Float,
    color: Color,
) {
    val top = (fullHeight - height) / 2
    drawRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(x, top),
        size = androidx.compose.ui.geometry.Size(width.coerceAtLeast(1f), height),
    )
}
