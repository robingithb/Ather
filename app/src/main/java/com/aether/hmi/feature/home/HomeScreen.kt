package com.aether.hmi.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aether.hmi.R
import com.aether.hmi.domain.*
import com.aether.hmi.feature.media.PlaybackState
import com.aether.hmi.ui.components.*
import com.aether.hmi.ui.navigation.AetherTab
import com.aether.hmi.ui.theme.AetherColors
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

@Composable
fun DashboardHeader(state: DashboardState, modifier: Modifier = Modifier, compact: Boolean = false,
    feedback: String? = null, feedbackWarning: Boolean = false) {
    val context = LocalContext.current
    val source = stringResource(when (state.snapshot.vehicle.source) {
        DataSource.Demo -> R.string.demo_vehicle
        DataSource.Live -> R.string.live_vehicle
        DataSource.Unavailable -> R.string.vehicle_unavailable
    })
    Row(modifier.fillMaxWidth().height(if (compact) 24.dp else 28.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PageText(if (feedback == null) source else "$source · $feedback", Modifier.weight(1f).fillMaxHeight(),
            color = if (feedbackWarning) AetherColors.Warning else AetherColors.TextSecondary, maxFontSize = 14f)
        state.snapshot.vehicle.outsideCelsius?.let { PageText(temperatureText(it.toFloat()), Modifier.width(54.dp).fillMaxHeight(), maxFontSize = 14f) }
        AetherGlyph(if (state.connectivity.transport == NetworkTransport.Mobile) Glyph.Signal else Glyph.Wifi,
            size = 18.dp, color = if (state.connectivity.online) AetherColors.TextSecondary else AetherColors.Muted)
        PageText(DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", state.now.toInstant().toEpochMilli()).toString(),
            Modifier.width(80.dp).fillMaxHeight(), maxFontSize = 16f)
    }
}

/** Personal widgets share finite landscape pages; customization stays alongside the preview. */
@Composable
fun HomeScreen(state: DashboardState, playback: PlaybackState, onNavigate: (AetherTab) -> Unit,
    modifier: Modifier = Modifier, playbackActions: PlaybackActions = PlaybackActions(),
    widgets: List<HomeWidget> = HomeWidgets.defaults, onWidgetsChange: (List<HomeWidget>) -> Unit = {},
    onAdjustTemperature: (Int) -> Unit = {}, onClimateToggle: (ClimateControl) -> Unit = {}) {
    var showRoute by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var anchor by rememberSaveable { mutableIntStateOf(0) }
    BoxWithConstraints(modifier) {
        val fit = HomePageGeometry.fit(maxWidth.value, maxHeight.value)
        val capacity = HomeWidgetLayout.pageCapacity
        val editWidth = minOf(maxWidth * .38f, 340.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(fit.gap.dp)) {
            Row(Modifier.fillMaxWidth().height(fit.header.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    PageText(stringResource(when (greetingAt(state.now)) {
                        Greeting.Morning -> R.string.greeting_morning
                        Greeting.Afternoon -> R.string.greeting_afternoon
                        Greeting.Evening -> R.string.greeting_evening
                        Greeting.Night -> R.string.greeting_night
                    }), Modifier.weight(1.2f).fillMaxWidth(), maxFontSize = 30f, minFontSize = 16f, fontWeight = FontWeight.SemiBold)
                    val attention = state.snapshot.vehicleAttention()
                    PageText(vehicleStatusText(attention), Modifier.weight(1f).fillMaxWidth(), color = vehicleStatusColor(attention), maxFontSize = 16f)
                }
                PageAction(stringResource(if (editing) R.string.home_finish_editing else R.string.home_add_widget),
                    { editing = !editing }, Modifier.width(if (editing) 100.dp else 148.dp).fillMaxHeight(),
                    selected = editing, glyph = if (editing) Glyph.Check else Glyph.Plus)
            }
            Row(Modifier.fillMaxWidth().height(fit.cards.dp), horizontalArrangement = Arrangement.spacedBy(fit.gap.dp)) {
                // Both slots keep equal widths as the preview resizes alongside the library.
                key(capacity) {
                    HomeWidgetPager(widgets, capacity, anchor, { anchor = it }, onWidgetsChange,
                        Modifier.weight(1f).fillMaxHeight(), onAdd = { editing = true }) { widget, tile ->
                        Column(tile, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                when (widget.kind) {
                                    HomeWidgetKind.Navigation -> RouteOverview(state, { showRoute = true }, Modifier.fillMaxSize())
                                    HomeWidgetKind.Music -> NowPlayingOverview(playback, playbackActions, { onNavigate(AetherTab.Media) }, Modifier.fillMaxSize())
                                    else -> HomeStatusWidget(widget.kind, state, onNavigate, onAdjustTemperature, onClimateToggle, Modifier.fillMaxSize())
                                }
                            }
                            if (editing) Row(Modifier.fillMaxWidth().height(32.dp)) {
                                val index = widgets.indexOfFirst { it.id == widget.id }
                                PageAction(stringResource(R.string.home_widget_remove), {
                                    onWidgetsChange(widgets.filterNot { it.id == widget.id }); anchor = index.coerceAtMost((widgets.size - 2).coerceAtLeast(0))
                                }, Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    }
                }
                if (editing) HomeWidgetLibrary(widgets.size, { kind ->
                    val updated = HomeWidgets.add(widgets, kind)
                    onWidgetsChange(updated)
                    anchor = updated.lastIndex.coerceAtLeast(0)
                }, { onWidgetsChange(HomeWidgets.defaults); anchor = 0 }, Modifier.width(editWidth).fillMaxHeight())
            }
        }
    }
    if (showRoute) Dialog(onDismissRequest = { showRoute = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DashboardSurface(Modifier.fillMaxWidth(.88f).fillMaxHeight(.86f)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
                val actionHeight = (maxHeight * .20f).coerceAtMost(56.dp)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PageText(stringResource(R.string.demo_route), Modifier.fillMaxWidth().weight(.65f), maxFontSize = 26f, fontWeight = FontWeight.SemiBold)
                    state.snapshot.route?.let {
                        PageText(it.destination, Modifier.fillMaxWidth().weight(.6f), maxFontSize = 24f)
                        PageText(routeText(it, state), Modifier.fillMaxWidth().weight(.6f), maxFontSize = 18f)
                    }
                    PageText(stringResource(R.string.route_preview_explanation), Modifier.fillMaxWidth().weight(1f),
                        color = AetherColors.TextSecondary, maxFontSize = 18f, maxLines = 4)
                    PageAction(stringResource(R.string.close), { showRoute = false }, Modifier.fillMaxWidth().height(actionHeight))
                }
            }
        }
    }
}

@Composable
private fun RouteOverview(state: DashboardState, onClick: () -> Unit, modifier: Modifier) {
    DashboardSurface(modifier, onClick) {
        Image(painterResource(R.drawable.navigation_road), null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(AetherColors.Panel.copy(alpha = .75f), AetherColors.PanelDeep.copy(alpha = .96f)))))
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PageText(stringResource(R.string.demo_route), Modifier.fillMaxWidth().weight(.6f), color = AetherColors.TextSecondary, maxFontSize = 18f)
            Box(Modifier.fillMaxWidth().weight(.6f), contentAlignment = Alignment.CenterStart) { AetherGlyph(Glyph.Arrow, size = 28.dp) }
            PageText(state.snapshot.route?.destination ?: stringResource(R.string.route_unavailable), Modifier.fillMaxWidth().weight(1f),
                maxFontSize = 30f, minFontSize = 16f, fontWeight = FontWeight.SemiBold, maxLines = 2)
            PageText(state.snapshot.route?.let { routeText(it, state) } ?: stringResource(R.string.route_unavailable),
                Modifier.fillMaxWidth().weight(.7f), color = AetherColors.TextSecondary, maxFontSize = 18f, maxLines = 2)
        }
    }
}

