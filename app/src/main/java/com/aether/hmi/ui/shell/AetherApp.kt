package com.aether.hmi.ui.shell

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.hmi.AetherApplication
import com.aether.hmi.R
import com.aether.hmi.domain.CabinAir
import com.aether.hmi.domain.AppSettings
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.domain.ConnectivityState
import com.aether.hmi.domain.DataSource
import com.aether.hmi.domain.NetworkTransport
import com.aether.hmi.domain.RoutePreview
import com.aether.hmi.domain.VehicleHealth
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.domain.VehicleState
import com.aether.hmi.feature.climate.ClimateActions
import com.aether.hmi.feature.climate.ClimateScreen
import com.aether.hmi.feature.home.ClimateUpdate
import com.aether.hmi.feature.home.DashboardHeader
import com.aether.hmi.feature.home.DashboardState
import com.aether.hmi.feature.home.DashboardViewModel
import com.aether.hmi.feature.home.HomeScreen
import com.aether.hmi.feature.home.PlaybackActions
import com.aether.hmi.feature.media.MediaScreen
import com.aether.hmi.feature.media.MediaScreenActions
import com.aether.hmi.feature.media.PlaybackState
import com.aether.hmi.feature.media.PlaybackViewModel
import com.aether.hmi.feature.media.QueueDialog
import com.aether.hmi.feature.vehicle.VehicleActions
import com.aether.hmi.feature.vehicle.VehicleScreen
import com.aether.hmi.feature.settings.SettingsScreen
import com.aether.hmi.ui.navigation.AetherTab
import com.aether.hmi.ui.theme.AetherColors
import com.aether.hmi.ui.theme.AetherTheme
import com.aether.hmi.ui.theme.LocalAetherPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AetherApp(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as AetherApplication).container
    val dashboard: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(container))
    val playback: PlaybackViewModel = viewModel(factory = PlaybackViewModel.factory(context))
    val state by dashboard.state.collectAsStateWithLifecycle()
    val media by playback.state.collectAsStateWithLifecycle()
    val update by dashboard.climateUpdate.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(AetherTab.Home) }
    var showQueue by rememberSaveable { mutableStateOf(false) }
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { uri ->
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers only grant temporary access; that grant still supports this session.
            }
        }
        playback.playDocuments(uris)
    }
    val actions = PlaybackActions(
        toggle = playback::togglePlayback, previous = playback::previous, next = playback::next,
        favorite = playback::toggleFavorite, openQueue = { showQueue = true },
        openMedia = { selectedTab = AetherTab.Media }, chooseAudio = { audioPicker.launch(arrayOf("audio/*")) },
    )
    AetherShell(selectedTab, { selectedTab = it }, state, media, playback.position, actions, update,
        dashboard::adjustTemperature, dashboard::toggle, playback::playIndex,
        climateActions = ClimateActions(dashboard::adjustTemperature, dashboard::adjustPassengerTemperature,
            dashboard::adjustFanSpeed, dashboard::setAirflowMode, dashboard::toggle, dashboard::cycleSeatLevel),
        vehicleActions = VehicleActions(dashboard::toggleVehiclePart, dashboard::setVehicleLocked,
            dashboard::setHeadlightMode, dashboard::setWindowPosition, dashboard::toggleParkingBrake, dashboard::setCharging,
            dashboard::setTurnSignal, dashboard::setVehicleGear, dashboard::setBrakeLights, dashboard::setAmbientLights),
        settings = settings, onSettingsChange = onSettingsChange,
        mediaScreenActions = MediaScreenActions(playback::seek, playback::toggleShuffle,
            playback::cycleRepeat, playback::startDemo, playback::retry, playback::playDemo))
    if (showQueue) QueueDialog(media, { showQueue = false }, {
        showQueue = false
        audioPicker.launch(arrayOf("audio/*"))
    }, { index -> playback.playIndex(index); showQueue = false })
}

