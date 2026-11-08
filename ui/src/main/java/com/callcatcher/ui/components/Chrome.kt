package com.callcatcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.callcatcher.ui.model.SourceFilter
import com.callcatcher.ui.theme.CC

/** Title, library summary, search toggle and setup status pill. */
@Composable
fun TopBar(
    summary: String,
    ready: Boolean,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onToggleStatus: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("CallCatcher", style = MaterialTheme.typography.headlineSmall, color = CC.TextPrimary)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = CC.TextSecondary, maxLines = 1)
        }
        RoundIconButton(onClick = onToggleSearch, active = searchOpen) {
            Icon(Icons.Filled.Search, contentDescription = "Search", tint = CC.TextPrimary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(CC.SurfaceRaised)
                .clickable(onClick = onToggleStatus)
                .padding(horizontal = 14.dp),
        ) {
            Box(Modifier.size(8.dp).background(if (ready) CC.Ok else CC.Warn, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text(if (ready) "Ready" else "Setup", style = MaterialTheme.typography.labelMedium, color = CC.TextPrimary)
        }
    }
}

@Composable
fun RoundIconButton(onClick: () -> Unit, active: Boolean = false, content: @Composable () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) CC.SurfaceHigh else CC.SurfaceRaised)
            .clickable(onClick = onClick),
    ) { content() }
}

/** All / Phone / WhatsApp as one segmented pill. */
@Composable
fun SegmentedFilter(selected: SourceFilter, onSelect: (SourceFilter) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(50))
            .background(CC.Surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SourceFilter.entries.forEach { option ->
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) CC.SurfaceHigh else Color.Transparent)
                    .clickable { onSelect(option) },
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) CC.TextPrimary else CC.TextSecondary,
                )
            }
        }
    }
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(CC.Surface)
            .padding(horizontal = 14.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = CC.TextTertiary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text("Name or number", style = MaterialTheme.typography.bodyMedium, color = CC.TextTertiary)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.merge(TextStyle(color = CC.TextPrimary)),
                cursorBrush = SolidColor(CC.Accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear",
                tint = CC.TextSecondary,
                modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onQueryChange("") },
            )
        }
    }
}

/**
 * Bottom record control over a fade: starts a microphone recording, or stops whatever is being
 * recorded (call or memo). Playback controls live in the player deck.
 */
@Composable
fun BottomDock(recording: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, CC.Canvas, CC.Canvas)))
            .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(50))
                .background(CC.SurfaceRaised)
                .border(1.dp, CC.Hairline, RoundedCornerShape(50))
                .clickable(onClick = onClick),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(CC.Record.copy(alpha = 0.16f)),
            ) {
                Icon(
                    if (recording) Icons.Filled.Stop else Icons.Filled.Mic,
                    contentDescription = null,
                    tint = CC.Record,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(if (recording) "Stop" else "Recording", style = MaterialTheme.typography.labelLarge, color = CC.TextPrimary)
        }
    }
}
