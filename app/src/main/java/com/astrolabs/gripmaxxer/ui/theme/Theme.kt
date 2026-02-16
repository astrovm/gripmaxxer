package com.astrolabs.gripmaxxer.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.astrolabs.gripmaxxer.datastore.ColorPalette

private fun createBlackAccentScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Black,
    secondary = accent,
    onSecondary = Black,
    tertiary = accent,
    onTertiary = Black,
    background = Black,
    onBackground = White,
    surface = SurfaceBlack,
    onSurface = White,
    surfaceVariant = SurfaceVariantBlack,
    onSurfaceVariant = MutedWhite,
    primaryContainer = SurfaceVariantBlack,
    onPrimaryContainer = accent,
    secondaryContainer = SurfaceVariantBlack,
    onSecondaryContainer = accent,
    error = DangerAccent,
    onError = Black,
)

private fun paletteScheme(palette: ColorPalette) = when (palette) {
    ColorPalette.BLACK_WHITE -> createBlackAccentScheme(White)
    ColorPalette.BLACK_PINK -> createBlackAccentScheme(PinkAccent)
    ColorPalette.BLACK_BLUE -> createBlackAccentScheme(BlueAccent)
    ColorPalette.BLACK_RED -> createBlackAccentScheme(RedAccent)
}

@Composable
fun GripmaxxerTheme(
    palette: ColorPalette = ColorPalette.BLACK_WHITE,
    content: @Composable () -> Unit
) {
    val colorScheme = paletteScheme(palette)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
