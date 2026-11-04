package com.veylo.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.veylo.app.R

object Palette {
    val Forest = Color(0xFF163D2A)
    val ForestDeep = Color(0xFF0F2A1D)
    val ForestSoft = Color(0xFF2B5A40)
    val Lime = Color(0xFFC8FF65)
    val LimeDeep = Color(0xFF8FD13F)
    val Cream = Color(0xFFF7F7F2)
    val Charcoal = Color(0xFF1C211D)
    val White = Color(0xFFFFFFFF)
}

/** VEYLO-specific colours that Material's scheme doesn't cover. */
@Immutable
data class VeyloColors(
    val hero: Color,
    val onHero: Color,
    val onHeroMuted: Color,
    val lime: Color,
    val onLime: Color,
    val limeTint: Color,
    val muted: Color,
    val card: Color,
    val cardAlt: Color,
    val divider: Color,
    val oweBg: Color,
    val oweFg: Color,
    val owedBg: Color,
    val owedFg: Color,
    val warn: Color,
)

private val LightVeylo = VeyloColors(
    hero = Palette.Forest, onHero = Color.White, onHeroMuted = Color(0xCCFFFFFF),
    lime = Palette.Lime, onLime = Palette.ForestDeep, limeTint = Color(0xFFEFF9DC),
    muted = Color(0xFF6E766F), card = Color.White, cardAlt = Color(0xFFF0F1EA),
    divider = Color(0xFFE6E8E0),
    oweBg = Color(0xFFFCE7E3), oweFg = Color(0xFFC2412D),
    owedBg = Color(0xFFE7F6D5), owedFg = Color(0xFF2F7A1F),
    warn = Color(0xFFD08A00),
)

private val DarkVeylo = VeyloColors(
    hero = Color(0xFF1C4A33), onHero = Color.White, onHeroMuted = Color(0xBBFFFFFF),
    lime = Palette.Lime, onLime = Palette.ForestDeep, limeTint = Color(0xFF243324),
    muted = Color(0xFF9AA59C), card = Color(0xFF172019), cardAlt = Color(0xFF1F2A22),
    divider = Color(0xFF2A352C),
    oweBg = Color(0xFF3A211D), oweFg = Color(0xFFFF8A75),
    owedBg = Color(0xFF223A1B), owedFg = Color(0xFFB4F06A),
    warn = Color(0xFFFFC14D),
)

val LocalVeyloColors = staticCompositionLocalOf { LightVeylo }

object Veylo {
    val colors: VeyloColors
        @Composable get() = LocalVeyloColors.current
}

@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: Int) = Font(
    R.font.manrope,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Manrope = FontFamily(manrope(400), manrope(500), manrope(600), manrope(700), manrope(800))

private fun style(size: Int, weight: Int, line: Int, spacing: Double = 0.0) = TextStyle(
    fontFamily = Manrope, fontSize = size.sp, fontWeight = FontWeight(weight),
    lineHeight = line.sp, letterSpacing = spacing.sp,
)

val VeyloTypography = Typography(
    displayLarge = style(48, 800, 54, -1.5),
    displayMedium = style(38, 800, 44, -1.0),
    displaySmall = style(32, 800, 38, -0.8),
    headlineLarge = style(28, 800, 34, -0.6),
    headlineMedium = style(24, 700, 30, -0.4),
    headlineSmall = style(20, 700, 26, -0.2),
    titleLarge = style(18, 700, 24, -0.1),
    titleMedium = style(16, 700, 22),
    titleSmall = style(14, 700, 20),
    bodyLarge = style(16, 500, 24),
    bodyMedium = style(14, 500, 20),
    bodySmall = style(12, 500, 16),
    labelLarge = style(15, 700, 20),
    labelMedium = style(12, 600, 16, 0.2),
    labelSmall = style(11, 600, 14, 0.4),
)

private val LightScheme = lightColorScheme(
    primary = Palette.Forest, onPrimary = Color.White,
    primaryContainer = Palette.Lime, onPrimaryContainer = Palette.ForestDeep,
    secondary = Palette.LimeDeep, onSecondary = Palette.ForestDeep,
    background = Palette.Cream, onBackground = Palette.Charcoal,
    surface = Palette.Cream, onSurface = Palette.Charcoal,
    surfaceVariant = Color(0xFFF0F1EA), onSurfaceVariant = Color(0xFF6E766F),
    surfaceContainerLow = Color.White, surfaceContainer = Color.White, surfaceContainerHigh = Color.White,
    outline = Color(0xFFDADDD3), outlineVariant = Color(0xFFE6E8E0),
    error = Color(0xFFC2412D),
)

private val DarkScheme = darkColorScheme(
    primary = Palette.Lime, onPrimary = Palette.ForestDeep,
    primaryContainer = Palette.Forest, onPrimaryContainer = Palette.Lime,
    secondary = Palette.LimeDeep, onSecondary = Palette.ForestDeep,
    background = Color(0xFF0C130E), onBackground = Color(0xFFE9EEE9),
    surface = Color(0xFF0C130E), onSurface = Color(0xFFE9EEE9),
    surfaceVariant = Color(0xFF1F2A22), onSurfaceVariant = Color(0xFF9AA59C),
    surfaceContainerLow = Color(0xFF172019), surfaceContainer = Color(0xFF172019), surfaceContainerHigh = Color(0xFF1F2A22),
    outline = Color(0xFF34413A), outlineVariant = Color(0xFF2A352C),
    error = Color(0xFFFF8A75),
)

@Composable
fun VeyloTheme(theme: String = "light", content: @Composable () -> Unit) {
    val dark = when (theme) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = VeyloTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(10.dp),
            small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(24.dp),
            extraLarge = RoundedCornerShape(28.dp),
        ),
    ) {
        val view = androidx.compose.ui.platform.LocalView.current
        if (!view.isInEditMode) {
            androidx.compose.runtime.SideEffect {
                val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                androidx.core.view.WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
        }
        CompositionLocalProvider(
            LocalVeyloColors provides if (dark) DarkVeylo else LightVeylo,
            // Default text/icon colour everywhere (otherwise plain Text falls back to black).
            androidx.compose.material3.LocalContentColor provides if (dark) DarkScheme.onBackground else LightScheme.onBackground,
        ) {
            content()
        }
    }
}
