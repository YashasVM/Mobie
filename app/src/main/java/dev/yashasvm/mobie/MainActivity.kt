package dev.yashasvm.mobie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.yashasvm.mobie.ui.MobieApp
import dev.yashasvm.mobie.ui.theme.MobieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as MobieApplication).container
            val preferences = remember { container.appContext.getSharedPreferences("ui_preferences", MODE_PRIVATE) }
            var darkTheme by rememberSaveable { mutableStateOf(preferences.getBoolean("dark_theme", true)) }
            MobieTheme(darkTheme = darkTheme) {
                MobieApp(
                    container = container,
                    darkTheme = darkTheme,
                    onDarkThemeChange = {
                        darkTheme = it
                        preferences.edit().putBoolean("dark_theme", it).apply()
                    },
                )
            }
        }
    }
}
