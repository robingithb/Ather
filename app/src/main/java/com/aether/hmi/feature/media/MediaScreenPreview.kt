package com.aether.hmi.feature.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.LocalContext
import com.aether.hmi.domain.AppSettings
import com.aether.hmi.domain.AppTheme
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.feature.home.ClimateUpdate
import com.aether.hmi.feature.home.DashboardState
import com.aether.hmi.feature.home.PlaybackActions
import com.aether.hmi.ui.navigation.AetherTab
import com.aether.hmi.ui.shell.AetherShell
import com.aether.hmi.ui.theme.AetherTheme
import kotlinx.coroutines.flow.MutableStateFlow

// Fixtures belong only to Android Studio previews. Runtime titles/artwork come from Media3.
@Preview(name = "Media · Landscape", widthDp = 960, heightDp = 540)
@Preview(name = "Media · Wide display", widthDp = 1280, heightDp = 720)
@Preview(name = "Media · Midnight", widthDp = 1200, heightDp = 650)
@Preview(name = "Media · Samsung display", widthDp = 780, heightDp = 360)
@Composable
private fun MediaMidnightPreview() = MediaPreview(AppTheme.Midnight)

@Preview(name = "Media · Arctic phone", widthDp = 880, heightDp = 400)
@Composable
private fun MediaArcticPreview() = MediaPreview(AppTheme.Arctic)

@Preview(name = "Media · Sport compact", widthDp = 640, heightDp = 320)
@Composable
private fun MediaSportPreview() = MediaPreview(AppTheme.Sport)

@Preview(name = "Media · Minimal large text", widthDp = 880, heightDp = 400, fontScale = 1.5f)
@Composable
private fun MediaMinimalPreview() = MediaPreview(AppTheme.Minimal)

@Preview(name = "Media · Demo suggestions", widthDp = 780, heightDp = 360)
@Composable
private fun MediaEmptyPreview() = MediaPreview(AppTheme.Midnight, empty = true)

@Composable
private fun MediaPreview(theme: AppTheme, empty: Boolean = false) {
    val settings = AppSettings(theme = theme)
    val position = remember { MutableStateFlow(9_000L) }
    val context = LocalContext.current
    val tracks = remember(context) { DemoMedia.queueTracks(context) }
    AetherTheme(settings) {
        AetherShell(AetherTab.Media, {}, DashboardState(snapshot = VehicleSnapshot(
            climate = ClimateState(available = true, driverCelsius = 21f, passengerCelsius = 21f, auto = true))),
            if (empty) PlaybackState(connected = true) else PlaybackState(
                connected = true, title = tracks[0].title, artist = tracks[0].artist, artworkUri = tracks[0].artworkUri,
                hasMedia = true, playing = true, durationMs = 24_000, canNext = true, canSeek = true,
                queue = tracks, currentIndex = 0, upcomingIndices = (1 until tracks.size).toList(), source = MediaSource.Demo),
            position, PlaybackActions(), ClimateUpdate.Idle, {}, {}, {}, settings = settings)
    }
}
