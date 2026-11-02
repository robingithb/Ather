package com.aether.hmi.feature.home

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aether.hmi.R
import com.aether.hmi.domain.*
import com.aether.hmi.ui.components.*
import com.aether.hmi.ui.navigation.AetherTab
import com.aether.hmi.ui.theme.AetherColors
import java.time.format.DateTimeFormatter

internal fun homeWidgetLabel(kind: HomeWidgetKind): Int = when (kind) {
    HomeWidgetKind.Navigation -> R.string.home_widget_navigation
    HomeWidgetKind.Music -> R.string.home_widget_music
    HomeWidgetKind.Climate -> R.string.home_widget_climate
    HomeWidgetKind.Vehicle -> R.string.home_widget_vehicle
    HomeWidgetKind.Energy -> R.string.home_widget_energy
    HomeWidgetKind.Clock -> R.string.home_widget_clock
    HomeWidgetKind.Settings -> R.string.home_widget_settings
}

private fun widgetGlyph(kind: HomeWidgetKind): Glyph = when (kind) {
    HomeWidgetKind.Navigation -> Glyph.Gps
    HomeWidgetKind.Music -> Glyph.Music
    HomeWidgetKind.Climate -> Glyph.Fan
    HomeWidgetKind.Vehicle -> Glyph.Car
    HomeWidgetKind.Energy -> Glyph.Battery
    HomeWidgetKind.Clock -> Glyph.Clock
    HomeWidgetKind.Settings -> Glyph.Settings
}

@Composable
internal fun HomeWidgetGrid(widgets: List<HomeWidget>, modifier: Modifier,
    empty: @Composable (Modifier) -> Unit, content: @Composable (HomeWidget, Modifier) -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HomeWidgetLayout.slots(widgets.size).forEach { index ->
            val cell = Modifier.weight(1f).fillMaxHeight()
            if (index == null) empty(cell) else content(widgets[index], cell)
        }
    }
}

