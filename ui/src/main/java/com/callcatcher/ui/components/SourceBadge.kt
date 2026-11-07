package com.callcatcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import com.callcatcher.core.model.CallDirection
import com.callcatcher.core.model.CallSource
import com.callcatcher.ui.theme.CC

fun CallSource.icon(): ImageVector = when (this) {
    CallSource.PHONE -> Icons.Filled.Call
    CallSource.WHATSAPP -> Icons.AutoMirrored.Filled.Chat
    CallSource.TEST -> Icons.Filled.Mic
    CallSource.UNKNOWN -> Icons.Filled.QuestionMark
}

fun CallSource.tint(): Color = when (this) {
    CallSource.PHONE -> CC.Phone
    CallSource.WHATSAPP -> CC.WhatsApp
    CallSource.TEST, CallSource.UNKNOWN -> CC.Test
}

fun CallSource.label(): String = when (this) {
    CallSource.PHONE -> "Phone"
    CallSource.WHATSAPP -> "WhatsApp"
    CallSource.TEST -> "Test"
    CallSource.UNKNOWN -> "Call"
}

fun CallDirection.label(): String? = when (this) {
    CallDirection.INCOMING -> "Incoming"
    CallDirection.OUTGOING -> "Outgoing"
    CallDirection.UNKNOWN -> null
}

/** Rounded-square tile with the source glyph, tinted per source. Vector icons, never emoji. */
@Composable
fun SourceTile(source: CallSource, size: Dp = 42.dp, modifier: Modifier = Modifier) {
    val tint = source.tint()
    Box(
        modifier = modifier
            .size(size)
            .background(tint.copy(alpha = 0.14f), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(source.icon(), contentDescription = source.label(), tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

@Composable
fun DirectionGlyph(direction: CallDirection, tint: Color, modifier: Modifier = Modifier) {
    val icon = when (direction) {
        CallDirection.INCOMING -> Icons.AutoMirrored.Filled.CallReceived
        CallDirection.OUTGOING -> Icons.AutoMirrored.Filled.CallMade
        CallDirection.UNKNOWN -> return
    }
    Icon(icon, contentDescription = direction.label(), tint = tint, modifier = modifier.size(13.dp))
}
