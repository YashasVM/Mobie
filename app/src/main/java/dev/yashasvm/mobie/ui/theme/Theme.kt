package dev.yashasvm.mobie.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import dev.yashasvm.mobie.R

private val MobieLightColors = lightColorScheme(
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBFF),
    onPrimaryContainer = Color(0xFF003A70),
    secondary = Color(0xFF5D6470),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E7EC),
    onSecondaryContainer = Color(0xFF1A1C20),
    tertiary = Color(0xFF168A83),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB8F1E9),
    onTertiaryContainer = Color(0xFF00201D),
    background = Color(0xFFF7F7F9),
    onBackground = Color(0xFF151619),
    surface = Color.White,
    onSurface = Color(0xFF151619),
    surfaceVariant = Color(0xFFE9E9EE),
    onSurfaceVariant = Color(0xFF6D6E75),
    outline = Color(0xFFC7C8CD),
    outlineVariant = Color(0xFFE0E0E5),
    error = Color(0xFFD92D20),
)

private val MobieDarkColors = darkColorScheme(
    primary = Color(0xFFA9C1FF),
    onPrimary = Color(0xFF10254A),
    primaryContainer = Color(0xFF2A4787),
    onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFC1C9D7),
    onSecondary = Color(0xFF28313E),
    secondaryContainer = Color(0xFF2B323D),
    onSecondaryContainer = Color(0xFFE3E8F1),
    tertiary = Color(0xFF76DCCF),
    onTertiary = Color(0xFF003733),
    tertiaryContainer = Color(0xFF0D534D),
    onTertiaryContainer = Color(0xFF96F0E3),
    background = Color(0xFF0B0E12),
    onBackground = Color(0xFFF1F3F8),
    surface = Color(0xFF15191F),
    onSurface = Color(0xFFF1F3F8),
    surfaceVariant = Color(0xFF232933),
    onSurfaceVariant = Color(0xFFB8C0CD),
    outline = Color(0xFF66707E),
    outlineVariant = Color(0xFF353D48),
    error = Color(0xFFFFB4AB),
)

private val DisplayFace = FontFamily(Font(R.font.manrope))
private val MessageFace = FontFamily.SansSerif

private val MobieTypography = Typography(
    displaySmall = TextStyle(fontFamily = DisplayFace, fontSize = 32.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = DisplayFace, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = DisplayFace, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = MessageFace, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = MessageFace, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontFamily = MessageFace, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = MessageFace, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = MessageFace, fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = TextStyle(fontFamily = MessageFace, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = MessageFace, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = MessageFace, fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.15.sp),
    labelSmall = TextStyle(fontFamily = MessageFace, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
)

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
    MaterialTheme(
        colorScheme = colors,
        typography = MobieTypography,
        content = content,
    )
}
