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
 * The bundled monospace face keeps live values aligned.
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

// Palette shared with the mobie(1) website: a warm near-black terminal, one amber accent, and
// classic ANSI-ish status colors. Light mode is the same idea printed on paper.
private val DarkSignals = MobieSignals(
    ready = Color(0xFF8FD16A),
    caution = Color(0xFFE8D27A),
    blocked = Color(0xFFFF6B57),
    thinking = Color(0xFF7FB4D9),
    track = Color(0xFF22211D),
    hairline = Color(0xFF2A2925),
)

private val LightSignals = MobieSignals(
    ready = Color(0xFF3F7A1E),
    caution = Color(0xFF8A6A00),
    blocked = Color(0xFFC0391F),
    thinking = Color(0xFF2D6A93),
    track = Color(0xFFE4E0D3),
    hairline = Color(0xFFD3CEBF),
)

private val MobieDarkColors = darkColorScheme(
    primary = Color(0xFFFFB547),
    onPrimary = Color(0xFF0C0C0B),
    primaryContainer = Color(0xFF3A2A10),
    onPrimaryContainer = Color(0xFFFFD9A0),
    secondary = Color(0xFF6F6C63),
    onSecondary = Color(0xFF0C0C0B),
    secondaryContainer = Color(0xFF1C1B18),
    onSecondaryContainer = Color(0xFFD6D3C8),
    tertiary = Color(0xFF7FB4D9),
    onTertiary = Color(0xFF0C0C0B),
    tertiaryContainer = Color(0xFF15222B),
    onTertiaryContainer = Color(0xFFC6DDED),
    background = Color(0xFF0C0C0B),
    onBackground = Color(0xFFD6D3C8),
    surface = Color(0xFF0C0C0B),
    onSurface = Color(0xFFD6D3C8),
    surfaceVariant = Color(0xFF141412),
    onSurfaceVariant = Color(0xFF8A877D),
    surfaceContainerLowest = Color(0xFF080807),
    surfaceContainerLow = Color(0xFF10100E),
    surfaceContainer = Color(0xFF141412),
    surfaceContainerHigh = Color(0xFF191916),
    surfaceContainerHighest = Color(0xFF1F1E1B),
    outline = Color(0xFF6F6C63),
    outlineVariant = Color(0xFF2A2925),
    error = Color(0xFFFF6B57),
    onError = Color(0xFF0C0C0B),
    inverseSurface = Color(0xFFD6D3C8),
    inverseOnSurface = Color(0xFF0C0C0B),
    inversePrimary = Color(0xFF8A5A00),
)

private val MobieLightColors = lightColorScheme(
    primary = Color(0xFF9A5B00),
    onPrimary = Color(0xFFFFFBF2),
    primaryContainer = Color(0xFFFFE2B5),
    onPrimaryContainer = Color(0xFF2E1B00),
    secondary = Color(0xFF6A665C),
    onSecondary = Color(0xFFFFFBF2),
    secondaryContainer = Color(0xFFEAE6DA),
    onSecondaryContainer = Color(0xFF1D1C18),
    tertiary = Color(0xFF2D6A93),
    onTertiary = Color(0xFFFFFBF2),
    tertiaryContainer = Color(0xFFD6E7F2),
    onTertiaryContainer = Color(0xFF0B2233),
    background = Color(0xFFF4F1E8),
    onBackground = Color(0xFF1D1C18),
    surface = Color(0xFFF4F1E8),
    onSurface = Color(0xFF1D1C18),
    surfaceVariant = Color(0xFFECE8DC),
    onSurfaceVariant = Color(0xFF5E5A50),
    surfaceContainerLowest = Color(0xFFFBF9F3),
    surfaceContainerLow = Color(0xFFF7F4EC),
    surfaceContainer = Color(0xFFEFEBE0),
    surfaceContainerHigh = Color(0xFFE9E5D9),
    surfaceContainerHighest = Color(0xFFE2DED1),
    outline = Color(0xFF8C887C),
    outlineVariant = Color(0xFFD3CEBF),
    error = Color(0xFFC0391F),
    onError = Color(0xFFFFFBF2),
)

/** IBM Plex Mono, the face the website uses. Everything in Mobie is set in it. */
private val TerminalFace = FontFamily(
    Font(R.font.plex_mono_regular, FontWeight.Normal),
    Font(R.font.plex_mono_medium, FontWeight.Medium),
    Font(R.font.plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.plex_mono_bold, FontWeight.Bold),
)

// Never go below 12sp: these are read on a phone at arm's length, often while text streams in.
// A monospace face runs wide, so sizes sit a step below a proportional scale.
private val MobieTypography = Typography(
    displaySmall = TextStyle(fontFamily = TerminalFace, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = TerminalFace, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.6).sp),
    headlineMedium = TextStyle(fontFamily = TerminalFace, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = TerminalFace, fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = TerminalFace, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = TerminalFace, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = TerminalFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = TerminalFace, fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = TerminalFace, fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontFamily = TerminalFace, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontFamily = TerminalFace, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = TerminalFace, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = TerminalFace, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
)

// Monospace digits are already fixed-width, so live values (tok/s, progress) never jitter.
private val MobieNumeric = MobieNumericType(
    large = TextStyle(fontFamily = TerminalFace, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    medium = TextStyle(fontFamily = TerminalFace, fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
    small = TextStyle(fontFamily = TerminalFace, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium),
    tiny = TextStyle(fontFamily = TerminalFace, fontSize = 12.sp, lineHeight = 16.sp),
    code = TextStyle(fontFamily = TerminalFace, fontSize = 14.sp, lineHeight = 21.sp),
)

// Tight corners keep the terminal character while softening sheets and composer controls.
private val MobieShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
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
