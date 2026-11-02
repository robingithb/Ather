package com.aether.hmi.feature.media

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.aether.hmi.R
import com.aether.hmi.ui.components.AetherGlyph
import com.aether.hmi.ui.components.DashboardSurface
import com.aether.hmi.ui.components.Glyph
import com.aether.hmi.ui.components.aetherRaisedClickable
import com.aether.hmi.ui.theme.AetherColors

@Composable
fun MediaLibrary(state: PlaybackState, onChooseAudio: () -> Unit, onPlayIndex: (Int) -> Unit,
    modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val tiny = maxHeight < 180.dp
        val headerHeight = minOf(56.dp, maxHeight * .28f)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth().height(headerHeight), verticalAlignment = Alignment.CenterVertically) {
                MediaFitText(stringResource(R.string.queue), color = AetherColors.TextPrimary,
                    maxFontSize = if (tiny) 13f else 16f, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).fillMaxHeight())
                MediaPageAction(stringResource(R.string.add_audio), onChooseAudio,
                    enabled = state.connected && !state.importing, modifier = Modifier.weight(1f).fillMaxHeight())
            }
            if (state.queue.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MediaFitText(stringResource(R.string.choose_audio_hint), color = AetherColors.TextSecondary,
                        maxFontSize = if (tiny) 11f else 13f, maxLines = 3,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxSize())
                }
            } else MediaPagedList(state.queue.size, "media-library", Modifier.weight(1f), compact = true) { index, rowHeight ->
                MediaTrackRow(state.queue[index], index == state.currentIndex, { onPlayIndex(index) },
                    compact = true, rowHeight = rowHeight)
            }
        }
    }
}

/** Dialog content is explicitly bounded and paged; no platform scroll container is needed. */
@Composable
fun QueueDialog(state: PlaybackState, onDismiss: () -> Unit, onChooseAudio: () -> Unit, onPlayIndex: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        DashboardSurface(Modifier.widthIn(max = 640.dp).fillMaxWidth(.88f).fillMaxHeight(.84f)) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(12.dp)) {
                val tiny = maxHeight < 220.dp
                val actionsHeight = minOf(56.dp, maxHeight * .24f)
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    MediaFitText(stringResource(R.string.queue), color = AetherColors.TextPrimary,
                        maxFontSize = if (tiny) 16f else 20f, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().height(if (tiny) 25.dp else 32.dp))
                    if (state.queue.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        MediaFitText(stringResource(R.string.queue_empty), color = AetherColors.TextSecondary,
                            maxFontSize = 13f, maxLines = 2, modifier = Modifier.fillMaxSize())
                    } else MediaPagedList(state.queue.size, "queue-dialog", Modifier.weight(1f), compact = true) { index, rowHeight ->
                        MediaTrackRow(state.queue[index], index == state.currentIndex, { onPlayIndex(index) },
                            compact = true, rowHeight = rowHeight)
                    }
                    Row(Modifier.fillMaxWidth().height(actionsHeight),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MediaPageAction(stringResource(R.string.add_audio), onChooseAudio,
                            enabled = state.connected && !state.importing, modifier = Modifier.weight(1f).fillMaxHeight())
                        MediaPageAction(stringResource(R.string.close), onDismiss,
                            modifier = Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** Every item stays reachable through pages sized from the available height. */
@Composable
internal fun MediaPagedList(itemCount: Int, contentKey: String, modifier: Modifier,
    compact: Boolean = true, fillPage: Boolean = false, row: @Composable (Int, Dp) -> Unit) {
    var anchor by rememberSaveable(contentKey) { mutableIntStateOf(0) }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val geometry = MediaListGeometry.fit(maxHeight.value, itemCount, compact, LocalDensity.current.fontScale)
        val controlsHeight = geometry.controlsHeight.dp
        val listHeight = (maxHeight - controlsHeight).coerceAtLeast(0.dp)
        val capacity = geometry.capacity
        val page = MediaPagination.window(itemCount, capacity, anchor)
        val rowHeight = if (fillPage) listHeight / (page.endExclusive - page.startIndex).coerceAtLeast(1)
            else geometry.rowHeight.dp
        LaunchedEffect(itemCount, capacity) { anchor = page.startIndex }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
                (page.startIndex until page.endExclusive).forEach { index -> row(index, rowHeight) }
                Spacer(Modifier.weight(1f, fill = true))
            }
            if (page.pageCount > 1) MediaPageControls(page, { anchor = page.previousAnchor }, { anchor = page.nextAnchor },
                Modifier.fillMaxWidth().height(controlsHeight))
        }
    }
}

@Composable
private fun MediaPageControls(page: MediaPageWindow, onPrevious: () -> Unit, onNext: () -> Unit, modifier: Modifier) {
    val previous = stringResource(R.string.media_previous_page)
    val next = stringResource(R.string.media_next_page)
    val position = stringResource(R.string.media_page_description, page.pageIndex + 1, page.pageCount)
    Row(modifier, verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight()
            .aetherRaisedClickable(onPrevious, shape = RoundedCornerShape(6.dp), enabled = page.hasPrevious, elevation = 3.dp)
            .semantics { contentDescription = previous }, contentAlignment = Alignment.Center) {
            AetherGlyph(Glyph.Previous, color = if (page.hasPrevious) AetherColors.Accent else AetherColors.Muted, size = 16.dp)
        }
        MediaFitText(stringResource(R.string.media_page_counter, page.pageIndex + 1, page.pageCount),
            color = AetherColors.TextSecondary, maxFontSize = 15f, textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = position })
        Box(Modifier.weight(1f).fillMaxHeight()
            .aetherRaisedClickable(onNext, shape = RoundedCornerShape(6.dp), enabled = page.hasNext, elevation = 3.dp)
            .semantics { contentDescription = next }, contentAlignment = Alignment.Center) {
            AetherGlyph(Glyph.Next, color = if (page.hasNext) AetherColors.Accent else AetherColors.Muted, size = 16.dp)
        }
    }
}

@Composable
internal fun MediaPageAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(modifier.aetherRaisedClickable(onClick, shape = RoundedCornerShape(10.dp), enabled = enabled,
        elevation = 4.dp).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center) {
        MediaFitText(label, color = if (enabled) AetherColors.Accent else AetherColors.Muted,
            maxFontSize = 16f, textAlign = TextAlign.Center, maxLines = 2, modifier = Modifier.fillMaxSize())
    }
}
