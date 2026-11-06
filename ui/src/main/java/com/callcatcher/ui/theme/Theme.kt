package com.callcatcher.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Total-black palette. Pure black surfaces, a single restrained accent, muted text tiers.
 * One theme, no light variant by default — the brief asks for total black (system optional).
 */
private val Black = Color(0xFF000000)
private val Elevated = Color(0xFF0E0E0E)
private val Line = Color(0xFF1C1C1E)
private val TextPrimary = Color(0xFFF2F2F2)
private val TextMuted = Color(0xFF8A8A8E)
private val Accent = Color(0xFF5E9EFF)

private val CallCatcherColors = darkColorScheme(
    primary = Accent,
    onPrimary = Black,
    background = Black,
    onBackground = TextPrimary,
    surface = Black,
    onSurface = TextPrimary,
    surfaceVariant = Elevated,
    onSurfaceVariant = TextMuted,
    outline = Line,
    secondary = TextMuted,
    onSecondary = Black,
)

private val CallCatcherShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(22.dp),
)

@Composable
fun CallCatcherTheme(
    followSystem: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Always dark; the toggle exists so "system" can later opt into a light scheme if wanted.
    val useLight = followSystem && !isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (useLight) CallCatcherColors else CallCatcherColors,
        typography = Typography(),
        shapes = CallCatcherShapes,
        content = content,
    )
}
