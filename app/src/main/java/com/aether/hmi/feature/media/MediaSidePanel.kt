package com.aether.hmi.feature.media

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aether.hmi.R
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.feature.climate.ClimateGlyph
import com.aether.hmi.feature.climate.ClimateIcon
import com.aether.hmi.feature.home.numberFormat
import com.aether.hmi.feature.home.temperatureText
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.DashboardSurface
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors

@Composable
fun MediaSidePanel(
    climate: ClimateState,
    onAdjustTemperature: (Int) -> Unit,
    onClimateToggle: (ClimateControl) -> Unit,
    onOpenClimate: () -> Unit,
    onChooseAudio: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    BoxWithConstraints(modifier) {
        val small = compact || maxWidth < 205.dp || maxHeight < 400.dp
        val gap = (maxHeight * .02f).coerceAtMost(10.dp)
        // Allocate the entire viewport before measuring either card. No row can push
        // the other card below the display, including at increased system font sizes.
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            MediaClimateCard(climate, onAdjustTemperature, onClimateToggle, onOpenClimate,
                small, Modifier.fillMaxWidth().weight(.43f))
            MediaOutputCard(onChooseAudio, small, Modifier.fillMaxWidth().weight(.57f))
        }
    }
}

@Composable
private fun MediaClimateCard(
    state: ClimateState,
    onAdjust: (Int) -> Unit,
    onToggle: (ClimateControl) -> Unit,
    onOpen: () -> Unit,
    compact: Boolean,
    modifier: Modifier,
) {
    DashboardSurface(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val padding = minOf(maxWidth * .045f, maxHeight * .045f, 10.dp)
            val gap = (maxHeight * .025f).coerceAtMost(8.dp)
            val contentHeight = (maxHeight - padding * 2 - gap * 2).coerceAtLeast(0.dp)
            val contentWidth = (maxWidth - padding * 2).coerceAtLeast(0.dp)
            val headerHeight = contentHeight * .28f
            val temperatureHeight = contentHeight * .4f
            val headerGlyph = minOf(headerHeight * .72f, contentWidth * .18f, 40.dp)
            val stepSize = minOf(temperatureHeight, contentWidth * .26f, 48.dp)
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(gap)) {
                val heading = stringResource(R.string.media_side_open_climate)
                Row(Modifier.fillMaxWidth().weight(.28f).clip(RoundedCornerShape(10.dp))
                    .background(AetherColors.PanelDeep.copy(alpha = .75f))
                    .border(.6.dp, AetherColors.Border, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button, onClick = onOpen).semantics { contentDescription = heading }
                    .padding(horizontal = contentWidth * .04f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(contentWidth * .035f)) {
                    Box(Modifier.size(headerGlyph)
                        .background(AetherColors.AccentSoft, CircleShape)
                        .border(.7.dp, AetherColors.Border, CircleShape), contentAlignment = Alignment.Center) {
                        ClimateGlyph(ClimateIcon.Fan, size = headerGlyph * .65f, color = AetherColors.Accent)
                    }
                    SideFittedText(stringResource(R.string.media_side_climate),
                        Modifier.weight(1f).fillMaxHeight(), if (compact) 13f else 17f,
                        fontWeight = FontWeight.SemiBold)
                    AetherGlyph(Glyph.Chevron, size = minOf(headerHeight * .5f, 15.dp), color = AetherColors.TextSecondary)
                }
                Row(Modifier.fillMaxWidth().weight(.4f), verticalAlignment = Alignment.CenterVertically) {
                    ClimateStepButton(Glyph.Minus, stringResource(R.string.decrease_temperature), { onAdjust(-1) },
                        state.available && state.driverCelsius != null && state.driverCelsius > state.minCelsius,
                        stepSize)
                    val description = state.driverCelsius?.let {
                        stringResource(R.string.celsius_accessible, numberFormat(1).format(it))
                    } ?: stringResource(R.string.climate_unavailable)
                    SideFittedText(temperatureText(state.driverCelsius),
                        Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = description },
                        if (compact) 25f else 35f, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    ClimateStepButton(Glyph.Plus, stringResource(R.string.increase_temperature), { onAdjust(1) },
                        state.available && state.driverCelsius != null && state.driverCelsius < state.maxCelsius,
                        stepSize)
                }
                Row(Modifier.fillMaxWidth().weight(.32f), horizontalArrangement = Arrangement.spacedBy(contentWidth * .025f)) {
                    listOf(ClimateControl.Auto to R.string.auto_mode, ClimateControl.Ac to R.string.ac,
                        ClimateControl.Sync to R.string.sync).forEach { (control, label) ->
                        val selected = state.isEnabled(control)
                        val shape = RoundedCornerShape(if (compact) 8.dp else 10.dp)
                        val color = if (!state.available) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextSecondary
                        val enabled = state.available && (control != ClimateControl.Sync || state.sync ||
                            (state.driverCelsius != null && state.passengerCelsius != null))
                        Box(Modifier.weight(1f).fillMaxHeight().clip(shape)
                            .background(if (selected) AetherColors.AccentSoft else AetherColors.PanelDeep)
                            .border(if (selected) 1.dp else .6.dp, if (selected) AetherColors.Accent else AetherColors.Border, shape)
                            .toggleable(selected, enabled = enabled, role = Role.Switch,
                                onValueChange = { onToggle(control) }).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                            SideFittedText(stringResource(label), Modifier.fillMaxSize(), if (compact) 11f else 14f,
                                color, if (selected) FontWeight.SemiBold else FontWeight.Normal, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClimateStepButton(glyph: Glyph, label: String, onClick: () -> Unit, enabled: Boolean, size: Dp) {
    Box(Modifier.size(size).clip(CircleShape)
        .background(AetherColors.PanelDeep)
        .border(.7.dp, AetherColors.Border, CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AetherGlyph(glyph, size = size * .52f,
            color = if (enabled) AetherColors.TextSecondary else AetherColors.Muted)
    }
}

@Composable
private fun MediaOutputCard(onChooseAudio: () -> Unit, compact: Boolean, modifier: Modifier) {
    val outputs = rememberMediaOutputs()
    val context = LocalContext.current
    val fallbackMessage = stringResource(R.string.media_side_settings_unavailable)
    var showRouting by remember { mutableStateOf(false) }
    val bluetooth = outputs.devices.firstOrNull { it.kind == MediaOutputKind.Bluetooth }
    val bluetoothSubtitle = when {
        bluetooth == null -> stringResource(R.string.media_side_bluetooth_settings)
        bluetooth.name.isBlank() -> stringResource(R.string.media_side_bluetooth_available)
        else -> stringResource(R.string.media_side_bluetooth_connected, bluetooth.name)
    }
    val labels = outputs.devices.map { it.kind }.distinct().map { kind ->
        stringResource(when (kind) {
            MediaOutputKind.Bluetooth -> R.string.media_side_bluetooth
            MediaOutputKind.Usb -> R.string.media_side_output_usb
            MediaOutputKind.Wired -> R.string.media_side_output_wired
            MediaOutputKind.BuiltIn -> R.string.media_side_output_builtin
            MediaOutputKind.Other -> R.string.media_side_output_other
        })
    }
    val outputDescription = when {
        !outputs.available -> stringResource(R.string.media_side_output_unknown)
        labels.isEmpty() -> stringResource(R.string.media_side_output_none)
        else -> stringResource(R.string.media_side_connected_outputs, labels.joinToString(", "))
    }
    DashboardSurface(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val padding = minOf(maxWidth * .045f, maxHeight * .04f, 10.dp)
            val gap = (maxHeight * .025f).coerceAtMost(8.dp)
            val contentHeight = (maxHeight - padding * 2 - gap * 3).coerceAtLeast(0.dp)
            val contentWidth = (maxWidth - padding * 2).coerceAtLeast(0.dp)
            val headerHeight = contentHeight * .2f
            val bluetoothHeight = contentHeight * .3f
            Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth().weight(.2f)
                    .clip(RoundedCornerShape(10.dp)).background(AetherColors.PanelDeep)
                    .border(.6.dp, AetherColors.Border, RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { showRouting = true }
                    .padding(horizontal = contentWidth * .04f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(contentWidth * .04f)) {
                    SideGlyph(SideIcon.Radio, size = minOf(headerHeight * .72f, contentWidth * .16f, 29.dp), color = AetherColors.TextPrimary)
                    SideFittedText(stringResource(R.string.media_side_output_source), Modifier.weight(1f).fillMaxHeight(),
                        if (compact) 12f else 15f, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
                Row(Modifier.fillMaxWidth().weight(.3f).clip(RoundedCornerShape(11.dp))
                    .background(Brush.linearGradient(listOf(AetherColors.PanelRaised.copy(alpha = .75f), AetherColors.PanelDeep)))
                    .border(.7.dp, AetherColors.Border, RoundedCornerShape(11.dp))
                    .clickable(role = Role.Button) {
                        try {
                            context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: ActivityNotFoundException) {
                            Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
                        } catch (_: SecurityException) {
                            Toast.makeText(context, fallbackMessage, Toast.LENGTH_SHORT).show()
                        }
                    }.padding(horizontal = contentWidth * .04f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(contentWidth * .04f)) {
                    SideGlyph(SideIcon.Bluetooth, size = minOf(bluetoothHeight * .6f, contentWidth * .18f, 34.dp), color = AetherColors.Accent)
                    Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = bluetoothHeight * .08f)) {
                        SideFittedText(stringResource(R.string.media_side_bluetooth), Modifier.fillMaxWidth().weight(.52f),
                            if (compact) 12f else 15f, fontWeight = FontWeight.Medium)
                        SideFittedText(bluetoothSubtitle, Modifier.fillMaxWidth().weight(.48f),
                            if (compact) 9f else 11f, AetherColors.TextSecondary)
                    }
                    AetherGlyph(Glyph.Chevron, size = minOf(bluetoothHeight * .35f, 15.dp), color = AetherColors.TextSecondary)
                }
                Row(Modifier.fillMaxWidth().weight(.4f), horizontalArrangement = Arrangement.spacedBy(contentWidth * .025f)) {
                    OutputCapability(SideIcon.Speaker, stringResource(R.string.media_side_device_audio), null,
                        compact, Modifier.weight(1f).fillMaxHeight(), description = outputDescription,
                        onClick = { showRouting = true })
                    OutputCapability(SideIcon.Usb, stringResource(R.string.media_side_usb_files),
                        stringResource(R.string.media_side_browse_files), compact, Modifier.weight(1f).fillMaxHeight(), onClick = onChooseAudio)
                    OutputCapability(SideIcon.Radio, stringResource(R.string.media_side_radio),
                        stringResource(R.string.media_side_radio_unavailable), compact, Modifier.weight(1f).fillMaxHeight(),
                        enabled = false, description = stringResource(R.string.media_side_radio_description))
                }
                SideFittedText(stringResource(R.string.media_side_routing_note), Modifier.fillMaxWidth().weight(.1f),
                    if (compact) 8f else 10f, AetherColors.Muted, textAlign = TextAlign.Center)
            }
        }
    }
    if (showRouting) MediaRoutingDialog(outputDescription, bluetoothSubtitle) { showRouting = false }
}

@Composable
private fun OutputCapability(
    icon: SideIcon,
    title: String,
    subtitle: String?,
    compact: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    description: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val color = if (enabled) AetherColors.TextSecondary else AetherColors.Muted
    BoxWithConstraints(modifier.then(if (onClick != null) Modifier.clip(RoundedCornerShape(9.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick) else Modifier)
        .semantics(mergeDescendants = true) {
            if (description != null) contentDescription = description
            if (!enabled) disabled()
        }) {
        val iconSize = minOf(maxWidth * .78f, maxHeight * .53f, 51.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.fillMaxWidth().weight(.55f), contentAlignment = Alignment.Center) {
                Box(Modifier.size(iconSize).clip(CircleShape)
                    .background(if (enabled && icon == SideIcon.Speaker) AetherColors.AccentSoft else AetherColors.PanelDeep)
                    .border(.7.dp, if (enabled && icon == SideIcon.Speaker) AetherColors.Accent else AetherColors.Border,
                        CircleShape), contentAlignment = Alignment.Center) {
                    SideGlyph(icon, size = iconSize * .55f,
                        color = if (enabled && icon == SideIcon.Speaker) AetherColors.Accent else color)
                }
            }
            SideFittedText(title, Modifier.fillMaxWidth().weight(if (subtitle == null) .45f else .25f),
                if (compact) 9f else 11f, color, textAlign = TextAlign.Center, maxLines = if (subtitle == null) 2 else 1)
            if (subtitle != null) SideFittedText(subtitle, Modifier.fillMaxWidth().weight(.2f),
                if (compact) 8f else 9f, color, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun MediaRoutingDialog(outputDescription: String, bluetoothDescription: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DashboardSurface(Modifier.fillMaxWidth(.82f).fillMaxHeight(.72f)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val padding = minOf(maxWidth * .04f, maxHeight * .05f, 20.dp)
                Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(padding * .4f)) {
                    SideFittedText(stringResource(R.string.media_side_output_source), Modifier.fillMaxWidth().weight(.16f),
                        21f, fontWeight = FontWeight.SemiBold)
                    SideFittedText(outputDescription, Modifier.fillMaxWidth().weight(.23f),
                        16f, AetherColors.TextSecondary, maxLines = 4)
                    SideFittedText(bluetoothDescription, Modifier.fillMaxWidth().weight(.23f),
                        16f, AetherColors.TextSecondary, maxLines = 3)
                    SideFittedText(stringResource(R.string.media_side_routing_note), Modifier.fillMaxWidth().weight(.18f),
                        15f, AetherColors.TextSecondary, maxLines = 2)
                    Box(Modifier.fillMaxWidth().weight(.2f).clip(RoundedCornerShape(10.dp))
                        .background(AetherColors.AccentSoft).border(.7.dp, AetherColors.Accent, RoundedCornerShape(10.dp))
                        .clickable(role = Role.Button, onClick = onDismiss), contentAlignment = Alignment.Center) {
                        SideFittedText(stringResource(R.string.media_side_close), Modifier.fillMaxSize(),
                            16f, AetherColors.Accent, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Text measures within its row so font scale and long device names cannot grow a card. */
@Composable
private fun SideFittedText(
    text: String,
    modifier: Modifier,
    maximumSize: Float,
    color: Color = AetherColors.TextPrimary,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
    maxLines: Int = 1,
) {
    BoxWithConstraints(modifier.clipToBounds(),
        contentAlignment = if (textAlign == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
        val minimumSize = with(LocalDensity.current) { (maxHeight * .9f).toSp().value }.coerceIn(.5f, 6f)
        BasicText(text, Modifier.fillMaxWidth(),
            style = TextStyle(color = color, fontSize = maximumSize.sp, lineHeight = 1.1.em,
                fontWeight = fontWeight, textAlign = textAlign),
            maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            autoSize = TextAutoSize.StepBased(minFontSize = minimumSize.sp, maxFontSize = maximumSize.sp, stepSize = .5.sp))
    }
}

private enum class SideIcon(val path: String) {
    Bluetooth("M5,5 L18,18 L12,23 L12,1 L18,6 L5,19"),
    Usb("M8,6 L8,1 L16,1 L16,6 M11,2 L11,5 M14,2 L14,5 M6,7 L18,7 L18,23 L6,23 Z M12,10 L12,20 M9,15 L12,18 L15,15 M9,12 L9,15 M15,11 L15,15"),
    Speaker("M2,9 L7,9 L13,4 L13,20 L7,15 L2,15 Z M17,8 Q21,12 17,16 M20,4 Q27,12 20,20"),
    Radio("M10,10 Q12,7 14,10 Q16,13 12,15 Q8,13 10,10 M8,6 Q2,12 8,18 M16,6 Q22,12 16,18 M5,3 Q-3,12 5,21 M19,3 Q27,12 19,21 M11,16 L8,24 M13,16 L16,24"),
}

@Composable
private fun SideGlyph(icon: SideIcon, size: Dp, color: Color) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(Modifier.size(size)) {
        scale(this.size.width / 24f, this.size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(1.55f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
