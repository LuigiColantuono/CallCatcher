package com.callcatcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callcatcher.core.capture.LiveRecording
import com.callcatcher.player.PlaybackState
import com.callcatcher.ui.model.RecordingUi
import com.callcatcher.ui.theme.CC
import com.callcatcher.ui.util.clockParts
import com.callcatcher.ui.util.formatDuration
import com.callcatcher.ui.util.formatSize
import kotlinx.coroutines.delay
import kotlin.math.max

private val DeckShape = RoundedCornerShape(28.dp)
private val PanelShape = RoundedCornerShape(20.dp)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/**
 * Player for the selected recording: readout with a close button, seekable waveform (the only
 * navigation: tap or drag), and play / speed / share / delete.
 */
@Composable
fun PlaybackDeck(
    recording: RecordingUi,
    playback: PlaybackState,
    onSeek: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onCycleSpeed: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = if (playback.durationMillis > 0) playback.durationMillis else recording.durationMillis

    DeckCard(modifier) {
        DisplayPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PanelChip(
                    text = listOfNotNull(recording.source.label(), recording.direction.label()).joinToString(" · "),
                )
                Spacer(Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(CC.DisplayInk.copy(alpha = 0.08f))
                        .clickable(onClick = onClose),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close player", tint = CC.DisplayInk, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                recording.title,
                style = MaterialTheme.typography.titleMedium,
                color = CC.DisplayInk,
                maxLines = 1,
            )
            BigClock(playback.positionMillis, color = CC.DisplayInk)
            Spacer(Modifier.height(10.dp))
            PanelFooter(
                left = "${recording.sampleRateHz / 1000} kHz · Opus · ${formatSize(recording.sizeBytes)}",
                right = "of ${formatDuration(duration)}",
            )
        }

        Spacer(Modifier.height(14.dp))
        PlaybackScope(
            peaks = recording.waveform,
            positionMillis = playback.positionMillis,
            durationMillis = duration,
            onSeek = onSeek,
        )
        Text(
            text = buildString {
                append(recording.captureSource)
                append(" · silence ${recording.silencePercent}%")
                playback.error?.let { append(" · playback error: $it") }
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (recording.lowQuality || playback.error != null) CC.Warn else CC.TextTertiary,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            ToolButton(
                onClick = onTogglePlay,
                modifier = Modifier.weight(2f),
                background = CC.Accent.copy(alpha = 0.16f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = CC.Accent,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(if (playback.isPlaying) "Pause" else "Play", style = MaterialTheme.typography.labelLarge, color = CC.TextPrimary)
                }
            }
            ToolButton(onClick = onCycleSpeed, modifier = Modifier.weight(1f)) {
                Text(speedLabel(playback.speed), style = MaterialTheme.typography.labelLarge.merge(Tabular), color = CC.TextPrimary)
            }
            ToolButton(onClick = onShare, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Share, contentDescription = "Share recording", tint = CC.TextPrimary)
            }
            DeleteButton(onDelete = onDelete, modifier = Modifier.weight(1f))
        }
    }
}

/** Live view while a call (or test) is being recorded. */
@Composable
fun LiveDeck(live: LiveRecording, levels: List<Float>, modifier: Modifier = Modifier) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(live.startedAtMillis) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    val who = live.metadata.contactName ?: live.metadata.address

    DeckCard(modifier) {
        DisplayPanel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RecChip()
                Spacer(Modifier.width(8.dp))
                PanelChip(text = live.metadata.source.label())
                Spacer(Modifier.weight(1f))
                if (live.sampleRateHz > 0) PanelChip(text = "${live.sampleRateHz / 1000} kHz")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                who ?: "Recording",
                style = MaterialTheme.typography.titleMedium,
                color = CC.DisplayInk,
                maxLines = 1,
            )
            BigClock(now - live.startedAtMillis, color = CC.DisplayInk)
            Spacer(Modifier.height(10.dp))
            PanelFooter(
                left = live.sourceLabel ?: "Choosing audio source…",
                right = "Opus",
            )
        }
        Spacer(Modifier.height(14.dp))
        LiveScope(levels)
    }
}

