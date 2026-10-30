package com.aether.hmi.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.domain.AppSettings
import com.aether.hmi.domain.AppTheme
import com.aether.hmi.domain.ConnectivityState
import com.aether.hmi.domain.NetworkTransport
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** A viewport-fitted dashboard. Every section and all four themes are visible together. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    connectivity: ConnectivityState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var connectionUnavailable by rememberSaveable { mutableStateOf<String?>(null) }
    var showUpdateInfo by rememberSaveable { mutableStateOf(false) }
    var showRouteChoices by rememberSaveable { mutableStateOf(false) }
    var showAvoidChoices by rememberSaveable { mutableStateOf(false) }
    val version = remember(context) { installedVersion(context) }
    val openConnection: (String, String) -> Unit = { action, label ->
        if (!openDeviceSettings(context, action)) connectionUnavailable = label
    }
    BoxWithConstraints(modifier) {
        // Phone landscape has about 640 x 300dp after the rail and system insets.
        // Proportional rows constrain native Compose layouts, including every small control.
        val scale = minOf(maxWidth.value / 1040f, maxHeight.value / 470f).coerceIn(.4f, 1.35f)
        val metrics = SettingsMetrics(scale)
        CompositionLocalProvider(LocalSettingsMetrics provides metrics) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                SettingsBanner(settings.theme, Modifier.fillMaxWidth().weight(.145f))
                ThemeGallery(settings.theme, { onSettingsChange(settings.copy(theme = it, accentColor = null)) },
                    Modifier.fillMaxWidth().weight(.255f))
                Row(Modifier.fillMaxWidth().weight(.245f), horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    ConnectivityPanel(connectivity, openConnection, Modifier.weight(1f).fillMaxHeight())
                    DisplayPanel(settings, onSettingsChange, Modifier.weight(1f).fillMaxHeight())
                    SoundPanel(settings, onSettingsChange, Modifier.weight(1.15f).fillMaxHeight())
                }
                Row(Modifier.fillMaxWidth().weight(.355f), horizontalArrangement = Arrangement.spacedBy(metrics.gap)) {
                    NavigationPanel(settings, onSettingsChange, { showRouteChoices = true }, { showAvoidChoices = true },
                        Modifier.weight(1.05f).fillMaxHeight())
                    Column(Modifier.weight(.95f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(metrics.gap)) {
                        SoftwarePanel(version, { showUpdateInfo = true }, Modifier.fillMaxWidth().weight(.48f))
                        StoragePanel(Modifier.fillMaxWidth().weight(.52f))
                    }
                    HealthPanel(Modifier.weight(1f).fillMaxHeight())
                    AppearancePanel(settings, onSettingsChange, Modifier.weight(1.15f).fillMaxHeight())
                }
            }
        }
    }
    connectionUnavailable?.let { label ->
        AlertDialog(onDismissRequest = { connectionUnavailable = null },
            title = { Text(stringResource(R.string.settings_unavailable)) },
            text = { Text(stringResource(R.string.settings_android_unavailable, label)) },
            confirmButton = { TextButton(onClick = { connectionUnavailable = null }) { Text(stringResource(R.string.settings_close)) } })
    }
    if (showUpdateInfo) AlertDialog(onDismissRequest = { showUpdateInfo = false },
        title = { Text(stringResource(R.string.settings_software)) },
        text = { Text(stringResource(R.string.settings_update_info)) },
        confirmButton = { TextButton(onClick = { showUpdateInfo = false }) { Text(stringResource(R.string.settings_close)) } })
    if (showRouteChoices) AlertDialog(onDismissRequest = { showRouteChoices = false },
        title = { Text(stringResource(R.string.settings_route_type)) },
        text = {
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Balanced" to R.string.settings_balanced, "Fastest" to R.string.settings_fastest, "Eco" to R.string.settings_efficient).forEach { (key, label) ->
                    Row(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(8.dp))
                        .background(if (settings.routeType == key) AetherColors.Accent.copy(alpha = .12f) else Color.Transparent)
                        .selectable(settings.routeType == key, role = Role.RadioButton,
                            onClick = { onSettingsChange(settings.copy(routeType = key)); showRouteChoices = false }).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), Modifier.weight(1f), color = AetherColors.TextPrimary, fontSize = 14.sp)
                        if (settings.routeType == key) AetherGlyph(Glyph.Check, size = 18.dp)
                    }
                }
                Text(stringResource(R.string.settings_navigation_hint), color = AetherColors.TextSecondary, fontSize = 10.sp)
            }
        }, confirmButton = { TextButton(onClick = { showRouteChoices = false }) { Text(stringResource(R.string.settings_close)) } })
    if (showAvoidChoices) AlertDialog(onDismissRequest = { showAvoidChoices = false },
        title = { Text(stringResource(R.string.settings_avoid)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactSettingsSwitch(stringResource(R.string.settings_avoid_tolls), settings.avoidTolls,
                    { onSettingsChange(settings.copy(avoidTolls = it)) }, Modifier.height(36.dp))
                CompactSettingsSwitch(stringResource(R.string.settings_avoid_ferries), settings.avoidFerries,
                    { onSettingsChange(settings.copy(avoidFerries = it)) }, Modifier.height(36.dp))
                Text(stringResource(R.string.settings_navigation_hint), color = AetherColors.TextSecondary, fontSize = 10.sp)
            }
        }, confirmButton = { TextButton(onClick = { showAvoidChoices = false }) { Text(stringResource(R.string.settings_close)) } })
}

