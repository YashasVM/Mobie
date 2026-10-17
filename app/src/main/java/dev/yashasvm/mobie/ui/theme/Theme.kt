package dev.yashasvm.mobie.ui.theme

import android.app.Activity
import android.os.Build
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import dev.yashasvm.mobie.R

/**
 * Status colors that Material's scheme does not model. Every measured or compatibility state in
 * the UI maps to one of these, so a given color always means the same thing across screens.
 */
@Immutable
data class MobieSignals(
    val ready: Color,
    val caution: Color,
    val blocked: Color,
    val thinking: Color,
    /** Faint fill for gauges, tracks, and inactive meter segments. */
    val track: Color,
    /** Hairline used for panel borders and dividers. */
    val hairline: Color,
)

/**
 * Styles for numbers that are measured or estimated (tok/s, RAM, sizes, latency) and for code.
 * Numbers use the body face with tabular figures so live values never jitter; only [code] is
 * monospace.
 */
@Immutable
data class MobieNumericType(
    val large: TextStyle,
    val medium: TextStyle,
    val small: TextStyle,
    val tiny: TextStyle,
    /** Monospace for code blocks, inline code, and file names. */
    val code: TextStyle,
)

// A calm, neutral palette in the spirit of modern chat apps: warm charcoal (dark) or warm paper
// (light) surfaces, one amber accent carried over from the Mobie mark, and status colors that
// only appear where a state needs explaining. Every text color here passes WCAG AA (4.5:1)
// against every surface container it can sit on.
private val DarkSignals = MobieSignals(
    ready = Color(0xFF7CC890),
    caution = Color(0xFFE6C364),
    blocked = Color(0xFFF2867D),
    thinking = Color(0xFF8DB8E2),
    track = Color(0xFF2E2E2B),
    hairline = Color(0xFF353532),
)

private val LightSignals = MobieSignals(
    ready = Color(0xFF2C6E3A),
    caution = Color(0xFF7D5800),
    blocked = Color(0xFFB3261E),
    thinking = Color(0xFF2B6293),
    track = Color(0xFFE6E4DE),
    hairline = Color(0xFFE2E0D9),
)

private val MobieDarkColors = darkColorScheme(
    primary = Color(0xFFF0A848),
    onPrimary = Color(0xFF1B1A17),
    primaryContainer = Color(0xFF3D2E14),
    onPrimaryContainer = Color(0xFFFFD9A3),
    secondary = Color(0xFFC9C7BF),
    onSecondary = Color(0xFF1B1A17),
    secondaryContainer = Color(0xFF363633),
    onSecondaryContainer = Color(0xFFECEBE6),
    tertiary = Color(0xFF8DB8E2),
    onTertiary = Color(0xFF10202F),
    tertiaryContainer = Color(0xFF1E2C39),
    onTertiaryContainer = Color(0xFFCFE2F4),
    background = Color(0xFF1C1C1A),
    onBackground = Color(0xFFECEBE6),
    surface = Color(0xFF1C1C1A),
    onSurface = Color(0xFFECEBE6),
    surfaceVariant = Color(0xFF2E2E2B),
    onSurfaceVariant = Color(0xFFABA9A1),
    surfaceContainerLowest = Color(0xFF161614),
    surfaceContainerLow = Color(0xFF21211F),
    surfaceContainer = Color(0xFF262624),
    surfaceContainerHigh = Color(0xFF2E2E2B),
    surfaceContainerHighest = Color(0xFF363633),
    outline = Color(0xFF6E6C65),
    outlineVariant = Color(0xFF3A3A36),
    error = Color(0xFFF2867D),
    onError = Color(0xFF1B1A17),
    inverseSurface = Color(0xFFECEBE6),
    inverseOnSurface = Color(0xFF1C1C1A),
    inversePrimary = Color(0xFF94500A),
)

private val MobieLightColors = lightColorScheme(
    primary = Color(0xFF94500A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE2BD),
    onPrimaryContainer = Color(0xFF3A2000),
    secondary = Color(0xFF5C5A53),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEECE6),
    onSecondaryContainer = Color(0xFF1F1E1B),
    tertiary = Color(0xFF2B6293),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD9E7F5),
    onTertiaryContainer = Color(0xFF0B2233),
    background = Color(0xFFFBFAF7),
    onBackground = Color(0xFF1F1E1B),
    surface = Color(0xFFFBFAF7),
    onSurface = Color(0xFF1F1E1B),
    surfaceVariant = Color(0xFFEEECE6),
    onSurfaceVariant = Color(0xFF5C5A53),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F4EF),
    surfaceContainer = Color(0xFFF0EEE9),
    surfaceContainerHigh = Color(0xFFE9E7E1),
    surfaceContainerHighest = Color(0xFFE3E1DA),
    outline = Color(0xFF8D8A82),
    outlineVariant = Color(0xFFDDDAD3),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF31302C),
    inverseOnSurface = Color(0xFFF4F2EC),
    inversePrimary = Color(0xFFF0A848),
)

/** The platform sans (Roboto / the OEM default) for everything people read. */
private val BodyFace = FontFamily.Default

/** IBM Plex Mono, bundled. Used only for code. */
private val CodeFace = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
)

// Comfortable reading sizes: chat and body text at 16sp with a 1.5 line height; nothing below 12sp.
private val MobieTypography = Typography(
    displaySmall = TextStyle(fontFamily = BodyFace, fontSize = 32.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = BodyFace, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = BodyFace, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = BodyFace, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = BodyFace, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium = TextStyle(fontFamily = BodyFace, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = BodyFace, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = BodyFace, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = BodyFace, fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = BodyFace, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.1.sp),
    labelLarge = TextStyle(fontFamily = BodyFace, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = BodyFace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = BodyFace, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

// Tabular figures keep live values (tok/s, progress) from shifting as digits change.
private const val TabularFigures = "tnum"
private val MobieNumeric = MobieNumericType(
    large = TextStyle(fontFamily = BodyFace, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularFigures),
    medium = TextStyle(fontFamily = BodyFace, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularFigures),
    small = TextStyle(fontFamily = BodyFace, fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TabularFigures),
    tiny = TextStyle(fontFamily = BodyFace, fontSize = 12.sp, lineHeight = 16.sp, fontFeatureSettings = TabularFigures),
    code = TextStyle(fontFamily = CodeFace, fontSize = 14.sp, lineHeight = 21.sp),
)

// Soft, consistent rounding: chips and small controls 8dp, cards 16dp, sheets and bubbles 20dp+.
private val MobieShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

private val LocalMobieSignals = staticCompositionLocalOf { DarkSignals }
private val LocalMobieNumeric = staticCompositionLocalOf { MobieNumeric }

/** Access to Mobie-specific tokens alongside [MaterialTheme]. */
object Mobie {
    val signals: MobieSignals
        @Composable get() = LocalMobieSignals.current
    val numeric: MobieNumericType
        @Composable get() = LocalMobieNumeric.current
}

@Composable
fun MobieTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val view = LocalView.current
    val colors = if (darkTheme) MobieDarkColors else MobieLightColors
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        window.navigationBarColor = colors.background.toArgb()
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }
    CompositionLocalProvider(
        LocalMobieSignals provides if (darkTheme) DarkSignals else LightSignals,
        LocalMobieNumeric provides MobieNumeric,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = MobieTypography,
            shapes = MobieShapes,
            content = content,
        )
    }
}
