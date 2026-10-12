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

/** Mono styles for numbers and chrome that are measured or estimated (tok/s, RAM, sizes, latency). */
@Immutable
data class MobieNumericType(
    val large: TextStyle,
    val medium: TextStyle,
    val small: TextStyle,
    val tiny: TextStyle,
    /** Code blocks and model ids pasted from the catalog. */
    val code: TextStyle,
)

// Near-black, not pure black, so raised cards read as surfaces. One warm amber accent is used
// sparingly (primary actions, selection). Green is reserved for "ready".
private val DarkSignals = MobieSignals(
    ready = Color(0xFF8FD16A),
    caution = Color(0xFFE8C872),
    blocked = Color(0xFFFF8A78),
    thinking = Color(0xFF8CC4E6),
    track = Color(0xFF1E2427),
    hairline = Color(0xFF262D30),
)

private val LightSignals = MobieSignals(
    ready = Color(0xFF2F6B12),
    caution = Color(0xFF7A5B00),
    blocked = Color(0xFFB3301A),
    thinking = Color(0xFF225E85),
    track = Color(0xFFE2E2DB),
    hairline = Color(0xFFD9D9D1),
)

private val MobieDarkColors = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF0B0D0E),
    primaryContainer = Color(0xFF3A2A10),
    onPrimaryContainer = Color(0xFFFFD9A0),
    secondary = Color(0xFFA9B2AF),
    onSecondary = Color(0xFF0B0D0E),
    secondaryContainer = Color(0xFF1E2427),
    onSecondaryContainer = Color(0xFFD9E0DD),
    tertiary = Color(0xFF8CC4E6),
    onTertiary = Color(0xFF0B0D0E),
    tertiaryContainer = Color(0xFF142430),
    onTertiaryContainer = Color(0xFFCBE5F5),
    background = Color(0xFF0B0D0E),
    onBackground = Color(0xFFE6EAE9),
    surface = Color(0xFF101415),
    onSurface = Color(0xFFE6EAE9),
    surfaceVariant = Color(0xFF171C1E),
    onSurfaceVariant = Color(0xFFA7B0AD),
    surfaceContainerLowest = Color(0xFF080A0B),
    surfaceContainerLow = Color(0xFF0F1213),
    surfaceContainer = Color(0xFF151A1C),
    surfaceContainerHigh = Color(0xFF1B2124),
    surfaceContainerHighest = Color(0xFF232A2D),
    outline = Color(0xFF6A7478),
    outlineVariant = Color(0xFF262D30),
    error = Color(0xFFFF8A78),
    onError = Color(0xFF0B0D0E),
    inverseSurface = Color(0xFFE6EAE9),
    inverseOnSurface = Color(0xFF0B0D0E),
    inversePrimary = Color(0xFF8A5A00),
)

private val MobieLightColors = lightColorScheme(
    primary = Color(0xFF8A5000),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFE2B5),
    onPrimaryContainer = Color(0xFF2E1B00),
    secondary = Color(0xFF555D5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3E7E5),
    onSecondaryContainer = Color(0xFF1A1F20),
    tertiary = Color(0xFF225E85),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD3E6F3),
    onTertiaryContainer = Color(0xFF0A2232),
    background = Color(0xFFF5F6F3),
    onBackground = Color(0xFF14191B),
    surface = Color(0xFFFCFCFA),
    onSurface = Color(0xFF14191B),
    surfaceVariant = Color(0xFFEBEEEB),
    onSurfaceVariant = Color(0xFF4B5452),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF9FAF8),
    surfaceContainer = Color(0xFFF0F2EF),
    surfaceContainerHigh = Color(0xFFE8EBE7),
    surfaceContainerHighest = Color(0xFFE0E4E0),
    outline = Color(0xFF7A8381),
    outlineVariant = Color(0xFFD9D9D1),
    error = Color(0xFFB3301A),
    onError = Color(0xFFFFFFFF),
)

/** IBM Plex Mono: chrome only (labels, stats, model ids, code, status lines). */
private val MonoFace = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
)

/**
 * Headings and body use the platform sans so long text reads comfortably. Mono is reserved for
 * chrome. Nothing goes below 12sp: these are read on a phone at arm's length.
 */
private val SansFace = FontFamily.Default

private val MobieTypography = Typography(
    displaySmall = TextStyle(fontFamily = SansFace, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = SansFace, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = SansFace, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    headlineSmall = TextStyle(fontFamily = SansFace, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = SansFace, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = SansFace, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = SansFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    // Body: sans at 15-16sp with a 1.5 line height.
    bodyLarge = TextStyle(fontFamily = SansFace, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = SansFace, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = SansFace, fontSize = 13.sp, lineHeight = 19.sp),
    // Buttons and other controls read as sans; the mono face is for chrome text.
    labelLarge = TextStyle(fontFamily = SansFace, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = MonoFace, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = MonoFace, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

// Monospace digits are fixed-width, so live values (tok/s, progress) never jitter.
private val MobieNumeric = MobieNumericType(
    large = TextStyle(fontFamily = MonoFace, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    medium = TextStyle(fontFamily = MonoFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    small = TextStyle(fontFamily = MonoFace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    tiny = TextStyle(fontFamily = MonoFace, fontSize = 12.sp, lineHeight = 16.sp),
    code = TextStyle(fontFamily = MonoFace, fontSize = 13.sp, lineHeight = 20.sp),
)

// Soft, rounded corners for a native Material feel. Cards are 14dp; sheets and large surfaces 20dp.
private val MobieShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
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
