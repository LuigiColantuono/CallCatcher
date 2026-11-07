package com.callcatcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.callcatcher.ui.model.RecordingUi
import com.callcatcher.ui.theme.CC
import com.callcatcher.ui.util.formatDayHeader
import com.callcatcher.ui.util.formatDuration
import com.callcatcher.ui.util.formatTimeOfDay
import com.callcatcher.ui.util.startOfDay

private val CardShape = RoundedCornerShape(20.dp)
private val Tabular = TextStyle(fontFeatureSettings = "tnum")

/** The single unified list, grouped by day. */
@Composable
fun RecordingList(
    recordings: List<RecordingUi>,
    selectedPath: String?,
    onSelect: (RecordingUi) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val groups = remember(recordings) { recordings.groupBy { startOfDay(it.startedAtMillis) } }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        groups.forEach { (day, dayRecordings) ->
            item(key = "day-$day") {
                Text(
                    formatDayHeader(day),
                    style = MaterialTheme.typography.labelMedium,
                    color = CC.TextTertiary,
                    modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
                )
            }
            items(dayRecordings, key = { it.id }) { recording ->
                RecordingCard(recording, selected = recording.path == selectedPath, onClick = { onSelect(recording) })
            }
        }
    }
}

@Composable
private fun RecordingCard(recording: RecordingUi, selected: Boolean, onClick: () -> Unit) {
    val base = Modifier
        .fillMaxWidth()
        .clip(CardShape)
        .background(if (selected) CC.SurfaceRaised else CC.Surface)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = (if (selected) base.border(1.dp, CC.Accent.copy(alpha = 0.45f), CardShape) else base)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        SourceTile(recording.source)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                recording.title,
                style = MaterialTheme.typography.titleMedium,
                color = CC.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                DirectionGlyph(recording.direction, tint = CC.TextSecondary)
                if (recording.direction.label() != null) Spacer(Modifier.width(4.dp))
                Text(
                    listOfNotNull(recording.direction.label(), formatTimeOfDay(recording.startedAtMillis)).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = CC.TextSecondary,
                    maxLines = 1,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                formatDuration(recording.durationMillis),
                style = MaterialTheme.typography.titleSmall.merge(Tabular),
                color = CC.TextPrimary,
            )
            if (recording.lowQuality) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = CC.Warn, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("Silent", style = MaterialTheme.typography.labelSmall, color = CC.Warn)
                }
            } else {
                Text(
                    "${recording.sampleRateHz / 1000} kHz",
                    style = MaterialTheme.typography.labelSmall,
                    color = CC.TextTertiary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyLibrary(filtered: Boolean, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxSize().padding(horizontal = 40.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(CC.Surface),
        ) {
            Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = CC.TextSecondary, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (filtered) "Nothing here" else "No recordings yet",
            style = MaterialTheme.typography.titleMedium,
            color = CC.TextPrimary,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (filtered) "Try another filter or search." else "Calls are recorded automatically. Use the test button to check playback.",
            style = MaterialTheme.typography.bodySmall,
            color = CC.TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(100.dp))
    }
}
