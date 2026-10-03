package com.astrovm.gripmaxxer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.astrovm.gripmaxxer.data.Accent

fun accentColor(accent: Accent): Color = when (accent) {
    Accent.WHITE -> Color(0xFFF5F5F5)
    Accent.PINK -> Color(0xFFFF6FAE)
    Accent.BLUE -> Color(0xFF5AA9FF)
    Accent.RED -> Color(0xFFFF5A5F)
    Accent.GREEN -> Color(0xFF4ADE80)
    Accent.PURPLE -> Color(0xFFB98CFF)
    Accent.ORANGE -> Color(0xFFFFA24C)
}

/** Shown when something goes wrong, whatever the accent. */
val ErrorRed = Color(0xFFFF6B6B)

/** True black for OLED screens, with one accent color. */
@Composable
fun GripTheme(accent: Accent, content: @Composable () -> Unit) {
    val primary = accentColor(accent)
    val colors = darkColorScheme(
        primary = primary,
        onPrimary = Color.Black,
        primaryContainer = primary.copy(alpha = 0.18f),
        onPrimaryContainer = primary,
        secondaryContainer = Color(0xFF262626),
        onSecondaryContainer = Color.White,
        background = Color.Black,
        onBackground = Color.White,
        surface = Color.Black,
        onSurface = Color.White,
        surfaceVariant = Color(0xFF1A1A1A),
        onSurfaceVariant = Color(0xFFA3A3A3),
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF111111),
        surfaceContainer = Color(0xFF161616),
        surfaceContainerHigh = Color(0xFF1C1C1C),
        surfaceContainerHighest = Color(0xFF242424),
        outline = Color(0xFF3A3A3A),
        outlineVariant = Color(0xFF2A2A2A),
        error = ErrorRed,
        // Snackbars: a raised dark card with the accent on its action.
        inverseSurface = Color(0xFF2E2E2E),
        inverseOnSurface = Color.White,
        inversePrimary = primary,
    )
    MaterialTheme(colorScheme = colors, content = content)
}
