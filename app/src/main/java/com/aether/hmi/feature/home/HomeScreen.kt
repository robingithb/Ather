package com.aether.hmi.feature.home

import android.text.format.DateFormat
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.domain.CabinAir
import com.aether.hmi.domain.DataSource
import com.aether.hmi.domain.Greeting
import com.aether.hmi.domain.NetworkTransport
import com.aether.hmi.domain.RoutePreview
import com.aether.hmi.domain.VehicleHealth
import com.aether.hmi.domain.VehicleState
import com.aether.hmi.domain.greetingAt
import com.aether.hmi.feature.media.PlaybackState
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.DashboardSurface
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.components.MediaArtwork
import com.aether.hmi.ui.navigation.AetherTab
import com.aether.hmi.ui.theme.AetherColors
import java.text.NumberFormat
import java.time.format.DateTimeFormatter

@Composable
fun DashboardHeader(state: DashboardState, modifier: Modifier = Modifier, compact: Boolean = false) {
    Row(modifier.fillMaxWidth().height(if (compact) 16.dp else 24.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(when (state.snapshot.vehicle.source) {
            DataSource.Demo -> R.string.demo_vehicle
            DataSource.Live -> R.string.live_vehicle
            DataSource.Unavailable -> R.string.vehicle_unavailable
        }), color = AetherColors.TextSecondary, fontSize = 10.sp, lineHeight = 12.sp)
        Spacer(Modifier.weight(1f))
        AetherGlyph(if (state.connectivity.transport == NetworkTransport.Mobile) Glyph.Signal else Glyph.Wifi,
            size = 16.dp, color = if (state.connectivity.online) AetherColors.TextPrimary else AetherColors.Muted)
        Text(stringResource(networkLabel(state.connectivity.transport)), fontSize = 12.sp, lineHeight = 14.sp, color = AetherColors.TextSecondary)
        state.snapshot.vehicle.outsideCelsius?.let { outside ->
            Text(temperatureText(outside.toFloat()), color = AetherColors.TextPrimary, fontSize = 12.sp)
        }
    }
}

@Composable
fun HomeScreen(
    state: DashboardState,
    playback: PlaybackState,
    onNavigate: (AetherTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRoute by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 700.dp || maxHeight < 180.dp
        val gap = if (compact) 4.dp else 8.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Hero(greetingAt(state.now), compact, Modifier.weight(if (compact) 0.54f else 0.59f).fillMaxHeight())
            BoxWithConstraints(Modifier.weight(if (compact) 0.46f else 0.41f).fillMaxHeight()) {
                val showIcons = maxWidth >= 265.dp
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                    NavigationCard(state, showIcons, { showRoute = true },
                        Modifier.fillMaxWidth().weight(0.34f))
                    VehicleCard(state.snapshot.vehicle, showIcons, { onNavigate(AetherTab.Vehicle) },
                        Modifier.fillMaxWidth().weight(0.30f))
                    Row(Modifier.fillMaxWidth().weight(0.36f), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        ClimateCard(state, { onNavigate(AetherTab.Climate) }, Modifier.weight(1f).fillMaxHeight())
                        MediaCard(playback, { onNavigate(AetherTab.Media) }, Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
    if (showRoute) {
        val route = state.snapshot.route
        AlertDialog(onDismissRequest = { showRoute = false }, title = { Text(stringResource(R.string.demo_route)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (route != null) { Text(route.destination); Text(routeText(route, state)) }
                    Text(stringResource(R.string.route_preview_explanation))
                }
            }, confirmButton = { TextButton(onClick = { showRoute = false }) { Text(stringResource(R.string.close)) } })
    }
}

@Composable
private fun Hero(greeting: Greeting, compact: Boolean, modifier: Modifier) {
    DashboardSurface(modifier) {
        Image(painterResource(R.drawable.home_hero), stringResource(R.string.hero_description),
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color(0xB3020A14), Color(0x18020A14), Color.Transparent), endY = Float.POSITIVE_INFINITY)))
        Column(Modifier.padding(if (compact) 12.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(when (greeting) {
                Greeting.Morning -> R.string.greeting_morning
                Greeting.Afternoon -> R.string.greeting_afternoon
                Greeting.Evening -> R.string.greeting_evening
                Greeting.Night -> R.string.greeting_night
            }), color = AetherColors.TextPrimary, fontWeight = FontWeight.Bold,
                fontSize = if (compact) 23.sp else 29.sp, lineHeight = if (compact) 27.sp else 34.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.ready_for_road), color = AetherColors.TextSecondary,
                fontSize = if (compact) 13.sp else 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NavigationCard(state: DashboardState, showIcons: Boolean, onClick: () -> Unit, modifier: Modifier) {
    DashboardSurface(modifier, onClick) {
        Image(painterResource(R.drawable.navigation_road), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF021320), Color(0xDD00101B), Color(0x60000B14)))))
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (showIcons) 5.dp else 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showIcons) IconBadge { AetherGlyph(Glyph.Arrow, size = 28.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                SmallLabel(stringResource(R.string.demo_route))
                Text(state.snapshot.route?.destination ?: stringResource(R.string.route_unavailable),
                    color = AetherColors.TextPrimary, fontSize = if (showIcons) 17.sp else 14.sp,
                    lineHeight = if (showIcons) 20.sp else 16.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.snapshot.route?.let { route ->
                    Text(routeText(route, state), color = AetherColors.TextSecondary, fontSize = 10.sp,
                        lineHeight = 12.sp, maxLines = if (showIcons) 1 else 2, overflow = TextOverflow.Ellipsis)
                }
            }
            AetherGlyph(Glyph.Chevron, size = 12.dp, color = AetherColors.TextPrimary)
        }
    }
}