@Composable
fun AetherShell(
    selectedTab: AetherTab,
    onTabSelected: (AetherTab) -> Unit,
    state: DashboardState,
    media: PlaybackState,
    position: StateFlow<Long>,
    playbackActions: PlaybackActions,
    climateUpdate: ClimateUpdate,
    onAdjustTemperature: (Int) -> Unit,
    onClimateToggle: (ClimateControl) -> Unit,
    onPlayIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
    climateActions: ClimateActions = ClimateActions({}, {}, {}, {}, {}, { _, _ -> }),
    vehicleActions: VehicleActions = VehicleActions({}, {}, {}, { _, _ -> }, {}, {}),
    settings: AppSettings = AppSettings(),
    onSettingsChange: (AppSettings) -> Unit = {},
    mediaScreenActions: MediaScreenActions = MediaScreenActions(),
) {
    val screenStates = rememberSaveableStateHolder()
    val colors = LocalAetherPalette.current
    Box(modifier.fillMaxSize().background(AetherColors.Background).drawWithCache {
        val ambient = Brush.radialGradient(listOf(colors.Panel.copy(alpha = 0.5f), colors.Background),
            center = Offset(size.width * 0.35f, size.height * 0.5f), radius = size.width * 0.8f)
        onDrawBehind { drawRect(ambient) }
    }) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)) {
            val compactShell = maxHeight < 400.dp
            val shellGap = if (compactShell) 4.dp else 8.dp
            val content: @Composable (Modifier) -> Unit = { contentModifier ->
                val screenLabel = stringResource(R.string.screen_label, stringResource(selectedTab.labelRes))
                Column(contentModifier, verticalArrangement = Arrangement.spacedBy(shellGap)) {
                    val feedback = if (selectedTab == AetherTab.Climate && climateUpdate != ClimateUpdate.Idle) stringResource(when (climateUpdate) {
                        ClimateUpdate.Pending -> R.string.climate_updating
                        ClimateUpdate.Confirmed -> R.string.climate_saved
                        ClimateUpdate.Failed -> R.string.climate_failed
                        ClimateUpdate.Idle -> R.string.unavailable_value
                    }) else null
                    DashboardHeader(state, compact = compactShell, feedback = feedback,
                        feedbackWarning = climateUpdate == ClimateUpdate.Failed,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Box(Modifier.weight(1f).fillMaxWidth().semantics { paneTitle = screenLabel }) {
                        when (selectedTab) {
                            AetherTab.Home -> HomeScreen(state, media, onTabSelected, Modifier.fillMaxSize(), playbackActions,
                                widgets = settings.homeWidgets,
                                onWidgetsChange = { onSettingsChange(settings.copy(homeWidgets = it)) },
                                onAdjustTemperature = onAdjustTemperature, onClimateToggle = onClimateToggle)
                            AetherTab.Climate -> screenStates.SaveableStateProvider("climate") {
                                ClimateScreen(state.snapshot.climate, climateActions, Modifier.fillMaxSize())
                            }
                            AetherTab.Vehicle -> screenStates.SaveableStateProvider("vehicle") {
                                VehicleScreen(state.snapshot, vehicleActions, climateActions, Modifier.fillMaxSize())
                            }
                            AetherTab.Media -> screenStates.SaveableStateProvider("media") {
                                MediaScreen(media, position, playbackActions, mediaScreenActions, state.snapshot.climate,
                                    onAdjustTemperature, onClimateToggle, { onTabSelected(AetherTab.Climate) },
                                    onPlayIndex, Modifier.fillMaxSize())
                            }
                            AetherTab.Settings -> screenStates.SaveableStateProvider("settings") {
                                SettingsScreen(settings, onSettingsChange, state.connectivity, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
            // Every demo device is an infotainment display in landscape. Keep navigation
            // in one stable location; individual screens adapt to the available content area.
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(if (compactShell) 8.dp else 12.dp)) {
                AetherNavigationRail(selectedTab, onTabSelected, now = state.now)
                content(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Preview(name = "Home / 16:9 landscape", widthDp = 960, heightDp = 540)
@Preview(name = "Home / head unit", widthDp = 1280, heightDp = 720)
@Preview(name = "Home / landscape", widthDp = 880, heightDp = 400)
@Preview(name = "Home / compact landscape", widthDp = 640, heightDp = 320)
@Composable
private fun AetherShellPreview() {
    // Preview-only fixtures; the running app observes repositories and the media session.
    val state = DashboardState(
        snapshot = VehicleSnapshot(
            vehicle = VehicleState(DataSource.Demo, 87, 420, VehicleHealth.Healthy, true, true, true),
            climate = ClimateState(available = true, driverCelsius = 21f, auto = true, cabinAir = CabinAir.Clean),
            route = RoutePreview("Office", 12, 8.4),
        ), connectivity = ConnectivityState(true, NetworkTransport.Wifi),
    )
    val position = remember { MutableStateFlow(0L) }
    AetherTheme {
        AetherShell(AetherTab.Home, {}, state, PlaybackState(connected = true), position,
            PlaybackActions(), ClimateUpdate.Idle, {}, {}, {})
    }
}
