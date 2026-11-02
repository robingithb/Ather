package com.aether.hmi.feature.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.Slider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import com.aether.hmi.R
import com.aether.hmi.domain.AppSettings
import com.aether.hmi.domain.AppTheme
import com.aether.hmi.domain.ConnectivityState
import com.aether.hmi.ui.components.*
import com.aether.hmi.ui.theme.AetherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private enum class SettingsSection(val label: Int, val glyph: Glyph) {
    Display(R.string.settings_display, Glyph.OverviewCamera), Sound(R.string.settings_sound, Glyph.Music),
    Connectivity(R.string.settings_connectivity, Glyph.Wifi), Appearance(R.string.settings_appearance_short, Glyph.Leaf),
    Navigation(R.string.settings_navigation_short, Glyph.Gps), System(R.string.review_system, Glyph.OverviewCamera)
}

/** All categories and detail pages have a finite viewport; secondary choices use tabs. */
@Composable
fun SettingsScreen(settings: AppSettings, onSettingsChange: (AppSettings) -> Unit,
    connectivity: ConnectivityState, modifier: Modifier = Modifier) {
    var selected by rememberSaveable { mutableStateOf<SettingsSection?>(null) }
    var unavailable by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    BackHandler(enabled = selected != null) { selected = null }
    BoxWithConstraints(modifier) {
        val headingHeight = (maxHeight * .15f).coerceAtMost(52.dp)
        val gap = (maxHeight * .025f).coerceAtMost(12.dp)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(Modifier.fillMaxWidth().height(headingHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selected != null) PageAction(stringResource(R.string.review_back), { selected = null }, Modifier.width(88.dp).fillMaxHeight())
                PageText(stringResource(selected?.label ?: R.string.settings_title), Modifier.weight(1f).fillMaxHeight(),
                    maxFontSize = 28f, minFontSize = 16f, fontWeight = FontWeight.SemiBold)
            }
            val section = selected
            if (section == null) Box(Modifier.weight(1f).fillMaxWidth()) {
                CompactSettingsPanel(244.dp, 760.dp) {
                    SettingsGrid(SettingsSection.entries.size, 2, Modifier.fillMaxSize()) { index, tile ->
                        val item = SettingsSection.entries[index]
                        PageAction(stringResource(item.label), { selected = item }, tile, glyph = item.glyph)
                    }
                }
            } else key(section) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (section) {
                        SettingsSection.Display -> CompactSettingsPanel(160.dp, 640.dp) {
                            PreferenceSwitch(stringResource(R.string.settings_auto_brightness), settings.autoBrightness,
                                Modifier.weight(.75f)) { onSettingsChange(settings.copy(autoBrightness = it)) }
                            PreferenceSlider(stringResource(R.string.settings_brightness), settings.brightness,
                                Modifier.weight(1f), !settings.autoBrightness) { onSettingsChange(settings.copy(brightness = it)) }
                        }
                        SettingsSection.Sound -> SoundPreferences(settings, onSettingsChange)
                        SettingsSection.Connectivity -> CompactSettingsPanel(224.dp, 640.dp) {
                            PageText(stringResource(if (connectivity.online) R.string.settings_network_active else R.string.settings_offline),
                                Modifier.fillMaxWidth().weight(.55f), color = AetherColors.TextSecondary, maxFontSize = 18f)
                            listOf(R.string.settings_bluetooth to Settings.ACTION_BLUETOOTH_SETTINGS,
                                R.string.settings_wifi to Settings.ACTION_WIFI_SETTINGS,
                                R.string.settings_hotspot to Settings.ACTION_WIRELESS_SETTINGS).forEach { (labelRes, action) ->
                                val label = stringResource(labelRes)
                                PageAction(label, { if (!openDeviceSettings(context, action)) unavailable = label }, Modifier.fillMaxWidth().weight(1f))
                            }
                        }
                        SettingsSection.Appearance -> AppearancePreferences(settings, onSettingsChange)
                        SettingsSection.Navigation -> NavigationPreferences(settings, onSettingsChange)
                        SettingsSection.System -> SystemPreferences()
                    }
                }
            }
        }
    }
    unavailable?.let { label -> Dialog(onDismissRequest = { unavailable = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DashboardSurface(Modifier.fillMaxWidth(.86f).fillMaxHeight(.82f)) {
            Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PageText(stringResource(R.string.settings_unavailable), Modifier.fillMaxWidth().weight(.6f), maxFontSize = 26f)
                PageText(stringResource(R.string.settings_android_unavailable, label), Modifier.fillMaxWidth().weight(1.6f), maxFontSize = 20f, maxLines = 5)
                PageAction(stringResource(R.string.close), { unavailable = null }, Modifier.fillMaxWidth().weight(.7f))
            }
        }
    } }
}

