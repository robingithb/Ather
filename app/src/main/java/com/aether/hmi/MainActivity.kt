package com.aether.hmi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.hmi.ui.shell.AetherApp
import com.aether.hmi.ui.theme.AetherTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemBars()
        setContent {
            val repository = (application as AetherApplication).container.settings
            val settings by repository.state.collectAsStateWithLifecycle()
            LaunchedEffect(settings.brightness, settings.autoBrightness) {
                window.attributes = window.attributes.apply {
                    screenBrightness = if (settings.autoBrightness) -1f else settings.brightness.coerceIn(.05f, 1f)
                }
            }
            AetherTheme(settings) {
                AetherApp(settings, repository::update)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Reapply after attachment, rotation and returning from the document picker.
        if (hasFocus) hideSystemBars()
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        window.decorView.requestApplyInsets()
    }
}