@Composable
private fun DeckCard(modifier: Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(DeckShape)
            .background(CC.Surface)
            .padding(14.dp),
    ) { content() }
}

@Composable
private fun DisplayPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Brush.verticalGradient(listOf(CC.Display, CC.DisplayDeep)))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) { content() }
}

/** Large tabular readout with small unit marks, e.g. 03m 12s. */
@Composable
private fun BigClock(millis: Long, color: Color) {
    Row(verticalAlignment = Alignment.Bottom) {
        clockParts(millis).forEachIndexed { i, (value, unit) ->
            if (i > 0) Spacer(Modifier.width(10.dp))
            Text(
                value,
                style = TextStyle(fontSize = 46.sp, fontWeight = FontWeight.Light, letterSpacing = (-1).sp).merge(Tabular),
                color = color,
            )
            Text(
                unit,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
                color = CC.DisplayInkSoft,
                modifier = Modifier.padding(start = 2.dp, bottom = 9.dp),
            )
        }
    }
}

@Composable
private fun PanelChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = CC.DisplayInk,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CC.DisplayInk.copy(alpha = 0.08f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun RecChip() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(CC.DisplayInk)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Box(Modifier.size(7.dp).background(CC.Record, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text("REC", style = MaterialTheme.typography.labelMedium, color = CC.Display)
    }
}

@Composable
private fun PanelFooter(left: String, right: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CC.DisplayInk.copy(alpha = 0.07f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(left, style = MaterialTheme.typography.labelMedium.merge(Tabular), color = CC.DisplayInkSoft, maxLines = 1, modifier = Modifier.weight(1f))
        Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(14.dp).background(CC.DisplayInkSoft.copy(alpha = 0.35f)))
        Text(right, style = MaterialTheme.typography.labelMedium.merge(Tabular), color = CC.DisplayInk)
    }
}

/**
 * Mirrored peak waveform with a time ruler and a red playhead. Tap or drag anywhere to seek.
 */
