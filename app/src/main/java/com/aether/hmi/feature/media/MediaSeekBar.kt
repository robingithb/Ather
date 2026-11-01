package com.aether.hmi.feature.media

import android.os.SystemClock
import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aether.hmi.R
import com.aether.hmi.ui.theme.AetherColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/** Standard slider interaction/semantics with a quiet straight track and a stable scrub preview. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun MediaSeekBar(state: PlaybackState, position: StateFlow<Long>, onSeek: (Long) -> Unit,
    compact: Boolean, modifier: Modifier) {
    val observed by position.collectAsStateWithLifecycle()
    val duration = state.durationMs.coerceAtLeast(0L)
    val mediaId = state.queue.getOrNull(state.currentIndex)?.id ?: state.title
    val enabled = state.connected && state.canSeek && duration > 0L
    var dragging by remember(mediaId, duration, enabled) { mutableStateOf<Float?>(null) }
    var pending by remember(mediaId, duration, enabled) { mutableStateOf<MediaPendingSeek?>(null) }
    val interactions = remember(mediaId) { MutableInteractionSource() }
    val dragged by interactions.collectIsDraggedAsState()
    val focused by interactions.collectIsFocusedAsState()

    LaunchedEffect(observed, pending) {
        pending = MediaSeekPolicy.resolvePending(pending, observed, SystemClock.uptimeMillis())
    }
    LaunchedEffect(pending) {
        val requested = pending ?: return@LaunchedEffect
        val wait = (MediaSeekPolicy.SETTLE_TIMEOUT_MS -
            (SystemClock.uptimeMillis() - requested.requestedAtUptimeMs)).coerceAtLeast(0L)
        delay(wait)
        if (pending == requested) pending = MediaSeekPolicy.resolvePending(requested, observed, SystemClock.uptimeMillis())
    }

    val target = MediaSeekPolicy.fraction(pending?.targetPositionMs ?: observed, duration)
    val animated by animateFloatAsState(target,
        animationSpec = if (state.playing && pending == null && dragging == null) tween(100, easing = LinearEasing) else snap(),
        label = "Playback position")
    val shown = dragging ?: if (pending != null) target else animated
    val elapsed = DateUtils.formatElapsedTime(MediaSeekPolicy.targetPosition(dragging ?: target, duration) / 1_000)
    val total = if (duration > 0) DateUtils.formatElapsedTime(duration / 1_000) else stringResource(R.string.unavailable_value)
    val seekLabel = stringResource(R.string.media_seek)
    val seekDescription = stringResource(R.string.media_seek_accessible, elapsed, total)
    val accent = AetherColors.Accent
    val rail = AetherColors.Border.copy(alpha = .6f)
    val ink = if (enabled) AetherColors.TextPrimary else AetherColors.Muted
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val active = dragging != null || dragged || focused
    val thumbDiameter by animateDpAsState(if (active) 13.dp else 9.dp, tween(120), label = "Seek thumb")

    BoxWithConstraints(modifier) {
        val sliderHeight = minOf(if (compact) 48.dp else 76.dp, maxHeight * .65f)
        val thumbSlot = minOf(32.dp, sliderHeight)
        val trackHeight = minOf(if (compact) 3.dp else 4.dp, maxHeight * .12f)
        Column(Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                Slider(value = shown.coerceIn(0f, 1f), onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { fraction ->
                            val destination = MediaSeekPolicy.targetPosition(fraction, duration)
                            pending = MediaSeekPolicy.beginSeek(destination, observed, duration, SystemClock.uptimeMillis())
                            onSeek(destination)
                        }
                        dragging = null
                    }, enabled = enabled, interactionSource = interactions,
                    thumb = {
                        Box(Modifier.size(thumbSlot), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                val core = minOf(thumbDiameter.toPx(), size.minDimension * .74f) / 2f
                                if (active && enabled) drawCircle(accent.copy(alpha = .16f), size.minDimension / 2f)
                                drawCircle(ink, core)
                                if (enabled) drawCircle(accent.copy(alpha = .8f), core, style = Stroke(.8.dp.toPx()))
                            }
                        }
                    }, track = {
                        Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
                            val radius = CornerRadius(size.height / 2f)
                            drawRoundRect(rail, cornerRadius = radius)
                            val played = size.width * shown.coerceIn(0f, 1f)
                            drawRoundRect(if (enabled) accent else ink,
                                topLeft = Offset(if (rtl) size.width - played else 0f, 0f),
                                size = Size(played, size.height), cornerRadius = radius)
                        }
                    }, modifier = Modifier.fillMaxWidth().height(sliderHeight).semantics {
                        contentDescription = seekLabel
                        stateDescription = seekDescription
                    })
            }
            Row(Modifier.fillMaxWidth().weight(1f).padding(horizontal = thumbSlot / 2)) {
                SeekTime(elapsed, compact, Modifier.weight(1f).fillMaxHeight())
                SeekTime(total, compact, Modifier.weight(1f).fillMaxHeight(), TextAlign.End)
            }
        }
    }
}

@Composable
private fun SeekTime(text: String, compact: Boolean, modifier: Modifier, alignment: TextAlign = TextAlign.Start) {
    Box(modifier, contentAlignment = if (alignment == TextAlign.End) Alignment.CenterEnd else Alignment.CenterStart) {
        BasicText(text, Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = TextStyle(color = AetherColors.TextSecondary, fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum", lineHeight = 1.1.em, textAlign = alignment),
            autoSize = TextAutoSize.StepBased(12.sp, if (compact) 13.sp else 15.sp, .5.sp))
    }
}
