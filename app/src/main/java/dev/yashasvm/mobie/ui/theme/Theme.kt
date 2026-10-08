package dev.yashasvm.mobie.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
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

/** Monospace styles for numbers that are measured or estimated (tok/s, RAM, sizes, latency). */
@Immutable
data class MobieMonoType(
    val large: TextStyle,
    val medium: TextStyle,
    val small: TextStyle,
    val tiny: TextStyle,
)

private val DarkSignals = MobieSignals(
    ready = Color(0xFFB4F05A),
    caution = Color(0xFFF5B841),
    blocked = Color(0xFFFF6B5E),
    thinking = Color(0xFF6CC7FF),
    track = Color(0xFF1C2024),
    hairline = Color(0xFF24292E),
)

private val LightSignals = MobieSignals(
    ready = Color(0xFF3D7A0C),
    caution = Color(0xFFA86A00),
    blocked = Color(0xFFC62E22),
    thinking = Color(0xFF0B6DA8),
    track = Color(0xFFE6E7E2),
    hairline = Color(0xFFDADBD5),
)

private val MobieDarkColors = darkColorScheme(
    primary = Color(0xFFB4F05A),
    onPrimary = Color(0xFF0D1400),
    primaryContainer = Color(0xFF243312),
    onPrimaryContainer = Color(0xFFD6FA9E),
    secondary = Color(0xFF9AA4AE),
    onSecondary = Color(0xFF0B0D0F),
    secondaryContainer = Color(0xFF1C2024),
    onSecondaryContainer = Color(0xFFE3E7EA),
    tertiary = Color(0xFF6CC7FF),
    onTertiary = Color(0xFF00243A),
    tertiaryContainer = Color(0xFF0E2C40),
    onTertiaryContainer = Color(0xFFC8E8FF),
    background = Color(0xFF08090A),
    onBackground = Color(0xFFECEEF0),
    surface = Color(0xFF101214),
    onSurface = Color(0xFFECEEF0),
    surfaceVariant = Color(0xFF171A1D),
    onSurfaceVariant = Color(0xFF8B949D),
    surfaceContainerLowest = Color(0xFF08090A),
    surfaceContainerLow = Color(0xFF0D0F11),
    surfaceContainer = Color(0xFF101214),
    surfaceContainerHigh = Color(0xFF15181B),
    surfaceContainerHighest = Color(0xFF1B1F22),
    outline = Color(0xFF3A4148),
    outlineVariant = Color(0xFF24292E),
    error = Color(0xFFFF6B5E),
    onError = Color(0xFF2A0400),
)

private val MobieLightColors = lightColorScheme(
    primary = Color(0xFF2F5F08),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF1C2),
    onPrimaryContainer = Color(0xFF142400),
    secondary = Color(0xFF5B636B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8E9E4),
    onSecondaryContainer = Color(0xFF181B1E),
    tertiary = Color(0xFF0B6DA8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3EBFA),
    onTertiaryContainer = Color(0xFF002235),
    background = Color(0xFFF4F4F0),
    onBackground = Color(0xFF111315),
    surface = Color(0xFFFBFBF8),
    onSurface = Color(0xFF111315),
    surfaceVariant = Color(0xFFEDEEE9),
    onSurfaceVariant = Color(0xFF5E666E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7F3),
    surfaceContainer = Color(0xFFFBFBF8),
    surfaceContainerHigh = Color(0xFFEFEFEA),
    surfaceContainerHighest = Color(0xFFE8E9E4),
    outline = Color(0xFFB9BCB5),
    outlineVariant = Color(0xFFDADBD5),
    error = Color(0xFFC62E22),
)

private val DisplayFace = FontFamily(Font(R.font.manrope))
private val MessageFace = FontFamily.SansSerif
private val MonoFace = FontFamily.Monospace

private val MobieTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFace, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.2).sp),
    headlineLarge = TextStyle(fontFamily = DisplayFace, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFace, fontSize = 23.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = DisplayFace, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = DisplayFace, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = MessageFace, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = MessageFace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = MessageFace, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = MessageFace, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = MessageFace, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = MessageFace, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = MessageFace, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.1.sp),
    labelSmall = TextStyle(fontFamily = MessageFace, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val MobieMono = MobieMonoType(
    large = TextStyle(fontFamily = MonoFace, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    medium = TextStyle(fontFamily = MonoFace, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    small = TextStyle(fontFamily = MonoFace, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    tiny = TextStyle(fontFamily = MonoFace, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

private val LocalMobieSignals = staticCompositionLocalOf { DarkSignals }
private val LocalMobieMono = staticCompositionLocalOf { MobieMono }

/** Access to Mobie-specific tokens alongside [MaterialTheme]. */
object Mobie {
    val signals: MobieSignals
        @Composable get() = LocalMobieSignals.current
    val mono: MobieMonoType
        @Composable get() = LocalMobieMono.current
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
        LocalMobieMono provides MobieMono,
    ) {
        MaterialTheme(
            colorScheme = colors,
            typography = MobieTypography,
            content = content,
        )
    }
}
