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
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.domain.ConnectivityState
import com.aether.hmi.domain.DataSource
import com.aether.hmi.domain.NetworkTransport
import com.aether.hmi.domain.RoutePreview
import com.aether.hmi.domain.VehicleHealth
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.domain.VehicleState
import com.aether.hmi.feature.home.ClimateUpdate
import com.aether.hmi.feature.home.DashboardDock
import com.aether.hmi.feature.home.DashboardHeader
import com.aether.hmi.feature.home.DashboardState
import com.aether.hmi.feature.home.DashboardViewModel
import com.aether.hmi.feature.home.HomeScreen
import com.aether.hmi.feature.home.PlaybackActions
import com.aether.hmi.feature.home.StatusChips
import com.aether.hmi.feature.media.MediaLibrary
import com.aether.hmi.feature.media.PlaybackState
import com.aether.hmi.feature.media.PlaybackViewModel
import com.aether.hmi.feature.media.QueueDialog
import com.aether.hmi.ui.navigation.AetherTab
import com.aether.hmi.ui.theme.AetherColors
import com.aether.hmi.ui.theme.AetherTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun AetherApp() {
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
        dashboard::adjustTemperature, dashboard::toggle, playback::playIndex)
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
) {
    var highlightedIndicators by rememberSaveable { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize().background(AetherColors.Background).drawWithCache {
        val ambient = Brush.radialGradient(listOf(AetherColors.Panel.copy(alpha = 0.5f), AetherColors.Background),
            center = Offset(size.width * 0.35f, size.height * 0.5f), radius = size.width * 0.8f)
        onDrawBehind { drawRect(ambient) }
    }) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).padding(6.dp)) {
            AetherNavigationRail(selectedTab, onTabSelected, now = state.now)
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(start = 8.dp, end = 3.dp)) {
                val compact = maxWidth < 680.dp || maxHeight < 350.dp
                val screenLabel = stringResource(R.string.screen_label, stringResource(selectedTab.labelRes))
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                    DashboardHeader(state, compact = compact)
                    Box(Modifier.weight(1f).semantics { paneTitle = screenLabel }) {
                        when (selectedTab) {
                            AetherTab.Home -> HomeScreen(state, media, onTabSelected, Modifier.fillMaxSize())
                            AetherTab.Media -> MediaLibrary(media, playbackActions.chooseAudio, onPlayIndex, Modifier.fillMaxSize())
                            else -> Unit // Other feature layouts await their own design phase.
                        }
                    }
                    if (selectedTab == AetherTab.Home) StatusChips(state, highlightedIndicators,
                        { index -> highlightedIndicators = highlightedIndicators xor (1 shl index) })
                    if (selectedTab != AetherTab.Settings) DashboardDock(media, position, playbackActions,
                        state.snapshot.climate, climateUpdate, onAdjustTemperature, onClimateToggle)
                }
            }
        }
    }
}

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
