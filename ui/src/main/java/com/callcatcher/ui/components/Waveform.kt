package com.callcatcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Peak-envelope waveform from the envelope stored at capture time. [progress] (0..1) tints the
 * played part; tapping calls [onSeek] with the tapped fraction.
 */
@Composable
fun Waveform(
    peaks: FloatArray,
    progress: Float,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
    barColor: Color = Color(0xFF2A2A2D),
    playedColor: Color = Color(0xFF5E9EFF),
) {
    val tap = if (onSeek == null) Modifier else Modifier.pointerInput(onSeek) {
        detectTapGestures { offset -> onSeek((offset.x / size.width).coerceIn(0f, 1f)) }
    }
    Canvas(modifier = modifier.fillMaxWidth().height(40.dp).then(tap)) {
        if (peaks.isEmpty()) return@Canvas
        val barWidth = size.width / peaks.size
        val gap = barWidth * 0.3f
        val playedUntil = size.width * progress.coerceIn(0f, 1f)
        peaks.forEachIndexed { i, peak ->
            val barHeight = max(size.height * peak.coerceIn(0f, 1f), 1.5f)
            val x = i * barWidth
            drawRect(
                color = if (x < playedUntil) playedColor else barColor,
                topLeft = Offset(x + gap / 2, (size.height - barHeight) / 2),
                size = Size((barWidth - gap).coerceAtLeast(1f), barHeight),
            )
        }
    }
}