@Composable
private fun SettingsBanner(theme: AppTheme, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    Box(modifier.clip(RoundedCornerShape(m.radius)).border(.7.dp, AetherColors.Border, RoundedCornerShape(m.radius))) {
        val bannerImage = when (theme) {
            AppTheme.Midnight -> R.drawable.home_hero
            AppTheme.Arctic -> R.drawable.theme_arctic
            AppTheme.Sport -> R.drawable.theme_sport
            AppTheme.Minimal -> R.drawable.theme_minimal
        }
        Image(painterResource(bannerImage), contentDescription = null, contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -.9f), modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(AetherColors.PanelDeep.copy(alpha = .92f),
            AetherColors.Panel.copy(alpha = .55f), AetherColors.PanelDeep.copy(alpha = .96f)))))
        Row(Modifier.fillMaxSize().padding(horizontal = m.dp(18f)), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(m.dp(12f))) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(2f))) {
                Text(stringResource(R.string.settings_title), color = AetherColors.TextPrimary,
                    fontSize = m.font(28f), lineHeight = m.font(30f), fontWeight = FontWeight.Bold, maxLines = 1)
                Text(stringResource(R.string.settings_subtitle), color = AetherColors.TextSecondary,
                    fontSize = m.font(12f), lineHeight = m.font(14f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.clip(RoundedCornerShape(m.dp(8f))).background(AetherColors.Panel.copy(alpha = .8f))
                .border(.6.dp, AetherColors.Border, RoundedCornerShape(m.dp(8f)))
                .padding(horizontal = m.dp(11f), vertical = m.dp(6f)),
                horizontalArrangement = Arrangement.spacedBy(m.dp(9f)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(m.dp(32f)).clip(CircleShape).background(AetherColors.Accent.copy(alpha = .1f))
                    .border(.7.dp, AetherColors.Accent.copy(alpha = .8f), CircleShape), contentAlignment = Alignment.Center) {
                    SettingsGlyph(SettingsIcon.User, Modifier.size(m.dp(23f)), color = AetherColors.Accent)
                }
                Column(verticalArrangement = Arrangement.spacedBy(m.dp(2f))) {
                    Text(stringResource(R.string.settings_profile_name), color = AetherColors.TextPrimary,
                        fontSize = m.font(14f), lineHeight = m.font(16f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text(stringResource(R.string.settings_profile_detail), color = AetherColors.TextSecondary,
                        fontSize = m.font(9f), lineHeight = m.font(11f), maxLines = 1)
                }
            }
        }
    }
}

private data class ThemeCardData(val theme: AppTheme, val title: Int, val description: Int, val image: Int, val swatches: List<Color>)

private val ThemeCards = listOf(
    ThemeCardData(AppTheme.Midnight, R.string.settings_midnight, R.string.settings_midnight_detail, R.drawable.home_hero,
        listOf(Color(0xFF00DFFF), Color(0xFF004DDF), Color(0xFF007DFF), Color(0xFFF2F7FF))),
    ThemeCardData(AppTheme.Arctic, R.string.settings_arctic, R.string.settings_arctic_detail, R.drawable.theme_arctic,
        listOf(Color(0xFFEAF5FF), Color(0xFF63A1C7), Color(0xFF167CA9), Color(0xFFB9D8EF))),
    ThemeCardData(AppTheme.Sport, R.string.settings_sport, R.string.settings_sport_detail, R.drawable.theme_sport,
        listOf(Color(0xFFFF314B), Color(0xFF713723), Color(0xFF202733), Color(0xFF333C48))),
    ThemeCardData(AppTheme.Minimal, R.string.settings_minimal, R.string.settings_minimal_detail, R.drawable.theme_minimal,
        listOf(Color(0xFF8B95A2), Color(0xFFBDC4CB), Color(0xFFF6F5F2), Color(0xFF4F5D68))),
)

@Composable
private fun ThemeGallery(selectedTheme: AppTheme, onSelect: (AppTheme) -> Unit, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    SettingsPanel(modifier) {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(m.gap)) {
            Column(Modifier.fillMaxWidth(.11f).padding(top = m.dp(3f)), verticalArrangement = Arrangement.spacedBy(m.dp(5f))) {
                Text(stringResource(R.string.settings_theme), color = AetherColors.TextPrimary,
                    fontWeight = FontWeight.SemiBold, fontSize = m.font(16f), maxLines = 1)
                Text(stringResource(R.string.settings_theme_hint), color = AetherColors.TextSecondary,
                    fontSize = m.font(10f), lineHeight = m.font(12f), maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
            Row(Modifier.weight(1f).fillMaxHeight().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(m.gap)) {
                ThemeCards.forEach { data -> ThemeCard(data, data.theme == selectedTheme, { onSelect(data.theme) }, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun ThemeCard(data: ThemeCardData, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    val title = stringResource(data.title)
    val label = stringResource(if (selected) R.string.settings_theme_selected else R.string.settings_theme_select, title)
    Box(modifier.clip(RoundedCornerShape(m.dp(8f)))
        .border(if (selected) 1.5.dp else .6.dp, if (selected) AetherColors.Accent else AetherColors.Border, RoundedCornerShape(m.dp(8f)))
        .selectable(selected, role = Role.RadioButton, onClick = onClick).semantics { contentDescription = label }) {
        Image(painterResource(data.image), contentDescription = null, contentScale = ContentScale.Crop,
            alignment = BiasAlignment(.2f, -.2f), modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x10000000), Color(0xE6000000), Color(0xFF08090B)))))
        if (selected) Box(Modifier.align(Alignment.TopEnd).padding(m.dp(6f)).size(m.dp(21f)).clip(CircleShape)
            .background(AetherColors.Accent), contentAlignment = Alignment.Center) {
            AetherGlyph(Glyph.Check, size = m.dp(14f), color = AetherColors.OnAccent)
        }
        Column(Modifier.align(Alignment.BottomStart).padding(m.dp(7f)), verticalArrangement = Arrangement.spacedBy(m.dp(2f))) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = m.font(15f), lineHeight = m.font(17f), maxLines = 1)
            if (!m.compact) Text(stringResource(data.description), color = Color(0xFFD5DEEA), fontSize = m.font(10f), lineHeight = m.font(12f),
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = m.dp(2f)), horizontalArrangement = Arrangement.spacedBy(m.dp(6f))) {
                data.swatches.forEach { color -> Box(Modifier.size(m.dp(12f)).clip(CircleShape).background(color).border(.4.dp, Color.White.copy(alpha = .2f), CircleShape)) }
            }
        }
    }
}

@Composable
private fun ConnectivityPanel(connectivity: ConnectivityState, onOpen: (String, String) -> Unit, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    val network = stringResource(when {
        !connectivity.online -> R.string.settings_offline
        connectivity.transport == NetworkTransport.Wifi -> R.string.settings_wifi_active
        connectivity.transport == NetworkTransport.Mobile -> R.string.settings_mobile_active
        connectivity.transport == NetworkTransport.Ethernet -> R.string.settings_ethernet_active
        else -> R.string.settings_network_active
    })
    SettingsPanel(modifier) {
        SettingsPanelHeader(stringResource(R.string.settings_connectivity), network, SettingsIcon.Wifi)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(m.dp(4f))) {
            ConnectionControl(R.string.settings_bluetooth, SettingsIcon.Bluetooth, Settings.ACTION_BLUETOOTH_SETTINGS, onOpen, Modifier.weight(1f).fillMaxHeight())
            ConnectionControl(R.string.settings_wifi, SettingsIcon.Wifi, Settings.ACTION_WIFI_SETTINGS, onOpen, Modifier.weight(1f).fillMaxHeight())
            ConnectionControl(R.string.settings_hotspot, SettingsIcon.Link, Settings.ACTION_WIRELESS_SETTINGS, onOpen, Modifier.weight(1f).fillMaxHeight())
        }
        if (m.compact) SettingsHint(network)
    }
}

