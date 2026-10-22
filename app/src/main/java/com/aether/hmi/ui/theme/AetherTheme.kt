package com.aether.hmi.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AetherColors {
    val Background = Color(0xFF020A14)
    val Panel = Color(0xFF071B2B)
    val Rail = Color(0xFF00080D)
    val RailBorder = Color(0xFF12364A)
    val Accent = Color(0xFF00DFFF)
    val TextPrimary = Color(0xFFF2F7FF)
    val TextSecondary = Color(0xFFB8D1EF)
    val Good = Color(0xFF16DB9A)
    val Warning = Color(0xFFFFAC3C)
    val Border = Color(0xFF1C536F)
    val Muted = Color(0xFF758CA1)
}

private val Colors = darkColorScheme(
    primary = AetherColors.Accent,
    onPrimary = AetherColors.Background,
    background = AetherColors.Background,
    onBackground = AetherColors.TextPrimary,
    surface = AetherColors.Panel,
    onSurface = AetherColors.TextPrimary,
    onSurfaceVariant = AetherColors.TextSecondary,
)

@Composable
fun AetherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
