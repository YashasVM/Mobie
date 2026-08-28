package dev.yashasvm.mobie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.yashasvm.mobie.ui.MobieApp
import dev.yashasvm.mobie.ui.theme.MobieTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobieTheme {
                MobieApp((application as MobieApplication).container)
            }
        }
    }
}
