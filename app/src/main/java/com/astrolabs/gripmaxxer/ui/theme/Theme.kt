package com.astrolabs.gripmaxxer.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.astrolabs.gripmaxxer.datastore.ColorPalette

val LocalIsWindows98Theme = staticCompositionLocalOf { false }

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

private fun createWindows98Scheme() = lightColorScheme(
    primary = Win98Blue,
    onPrimary = White,
    secondary = Win98Teal,
    onSecondary = White,
    tertiary = Win98Blue,
    onTertiary = White,
    background = Win98Gray,
    onBackground = Black,
    surface = Win98Surface,
    onSurface = Black,
    surfaceVariant = Win98SurfaceVariant,
    onSurfaceVariant = Black,
    primaryContainer = Win98Blue,
    onPrimaryContainer = White,
    secondaryContainer = Win98SurfaceVariant,
    onSecondaryContainer = Black,
    error = DangerAccent,
    onError = White,
)

private val windows98Shapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

private val windows98Typography = Typography(
    displayLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 48.sp),
    displayMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 40.sp),
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 36.sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 11.sp),
)

private fun paletteScheme(palette: ColorPalette) = when (palette) {
    ColorPalette.BLACK_WHITE -> createBlackAccentScheme(White)
    ColorPalette.BLACK_PINK -> createBlackAccentScheme(PinkAccent)
    ColorPalette.BLACK_BLUE -> createBlackAccentScheme(BlueAccent)
    ColorPalette.BLACK_RED -> createBlackAccentScheme(RedAccent)
    ColorPalette.BLACK_GREEN -> createBlackAccentScheme(GreenAccent)
    ColorPalette.BLACK_PURPLE -> createBlackAccentScheme(PurpleAccent)
    ColorPalette.BLACK_ORANGE -> createBlackAccentScheme(OrangeAccent)
    ColorPalette.WINDOWS_98 -> createWindows98Scheme()
}

@Composable
fun GripmaxxerTheme(
    palette: ColorPalette = ColorPalette.BLACK_WHITE,
    content: @Composable () -> Unit
) {
    val colorScheme = paletteScheme(palette)
    val isWindows98 = palette == ColorPalette.WINDOWS_98
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                colorScheme.background.luminance() > 0.5f
        }
    }

    CompositionLocalProvider(LocalIsWindows98Theme provides isWindows98) {
        if (isWindows98) {
            MaterialTheme(
                colorScheme = colorScheme,
                shapes = windows98Shapes,
                typography = windows98Typography,
                content = content,
            )
        } else {
            MaterialTheme(
                colorScheme = colorScheme,
                content = content,
            )
        }
    }
}
