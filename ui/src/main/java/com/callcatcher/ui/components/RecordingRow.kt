package com.callcatcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callcatcher.player.PlaybackState
import com.callcatcher.ui.model.RecordingUi
import com.callcatcher.ui.util.formatDateTime
import com.callcatcher.ui.util.formatDuration
import com.callcatcher.ui.util.formatSize

/**
 * One row of the unified list. Tap plays/pauses; long-press asks to delete. The active row expands
 * with a seekable waveform and the capture details (source, rate, size, silence) used for testing.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecordingRow(
    recording: RecordingUi,
    isActive: Boolean,
    playback: PlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { if (isActive && playback.isPlaying) onPause() else onPlay() },
                onLongClick = onLongPress,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SourceIcon(recording.source)
            DirectionIcon(recording.direction)

            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = recording.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatDateTime(recording.startedAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (recording.lowQuality) {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = "Mostly silent",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = formatDuration(recording.durationMillis),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (isActive) {
            val duration = if (playback.durationMillis > 0) playback.durationMillis else recording.durationMillis
            val progress = if (duration > 0) playback.positionMillis.toFloat() / duration else 0f
            Waveform(
                peaks = recording.waveform,
                progress = progress,
                onSeek = { fraction -> onSeek((fraction * duration).toLong()) },
            )
            Text(
                text = "${recording.captureSource} · ${recording.sampleRateHz / 1000} kHz · " +
                    "${formatSize(recording.sizeBytes)} · silence ${recording.silencePercent}%" +
                    (playback.error?.let { " · playback error: $it" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
