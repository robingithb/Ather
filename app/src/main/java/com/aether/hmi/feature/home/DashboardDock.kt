package com.aether.hmi.feature.home

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.hmi.R
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.feature.media.PlaybackState
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.DashboardSurface
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.components.MediaArtwork
import com.aether.hmi.ui.theme.AetherColors
import kotlinx.coroutines.flow.StateFlow

data class PlaybackActions(
    val toggle: () -> Unit = {}, val previous: () -> Unit = {}, val next: () -> Unit = {},
    val favorite: () -> Unit = {}, val openQueue: () -> Unit = {}, val openMedia: () -> Unit = {},
    val chooseAudio: () -> Unit = {},
)

@Composable
fun DashboardDock(
    playback: PlaybackState, position: StateFlow<Long>, playbackActions: PlaybackActions,
    climate: ClimateState, update: ClimateUpdate, onAdjust: (Int) -> Unit,
    onToggle: (ClimateControl) -> Unit, modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 650.dp
        DashboardSurface(Modifier.fillMaxWidth().height(if (compact) 82.dp else 92.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = if (compact) 6.dp else 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MediaDock(playback, position, playbackActions, compact, Modifier.weight(0.56f).fillMaxHeight())
                Divider()
                ClimateDock(climate, update, onAdjust, onToggle, compact, Modifier.weight(0.44f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun MediaDock(state: PlaybackState, position: StateFlow<Long>, actions: PlaybackActions,
    compact: Boolean, modifier: Modifier) {
    val button = if (compact) 23.dp else 33.dp
    val playSize = if (compact) 39.dp else 51.dp
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
        MediaArtwork(state.artwork, Modifier.width(if (compact) 30.dp else 46.dp)
            .height(if (compact) 46.dp else 64.dp).clickable(role = Role.Button, onClick = actions.openMedia),
            fallback = R.drawable.home_album_placeholder)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 8.dp)) {
            Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = actions.openMedia),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(state.title ?: stringResource(if (state.hasMedia) R.string.unknown_title else R.string.nothing_playing),
                    color = AetherColors.TextPrimary, fontSize = if (compact) 10.sp else 13.sp,
                    lineHeight = if (compact) 12.sp else 16.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (state.hasMedia) state.artist ?: stringResource(R.string.unknown_artist) else stringResource(R.string.choose_audio),
                    color = AetherColors.TextSecondary, fontSize = if (compact) 8.sp else 11.sp,
                    lineHeight = if (compact) 10.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            MediaProgress(state, position, compact, Modifier.fillMaxWidth())
        }
        DockButton(Glyph.Previous, stringResource(R.string.previous_track), actions.previous,
            Modifier.size(button), state.canPrevious)
        DockButton(if (state.playing) Glyph.Pause else Glyph.Play,
            stringResource(if (!state.hasMedia) R.string.choose_audio else if (state.playing) R.string.pause else R.string.play),
            if (state.hasMedia) actions.toggle else actions.chooseAudio,
            Modifier.size(playSize).accentGlow(CircleShape).border(1.3.dp, AetherColors.Accent, CircleShape),
            enabled = state.connected, glyphSize = if (compact) 22.dp else 29.dp, shape = CircleShape)
        DockButton(Glyph.Next, stringResource(R.string.next_track), actions.next, Modifier.size(button), state.canNext)
        DockButton(Glyph.Heart, stringResource(if (state.favorite) R.string.remove_favorite else R.string.add_favorite),
            actions.favorite, Modifier.size(button), state.hasMedia, selected = state.favorite)
        DockButton(Glyph.Queue, stringResource(R.string.queue), actions.openQueue, Modifier.size(button))
    }
}

@Composable
private fun MediaProgress(state: PlaybackState, position: StateFlow<Long>, compact: Boolean, modifier: Modifier) {
    // Keep the frequent position subscription below the dashboard and artwork compositions.
    val elapsed by position.collectAsStateWithLifecycle()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        LinearProgressIndicator(progress = {
            if (state.hasMedia && state.durationMs > 0) (elapsed.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
        }, color = AetherColors.Accent, trackColor = AetherColors.Border,
            modifier = Modifier.fillMaxWidth().height(3.dp), gapSize = 0.dp, drawStopIndicator = {})
        if (!state.error && state.hasMedia && state.durationMs > 0) {
            Row(Modifier.fillMaxWidth()) {
                Text(DateUtils.formatElapsedTime(elapsed / 1_000), color = AetherColors.TextSecondary,
                    fontSize = if (compact) 7.sp else 9.sp, lineHeight = 10.sp)
                Spacer(Modifier.weight(1f))
                Text(DateUtils.formatElapsedTime(state.durationMs / 1_000), color = AetherColors.TextSecondary,
                    fontSize = if (compact) 7.sp else 9.sp, lineHeight = 10.sp)
            }
        } else if (state.error || !state.connected || state.buffering || state.hasMedia) {
            Text(stringResource(when {
                state.error -> R.string.playback_error
                !state.connected -> R.string.playback_connecting
                state.buffering -> R.string.playback_buffering
                else -> R.string.duration_unavailable
            }), color = if (state.error) AetherColors.Warning else AetherColors.Muted,
                fontSize = 8.sp, lineHeight = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ClimateDock(state: ClimateState, update: ClimateUpdate, onAdjust: (Int) -> Unit,
    onToggle: (ClimateControl) -> Unit, compact: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 7.dp)) {
        Column(Modifier.weight(0.62f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth().weight(1f), verticalAlignment = Alignment.CenterVertically) {
                val shape = RoundedCornerShape(7.dp)
                val buttonSize = if (compact) 27.dp else 36.dp
                DockButton(Glyph.Minus, stringResource(R.string.decrease_temperature), { onAdjust(-1) },
                    Modifier.size(buttonSize).background(AetherColors.Panel, shape).border(0.6.dp, AetherColors.Border, shape),
                    enabled = state.available && (state.driverCelsius ?: state.minCelsius) > state.minCelsius,
                    glyphSize = if (compact) 18.dp else 23.dp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val description = state.driverCelsius?.let {
                        stringResource(R.string.celsius_accessible, numberFormat(1).format(it))
                    } ?: stringResource(R.string.climate_unavailable)
                    Text(temperatureText(state.driverCelsius), color = AetherColors.Accent,
                        fontSize = if (update == ClimateUpdate.Idle) {
                            if (compact) 20.sp else 27.sp
                        } else if (compact) 18.sp else 24.sp,
                        lineHeight = if (update == ClimateUpdate.Idle) {
                            if (compact) 24.sp else 31.sp
                        } else if (compact) 21.sp else 25.sp,
                        fontWeight = FontWeight.Medium, maxLines = 1,
                        modifier = Modifier.semantics { contentDescription = description })
                    if (update != ClimateUpdate.Idle) Text(stringResource(when (update) {
                        ClimateUpdate.Pending -> R.string.climate_updating
                        ClimateUpdate.Confirmed -> R.string.climate_saved
                        ClimateUpdate.Failed -> R.string.climate_failed
                        ClimateUpdate.Idle -> R.string.unavailable_value
                    }), color = if (update == ClimateUpdate.Failed) AetherColors.Warning else AetherColors.TextSecondary,
                        fontSize = 7.sp, lineHeight = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                DockButton(Glyph.Plus, stringResource(R.string.increase_temperature), { onAdjust(1) },
                    Modifier.size(buttonSize).background(AetherColors.Panel, shape).border(0.6.dp, AetherColors.Border, shape),
                    enabled = state.available && (state.driverCelsius ?: state.maxCelsius) < state.maxCelsius,
                    glyphSize = if (compact) 18.dp else 23.dp)
            }
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp)) {
                listOf(ClimateControl.Auto to R.string.auto_mode, ClimateControl.Ac to R.string.ac,
                    ClimateControl.Sync to R.string.sync).forEach { (control, label) ->
                    ClimateToggle(state, control, stringResource(label), null, compact, onToggle,
                        Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        Row(Modifier.weight(0.38f).fillMaxHeight().padding(vertical = 5.dp)) {
            listOf(Triple(ClimateControl.FrontDefrost, R.string.front, Glyph.Front),
                Triple(ClimateControl.RearDefrost, R.string.rear, Glyph.Rear),
                Triple(ClimateControl.SeatHeating, R.string.seats, Glyph.Seat)).forEach { (control, label, glyph) ->
                Divider()
                ClimateToggle(state, control, stringResource(label), glyph, compact, onToggle,
                    Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

@Composable
private fun ClimateToggle(state: ClimateState, control: ClimateControl, label: String, glyph: Glyph?,
    compact: Boolean, onToggle: (ClimateControl) -> Unit, modifier: Modifier) {
    val selected = state.isEnabled(control)
    val shape = RoundedCornerShape(7.dp)
    val description = stringResource(when (control) {
        ClimateControl.FrontDefrost -> R.string.front_defrost
        ClimateControl.RearDefrost -> R.string.rear_defrost
        ClimateControl.SeatHeating -> R.string.seat_heating
        ClimateControl.Auto -> R.string.auto_mode
        ClimateControl.Ac -> R.string.ac
        ClimateControl.Sync -> R.string.sync
    })
    Column(modifier.clip(shape).then(if (glyph == null) {
        (if (selected) Modifier.accentGlow(shape) else Modifier.background(AetherColors.Panel.copy(alpha = 0.4f)))
            .border(if (selected) 1.dp else 0.6.dp, if (selected) AetherColors.Accent else AetherColors.Border, shape)
    } else if (selected) Modifier.background(AetherColors.Accent.copy(alpha = 0.1f)) else Modifier)
        .toggleable(selected, enabled = state.available, role = Role.Switch, onValueChange = { onToggle(control) })
        .semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp, Alignment.CenterVertically)) {
        val color = if (!state.available) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextSecondary
        if (glyph != null) AetherGlyph(glyph, size = if (compact) 18.dp else 24.dp, color = color)
        Text(label, color = color, fontSize = if (compact) 8.sp else 10.sp, lineHeight = if (compact) 10.sp else 12.sp,
            maxLines = 1, overflow = TextOverflow.Clip)
    }
}

@Composable
private fun DockButton(glyph: Glyph, label: String, onClick: () -> Unit, modifier: Modifier,
    enabled: Boolean = true, selected: Boolean = false, glyphSize: Dp = 21.dp,
    shape: Shape = RoundedCornerShape(7.dp)) {
    Box(modifier.clip(shape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AetherGlyph(glyph, size = glyphSize, color = when {
            !enabled -> AetherColors.Muted.copy(alpha = 0.45f)
            selected -> AetherColors.Accent
            else -> AetherColors.TextPrimary
        })
    }
}

@Composable
private fun Divider() {
    Box(Modifier.width(0.6.dp).fillMaxHeight().background(AetherColors.Border))
}

private fun Modifier.accentGlow(shape: Shape) = clip(shape).background(AetherColors.Panel, shape).drawWithCache {
    val glow = Brush.radialGradient(listOf(AetherColors.Accent.copy(alpha = 0.03f), AetherColors.Accent.copy(alpha = 0.22f)),
        radius = size.maxDimension * 0.65f)
    onDrawBehind { drawRect(glow) }
}


/** The Climate screen uses the same player/session with its own media-only bar. */
@Composable
fun ClimateMediaBar(state: PlaybackState, position: StateFlow<Long>, actions: PlaybackActions, modifier: Modifier = Modifier) {
    var collapsed by rememberSaveable { mutableStateOf(false) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < 650.dp
        DashboardSurface(Modifier.fillMaxWidth().height(if (collapsed) 34.dp else if (compact) 58.dp else 72.dp)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = if (collapsed) 3.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (collapsed) {
                    Text(state.title ?: stringResource(if (state.hasMedia) R.string.unknown_title else R.string.nothing_playing),
                        color = AetherColors.TextPrimary, fontSize = 11.sp, lineHeight = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    DockButton(if (state.playing) Glyph.Pause else Glyph.Play,
                        stringResource(if (!state.hasMedia) R.string.choose_audio else if (state.playing) R.string.pause else R.string.play),
                        if (state.hasMedia) actions.toggle else actions.chooseAudio, Modifier.size(28.dp), enabled = state.connected)
                    DockButton(Glyph.Queue, stringResource(R.string.queue), actions.openQueue, Modifier.size(28.dp))
                } else {
                    MediaDock(state, position, actions, compact, Modifier.weight(0.73f).fillMaxHeight())
                    Spacer(Modifier.weight(0.27f))
                }
                DockButton(if (collapsed) Glyph.Expand else Glyph.Collapse,
                    stringResource(if (collapsed) R.string.expand_media_bar else R.string.collapse_media_bar),
                    { collapsed = !collapsed }, Modifier.size(if (collapsed) 28.dp else 32.dp))
            }
        }
    }
}
