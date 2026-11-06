package com.callcatcher.ui.components

import androidx.compose.foundation.clickable
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

/**
 * One row in the single unified list. Shows source icon, direction, title, time, duration and —
 * when active — the waveform with playback progress. Context-aware per the brief, in one surface.
 */
@Composable
fun RecordingRow(
    recording: RecordingUi,
    isActive: Boolean,
    playback: PlaybackState,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeek: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (isActive && playback.isPlaying) onPause() else onPlay() }
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

            Column(modifier = Modifier.padding(end = 8.dp).weight(1f)) {
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
                    contentDescription = "Low quality / mostly silent",
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
            val progress = if (playback.durationMillis > 0) {
                playback.positionMillis.toFloat() / playback.durationMillis
            } else 0f
            Waveform(peaks = recording.waveform, progress = progress)
        }
    }
}