@Composable
private fun PlaybackScope(peaks: FloatArray, positionMillis: Long, durationMillis: Long, onSeek: (Long) -> Unit) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: if (durationMillis > 0) (positionMillis.toFloat() / durationMillis).coerceIn(0f, 1f) else 0f
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 10.sp, color = CC.TextTertiary).merge(Tabular)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .pointerInput(durationMillis) {
                detectTapGestures { o -> onSeek(((o.x / size.width).coerceIn(0f, 1f) * durationMillis).toLong()) }
            }
            .pointerInput(durationMillis) {
                var latest = 0f
                detectHorizontalDragGestures(
                    onDragStart = { o -> latest = (o.x / size.width).coerceIn(0f, 1f); dragFraction = latest },
                    onDragEnd = { onSeek((latest * durationMillis).toLong()); dragFraction = null },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, _ ->
                        latest = (change.position.x / size.width).coerceIn(0f, 1f)
                        dragFraction = latest
                    },
                )
            },
    ) {
        val rulerHeight = 24.dp.toPx()
        val waveHeight = size.height - rulerHeight
        val mid = waveHeight / 2
        val playX = size.width * fraction

        // Centre line, then bars.
        drawRect(CC.Hairline, topLeft = Offset(0f, mid - 0.5f), size = Size(size.width, 1f))
        if (peaks.isNotEmpty()) {
            val slot = size.width / peaks.size
            val barWidth = max(slot * 0.55f, 1f)
            peaks.forEachIndexed { i, peak ->
                val h = max(waveHeight * 0.92f * peak.coerceIn(0f, 1f), 2f)
                val x = i * slot + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (x + barWidth / 2 <= playX) CC.TextPrimary else CC.TextTertiary,
                    topLeft = Offset(x, mid - h / 2),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
        }

        // Ruler: minor ticks, major ticks with labels.
        if (durationMillis > 0) {
            val totalSec = durationMillis / 1000f
            val major = majorTickSeconds(totalSec)
            val minor = major / 5f
            var t = 0f
            while (t <= totalSec + 0.001f) {
                val x = size.width * (t / totalSec)
                val isMajor = (t / major).let { kotlin.math.abs(it - Math.round(it)) < 0.001f }
                val tickH = if (isMajor) 8.dp.toPx() else 4.dp.toPx()
                drawRect(CC.TextTertiary, topLeft = Offset(x, waveHeight + 2.dp.toPx()), size = Size(1f, tickH))
                if (isMajor) {
                    val label = formatDuration((t * 1000).toLong())
                    val layout = measurer.measure(label, labelStyle)
                    val lx = (x - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                    drawText(layout, topLeft = Offset(lx, waveHeight + 11.dp.toPx()))
                }
                t += minor
            }
        }

        // Playhead.
        drawRect(CC.Record, topLeft = Offset(playX - 1f, 0f), size = Size(2f, waveHeight))
        drawCircle(CC.Record, radius = 4.dp.toPx(), center = Offset(playX, 4.dp.toPx()))
        drawCircle(CC.Record, radius = 4.dp.toPx(), center = Offset(playX, waveHeight - 4.dp.toPx()))
    }
}

/** Scrolling live levels; the red line on the right is "now". */
@Composable
private fun LiveScope(levels: List<Float>) {
    Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
        val mid = size.height / 2
        val slots = LIVE_SLOTS
        val slot = size.width / slots
        val barWidth = max(slot * 0.55f, 1f)
        drawRect(CC.Hairline, topLeft = Offset(0f, mid - 0.5f), size = Size(size.width, 1f))
        val start = slots - levels.size
        levels.forEachIndexed { i, level ->
            val h = max(size.height * 0.9f * level.coerceIn(0f, 1f), 2f)
            val x = (start + i) * slot + (slot - barWidth) / 2
            drawRoundRect(
                color = CC.TextPrimary,
                topLeft = Offset(x, mid - h / 2),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2),
            )
        }
        drawRect(CC.Record, topLeft = Offset(size.width - 2f, 0f), size = Size(2f, size.height))
    }
}

/** Number of bars the live scope shows; the screen keeps this many levels. */
const val LIVE_SLOTS = 90

@Composable
private fun ToolButton(onClick: () -> Unit, modifier: Modifier = Modifier, background: Color = CC.SurfaceRaised, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick),
    ) { content() }
}

/** Two-step delete inside the deck: first tap arms it (turns red), second tap deletes. */
@Composable
private fun DeleteButton(onDelete: () -> Unit, modifier: Modifier) {
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3_000)
            armed = false
        }
    }
    ToolButton(
        onClick = { if (armed) onDelete() else armed = true },
        modifier = modifier,
        background = if (armed) CC.Record.copy(alpha = 0.2f) else CC.SurfaceRaised,
    ) {
        if (armed) {
            Text("Delete", style = MaterialTheme.typography.labelLarge, color = CC.Record)
        } else {
            Icon(Icons.Filled.Delete, contentDescription = "Delete recording", tint = CC.TextPrimary)
        }
    }
}

private fun speedLabel(speed: Float): String =
    if (speed % 1f == 0f) "${speed.toInt()}×" else "${speed}×"

/** Pick a label step giving at most ~5 labels: 5 s, 10 s, 15 s, 30 s, 1 min, … */
private fun majorTickSeconds(totalSec: Float): Float {
    val steps = floatArrayOf(5f, 10f, 15f, 30f, 60f, 120f, 300f, 600f, 900f, 1800f, 3600f)
    return steps.firstOrNull { totalSec / it <= 5f } ?: 3600f
}