/** Compact controls keep their ordinary size on large head units and only contract for short windows. */
@Composable
private fun CompactSettingsPanel(maximumHeight: Dp, maximumWidth: Dp = 680.dp,
    content: @Composable ColumnScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val panelWidth = minOf(maxWidth, maximumWidth)
        val panelHeight = minOf(maxHeight, maximumHeight)
        Column(Modifier.width(panelWidth).height(panelHeight), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

/** Grid cells share the available height; there is no minimum size that can push a row off-page. */
@Composable
private fun SettingsGrid(count: Int, columns: Int, modifier: Modifier, content: @Composable (Int, Modifier) -> Unit) {
    val rows = (count + columns - 1) / columns
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(columns) { col ->
                    val index = row * columns + col
                    val cell = Modifier.weight(1f).fillMaxHeight()
                    if (index < count) content(index, cell) else Box(cell)
                }
            }
        }
    }
}

@Composable
internal fun SoundPreferences(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    CompactSettingsPanel(162.dp, 640.dp) {
        PreferenceSlider(stringResource(R.string.settings_media_volume), settings.mediaVolume,
            Modifier.weight(1f)) { onChange(settings.copy(mediaVolume = it)) }
        PreferenceSlider(stringResource(R.string.settings_guidance_saved), settings.navigationVolume,
            Modifier.weight(1f)) { onChange(settings.copy(navigationVolume = it)) }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun PreferenceSlider(label: String, value: Float, modifier: Modifier, enabled: Boolean = true, onChange: (Float) -> Unit) {
    Row(modifier.fillMaxWidth().aetherRaised(shape = RoundedCornerShape(12.dp), enabled = enabled,
        elevation = 2.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PageText(label, Modifier.weight(.44f).fillMaxHeight(), maxFontSize = 18f, minFontSize = 12f, maxLines = 2,
            color = if (enabled) AetherColors.TextPrimary else AetherColors.Muted)
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Slider(value, onChange, enabled = enabled, modifier = Modifier.weight(.56f).fillMaxHeight()
                .semantics { contentDescription = label })
        }
        PageText(stringResource(R.string.settings_percent, (value * 100).roundToInt()),
            Modifier.width(44.dp).fillMaxHeight(), maxFontSize = 16f, minFontSize = 11f,
            color = if (enabled) AetherColors.Accent else AetherColors.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
private fun PreferenceSwitch(label: String, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Row(modifier.fillMaxWidth().aetherRaisedToggleable(checked, onChange,
        shape = RoundedCornerShape(12.dp), elevation = 3.dp, role = Role.Switch).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        PageText(label, Modifier.weight(1f).fillMaxHeight(), maxFontSize = 18f, maxLines = 2)
        Box(Modifier.width(36.dp).height(22.dp).clip(CircleShape).background(if (checked) AetherColors.Accent else AetherColors.Border).padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(16.dp).aetherRaised(shape = CircleShape, elevation = 2.dp,
                background = if (checked) AetherColors.OnAccent else AetherColors.TextSecondary))
        }
    }
}

@Composable
private fun SectionTabs(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label -> PageAction(label, { onSelect(index) }, Modifier.weight(1f).fillMaxHeight(), selected = selected == index) }
    }
}

@Composable
internal fun AppearancePreferences(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    CompactSettingsPanel(250.dp, 780.dp) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val tabsHeight = minOf(48.dp, maxHeight * .22f)
            val galleryHeight = minOf(164.dp, (maxHeight - tabsHeight - 10.dp).coerceAtLeast(0.dp))
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTabs(listOf(stringResource(R.string.settings_theme), stringResource(R.string.settings_accent), stringResource(R.string.settings_ambient)),
                    tab, { tab = it }, Modifier.fillMaxWidth().height(tabsHeight))
                if (tab == 0) ThemeChoices(settings, onChange, Modifier.fillMaxWidth().height(minOf(132.dp, galleryHeight)))
                else Row(Modifier.fillMaxWidth().height(galleryHeight), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (tab == 2) CabinPreview(settings.ambientColor, Modifier.weight(.35f).fillMaxHeight())
                    ColorChoices(settings, tab == 1, onChange, Modifier.weight(if (tab == 2) .65f else 1f).fillMaxHeight())
                }
            }
        }
    }
}

