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
 * Status colors that Material's scheme does not model. A given color always means the same thing
 * on every screen: [ready] runs here, [caution] needs a step first, [blocked] will not run.
 */
@Immutable
data class MobieSignals(
    val ready: Color,
    val caution: Color,
    val blocked: Color,
    val thinking: Color,
    /** Faint fill for gauges, tracks, and inactive meter segments. */
    val track: Color,
    /** Hairline used for card borders and dividers. */
    val hairline: Color,
)

/** Shared styles for measured values and literal code. */
@Immutable
data class MobieNumericType(
    val large: TextStyle,
    val medium: TextStyle,
    val small: TextStyle,
    val tiny: TextStyle,
    /** Code blocks and model ids pasted from the catalog. */
    val code: TextStyle,
)

// Both palettes are designed independently so surfaces and text retain contrast in either mode.
private val DarkSignals = MobieSignals(
    ready = Color(0xFFA2C79F),
    caution = Color(0xFFE2C088),
    blocked = Color(0xFFFFAA9C),
    thinking = Color(0xFFA5BEDA),
    track = Color(0xFF343431),
    hairline = Color(0xFF3B3B37),
)

private val LightSignals = MobieSignals(
    ready = Color(0xFF356534),
    caution = Color(0xFF795413),
    blocked = Color(0xFFAC3829),
    thinking = Color(0xFF365F87),
    track = Color(0xFFE7E5DF),
    hairline = Color(0xFFE0DDD5),
)

private val MobieDarkColors = darkColorScheme(
    primary = Color(0xFFF5F4EF),
    onPrimary = Color(0xFF20201E),
    primaryContainer = Color(0xFF393936),
    onPrimaryContainer = Color(0xFFF5F4EF),
    secondary = Color(0xFFC7C5BD),
    onSecondary = Color(0xFF20201E),
    secondaryContainer = Color(0xFF30302D),
    onSecondaryContainer = Color(0xFFE7E5DE),
    tertiary = Color(0xFFE4A087),
    onTertiary = Color(0xFF342018),
    tertiaryContainer = Color(0xFF50352B),
    onTertiaryContainer = Color(0xFFFFD8C8),
    background = Color(0xFF151515),
    onBackground = Color(0xFFF3F2EC),
    surface = Color(0xFF1B1B1A),
    onSurface = Color(0xFFF3F2EC),
    surfaceVariant = Color(0xFF2B2B28),
    onSurfaceVariant = Color(0xFFB9B7AE),
    surfaceContainerLowest = Color(0xFF121212),
    surfaceContainerLow = Color(0xFF1C1C1A),
    surfaceContainer = Color(0xFF222220),
    surfaceContainerHigh = Color(0xFF2B2B28),
    surfaceContainerHighest = Color(0xFF343431),
    outline = Color(0xFF85837A),
    outlineVariant = Color(0xFF3B3B37),
    error = Color(0xFFFFAA9C),
    onError = Color(0xFF3A100B),
    errorContainer = Color(0xFF54231C),
    onErrorContainer = Color(0xFFFFDAD2),
    inverseSurface = Color(0xFFF3F2EC),
    inverseOnSurface = Color(0xFF20201E),
    inversePrimary = Color(0xFF373732),
)

private val MobieLightColors = lightColorScheme(
    primary = Color(0xFF2C2C28),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEAE8E1),
    onPrimaryContainer = Color(0xFF292925),
    secondary = Color(0xFF605F57),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEEECE5),
    onSecondaryContainer = Color(0xFF35352F),
    tertiary = Color(0xFF9E5039),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF9DFD3),
    onTertiaryContainer = Color(0xFF572817),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF252522),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF252522),
    surfaceVariant = Color(0xFFEEECE6),
    onSurfaceVariant = Color(0xFF636158),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F6F2),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0EEE8),
    surfaceContainerHighest = Color(0xFFE9E7DF),
    outline = Color(0xFF858278),
    outlineVariant = Color(0xFFE0DDD5),
    error = Color(0xFFAC3829),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD2),
    onErrorContainer = Color(0xFF4A160D),
    inverseSurface = Color(0xFF30302C),
    inverseOnSurface = Color(0xFFF7F6F0),
    inversePrimary = Color(0xFFE7E5DE),
)

/** Monospace is reserved for code and literal identifiers. */
private val MonoFace = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
)

/**
 * Serif display text gives the welcome screen character; controls and reading text use sans.
 * Monospace is reserved for literal code. Nothing goes below 12sp.
 */
private val SansFace = FontFamily.Default

private val MobieTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Serif, fontSize = 36.sp, lineHeight = 44.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = SansFace, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = SansFace, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = SansFace, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = SansFace, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = SansFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    // Body: sans at 15-16sp with a 1.5 line height.
    bodyLarge = TextStyle(fontFamily = SansFace, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = SansFace, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = SansFace, fontSize = 13.sp, lineHeight = 19.sp),
    // Buttons, labels, and other controls share a comfortable sans face.
    labelLarge = TextStyle(fontFamily = SansFace, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = SansFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = SansFace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
)

// Use the same readable sans face for numbers; code keeps its literal monospace style.
private val MobieNumeric = MobieNumericType(
    large = TextStyle(fontFamily = SansFace, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    medium = TextStyle(fontFamily = SansFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    small = TextStyle(fontFamily = SansFace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    tiny = TextStyle(fontFamily = SansFace, fontSize = 12.sp, lineHeight = 16.sp),
    code = TextStyle(fontFamily = MonoFace, fontSize = 13.sp, lineHeight = 20.sp),
)

// Generous corners keep cards and sheets visually quiet.
private val MobieShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
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
        window.statusBarColor = Color.Transparent.toArgb()
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
