package com.aether.hmi.feature.media

import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.hmi.R
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.feature.home.PlaybackActions
import com.aether.hmi.ui.components.aetherRaisedClickable
import com.aether.hmi.ui.components.aetherRaisedSelectable
import com.aether.hmi.ui.theme.AetherColors
import kotlinx.coroutines.flow.StateFlow

data class MediaScreenActions(
    val seek: (Long) -> Unit = {},
    val shuffle: () -> Unit = {},
    val repeat: () -> Unit = {},
    val demo: () -> Unit = {},
    val retry: () -> Unit = {},
    val demoTrack: (Int) -> Unit = {},
)

internal enum class MediaSection(@param:StringRes val label: Int, val glyph: MediaIcon) {
    NowPlaying(R.string.media_now_playing, MediaIcon.Equalizer),
    Songs(R.string.media_songs, MediaIcon.Songs),
    Albums(R.string.media_albums, MediaIcon.Album),
    Artists(R.string.media_artists, MediaIcon.Artist),
    Playlists(R.string.media_playlists, MediaIcon.Playlist),
    Usb(R.string.media_usb, MediaIcon.Usb),
    Bluetooth(R.string.media_bluetooth, MediaIcon.Bluetooth),
    Radio(R.string.media_radio, MediaIcon.Radio),
}

private enum class MediaDestination(@param:StringRes val label: Int) {
    Player(R.string.media_player), Library(R.string.media_library), Queue(R.string.media_queue_tab),
}

/** One task at a time. Playback options and sources are disclosed on demand. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun MediaScreen(
    state: PlaybackState,
    position: StateFlow<Long>,
    playback: PlaybackActions,
    actions: MediaScreenActions,
    climate: ClimateState,
    onAdjustTemperature: (Int) -> Unit,
    onClimateToggle: (ClimateControl) -> Unit,
    onOpenClimate: () -> Unit,
    onPlayIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var destination by rememberSaveable { mutableStateOf(MediaDestination.Player) }
    var section by rememberSaveable { mutableStateOf(MediaSection.Songs) }
    var showOptions by rememberSaveable { mutableStateOf(false) }
    var showCategories by rememberSaveable { mutableStateOf(false) }
    var showOutputs by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        val compact = maxWidth < 840.dp || maxHeight < 500.dp
        val navHeight = minOf(if (compact) 52.dp else 76.dp, maxHeight * .22f)
        val gap = minOf(if (compact) 8.dp else 16.dp, maxHeight * .025f)
        val narrow = maxWidth < 360.dp
        val hasFeedback = state.error || state.importing || !state.connected || state.buffering
        val feedbackHeight = minOf(if (state.error) 48.dp else 28.dp, maxHeight * .2f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(Modifier.fillMaxWidth().height(navHeight).selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MediaDestination.entries.forEach { item ->
                        val selected = destination == item
                        Box(Modifier.weight(1f).fillMaxHeight()
                            .aetherRaisedSelectable(selected, { destination = item }, shape = RoundedCornerShape(12.dp),
                                elevation = 4.dp, role = Role.Tab)
                            .padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            MediaFitText(stringResource(item.label), color = if (selected) AetherColors.Accent else AetherColors.TextSecondary,
                                maxFontSize = if (narrow) 12f else 16f,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                modifier = Modifier.fillMaxSize(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                    Box(Modifier.width(if (compact) 64.dp else 80.dp).fillMaxHeight()
                        .aetherRaisedClickable({ showOptions = true }, shape = RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        MediaFitText(stringResource(R.string.media_more), color = AetherColors.TextSecondary, maxFontSize = 15f,
                            modifier = Modifier.fillMaxSize(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                when (destination) {
                    MediaDestination.Player -> MediaNowPlaying(state, position, playback, actions, compact,
                        Modifier.weight(1f).fillMaxWidth())
                    MediaDestination.Library -> MediaBrowse(section, state, playback.chooseAudio, actions.demo, onPlayIndex,
                        compact, Modifier.weight(1f).fillMaxWidth(), onChangeSection = { showCategories = true })
                    MediaDestination.Queue -> MediaUpNext(state, onPlayIndex, playback.openQueue,
                        actions.demoTrack, actions.demo, compact, Modifier.weight(1f).fillMaxWidth())
                }
                if (hasFeedback) MediaPlaybackFeedback(state, actions.retry, playback.chooseAudio,
                    Modifier.fillMaxWidth().height(feedbackHeight), compact = compact)
        }
        if (showCategories) MediaCategoryDialog(section, {
            section = it
            showCategories = false
        }, { showCategories = false }, roomy = !compact)
        if (showOptions) MediaOptionsDialog(state, playback, actions, onOutput = {
            showOptions = false
            showOutputs = true
        }, onDismiss = { showOptions = false }, roomy = !compact)
        if (showOutputs) MediaOutputDialog(playback.chooseAudio, { showOutputs = false }, roomy = !compact)
    }
}
