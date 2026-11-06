package com.callcatcher.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.callcatcher.core.model.CallDirection
import com.callcatcher.core.model.CallSource

/** Source icon — bespoke vector glyphs, never emoji (brief). */
@Composable
fun SourceIcon(source: CallSource, modifier: Modifier = Modifier) {
    val icon: ImageVector = when (source) {
        CallSource.PHONE -> Icons.Filled.Call
        CallSource.WHATSAPP -> Icons.Filled.Chat
        CallSource.UNKNOWN -> Icons.Filled.QuestionMark
    }
    Icon(
        imageVector = icon,
        contentDescription = source.name,
        modifier = modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun DirectionIcon(direction: CallDirection, modifier: Modifier = Modifier) {
    val icon = when (direction) {
        CallDirection.INCOMING -> Icons.Filled.CallReceived
        CallDirection.OUTGOING -> Icons.Filled.CallMade
        CallDirection.UNKNOWN -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = direction.name,
        modifier = modifier.size(14.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
