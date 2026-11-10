package com.fadysaeed.ketaby.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.fadysaeed.ketaby.R
import com.fadysaeed.ketaby.data.Prefs

val Gold = Color(0xFFD4AF37)
val Teal = Color(0xFF2EC4B6)
val Ink = Color(0xFFECE7DA)
val Muted = Color(0xFF9A968C)
val Panel = Color(0xFF111111)
val PanelHi = Color(0xFF1C1C1C)
val Line = Color(0xFF2A2A2A)
val Rose = Color(0xFFE07A5F)

val Plex = FontFamily(
    Font(R.font.plex_regular, FontWeight.Normal),
    Font(R.font.plex_semibold, FontWeight.SemiBold),
    Font(R.font.plex_semibold, FontWeight.Bold),
)
val Naskh = FontFamily(Font(R.font.naskh_regular, FontWeight.Normal))
val HebrewFont = FontFamily(Font(R.font.hebrew_serif, FontWeight.Normal))
val GreekFont = FontFamily(Font(R.font.greek_serif, FontWeight.Normal))

val LocalPrefs = staticCompositionLocalOf<Prefs> { error("no prefs") }

private fun TextStyle.plex() = copy(fontFamily = Plex)

@Composable
fun KetabyTheme(content: @Composable () -> Unit) {
    val t = Typography()
    val typography = Typography(
        displayLarge = t.displayLarge.plex(), displayMedium = t.displayMedium.plex(), displaySmall = t.displaySmall.plex(),
        headlineLarge = t.headlineLarge.plex(), headlineMedium = t.headlineMedium.plex(), headlineSmall = t.headlineSmall.plex(),
        titleLarge = t.titleLarge.plex(), titleMedium = t.titleMedium.plex(), titleSmall = t.titleSmall.plex(),
        bodyLarge = t.bodyLarge.plex(), bodyMedium = t.bodyMedium.plex(), bodySmall = t.bodySmall.plex(),
        labelLarge = t.labelLarge.plex(), labelMedium = t.labelMedium.plex(), labelSmall = t.labelSmall.plex(),
    )
    val colors = darkColorScheme(
        primary = Gold, onPrimary = Color.Black,
        secondary = Teal, onSecondary = Color.Black,
        tertiary = Rose,
        background = Color.Black, onBackground = Ink,
        surface = Color.Black, onSurface = Ink,
        surfaceVariant = Panel, onSurfaceVariant = Muted,
        surfaceContainer = Panel, surfaceContainerHigh = PanelHi, surfaceContainerLow = Panel,
        surfaceContainerHighest = PanelHi, surfaceContainerLowest = Color.Black,
        outline = Line, outlineVariant = Line,
    )
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