@Composable
private fun ConnectionControl(labelRes: Int, icon: SettingsIcon, action: String, onOpen: (String, String) -> Unit, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    val label = stringResource(labelRes)
    Row(modifier.clip(RoundedCornerShape(m.dp(7f))).background(AetherColors.Panel)
        .border(.5.dp, AetherColors.Border, RoundedCornerShape(m.dp(7f))).clickable(role = Role.Button, onClick = { onOpen(action, label) })
        .padding(horizontal = m.dp(4f)), horizontalArrangement = Arrangement.spacedBy(m.dp(4f)), verticalAlignment = Alignment.CenterVertically) {
        SettingsGlyph(icon, modifier = Modifier.size(m.dp(20f)))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(2f))) {
            Text(label, color = AetherColors.TextPrimary, fontSize = m.font(10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!m.compact) Text(stringResource(R.string.settings_device_controls), color = AetherColors.TextSecondary,
                fontSize = m.font(8f), lineHeight = m.font(10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DisplayPanel(settings: AppSettings, onChange: (AppSettings) -> Unit, modifier: Modifier) {
    SettingsPanel(modifier) {
        SettingsPanelHeader(stringResource(R.string.settings_display), stringResource(R.string.settings_display_detail), SettingsIcon.Sun)
        CompactSettingsSlider(stringResource(R.string.settings_brightness), settings.brightness,
            { onChange(settings.copy(brightness = it)) }, Modifier.weight(1f), enabled = !settings.autoBrightness)
        CompactSettingsSwitch(stringResource(R.string.settings_auto_brightness), settings.autoBrightness,
            { onChange(settings.copy(autoBrightness = it)) }, Modifier.weight(1f))
    }
}

@Composable
private fun SoundPanel(settings: AppSettings, onChange: (AppSettings) -> Unit, modifier: Modifier) {
    SettingsPanel(modifier) {
        SettingsPanelHeader(stringResource(R.string.settings_sound), stringResource(R.string.settings_sound_detail), SettingsIcon.Sound)
        CompactSettingsSlider(stringResource(R.string.settings_media_volume), settings.mediaVolume,
            { onChange(settings.copy(mediaVolume = it)) }, Modifier.weight(1f))
        CompactSettingsSlider(stringResource(R.string.settings_guidance_saved), settings.navigationVolume,
            { onChange(settings.copy(navigationVolume = it)) }, Modifier.weight(1f))
    }
}

@Composable
private fun NavigationPanel(settings: AppSettings, onChange: (AppSettings) -> Unit,
    onRoute: () -> Unit, onAvoid: () -> Unit, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    val route = stringResource(when (settings.routeType) {
        "Fastest" -> R.string.settings_fastest
        "Eco" -> R.string.settings_efficient
        else -> R.string.settings_balanced
    })
    val avoid = when {
        settings.avoidTolls && settings.avoidFerries -> stringResource(R.string.settings_tolls_ferries)
        settings.avoidTolls -> stringResource(R.string.settings_tolls)
        settings.avoidFerries -> stringResource(R.string.settings_ferries)
        else -> stringResource(R.string.settings_none)
    }
    SettingsPanel(modifier) {
        SettingsPanelHeader(stringResource(if (m.compact) R.string.settings_navigation_short else R.string.settings_navigation),
            stringResource(R.string.settings_navigation_detail), SettingsIcon.Navigation)
        SummaryRow(stringResource(R.string.settings_route_type), route, onRoute, Modifier.weight(1f))
        SummaryRow(stringResource(R.string.settings_avoid), avoid, onAvoid, Modifier.weight(1f))
        SummaryRow(stringResource(R.string.settings_map_view), stringResource(if (settings.map3D) R.string.settings_3d else R.string.settings_2d),
            { onChange(settings.copy(map3D = !settings.map3D)) }, Modifier.weight(1f))
        SettingsHint(stringResource(R.string.settings_app_preferences))
    }
}

@Composable
private fun SummaryRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(m.dp(5f))).background(AetherColors.Panel)
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = m.dp(6f)),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(4f))) {
        Text(label, Modifier.weight(.9f), color = AetherColors.TextSecondary, fontSize = m.font(10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, Modifier.weight(1.1f), color = AetherColors.Accent, fontSize = m.font(10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        AetherGlyph(Glyph.Chevron, size = m.dp(11f), color = AetherColors.TextSecondary)
    }
}

@Composable
private fun SoftwarePanel(version: String?, onInfo: () -> Unit, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    SettingsPanel(modifier.clickable(role = Role.Button, onClick = onInfo)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(6f))) {
            SettingsGlyph(SettingsIcon.Update, Modifier.size(m.dp(22f)))
            Text(stringResource(R.string.settings_software), Modifier.weight(1f), color = AetherColors.TextPrimary,
                fontWeight = FontWeight.SemiBold, fontSize = m.font(12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            AetherGlyph(Glyph.Chevron, size = m.dp(11f), color = AetherColors.TextSecondary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.settings_version, version ?: stringResource(R.string.settings_version_unknown)),
                color = AetherColors.TextPrimary, fontSize = m.font(10f), lineHeight = m.font(12f), maxLines = 1)
            Text(stringResource(R.string.settings_no_updater), color = AetherColors.TextSecondary,
                fontSize = m.font(9f), lineHeight = m.font(11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class StorageVolume(val total: Long, val usable: Long)

@Composable
private fun StoragePanel(modifier: Modifier) {
    val context = LocalContext.current
    val m = LocalSettingsMetrics.current
    val storage by produceState<StorageVolume?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val volume = StatFs(context.filesDir.absolutePath)
                StorageVolume(volume.totalBytes, volume.availableBytes)
            }.getOrNull()
        }
    }
    SettingsPanel(modifier) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(6f))) {
            SettingsGlyph(SettingsIcon.Storage, Modifier.size(m.dp(22f)))
            Text(stringResource(R.string.settings_storage), Modifier.weight(1f), color = AetherColors.TextPrimary,
                fontWeight = FontWeight.SemiBold, fontSize = m.font(12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val capacity = storage
        if (capacity != null && capacity.total > 0) {
            val used = (capacity.total - capacity.usable).coerceIn(0L, capacity.total)
            val fraction = (used.toDouble() / capacity.total).toFloat()
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(m.dp(6f)), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).height(m.dp(6f)).clip(CircleShape).background(AetherColors.RailBorder)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(AetherColors.Accent))
                }
                Text(stringResource(R.string.settings_percent, (fraction * 100).roundToInt()), color = AetherColors.TextPrimary, fontSize = m.font(10f), maxLines = 1)
            }
            Text(stringResource(R.string.settings_storage_amount, Formatter.formatShortFileSize(context, used),
                Formatter.formatShortFileSize(context, capacity.total)), color = AetherColors.TextSecondary,
                fontSize = m.font(9f), lineHeight = m.font(10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else SettingsHint(stringResource(R.string.settings_storage_unavailable))
    }
}

@Composable
private fun HealthPanel(modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    SettingsPanel(modifier) {
        SettingsPanelHeader(stringResource(R.string.settings_vehicle_health), stringResource(R.string.settings_vehicle_health_detail), SettingsIcon.Health)
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(m.dp(8f)), verticalAlignment = Alignment.CenterVertically) {
            val healthDescription = stringResource(R.string.settings_demo_health)
            Box(Modifier.width(m.dp(76f)).fillMaxHeight().semantics { contentDescription = healthDescription }, contentAlignment = Alignment.Center) {
                val ringColor = AetherColors.Good
                val track = AetherColors.RailBorder
                Canvas(Modifier.size(m.dp(72f)).padding(m.dp(5f))) {
                    drawArc(track, -90f, 360f, false, style = Stroke(m.dp(8f).toPx(), cap = StrokeCap.Round))
                    drawArc(ringColor, -90f, 352.8f, false, style = Stroke(m.dp(8f).toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("98%", color = AetherColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = m.font(22f), lineHeight = m.font(24f))
                    Text(stringResource(R.string.settings_demo), color = AetherColors.Good, fontSize = m.font(9f), lineHeight = m.font(11f))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                listOf(R.string.settings_powertrain, R.string.settings_battery, R.string.settings_tires,
                    R.string.settings_brakes, R.string.settings_safety).forEach { label ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.dp(3f)), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(label), Modifier.weight(1f), color = AetherColors.TextSecondary,
                            fontSize = m.font(9f), lineHeight = m.font(10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        AetherGlyph(Glyph.Check, size = m.dp(11f), color = AetherColors.Good)
                    }
                }
            }
        }
        SettingsHint(stringResource(R.string.settings_demo_diagnostics))
    }
}

private val AppearanceColors = listOf(
    0xFF00DFFF to R.string.settings_cyan,
    0xFF007DFF to R.string.settings_blue,
    0xFF9B4DFF to R.string.settings_purple,
    0xFFFF45B4 to R.string.settings_pink,
    0xFFFFAD42 to R.string.settings_orange,
    0xFFFFDF55 to R.string.settings_yellow,
    0xFF00DDA3 to R.string.settings_green,
)

@Composable
private fun AppearancePanel(settings: AppSettings, onChange: (AppSettings) -> Unit, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    var accentMode by rememberSaveable { mutableStateOf(false) }
    val modeLabel = stringResource(if (accentMode) R.string.settings_accent else R.string.settings_ambient)
    SettingsPanel(modifier) {
        SettingsPanelHeader(stringResource(if (m.compact) R.string.settings_appearance_short else R.string.settings_appearance),
            stringResource(R.string.settings_appearance_detail), SettingsIcon.Car)
        Row(Modifier.fillMaxWidth().weight(.65f).clip(RoundedCornerShape(m.dp(7f))).background(AetherColors.Panel).selectableGroup()) {
            listOf(false to R.string.settings_ambient, true to R.string.settings_accent).forEach { (accent, label) ->
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(m.dp(7f)))
                    .background(if (accentMode == accent) AetherColors.Accent else Color.Transparent)
                    .selectable(accentMode == accent, role = Role.Tab, onClick = { accentMode = accent }), contentAlignment = Alignment.Center) {
                    Text(stringResource(label), color = if (accentMode == accent) AetherColors.OnAccent else AetherColors.TextSecondary,
                        fontSize = m.font(10f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(.75f).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(m.dp(2f))) {
            AppearanceColors.forEach { (value, nameRes) ->
                val selected = if (accentMode) settings.accentColor == value else settings.ambientColor == value
                val description = stringResource(R.string.settings_color_option, stringResource(nameRes), modeLabel)
                Box(Modifier.weight(1f).fillMaxHeight().selectable(selected, role = Role.RadioButton,
                    onClick = { onChange(if (accentMode) settings.copy(accentColor = value) else settings.copy(ambientColor = value)) })
                    .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(m.dp(21f)).border(if (selected) 1.3.dp else .5.dp,
                        if (selected) AetherColors.TextPrimary else AetherColors.Border, CircleShape).padding(if (selected) m.dp(3f) else m.dp(1f))
                        .clip(CircleShape).background(Color(value)))
                }
            }
        }
        if (accentMode) {
            Box(Modifier.fillMaxWidth().weight(1.1f).clip(RoundedCornerShape(m.dp(6f)))
                .border(.5.dp, AetherColors.Border, RoundedCornerShape(m.dp(6f)))
                .clickable(role = Role.Button, onClick = { onChange(settings.copy(accentColor = null)) }), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.settings_theme_default), color = AetherColors.Accent, fontSize = m.font(10f), maxLines = 1)
            }
        } else CabinLightPreview(Color(settings.ambientColor), Modifier.fillMaxWidth().weight(1.1f))
        SettingsHint(stringResource(if (accentMode) R.string.settings_accent_short_hint else R.string.settings_preview_only))
    }
}

@Composable
private fun CabinLightPreview(ambientColor: Color, modifier: Modifier) {
    val m = LocalSettingsMetrics.current
    val previewDescription = stringResource(R.string.settings_preview)
    Box(modifier.clip(RoundedCornerShape(m.dp(6f)))
        .border(.6.dp, AetherColors.Border, RoundedCornerShape(m.dp(6f))).semantics { contentDescription = previewDescription }) {
        Image(painterResource(R.drawable.climate_cabin_clean_v2), contentDescription = null, contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -.05f), colorFilter = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }),
            modifier = Modifier.fillMaxSize())
        Canvas(Modifier.fillMaxSize()) {
            val lightPath = Path().apply {
                moveTo(0f, size.height * .51f)
                cubicTo(size.width * .12f, size.height * .43f, size.width * .23f, size.height * .4f, size.width * .34f, size.height * .39f)
                moveTo(size.width * .37f, size.height * .39f)
                cubicTo(size.width * .55f, size.height * .4f, size.width * .72f, size.height * .4f, size.width, size.height * .48f)
                moveTo(0f, size.height * .66f)
                lineTo(size.width * .18f, size.height * .59f)
                moveTo(size.width * .38f, size.height * .54f)
                cubicTo(size.width * .6f, size.height * .52f, size.width * .78f, size.height * .53f, size.width, size.height * .6f)
            }
            drawPath(lightPath, ambientColor.copy(alpha = .15f), style = Stroke(m.dp(7f).toPx(), cap = StrokeCap.Round))
            drawPath(lightPath, ambientColor.copy(alpha = .5f), style = Stroke(m.dp(3f).toPx(), cap = StrokeCap.Round))
            drawPath(lightPath, ambientColor, style = Stroke(m.dp(1f).toPx(), cap = StrokeCap.Round))
        }
    }
}

private fun openDeviceSettings(context: Context, action: String): Boolean = try {
    context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

@Suppress("DEPRECATION")
private fun installedVersion(context: Context): String? = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull()