@Composable
private fun VehicleCard(state: VehicleState, showIcons: Boolean, onClick: () -> Unit, modifier: Modifier) {
    DashboardSurface(modifier, onClick) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (showIcons) 5.dp else 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (showIcons) IconBadge { Icon(painterResource(R.drawable.ic_vehicle), null, tint = AetherColors.TextSecondary,
                modifier = Modifier.size(29.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                SmallLabel(stringResource(if (state.source == DataSource.Demo) R.string.vehicle_demo_title else R.string.tab_vehicle))
                Text(stringResource(when (state.health) {
                    VehicleHealth.Healthy -> R.string.systems_good
                    VehicleHealth.Attention -> R.string.systems_attention
                    VehicleHealth.Unavailable -> R.string.systems_unavailable
                }), fontSize = if (showIcons) 16.sp else 13.sp, lineHeight = if (showIcons) 19.sp else 15.sp, color = AetherColors.TextPrimary,
                    fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val format = numberFormat(0)
                Text(stringResource(R.string.vehicle_summary,
                    state.rangeKm?.let { stringResource(R.string.range_km, format.format(it)) } ?: stringResource(R.string.unavailable_value),
                    state.batteryPercent?.let { stringResource(R.string.battery_percent, it) } ?: stringResource(R.string.unavailable_value)),
                    color = AetherColors.TextSecondary, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showIcons && state.health == VehicleHealth.Healthy) {
                Box(Modifier.size(22.dp).background(AetherColors.Good, CircleShape), contentAlignment = Alignment.Center) {
                    AetherGlyph(Glyph.Check, size = 15.dp, color = AetherColors.Background)
                }
            }
            AetherGlyph(Glyph.Chevron, size = 12.dp, color = AetherColors.TextPrimary)
        }
    }
}

@Composable
private fun ClimateCard(state: DashboardState, onClick: () -> Unit, modifier: Modifier) {
    val climate = state.snapshot.climate
    DashboardSurface(modifier, onClick) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 145.dp
            Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 5.dp else 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                DashboardSurface(Modifier.width(if (compact) 27.dp else 34.dp).height(if (compact) 36.dp else 48.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_climate_filled), null, tint = AetherColors.Accent,
                            modifier = Modifier.size(if (compact) 23.dp else 31.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tab_climate), color = AetherColors.TextPrimary,
                        fontSize = if (compact) 9.sp else 11.sp, lineHeight = 12.sp, maxLines = 1)
                    Text(temperatureText(climate.driverCelsius), fontSize = if (compact) 17.sp else 23.sp,
                        lineHeight = if (compact) 20.sp else 26.sp, fontWeight = FontWeight.Medium,
                        color = AetherColors.TextPrimary, maxLines = 1)
                    Text(stringResource(R.string.climate_summary,
                        stringResource(if (climate.auto) R.string.auto_mode else R.string.manual_mode),
                        stringResource(if (climate.cabinAir == CabinAir.Clean) R.string.cabin_clean else R.string.cabin_unknown)),
                        color = AetherColors.TextSecondary, fontSize = if (compact) 7.sp else 8.sp,
                        lineHeight = 10.sp, letterSpacing = 0.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AetherGlyph(Glyph.Chevron, size = if (compact) 8.dp else 10.dp, color = AetherColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun MediaCard(state: PlaybackState, onClick: () -> Unit, modifier: Modifier) {
    DashboardSurface(modifier, onClick) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxWidth < 145.dp
            Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 5.dp else 7.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 6.dp)) {
                MediaArtwork(state.artwork, Modifier.width(if (compact) 27.dp else 34.dp)
                    .height(if (compact) 36.dp else 48.dp), fallback = R.drawable.home_album_placeholder)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.tab_media), color = AetherColors.TextPrimary,
                        fontSize = if (compact) 9.sp else 11.sp, lineHeight = 12.sp, maxLines = 1)
                    Text(state.title ?: stringResource(if (state.hasMedia) R.string.unknown_title else R.string.nothing_playing),
                        fontSize = if (compact) 9.sp else 10.sp, lineHeight = 12.sp, letterSpacing = 0.sp, fontWeight = FontWeight.Medium,
                        color = AetherColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.hasMedia) state.artist ?: stringResource(R.string.unknown_artist) else stringResource(R.string.choose_audio),
                        fontSize = if (compact) 8.sp else 10.sp, lineHeight = 11.sp, letterSpacing = 0.sp,
                        color = AetherColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AetherGlyph(Glyph.Chevron, size = if (compact) 8.dp else 10.dp, color = AetherColors.TextPrimary)
            }
        }
    }
}

@Composable
private fun IconBadge(content: @Composable () -> Unit) {
    DashboardSurface(Modifier.size(46.dp)) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() } }
}

@Composable
private fun SmallLabel(text: String) {
    Text(text, color = AetherColors.TextPrimary, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@StringRes
private fun networkLabel(transport: NetworkTransport): Int = when (transport) {
    NetworkTransport.Wifi -> R.string.network_wifi
    NetworkTransport.Mobile -> R.string.network_mobile
    NetworkTransport.Ethernet -> R.string.network_ethernet
    NetworkTransport.Other -> R.string.network_other
    NetworkTransport.None -> R.string.network_none
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
