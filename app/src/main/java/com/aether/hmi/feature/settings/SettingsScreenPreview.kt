package com.aether.hmi.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.aether.hmi.domain.AppSettings
import com.aether.hmi.domain.ConnectivityState
import com.aether.hmi.domain.NetworkTransport
import com.aether.hmi.ui.theme.AetherTheme

@Preview(name = "Settings landscape", widthDp = 960, heightDp = 540)
@Preview(name = "Settings short", widthDp = 640, heightDp = 320)
@Preview(name = "Settings head unit", widthDp = 1280, heightDp = 720)
@Preview(name = "Settings large text", widthDp = 640, heightDp = 320, fontScale = 1.5f)
@Composable
private fun SettingsAdaptivePreview() {
    AetherTheme { SettingsScreen(AppSettings(), {}, ConnectivityState(true, NetworkTransport.Wifi)) }
}

@Preview(name = "Sound compact controls", widthDp = 640, heightDp = 320)
@Preview(name = "Sound head unit controls", widthDp = 1280, heightDp = 720)
@Preview(name = "Sound large text", widthDp = 640, heightDp = 320, fontScale = 1.5f)
@Composable
private fun SettingsSoundPreview() {
    AetherTheme { SoundPreferences(AppSettings(), {}) }
}

@Preview(name = "Small theme gallery", widthDp = 640, heightDp = 320)
@Preview(name = "Theme gallery head unit", widthDp = 1280, heightDp = 720)
@Preview(name = "Theme gallery large text", widthDp = 640, heightDp = 320, fontScale = 1.5f)
@Composable
private fun SettingsAppearancePreview() {
    AetherTheme { AppearancePreferences(AppSettings(), {}) }
}
