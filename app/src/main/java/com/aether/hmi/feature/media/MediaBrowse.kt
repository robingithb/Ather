package com.aether.hmi.feature.media

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.hmi.R
import com.aether.hmi.ui.components.DashboardSurface
import com.aether.hmi.ui.theme.AetherColors

/** Each category uses finite page slots with explicit navigation to every imported item. */
@Composable
internal fun MediaBrowse(section: MediaSection, state: PlaybackState, onChooseAudio: () -> Unit,
    onDemo: () -> Unit, onPlayIndex: (Int) -> Unit, compact: Boolean, modifier: Modifier,
    onChangeSection: () -> Unit = {}) {
    var group by rememberSaveable(section) { mutableStateOf<String?>(null) }
    val unknownAlbum = stringResource(R.string.media_unknown_album)
    val unknownArtist = stringResource(R.string.unknown_artist)
    val favorites = stringResource(R.string.media_favorites)
    val queue = stringResource(R.string.media_current_queue)
    val groups = when (section) {
        MediaSection.Albums -> state.queue.indices.groupBy { state.queue[it].album ?: unknownAlbum }
        MediaSection.Artists -> state.queue.indices.groupBy { state.queue[it].artist ?: unknownArtist }
        MediaSection.Playlists -> linkedMapOf(queue to state.queue.indices.toList(),
            favorites to state.queue.indices.filter { state.queue[it].id in state.favoriteIds })
        else -> emptyMap()
    }
    DashboardSurface(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp)) {
            val actionHeight = minOf(if (compact) 48.dp else 76.dp, maxHeight * .23f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().height(actionHeight), verticalAlignment = Alignment.CenterVertically) {
                        MediaFitText(group ?: stringResource(section.label), color = AetherColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold, maxFontSize = if (compact) 20f else 25f,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                        if (group != null) MediaPageAction(stringResource(R.string.media_back), { group = null },
                            modifier = Modifier.weight(.5f).fillMaxHeight())
                        else MediaPageAction(stringResource(R.string.media_categories), onChangeSection,
                            modifier = Modifier.weight(.5f).fillMaxHeight())
                    }
                    if (section in listOf(MediaSection.Usb, MediaSection.Bluetooth, MediaSection.Radio)) {
                        MediaSourceInfo(section, onChooseAudio, compact, Modifier.weight(1f))
                    } else if (state.queue.isEmpty()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            MediaFitText(stringResource(R.string.media_library_hint), color = AetherColors.TextSecondary,
                                maxFontSize = 17f, maxLines = 2, modifier = Modifier.weight(1f).fillMaxWidth())
                            Row(Modifier.fillMaxWidth().height(actionHeight)) {
                                MediaPageAction(stringResource(R.string.choose_audio), onChooseAudio,
                                    enabled = state.connected && !state.importing, modifier = Modifier.weight(1f).fillMaxHeight())
                                MediaPageAction(stringResource(R.string.media_play_demo), onDemo,
                                    enabled = state.connected && !state.importing, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                        }
                    } else {
                        val indices = if (section == MediaSection.Songs) state.queue.indices.toList() else group?.let { groups[it] }
                        val pageKey = "${section.name}:${group.orEmpty()}"
                        if (indices != null) {
                            if (indices.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                MediaFitText(stringResource(R.string.media_no_favorites), color = AetherColors.TextSecondary,
                                    maxFontSize = 17f, modifier = Modifier.fillMaxSize())
                            } else MediaPagedList(indices.size, pageKey, Modifier.weight(1f), compact) { item, rowHeight ->
                                val index = indices[item]
                                MediaTrackRow(state.queue[index], index == state.currentIndex, { onPlayIndex(index) }, compact, rowHeight)
                            }
                        } else {
                            val titles = groups.keys.toList()
                            MediaPagedList(titles.size, pageKey, Modifier.weight(1f), compact) { item, rowHeight ->
                                val title = titles[item]
                                Row(Modifier.fillMaxWidth().height(rowHeight)
                                    .clickable(role = Role.Button, onClick = { group = title }).padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    MediaGlyph(if (title == favorites) MediaIcon.Playlist else section.glyph, AetherColors.Accent, 28.dp)
                                    Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp)) {
                                        MediaFitText(title, color = AetherColors.TextPrimary, maxFontSize = 18f,
                                            modifier = Modifier.weight(1f).fillMaxWidth())
                                        MediaFitText(pluralStringResource(R.plurals.media_track_count, groups.getValue(title).size, groups.getValue(title).size),
                                            color = AetherColors.TextSecondary, maxFontSize = 14f,
                                            modifier = Modifier.weight(1f).fillMaxWidth())
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun MediaSourceInfo(section: MediaSection, onChooseAudio: () -> Unit, compact: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val actionHeight = minOf(if (compact) 48.dp else 76.dp, maxHeight * .4f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MediaFitText(stringResource(when (section) {
                MediaSection.Usb -> R.string.media_source_usb_compact
                MediaSection.Bluetooth -> R.string.media_source_bluetooth_compact
                else -> R.string.media_source_radio_compact
            }), color = AetherColors.TextSecondary, maxFontSize = 17f, maxLines = 3,
                modifier = Modifier.weight(1f).fillMaxWidth())
            if (section == MediaSection.Usb) MediaPageAction(stringResource(R.string.choose_audio), onChooseAudio,
                modifier = Modifier.fillMaxWidth().height(actionHeight))
            if (section == MediaSection.Bluetooth) MediaPageAction(stringResource(R.string.media_open_bluetooth), {
                try { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                catch (_: ActivityNotFoundException) { Toast.makeText(context, R.string.media_settings_unavailable, Toast.LENGTH_SHORT).show() }
                catch (_: SecurityException) { Toast.makeText(context, R.string.media_settings_unavailable, Toast.LENGTH_SHORT).show() }
            }, modifier = Modifier.fillMaxWidth().height(actionHeight))
        }
    }
}
