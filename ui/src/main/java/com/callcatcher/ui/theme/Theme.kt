package com.callcatcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CallCatcher palette. Pure black canvas, graphite surfaces in two elevations, one pale "display"
 * panel for the live readout, and a small set of signal colours used sparingly.
 */
object CC {
    val Canvas = Color(0xFF000000)
    val Surface = Color(0xFF0F0F11)
    val SurfaceRaised = Color(0xFF19191C)
    val SurfaceHigh = Color(0xFF232327)
    val Hairline = Color(0xFF26262B)

    val TextPrimary = Color(0xFFF2F2F4)
    val TextSecondary = Color(0xFF9A9AA2)
    val TextTertiary = Color(0xFF5E5E66)

    /** The readout panel, a cool pale blue like a backlit display. */
    val Display = Color(0xFFCADDF0)
    val DisplayDeep = Color(0xFFAFC8E2)
    val DisplayInk = Color(0xFF0D1A27)
    val DisplayInkSoft = Color(0xFF3F566C)

    val Accent = Color(0xFF6AA6FF)
    val Record = Color(0xFFFF453A)
    val Ok = Color(0xFF32D74B)
    val Warn = Color(0xFFFFB340)

    /** Source tints for the list tiles. */
    val Phone = Color(0xFF6AA6FF)
    val WhatsApp = Color(0xFF34C759)
    val Test = Color(0xFF9A9AA2)
}

private val AppColors = darkColorScheme(
    primary = CC.Accent,
    onPrimary = CC.Canvas,
    background = CC.Canvas,
    onBackground = CC.TextPrimary,
    surface = CC.Surface,
    onSurface = CC.TextPrimary,
    surfaceVariant = CC.SurfaceRaised,
    onSurfaceVariant = CC.TextSecondary,
    surfaceContainerHigh = CC.SurfaceRaised,
    surfaceContainerHighest = CC.SurfaceHigh,
    outline = CC.Hairline,
    outlineVariant = CC.Hairline,
    secondary = CC.TextSecondary,
    error = CC.Record,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

private val AppType = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp),
    titleSmall = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
)

@Composable
fun CallCatcherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AppColors, typography = AppType, shapes = AppShapes, content = content)
}
