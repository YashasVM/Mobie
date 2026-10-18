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

// Palette taken from the Mobie site: warm near-black, a single amber accent, terminal status colors.
private val DarkSignals = MobieSignals(
    ready = Color(0xFF8FD16A),
    caution = Color(0xFFE8D27A),
    blocked = Color(0xFFFF6B57),
    thinking = Color(0xFF7FB4D9),
    track = Color(0xFF2A2925),
    hairline = Color(0xFF2A2925),
)

private val LightSignals = MobieSignals(
    ready = Color(0xFF3E7A22),
    caution = Color(0xFF7A6208),
    blocked = Color(0xFFB8402F),
    thinking = Color(0xFF2F6590),
    track = Color(0xFFE4DFD0),
    hairline = Color(0xFFDDD7C6),
)

private val MobieDarkColors = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF0C0C0B),
    primaryContainer = Color(0xFF3A2A0E),
    onPrimaryContainer = Color(0xFFFFD99A),
    secondary = Color(0xFFD6D3C8),
    onSecondary = Color(0xFF0C0C0B),
    secondaryContainer = Color(0xFF22211E),
    onSecondaryContainer = Color(0xFFD6D3C8),
    tertiary = Color(0xFF7FB4D9),
    onTertiary = Color(0xFF0A2236),
    tertiaryContainer = Color(0xFF1B3346),
    onTertiaryContainer = Color(0xFFCBE5F8),
    background = Color(0xFF0C0C0B),
    onBackground = Color(0xFFD6D3C8),
    surface = Color(0xFF141412),
    onSurface = Color(0xFFD6D3C8),
    surfaceVariant = Color(0xFF1E1E1B),
    onSurfaceVariant = Color(0xFF9A978B),
    surfaceContainerLowest = Color(0xFF090908),
    surfaceContainerLow = Color(0xFF111110),
    surfaceContainer = Color(0xFF161614),
    surfaceContainerHigh = Color(0xFF1E1E1B),
    surfaceContainerHighest = Color(0xFF282723),
    outline = Color(0xFF6F6C63),
    outlineVariant = Color(0xFF2A2925),
    error = Color(0xFFFF6B57),
    onError = Color(0xFF2E0B06),
    errorContainer = Color(0xFF4A1D16),
    onErrorContainer = Color(0xFFFFDAD2),
    inverseSurface = Color(0xFFD6D3C8),
    inverseOnSurface = Color(0xFF141412),
    inversePrimary = Color(0xFF8A5300),
)

private val MobieLightColors = lightColorScheme(
    primary = Color(0xFF9A5C00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE3B3),
    onPrimaryContainer = Color(0xFF3B2400),
    secondary = Color(0xFF4F4C42),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEAE5D6),
    onSecondaryContainer = Color(0xFF2C2A24),
    tertiary = Color(0xFF2F6590),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E6F6),
    onTertiaryContainer = Color(0xFF0B2A44),
    background = Color(0xFFF5F2E9),
    onBackground = Color(0xFF1F1E1A),
    surface = Color(0xFFFBF9F3),
    onSurface = Color(0xFF1F1E1A),
    surfaceVariant = Color(0xFFECE7D8),
    onSurfaceVariant = Color(0xFF625F54),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9F6EE),
    surfaceContainer = Color(0xFFFBF9F3),
    surfaceContainerHigh = Color(0xFFEFEBDD),
    surfaceContainerHighest = Color(0xFFE6E1D0),
    outline = Color(0xFF8A867A),
    outlineVariant = Color(0xFFDDD7C6),
    error = Color(0xFFB8402F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD2),
    onErrorContainer = Color(0xFF4A160D),
    inverseSurface = Color(0xFF2B2A26),
    inverseOnSurface = Color(0xFFF5F2E9),
    inversePrimary = Color(0xFFFFB547),
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
    displaySmall = TextStyle(fontFamily = MonoFace, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = MonoFace, fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = MonoFace, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = MonoFace, fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = MonoFace, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = SansFace, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = SansFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    // Reading text stays sans for long replies; chrome and measurements are mono.
    bodyLarge = TextStyle(fontFamily = SansFace, fontSize = 16.sp, lineHeight = 25.sp),
    bodyMedium = TextStyle(fontFamily = SansFace, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = SansFace, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = MonoFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = MonoFace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = MonoFace, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

// Measured values are mono so digits align, as on the site.
private val MobieNumeric = MobieNumericType(
    large = TextStyle(fontFamily = MonoFace, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    medium = TextStyle(fontFamily = MonoFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    small = TextStyle(fontFamily = MonoFace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    tiny = TextStyle(fontFamily = MonoFace, fontSize = 12.sp, lineHeight = 16.sp),
    code = TextStyle(fontFamily = MonoFace, fontSize = 13.sp, lineHeight = 20.sp),
)

// Softer than the site's square corners so touch targets feel native, but tighter than before.
// Sharp by design: square corners everywhere, with a 2dp softening only on tiny chips.
private val MobieShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
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