@Composable
private fun NowPlayingOverview(state: PlaybackState, actions: PlaybackActions, onOpen: () -> Unit, modifier: Modifier) {
    DashboardSurface(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
            val geometry = HomeTransportGeometry.fit(maxWidth.value, maxHeight.value)
            val control = geometry.control.dp
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PageAction(stringResource(R.string.tab_media), onOpen, Modifier.fillMaxWidth().weight(.75f), glyph = Glyph.Chevron)
                PageText(state.title ?: stringResource(if (state.hasMedia) R.string.unknown_title else R.string.nothing_playing),
                    Modifier.fillMaxWidth().weight(1f), maxFontSize = 28f, minFontSize = 14f, fontWeight = FontWeight.Medium, maxLines = 2)
                val status = if (state.error || !state.connected || state.buffering) stringResource(when {
                    state.error -> R.string.playback_error
                    !state.connected -> R.string.playback_connecting
                    else -> R.string.playback_buffering
                }) else if (state.hasMedia) state.artist ?: stringResource(R.string.unknown_artist) else stringResource(R.string.choose_audio_hint)
                PageText(status, Modifier.fillMaxWidth().weight(.7f), color = if (state.error) AetherColors.Warning else AetherColors.TextSecondary,
                    maxFontSize = 17f, maxLines = 2)
                Row(Modifier.fillMaxWidth().height(control), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    HomeTransportButton(Glyph.Previous, stringResource(R.string.previous_track), actions.previous, control, state.canPrevious)
                    HomeTransportButton(if (state.playing) Glyph.Pause else Glyph.Play,
                        stringResource(if (!state.hasMedia) R.string.choose_audio else if (state.playing) R.string.pause else R.string.play),
                        if (state.hasMedia) actions.toggle else onOpen, control, state.connected, true)
                    HomeTransportButton(Glyph.Next, stringResource(R.string.next_track), actions.next, control, state.canNext)
                }
            }
        }
    }
}

@Composable
private fun HomeTransportButton(glyph: Glyph, label: String, onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp, enabled: Boolean, selected: Boolean = false) {
    Box(Modifier.size(size).background(if (selected) AetherColors.AccentSoft else AetherColors.Panel, CircleShape)
        .clickable(enabled, role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AetherGlyph(glyph, size = size * .45f, color = if (enabled) AetherColors.TextPrimary else AetherColors.Muted)
    }
}

@Composable
internal fun numberFormat(fractionDigits: Int): NumberFormat {
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale, fractionDigits) { NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = fractionDigits; maximumFractionDigits = fractionDigits
    } }
}

@Composable
internal fun temperatureText(value: Float?): String = if (value == null) stringResource(R.string.unavailable_value)
    else stringResource(R.string.celsius_value, numberFormat(1).format(value))

@Composable
private fun routeText(route: RoutePreview, state: DashboardState): String {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale, DateFormat.is24HourFormat(context)) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
    }
    return stringResource(R.string.route_summary, route.durationMinutes, numberFormat(1).format(route.distanceKm),
        route.arrival(state.now).format(formatter))
}
