package com.kambesembi.famobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kambesembi.famobile.data.ThemeMode

// FA's modern dark theme (page #1B1B1D, menu #141414, panels #20242A) with the amber accent of
// FA-ImprovedLayout's bubbles design.
private val Dark: ColorScheme = darkColorScheme(
    primary = Color(0xFFF0C27A),
    onPrimary = Color(0xFF3A2806),
    primaryContainer = Color(0xFF4A3712),
    onPrimaryContainer = Color(0xFFFFE0AE),
    secondary = Color(0xFF9DB2C9),
    onSecondary = Color(0xFF14202C),
    secondaryContainer = Color(0xFF2B3542),
    onSecondaryContainer = Color(0xFFD5E2F0),
    tertiary = Color(0xFF6FD38A),
    onTertiary = Color(0xFF07361A),
    error = Color(0xFFF07A7A),
    onError = Color(0xFF3D0909),
    errorContainer = Color(0xFF5A1D1D),
    onErrorContainer = Color(0xFFF3A3A3),
    background = Color(0xFF1B1B1D),
    onBackground = Color(0xFFEBEBEB),
    surface = Color(0xFF1B1B1D),
    onSurface = Color(0xFFEBEBEB),
    surfaceVariant = Color(0xFF2A2E35),
    onSurfaceVariant = Color(0xFFB8BCC4),
    surfaceContainerLowest = Color(0xFF141414),
    surfaceContainerLow = Color(0xFF1E2024),
    surfaceContainer = Color(0xFF20242A),
    surfaceContainerHigh = Color(0xFF282C33),
    surfaceContainerHighest = Color(0xFF30353D),
    outline = Color(0xFF4A4F58),
    outlineVariant = Color(0xFF2E323A),
    inverseSurface = Color(0xFFEBEBEB),
    inverseOnSurface = Color(0xFF1B1B1D),
    inversePrimary = Color(0xFF8A5A0C),
    scrim = Color(0xFF000000),
)

private val Light: ColorScheme = lightColorScheme(
    primary = Color(0xFF8A5A0C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE3B8),
    onPrimaryContainer = Color(0xFF2E1F00),
    secondary = Color(0xFF3E5A78),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD7E4F3),
    onSecondaryContainer = Color(0xFF0E1D2C),
    tertiary = Color(0xFF1D8A3C),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFC62828),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF4F4F6),
    onBackground = Color(0xFF1C1C1F),
    surface = Color(0xFFF4F4F6),
    onSurface = Color(0xFF1C1C1F),
    surfaceVariant = Color(0xFFE4E5E9),
    onSurfaceVariant = Color(0xFF50545C),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDEEF1),
    surfaceContainerHighest = Color(0xFFE5E6EA),
    outline = Color(0xFFB9BCC3),
    outlineVariant = Color(0xFFDFE0E4),
    inverseSurface = Color(0xFF2E3036),
    inverseOnSurface = Color(0xFFF1F1F3),
    inversePrimary = Color(0xFFF0C27A),
    scrim = Color(0xFF000000),
)

/** Rounded "bubbles": cards 18dp, buttons and chips 14dp. */
private val BubbleShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun FaTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        shapes = BubbleShapes,
        content = content,
    )
}
