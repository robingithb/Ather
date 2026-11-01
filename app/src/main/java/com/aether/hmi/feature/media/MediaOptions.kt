package com.aether.hmi.feature.media

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import com.aether.hmi.R
import com.aether.hmi.feature.home.PlaybackActions
import com.aether.hmi.ui.components.DashboardSurface
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.theme.AetherColors

@Composable
internal fun MediaCategoryDialog(selected: MediaSection, onSelect: (MediaSection) -> Unit, onDismiss: () -> Unit,
    roomy: Boolean = false) {
    val sections = MediaSection.entries.filter { it != MediaSection.NowPlaying }
    MediaModal(stringResource(R.string.media_library), onDismiss, roomy, sections.size, "categories") { index, height ->
        val section = sections[index]
        MediaOptionRow(stringResource(section.label), { onSelect(section) }, height, selected = selected == section)
    }
}

/** The five everyday options stay together in two named groups, with their current states visible. */
@Composable
internal fun MediaOptionsDialog(state: PlaybackState, playback: PlaybackActions, actions: MediaScreenActions,
    onOutput: () -> Unit, onDismiss: () -> Unit, roomy: Boolean = false) {
    val playbackEnabled = state.hasMedia && state.connected
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DashboardSurface(Modifier.widthIn(max = if (roomy) 900.dp else 820.dp).heightIn(max = 390.dp)
            .fillMaxWidth(.94f).fillMaxHeight(.92f)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val padding = minOf(16.dp, maxHeight * .035f)
                val headerHeight = minOf(44.dp, maxHeight * .16f)
                Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().height(headerHeight), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MediaGlyph(MediaIcon.Equalizer, AetherColors.Accent, 23.dp)
                        MediaFitText(stringResource(R.string.media_options), maxFontSize = 24f,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).fillMaxHeight())
                        MediaPageAction(stringResource(R.string.close), onDismiss, Modifier.width(84.dp).fillMaxHeight())
                    }
                    Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MediaOptionGroup(stringResource(R.string.media_options_playback), Modifier.weight(.57f).fillMaxHeight()) {
                            MediaStateOption(stringResource(R.string.media_options_favorite),
                                stringResource(R.string.media_options_favorite_hint), state.favorite, playback.favorite,
                                playbackEnabled, Modifier.weight(1f).fillMaxWidth()) {
                                AetherGlyph(Glyph.Heart, color = it, size = 23.dp)
                            }
                            MediaStateOption(stringResource(R.string.media_shuffle),
                                stringResource(R.string.media_options_shuffle_hint), state.shuffleEnabled, actions.shuffle,
                                playbackEnabled, Modifier.weight(1f).fillMaxWidth()) {
                                MediaGlyph(MediaIcon.Shuffle, it, 23.dp)
                            }
                            MediaRepeatOptions(state.repeatMode, actions.repeat, playbackEnabled,
                                Modifier.weight(2f).fillMaxWidth())
                        }
                        MediaOptionGroup(stringResource(R.string.media_options_sources), Modifier.weight(.43f).fillMaxHeight()) {
                            MediaSourceOption(stringResource(R.string.choose_audio), stringResource(R.string.media_options_import_hint),
                                { onDismiss(); playback.chooseAudio() }, state.connected && !state.importing,
                                Modifier.weight(1f).fillMaxWidth(), MediaIcon.Usb)
                            MediaSourceOption(stringResource(R.string.media_options_output), stringResource(R.string.media_options_output_hint),
                                onOutput, true, Modifier.weight(1f).fillMaxWidth(), MediaIcon.Bluetooth)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaOptionGroup(label: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(AetherColors.PanelDeep).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)) {
        MediaFitText(label, color = AetherColors.TextSecondary, maxFontSize = 14f,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().height(22.dp))
        content()
    }
}