@Composable
internal fun HomeWidgetLibrary(count: Int, onAdd: (HomeWidgetKind) -> Unit, onReset: () -> Unit, modifier: Modifier) {
    val library = rememberPagerState { 2 }
    DashboardSurface(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
            val heading = minOf(30.dp, maxHeight * .16f)
            val pager = minOf(36.dp, maxHeight * .19f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                PageText(stringResource(R.string.home_widget_library), Modifier.fillMaxWidth().height(heading),
                    maxFontSize = 22f, minFontSize = 11f, fontWeight = FontWeight.SemiBold)
                HorizontalPager(library, Modifier.fillMaxWidth().weight(1f), pageSpacing = 6.dp) { libraryPage ->
                    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        repeat(2) { row ->
                            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(2) { col ->
                                    val index = libraryPage * 4 + row * 2 + col
                                    val kind = HomeWidgetKind.entries.getOrNull(index)
                                    if (kind == null) PageAction(stringResource(R.string.home_widget_restore), onReset, Modifier.weight(1f).fillMaxHeight())
                                    else Box(Modifier.weight(1f).fillMaxHeight().aetherRaisedClickable({ onAdd(kind) },
                                        shape = RoundedCornerShape(8.dp), enabled = count < HomeWidgets.limit, elevation = 4.dp)) {
                                        PageText(stringResource(homeWidgetLabel(kind)), Modifier.fillMaxSize().padding(horizontal = 5.dp),
                                            maxFontSize = 16f, minFontSize = 10f, maxLines = 2, textAlign = TextAlign.Center,
                                            color = if (count < HomeWidgets.limit) AetherColors.TextPrimary else AetherColors.Muted)
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().height(pager), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PageText(stringResource(R.string.home_widget_library_swipe), Modifier.weight(1f).fillMaxHeight(),
                        maxFontSize = 12f, color = AetherColors.TextSecondary)
                    PageText(stringResource(R.string.home_widget_page, library.currentPage + 1, 2), Modifier.width(42.dp).fillMaxHeight(),
                        maxFontSize = 14f, textAlign = TextAlign.End)
                }
                PageText(stringResource(R.string.home_widget_count, count, HomeWidgets.limit), Modifier.fillMaxWidth().height(16.dp),
                    maxFontSize = 12f, minFontSize = 10f, color = if (count >= HomeWidgets.limit) AetherColors.Warning else AetherColors.TextSecondary,
                    textAlign = TextAlign.End)
            }
        }
    }
}

@Composable
internal fun HomeAddWidgetSlot(onAdd: () -> Unit, modifier: Modifier) {
    val label = stringResource(R.string.home_add_widget_single)
    Box(modifier, contentAlignment = Alignment.Center) {
        Row(Modifier.widthIn(min = 120.dp, max = 200.dp).heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button, onClick = onAdd)
            .semantics { contentDescription = label }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AetherGlyph(Glyph.Plus, size = 20.dp, color = AetherColors.TextSecondary)
            PageText(label, Modifier.weight(1f).height(24.dp), maxFontSize = 16f,
                color = AetherColors.TextSecondary, textAlign = TextAlign.Center)
        }
    }
}

@Composable
internal fun HomeWidgetIconAction(glyph: Glyph, label: String, onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true) {
    Box(modifier.aetherRaisedClickable(onClick, RoundedCornerShape(9.dp), enabled = enabled, elevation = 4.dp)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AetherGlyph(glyph, size = 22.dp, color = if (enabled) AetherColors.Accent else AetherColors.Muted)
    }
}

@Composable
internal fun HomeStatusWidget(kind: HomeWidgetKind, state: DashboardState, onNavigate: (AetherTab) -> Unit,
    onAdjustTemperature: (Int) -> Unit, onClimateToggle: (ClimateControl) -> Unit, modifier: Modifier) {
    val climate = state.snapshot.climate
    val vehicle = state.snapshot.vehicle
    val target = when (kind) {
        HomeWidgetKind.Climate -> AetherTab.Climate
        HomeWidgetKind.Settings -> AetherTab.Settings
        else -> AetherTab.Vehicle
    }
    DashboardSurface(modifier) {
        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            PageAction(stringResource(homeWidgetLabel(kind)), { if (kind != HomeWidgetKind.Clock) onNavigate(target) },
                Modifier.fillMaxWidth().weight(.7f), glyph = widgetGlyph(kind), enabled = kind != HomeWidgetKind.Clock)
            when (kind) {
                HomeWidgetKind.Climate -> {
                    Row(Modifier.fillMaxWidth().weight(1.3f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            PageText(stringResource(R.string.home_widget_driver), Modifier.weight(.5f).fillMaxWidth(), maxFontSize = 14f, color = AetherColors.TextSecondary)
                            PageText(temperatureText(climate.driverCelsius), Modifier.weight(1f).fillMaxWidth(), maxFontSize = 36f, minFontSize = 18f)
                        }
                        Column(Modifier.weight(1f).fillMaxHeight()) {
                            PageText(stringResource(R.string.home_widget_passenger), Modifier.weight(.5f).fillMaxWidth(), maxFontSize = 14f, color = AetherColors.TextSecondary)
                            PageText(temperatureText(climate.passengerCelsius), Modifier.weight(1f).fillMaxWidth(), maxFontSize = 36f, minFontSize = 18f)
                        }
                    }
                    Row(Modifier.fillMaxWidth().weight(.8f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HomeWidgetIconAction(Glyph.Minus, stringResource(R.string.decrease_temperature), { onAdjustTemperature(-1) },
                            Modifier.weight(.8f).fillMaxHeight(), climate.available && (climate.driverCelsius ?: climate.minCelsius) > climate.minCelsius)
                        PageAction(stringResource(R.string.auto_mode), { onClimateToggle(ClimateControl.Auto) }, Modifier.weight(1.2f).fillMaxHeight(),
                            enabled = climate.available, selected = climate.auto)
                        HomeWidgetIconAction(Glyph.Plus, stringResource(R.string.increase_temperature), { onAdjustTemperature(1) },
                            Modifier.weight(.8f).fillMaxHeight(), climate.available && (climate.driverCelsius ?: climate.maxCelsius) < climate.maxCelsius)
                    }
                }
                HomeWidgetKind.Energy -> {
                    PageText(vehicle.batteryPercent?.let { stringResource(R.string.battery_percent, it) } ?: stringResource(R.string.unavailable_value),
                        Modifier.weight(1.3f).fillMaxWidth(), maxFontSize = 52f, minFontSize = 22f, fontWeight = FontWeight.Light)
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(AetherColors.Border)) {
                        vehicle.batteryPercent?.let { percent -> Box(Modifier.fillMaxWidth((percent / 100f).coerceIn(0f, 1f)).fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(AetherColors.Accent, AetherColors.Good)))) }
                    }
                    PageText(vehicle.rangeKm?.let { stringResource(R.string.range_km, numberFormat(0).format(it)) } ?: stringResource(R.string.unavailable_value),
                        Modifier.weight(.8f).fillMaxWidth(), maxFontSize = 28f, color = AetherColors.TextSecondary)
                    if (vehicle.charging == true) PageText(stringResource(R.string.charging), Modifier.weight(.5f).fillMaxWidth(), maxFontSize = 16f, color = AetherColors.Good)
                }
                HomeWidgetKind.Vehicle -> {
                    val attention = state.snapshot.vehicleAttention()
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) { VehicleStatusBadge(attention, Modifier.size(36.dp)) }
                    PageText(vehicleStatusText(attention), Modifier.fillMaxWidth().weight(1f), color = vehicleStatusColor(attention),
                        maxFontSize = 26f, minFontSize = 14f, maxLines = 2)
                    PageText(stringResource(if (state.snapshot.controls.locked) R.string.vehicle_locked else R.string.vehicle_unlocked),
                        Modifier.fillMaxWidth().weight(.6f), maxFontSize = 18f, color = AetherColors.TextSecondary)
                }
                HomeWidgetKind.Clock -> {
                    val context = LocalContext.current
                    PageText(DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", state.now.toInstant().toEpochMilli()).toString(),
                        Modifier.fillMaxWidth().weight(1.8f), maxFontSize = 56f, minFontSize = 24f, fontWeight = FontWeight.Light)
                    PageText(state.now.format(DateTimeFormatter.ofPattern("EEE, d MMM")), Modifier.fillMaxWidth().weight(.8f),
                        maxFontSize = 22f, color = AetherColors.TextSecondary)
                }
                HomeWidgetKind.Settings -> {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.CenterStart) { AetherGlyph(Glyph.Settings, size = 36.dp) }
                    PageText(stringResource(R.string.home_widget_preferences), Modifier.fillMaxWidth().weight(1.4f), maxFontSize = 28f, minFontSize = 14f, maxLines = 3,
                        color = AetherColors.TextSecondary)
                }
                else -> Unit
            }
        }
    }
}