/** Four small previews stay together in one gallery instead of becoming full-page buttons. */
@Composable
private fun ThemeChoices(settings: AppSettings, onChange: (AppSettings) -> Unit, modifier: Modifier) {
    val themes = listOf(Triple(AppTheme.Midnight, R.string.settings_midnight, R.drawable.theme_midnight_black),
        Triple(AppTheme.Arctic, R.string.settings_arctic, R.drawable.theme_arctic),
        Triple(AppTheme.Sport, R.string.settings_sport, R.drawable.theme_sport),
        Triple(AppTheme.Minimal, R.string.settings_minimal, R.drawable.theme_minimal))
    Row(modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        themes.forEach { (theme, label, image) ->
            val selected = settings.theme == theme
            Column(Modifier.weight(1f).fillMaxHeight()
                .aetherRaisedSelectable(selected, { onChange(settings.copy(theme = theme, accentColor = null)) },
                    shape = RoundedCornerShape(12.dp), elevation = 4.dp, role = Role.RadioButton)
                .padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().weight(.65f).clip(RoundedCornerShape(7.dp))) {
                    Image(painterResource(image), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    if (selected) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).clip(CircleShape)
                        .background(AetherColors.PanelDeep), contentAlignment = Alignment.Center) { AetherGlyph(Glyph.Check, size = 14.dp) }
                }
                PageText(stringResource(label), Modifier.fillMaxWidth().weight(.35f), maxFontSize = 16f, minFontSize = 11f,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun ColorChoices(settings: AppSettings, accent: Boolean, onChange: (AppSettings) -> Unit, modifier: Modifier) {
    val colors = listOf(0xFF00DFFF to R.string.settings_cyan, 0xFF008CFF to R.string.settings_blue,
        0xFFA34DFF to R.string.settings_purple, 0xFFFF45B4 to R.string.settings_pink,
        0xFFFFAD42 to R.string.settings_orange, 0xFFFFDF55 to R.string.settings_yellow, 0xFF00DDA3 to R.string.settings_green)
    SettingsGrid(colors.size + if (accent) 1 else 0, 4, modifier.selectableGroup()) { index, tile ->
        if (index == colors.size) PageAction(stringResource(R.string.settings_theme_default), { onChange(settings.copy(accentColor = null)) },
            tile, selected = settings.accentColor == null)
        else {
            val (value, label) = colors[index]
            val selected = (if (accent) settings.accentColor else settings.ambientColor) == value
            Column(tile.aetherRaisedSelectable(selected, { onChange(if (accent) settings.copy(accentColor = value) else settings.copy(ambientColor = value)) },
                shape = RoundedCornerShape(10.dp), elevation = 3.dp, role = Role.RadioButton)
                .padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth().weight(.55f), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(20.dp).aetherRaised(shape = CircleShape, elevation = 2.dp, background = Color(value)))
                }
                PageText(stringResource(label), Modifier.weight(.45f).fillMaxWidth(), maxFontSize = 18f, minFontSize = 10f,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun CabinPreview(color: Long, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        Image(painterResource(R.drawable.climate_cabin_clean_v2), stringResource(R.string.settings_preview), Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Canvas(Modifier.fillMaxSize()) {
            val path = Path().apply { moveTo(0f, size.height * .50f); cubicTo(size.width * .25f, size.height * .34f, size.width * .70f, size.height * .40f, size.width, size.height * .48f) }
            drawPath(path, Color(color).copy(alpha = .18f), style = Stroke(14.dp.toPx()))
            drawPath(path, Color(color), style = Stroke(2.dp.toPx()))
        }
    }
}

@Composable
private fun NavigationPreferences(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    CompactSettingsPanel(246.dp, 640.dp) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val tabsHeight = minOf(48.dp, maxHeight * .20f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTabs(listOf(stringResource(R.string.settings_route_type), stringResource(R.string.review_preferences)), tab,
                { tab = it }, Modifier.fillMaxWidth().height(tabsHeight))
            if (tab == 0) Column(Modifier.fillMaxWidth().weight(1f).selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Balanced" to R.string.settings_balanced, "Fastest" to R.string.settings_fastest, "Eco" to R.string.settings_efficient).forEach { (value, label) ->
                    PageAction(stringResource(label), { onChange(settings.copy(routeType = value)) }, Modifier.fillMaxWidth().weight(1f), selected = settings.routeType == value)
                }
            } else Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PreferenceSwitch(stringResource(R.string.settings_avoid_tolls), settings.avoidTolls, Modifier.weight(1f)) { onChange(settings.copy(avoidTolls = it)) }
                PreferenceSwitch(stringResource(R.string.settings_avoid_ferries), settings.avoidFerries, Modifier.weight(1f)) { onChange(settings.copy(avoidFerries = it)) }
                PreferenceSwitch(stringResource(R.string.review_3d_map), settings.map3D, Modifier.weight(1f)) { onChange(settings.copy(map3D = it)) }
            }
            PageText(stringResource(R.string.settings_navigation_hint), Modifier.fillMaxWidth().height((tabsHeight * .55f)), color = AetherColors.TextSecondary, maxFontSize = 14f)
        }
    }
    }
}

@Composable
private fun SystemPreferences() {
    val context = LocalContext.current
    val version = remember(context) { installedVersion(context) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val storage by produceState<Pair<Long, Long>?>(null, context) {
        value = withContext(Dispatchers.IO) { runCatching { StatFs(context.filesDir.absolutePath).let { it.totalBytes to it.availableBytes } }.getOrNull() }
    }
    CompactSettingsPanel(250.dp, 720.dp) {
      BoxWithConstraints(Modifier.fillMaxSize()) {
        val tabsHeight = minOf(48.dp, maxHeight * .20f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTabs(listOf(stringResource(R.string.review_about), stringResource(R.string.settings_storage), stringResource(R.string.settings_vehicle_health)), tab,
                { tab = it }, Modifier.fillMaxWidth().height(tabsHeight))
            when (tab) {
                0 -> {
                    PageText(stringResource(R.string.settings_version, version ?: stringResource(R.string.settings_version_unknown)),
                        Modifier.fillMaxWidth().weight(.7f), maxFontSize = 26f)
                    PageText(stringResource(R.string.settings_update_info), Modifier.fillMaxWidth().weight(1.3f), color = AetherColors.TextSecondary, maxFontSize = 20f, maxLines = 5)
                }
                1 -> PageText(storage?.let { (total, available) -> stringResource(R.string.settings_storage_amount,
                    Formatter.formatShortFileSize(context, total - available), Formatter.formatShortFileSize(context, total)) }
                    ?: stringResource(R.string.settings_storage_unavailable), Modifier.fillMaxWidth().weight(1f), maxFontSize = 30f, maxLines = 3)
                else -> PageText(stringResource(R.string.settings_demo_health), Modifier.fillMaxWidth().weight(1f), maxFontSize = 26f, maxLines = 3)
            }
        }
    }
    }
}

private fun openDeviceSettings(context: Context, action: String): Boolean = try {
    context.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
} catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

@Suppress("DEPRECATION")
private fun installedVersion(context: Context): String? = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