@Composable
private fun MediaStateOption(label: String, hint: String, checked: Boolean, onClick: () -> Unit,
    enabled: Boolean, modifier: Modifier, icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit) {
    val status = stringResource(if (checked) R.string.media_options_on else R.string.media_options_off)
    val color = if (!enabled) AetherColors.Muted else if (checked) AetherColors.Accent else AetherColors.TextPrimary
    BoxWithConstraints(modifier) {
        val showHint = maxHeight >= 56.dp
        Row(Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(if (checked) AetherColors.AccentSoft else AetherColors.Panel)
            .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = { onClick() })
            .semantics { contentDescription = "$label, $hint"; stateDescription = status }.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            icon(color)
            if (showHint) Column(Modifier.weight(1f).fillMaxHeight()) {
                MediaFitText(label, color = color, maxFontSize = 17f, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).fillMaxWidth())
                MediaFitText(hint, color = AetherColors.TextSecondary, maxFontSize = 12f,
                    modifier = Modifier.weight(.8f).fillMaxWidth())
            } else MediaFitText(label, color = color, maxFontSize = 17f, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f).fillMaxHeight())
            MediaFitText(status, color = color, maxFontSize = 15f, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.width(38.dp).fillMaxHeight(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

@Composable
private fun MediaRepeatOptions(current: Int, onCycle: () -> Unit, enabled: Boolean, modifier: Modifier) {
    val modes = listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE)
    val descriptions = listOf(R.string.media_options_repeat_off_hint, R.string.media_options_repeat_all_hint, R.string.media_options_repeat_one_hint)
    val currentIndex = modes.indexOf(current).coerceAtLeast(0)
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(AetherColors.Panel).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().weight(.8f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaGlyph(MediaIcon.Repeat, if (enabled) AetherColors.Accent else AetherColors.Muted, 20.dp)
            MediaFitText(stringResource(R.string.media_options_repeat), maxFontSize = 16f,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(.65f).fillMaxHeight())
            MediaFitText(stringResource(descriptions[currentIndex]), color = AetherColors.TextSecondary,
                maxFontSize = 11f, maxLines = 2, modifier = Modifier.weight(1f).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(R.string.media_options_off, R.string.media_options_repeat_all, R.string.media_options_repeat_one).forEachIndexed { index, label ->
                val description = stringResource(descriptions[index])
                Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                    .background(if (index == currentIndex) AetherColors.AccentSoft else AetherColors.PanelDeep)
                    .border(.7.dp, if (index == currentIndex) AetherColors.Accent else AetherColors.Border, RoundedCornerShape(7.dp))
                    .clickable(enabled = enabled, role = Role.RadioButton) {
                        repeat((index - currentIndex + modes.size) % modes.size) { onCycle() }
                    }.semantics { selected = index == currentIndex; contentDescription = description }, contentAlignment = Alignment.Center) {
                    MediaFitText(stringResource(label), maxFontSize = 14f,
                        color = if (!enabled) AetherColors.Muted else if (index == currentIndex) AetherColors.Accent else AetherColors.TextSecondary,
                        modifier = Modifier.fillMaxSize(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun MediaSourceOption(label: String, hint: String, onClick: () -> Unit, enabled: Boolean,
    modifier: Modifier, icon: MediaIcon) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(AetherColors.Panel)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth().weight(.65f), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            MediaGlyph(icon, if (enabled) AetherColors.Accent else AetherColors.Muted, 22.dp)
            MediaFitText(label, maxFontSize = 17f, maxLines = 2, fontWeight = FontWeight.SemiBold,
                color = if (enabled) AetherColors.TextPrimary else AetherColors.Muted,
                modifier = Modifier.weight(1f).fillMaxHeight())
        }
        MediaFitText(hint, color = AetherColors.TextSecondary, maxFontSize = 13f, maxLines = 2,
            modifier = Modifier.fillMaxWidth().weight(.6f))
    }
}

@Composable
internal fun MediaOutputDialog(onChooseAudio: () -> Unit, onDismiss: () -> Unit, roomy: Boolean = false) {
    val outputs = rememberMediaOutputs()
    val context = LocalContext.current
    val resources = LocalResources.current
    val labels = outputs.devices.map { output ->
        output.name.ifBlank { resources.getString(when (output.kind) {
            MediaOutputKind.Bluetooth -> R.string.media_side_bluetooth
            MediaOutputKind.Usb -> R.string.media_side_output_usb
            MediaOutputKind.Wired -> R.string.media_side_output_wired
            MediaOutputKind.BuiltIn -> R.string.media_side_output_builtin
            MediaOutputKind.Other -> R.string.media_side_output_other
        }) }
    }
    val outputRows = when {
        !outputs.available -> listOf(stringResource(R.string.media_side_output_unknown))
        labels.isEmpty() -> listOf(stringResource(R.string.media_side_output_none))
        else -> labels
    }
    MediaModal(stringResource(R.string.media_side_output_source), onDismiss, roomy,
        outputRows.size + 3, "outputs") { index, height ->
        when {
            index < outputRows.size -> MediaFitText(outputRows[index], color = AetherColors.TextPrimary,
                maxFontSize = 17f, maxLines = 3, modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp))
            index == outputRows.size -> MediaFitText(stringResource(R.string.media_side_routing_note),
                color = AetherColors.TextSecondary, maxFontSize = 15f, maxLines = 3,
                modifier = Modifier.fillMaxWidth().height(height).padding(horizontal = 8.dp))
            index == outputRows.size + 1 -> MediaOptionRow(stringResource(R.string.media_open_bluetooth), {
                try {
                    context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(context, R.string.media_settings_unavailable, Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(context, R.string.media_settings_unavailable, Toast.LENGTH_SHORT).show()
                }
            }, height)
            else -> MediaOptionRow(stringResource(R.string.choose_audio), {
                onDismiss()
                onChooseAudio()
            }, height)
        }
    }
}

/** All modal content is paged inside a height derived from the window, with Close always visible. */
@Composable
private fun MediaModal(title: String, onDismiss: () -> Unit, roomy: Boolean, itemCount: Int,
    contentKey: String, row: @Composable (Int, Dp) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DashboardSurface(Modifier.widthIn(max = 640.dp).fillMaxWidth(.94f).fillMaxHeight(.92f)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val padding = minOf(16.dp, maxHeight * .035f)
                val titleHeight = minOf(38.dp, maxHeight * .14f)
                val closeHeight = minOf(if (roomy) 76.dp else 48.dp, maxHeight * .2f)
                Column(Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    MediaFitText(title, color = AetherColors.TextPrimary, maxFontSize = 23f,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().height(titleHeight))
                    MediaPagedList(itemCount, "modal:$contentKey", Modifier.weight(1f).fillMaxWidth(), compact = !roomy,
                        row = row)
                    MediaOptionRow(stringResource(R.string.close), onDismiss, closeHeight)
                }
            }
        }
    }
}

@Composable
private fun MediaOptionRow(label: String, onClick: () -> Unit, height: Dp, detail: String? = null,
    selected: Boolean = false, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(height).padding(vertical = 2.dp).clip(RoundedCornerShape(12.dp))
        .background(if (selected) AetherColors.AccentSoft else AetherColors.PanelDeep)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { this.selected = selected }.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaFitText(label, modifier = Modifier.weight(1f).fillMaxHeight(), maxFontSize = 17f, maxLines = 2,
            color = if (!enabled) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextPrimary)
        if (detail != null) MediaFitText(detail, color = AetherColors.TextSecondary, maxFontSize = 15f,
            modifier = Modifier.weight(.4f).fillMaxHeight())
    }
}
