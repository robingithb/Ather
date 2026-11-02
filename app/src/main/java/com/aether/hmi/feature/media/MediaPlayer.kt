package com.aether.hmi.feature.media

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aether.hmi.R
import com.aether.hmi.feature.home.PlaybackActions
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.DashboardSurface
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.components.MediaArtwork
import com.aether.hmi.ui.components.aetherRaisedClickable
import com.aether.hmi.ui.theme.AetherColors
import kotlinx.coroutines.flow.StateFlow

/** Artwork and details share the actual viewport; transport and seek slots are always reserved. */
@Composable
internal fun MediaNowPlaying(state: PlaybackState, position: StateFlow<Long>, playback: PlaybackActions,
    actions: MediaScreenActions, compact: Boolean, modifier: Modifier) {
    DashboardSurface(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val padding = minOf(if (compact) 12.dp else 24.dp, maxHeight * .04f)
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val geometry = MediaPlayerGeometry.fit(maxWidth.value, maxHeight.value, state.hasMedia)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(geometry.gap.dp)) {
                    if (geometry.artwork > 0f) MediaArtwork(state.artwork, Modifier.size(geometry.artwork.dp),
                        fallback = R.drawable.home_album_placeholder, artworkUri = state.artworkUri)
                    val detailsHeight = geometry.metadata + geometry.progress + geometry.transport +
                        geometry.gap * if (state.hasMedia) 2 else 1
                    Column(Modifier.weight(1f).height(detailsHeight.dp),
                        verticalArrangement = Arrangement.spacedBy(geometry.gap.dp)) {
                        Column(Modifier.fillMaxWidth().height(geometry.metadata.dp)) {
                            MediaFitText(state.title ?: stringResource(if (state.hasMedia) R.string.unknown_title else R.string.nothing_playing),
                                color = AetherColors.TextPrimary, maxFontSize = if (compact) 25f else 36f,
                                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.1f).fillMaxWidth())
                            MediaFitText(if (state.hasMedia) state.artist ?: stringResource(R.string.unknown_artist)
                                else stringResource(R.string.media_empty_hint), color = AetherColors.TextSecondary,
                                maxFontSize = if (compact) 16f else 21f, maxLines = if (state.hasMedia) 1 else 2,
                                modifier = Modifier.weight(1f).fillMaxWidth())
                        }
                        if (state.hasMedia) {
                            MediaTransport(state, playback, geometry)
                            MediaSeekBar(state, position, actions.seek, compact,
                                Modifier.fillMaxWidth().height(geometry.progress.dp))
                        } else Row(Modifier.fillMaxWidth().height(geometry.transport.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediaPageAction(stringResource(R.string.choose_audio), playback.chooseAudio,
                                enabled = state.connected && !state.importing, modifier = Modifier.weight(1f).fillMaxHeight())
                            MediaPageAction(stringResource(R.string.media_play_demo), actions.demo,
                                enabled = state.connected && !state.importing, modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaTransport(state: PlaybackState, playback: PlaybackActions, geometry: MediaPlayerGeometry) {
    Row(Modifier.fillMaxWidth().height(geometry.transport.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly) {
        MediaControl(stringResource(R.string.previous_track), playback.previous, state.canPrevious,
            size = geometry.smallButton.dp) { AetherGlyph(Glyph.Previous, color = it, size = minOf(30.dp, geometry.smallButton.dp * .5f)) }
        MediaPlayControl(state, playback, geometry.playButton.dp)
        MediaControl(stringResource(R.string.next_track), playback.next, state.canNext,
            size = geometry.smallButton.dp) { AetherGlyph(Glyph.Next, color = it, size = minOf(30.dp, geometry.smallButton.dp * .5f)) }
    }
}

@Composable
internal fun MediaControl(label: String, onClick: () -> Unit, enabled: Boolean = true,
    selected: Boolean? = null, size: Dp = 56.dp, content: @Composable (Color) -> Unit) {
    val active = stringResource(R.string.media_enabled)
    val inactive = stringResource(R.string.media_disabled)
    Box(Modifier.size(size).aetherRaisedClickable(onClick, shape = CircleShape,
        selected = selected == true, enabled = enabled, elevation = 4.dp)
        .semantics {
            contentDescription = label
            if (selected != null) stateDescription = if (selected) active else inactive
        }, contentAlignment = Alignment.Center) {
        content(if (!enabled) AetherColors.Muted.copy(alpha = .45f) else if (selected == true) AetherColors.Accent else AetherColors.TextPrimary)
    }
}

@Composable
private fun MediaPlayControl(state: PlaybackState, playback: PlaybackActions, size: Dp) {
    val label = stringResource(if (state.playing) R.string.pause else R.string.play)
    Box(Modifier.size(size).aetherRaisedClickable(playback.toggle, shape = CircleShape,
        selected = true, enabled = state.connected && state.hasMedia, elevation = 8.dp)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        if (state.buffering) CircularProgressIndicator(Modifier.size(size * .45f), strokeWidth = 2.dp)
        else AetherGlyph(if (state.playing) Glyph.Pause else Glyph.Play, color = AetherColors.TextPrimary, size = size * .46f)
    }
}

@Composable
internal fun MediaUpNext(state: PlaybackState, onPlayIndex: (Int) -> Unit, onSeeAll: () -> Unit,
    onPlayDemo: (Int) -> Unit, onPlayMix: () -> Unit, compact: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    val demos = remember(context) { DemoMedia.queueTracks(context) }
    val entries = remember(state.queue, state.upcomingIndices, state.currentIndex, demos) {
        MediaUpNextPolicy.entries(state.queue.map { it.id }, state.upcomingIndices,
            state.currentIndex, demos.map { it.id })
    }
    DashboardSurface(modifier) {
        BoxWithConstraints(Modifier.fillMaxSize().padding(if (compact) 12.dp else 20.dp)) {
            val headerHeight = minOf(if (compact) 48.dp else 76.dp, maxHeight * .25f)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth().height(headerHeight), verticalAlignment = Alignment.CenterVertically) {
                    MediaFitText(stringResource(R.string.media_up_next), color = AetherColors.TextPrimary,
                        maxFontSize = if (compact) 20f else 26f, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).fillMaxHeight())
                    MediaPageAction(stringResource(if (state.hasMedia) R.string.media_see_all else R.string.media_play_mix),
                        if (state.hasMedia) onSeeAll else onPlayMix,
                        modifier = Modifier.weight(.6f).fillMaxHeight(), enabled = state.connected && !state.importing)
                }
                MediaPagedList(entries.size, "up-next", Modifier.weight(1f), compact) { item, rowHeight ->
                    val entry = entries[item]
                    val queueIndex = entry.queueIndex
                    val demoIndex = entry.demoIndex
                    if (queueIndex != null) state.queue.getOrNull(queueIndex)?.let { track ->
                        MediaTrackRow(track, queueIndex == state.currentIndex, { onPlayIndex(queueIndex) }, compact, rowHeight)
                    } else if (demoIndex != null) {
                        MediaTrackRow(demos[demoIndex], false, { onPlayDemo(demoIndex) }, compact, rowHeight,
                            recommendation = true, enabled = state.connected && !state.importing)
                    }
                }
            }
        }
    }
}

@Composable
internal fun MediaTrackRow(track: QueueTrack, selected: Boolean, onClick: () -> Unit,
    compact: Boolean, rowHeight: Dp? = null, recommendation: Boolean = false, enabled: Boolean = true) {
    Row(Modifier.fillMaxWidth().height(rowHeight ?: if (compact) 64.dp else 80.dp).clip(RoundedCornerShape(10.dp))
        .background(if (selected) AetherColors.AccentSoft else Color.Transparent)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).semantics { this.selected = selected }
        .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val height = rowHeight ?: if (compact) 64.dp else 80.dp
        MediaArtwork(track.artwork, Modifier.size(minOf(if (compact) 48.dp else 60.dp, height * .76f)),
            fallback = if (track.source == MediaSource.Demo) R.drawable.home_album_placeholder else null,
            artworkUri = track.artworkUri)
        Column(Modifier.weight(1f).fillMaxHeight().padding(vertical = 3.dp)) {
            MediaFitText(track.title ?: stringResource(R.string.unknown_title),
                color = if (selected) AetherColors.Accent else AetherColors.TextPrimary,
                maxFontSize = if (compact) 16f else 20f, modifier = Modifier.weight(1f).fillMaxWidth())
            MediaFitText(if (recommendation) stringResource(R.string.media_demo_recommendation)
                else track.artist ?: stringResource(R.string.unknown_artist),
                color = AetherColors.TextSecondary, maxFontSize = if (compact) 13f else 16f,
                modifier = Modifier.weight(1f).fillMaxWidth())
        }
        if (selected) MediaGlyph(MediaIcon.Equalizer, AetherColors.Accent, 20.dp)
    }
}

@Composable
internal fun MediaPlaybackFeedback(state: PlaybackState, onRetry: () -> Unit, onChooseAudio: () -> Unit,
    modifier: Modifier = Modifier, compact: Boolean = true) {
    Row(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MediaFitText(if (state.error) state.errorMessage ?: stringResource(R.string.playback_error)
            else stringResource(when {
                state.importing -> R.string.media_importing
                !state.connected -> R.string.playback_connecting
                else -> R.string.playback_buffering
            }), color = if (state.error) AetherColors.Warning else AetherColors.TextSecondary,
            maxFontSize = 14f, maxLines = 2, modifier = Modifier.weight(1.6f).fillMaxHeight())
        if (state.error) {
            MediaPageAction(stringResource(R.string.media_retry), onRetry,
                enabled = state.connected && state.hasMedia, modifier = Modifier.weight(.7f).fillMaxHeight())
            MediaPageAction(stringResource(R.string.choose_audio), onChooseAudio,
                modifier = Modifier.weight(1f).fillMaxHeight())
        }
    }
}
