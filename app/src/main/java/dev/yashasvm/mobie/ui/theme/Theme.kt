package dev.yashasvm.mobie.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFF4857D8),
    onPrimary = Color.White,
    secondary = Color(0xFF53606F),
    background = Color(0xFFF8F9FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF0F6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBCC2FF),
    background = Color(0xFF101116),
    surface = Color(0xFF18191F),
    surfaceVariant = Color(0xFF24262F),
)

@Composable
fun MobieTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
    }
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
