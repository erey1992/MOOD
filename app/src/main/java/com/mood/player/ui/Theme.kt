package com.mood.player.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.mood.player.R

/** Colores de marca de MOOD que no encajan en el ColorScheme de Material (fondo, cristal, degradados). */
@Immutable
data class MoodColors(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val bokeh: List<Color>,
    val glass: Color,
    val glassStrong: Color,
    val glassBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val iconMuted: Color,
    val accent: Color,
    val accentLight: Color,
    val accentDeep: Color,
    val progressStart: Color,
    val progressEnd: Color,
    val track: Color,
    val artworkTop: Color,
    val artworkBottom: Color,
)

val LightMoodColors = MoodColors(
    backgroundTop = Color(0xFFF7F3FF),
    backgroundBottom = Color(0xFFEEE8FC),
    bokeh = listOf(Color(0x40FFB8DD), Color(0x40BBA6FF), Color(0x40A9C8FF), Color(0x33FFE4A0)),
    glass = Color(0xA6FFFFFF),
    glassStrong = Color(0xF0FBF9FF),
    glassBorder = Color(0xE6FFFFFF),
    textPrimary = Color(0xFF1B1446),
    textSecondary = Color(0xFF6B6396),
    iconMuted = Color(0xFF8F87BE),
    accent = Color(0xFF7C3AED),
    accentLight = Color(0xFF9F6BFF),
    accentDeep = Color(0xFF5B21B6),
    progressStart = Color(0xFFFFC93C),
    progressEnd = Color(0xFFA855F7),
    track = Color(0xFFDCD4F7),
    artworkTop = Color(0xFFE9DEFF),
    artworkBottom = Color(0xFFF9E8FF),
)

val DarkMoodColors = MoodColors(
    backgroundTop = Color(0xFF16113F),
    backgroundBottom = Color(0xFF0B0926),
    bokeh = listOf(Color(0x33EC4899), Color(0x407C3AED), Color(0x333B82F6), Color(0x1FF59E0B)),
    glass = Color(0x1FFFFFFF),
    glassStrong = Color(0xF21C1748),
    glassBorder = Color(0x29FFFFFF),
    textPrimary = Color(0xFFF4F1FF),
    textSecondary = Color(0xFFB0A9DA),
    iconMuted = Color(0xFF8D86BA),
    accent = Color(0xFFB794F6),
    accentLight = Color(0xFF9F6BFF),
    accentDeep = Color(0xFF6D28D9),
    progressStart = Color(0xFFFFC93C),
    progressEnd = Color(0xFFA855F7),
    track = Color(0x33FFFFFF),
    artworkTop = Color(0xFF2A2163),
    artworkBottom = Color(0xFF1A1446),
)

val LocalMoodColors = staticCompositionLocalOf { LightMoodColors }

/** Lexend (Google Fonts, licencia OFL) como fuente variable: cada peso fija el eje "wght". */
@OptIn(ExperimentalTextApi::class)
val Lexend = FontFamily(
    listOf(
        FontWeight.Normal to 400,
        FontWeight.Medium to 500,
        FontWeight.SemiBold to 600,
        FontWeight.Bold to 700,
        FontWeight.ExtraBold to 800,
    ).map { (weight, value) ->
        Font(R.font.lexend, weight, variationSettings = FontVariation.Settings(FontVariation.weight(value)))
    }
)

private fun Typography.withFont(family: FontFamily): Typography {
    fun TextStyle.f() = copy(fontFamily = family)
    return copy(
        displayLarge = displayLarge.f(), displayMedium = displayMedium.f(), displaySmall = displaySmall.f(),
        headlineLarge = headlineLarge.f(), headlineMedium = headlineMedium.f(), headlineSmall = headlineSmall.f(),
        titleLarge = titleLarge.f(), titleMedium = titleMedium.f(), titleSmall = titleSmall.f(),
        bodyLarge = bodyLarge.f(), bodyMedium = bodyMedium.f(), bodySmall = bodySmall.f(),
        labelLarge = labelLarge.f(), labelMedium = labelMedium.f(), labelSmall = labelSmall.f(),
    )
}

private val MoodTypography = Typography().withFont(Lexend)

private fun lightScheme(c: MoodColors) = lightColorScheme(
    primary = c.accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE4FF),
    onPrimaryContainer = c.accentDeep,
    secondary = c.accentLight,
    secondaryContainer = Color(0xFFE6DCFF),
    onSecondaryContainer = c.textPrimary,
    background = c.backgroundTop,
    onBackground = c.textPrimary,
    surface = Color(0xFFFBF9FF),
    onSurface = c.textPrimary,
    surfaceVariant = Color(0xFFECE6FA),
    onSurfaceVariant = c.textSecondary,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F5FF),
    surfaceContainer = Color(0xFFF5F1FF),
    surfaceContainerHigh = Color(0xFFF7F3FF),
    surfaceContainerHighest = Color(0xFFEDE7FB),
    outline = Color(0xFFC6BDE8),
    outlineVariant = Color(0xFFE3DCF6),
)

private fun darkScheme(c: MoodColors) = darkColorScheme(
    primary = c.accent,
    onPrimary = Color(0xFF21104F),
    primaryContainer = Color(0xFF3B2A7A),
    onPrimaryContainer = Color(0xFFEDE4FF),
    secondary = c.accentLight,
    secondaryContainer = Color(0xFF2E2468),
    onSecondaryContainer = c.textPrimary,
    background = c.backgroundTop,
    onBackground = c.textPrimary,
    surface = Color(0xFF1C1748),
    onSurface = c.textPrimary,
    surfaceVariant = Color(0xFF2A2361),
    onSurfaceVariant = c.textSecondary,
    surfaceContainerLowest = Color(0xFF110D30),
    surfaceContainerLow = Color(0xFF19143F),
    surfaceContainer = Color(0xFF1E1849),
    surfaceContainerHigh = Color(0xFF241D55),
    surfaceContainerHighest = Color(0xFF2C2462),
    outline = Color(0xFF5E558F),
    outlineVariant = Color(0xFF3A3270),
)

@Composable
fun MoodTheme(content: @Composable () -> Unit) {
    val moodColors = if (isSystemInDarkTheme()) DarkMoodColors else LightMoodColors
    val scheme = if (isSystemInDarkTheme()) darkScheme(moodColors) else lightScheme(moodColors)
    CompositionLocalProvider(LocalMoodColors provides moodColors) {
        MaterialTheme(colorScheme = scheme, typography = MoodTypography, content = content)
    }
}
