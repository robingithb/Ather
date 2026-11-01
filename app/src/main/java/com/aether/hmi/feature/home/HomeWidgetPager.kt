package com.aether.hmi.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.aether.hmi.R
import com.aether.hmi.domain.HomeWidget
import com.aether.hmi.domain.HomeWidgets
import com.aether.hmi.ui.components.PageText
import com.aether.hmi.ui.theme.AetherColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

private data class WidgetDrag(
    val widget: HomeWidget,
    val position: Offset,
    val grabOffset: Offset,
    val size: Size,
)

/** The gesture owner stays outside the pager so a held widget survives a page turn. */
@Composable
internal fun HomeWidgetPager(
    widgets: List<HomeWidget>,
    capacity: Int,
    anchor: Int,
    onAnchorChange: (Int) -> Unit,
    onWidgetsChange: (List<HomeWidget>) -> Unit,
    modifier: Modifier,
    onAdd: () -> Unit,
    content: @Composable (HomeWidget, Modifier) -> Unit,
) {
    val count = HomeWidgetLayout.page(widgets.size, capacity, anchor).count
    val pager = rememberPagerState(initialPage = (anchor / capacity).coerceIn(0, count - 1)) { count }
    val latestWidgets by rememberUpdatedState(widgets)
    val latestOnChange by rememberUpdatedState(onWidgetsChange)
    val latestOnAnchor by rememberUpdatedState(onAnchorChange)
    val bounds = remember { mutableMapOf<Long, Rect>() }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var drag by remember { mutableStateOf<WidgetDrag?>(null) }
    val dragScale by animateFloatAsState(if (drag == null) 1f else .72f, tween(160), label = "Widget lift size")
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val edgeWidth = with(density) { 48.dp.toPx() }
    val direction = drag?.takeIf { it.position.y in 0f..viewport.height }
        ?.let { HomeWidgetDragPolicy.edgeDirection(it.position.x, viewport.width, edgeWidth) } ?: 0

    LaunchedEffect(anchor, count, drag == null) {
        val target = (anchor / capacity).coerceIn(0, count - 1)
        if (drag == null && (target != pager.currentPage || pager.currentPageOffsetFraction != 0f)) {
            pager.animateScrollToPage(target)
        }
    }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { latestOnAnchor(it * capacity) }
    }
    LaunchedEffect(drag?.widget?.id, direction, count) {
        if (direction == 0) return@LaunchedEffect
        while (isActive && drag != null) {
            // Deliberate edge dwell prevents accidental turns while crossing a card.
            delay(650)
            if (drag == null) break
            val target = HomeWidgetDragPolicy.targetPage(pager.currentPage, count, direction)
            if (target == pager.currentPage) break
            pager.animateScrollToPage(target)
        }
    }
    LaunchedEffect(widgets) {
        if (drag?.widget?.id?.let { id -> widgets.none { it.id == id } } == true) drag = null
    }
    BackHandler(drag != null) { drag = null }

    fun dropIndex(current: WidgetDrag): Int {
        val page = pager.targetPage.coerceIn(0, count - 1)
        val start = page * capacity
        val candidates = latestWidgets.drop(start).take(capacity)
        val pointer = current.position + viewport.topLeft
        // Nearest cell also handles the narrow gaps between cards.
        val slot = candidates.indices.minByOrNull { index ->
            bounds[candidates[index].id]?.center?.let { (it - pointer).getDistanceSquared() } ?: Float.MAX_VALUE
        } ?: 0
        return HomeWidgetDragPolicy.dropIndex(latestWidgets.size, capacity, page, slot)
    }
    val targetIndex = drag?.let(::dropIndex)
    val earlier = stringResource(R.string.home_widget_move_back)
    val later = stringResource(R.string.home_widget_move_forward)
    val previousPage = stringResource(R.string.home_widget_move_previous_page)
    val nextPage = stringResource(R.string.home_widget_move_next_page)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()
            .onGloballyPositioned { viewport = it.boundsInRoot() }
            .pointerInput(capacity, pager, count, edgeWidth) {
                try {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val heldPointer = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val point = heldPointer.position
                        val page = pager.currentPage
                        val hit = latestWidgets.drop(page * capacity).take(capacity).firstOrNull { widget ->
                            bounds[widget.id]?.contains(point + viewport.topLeft) == true
                        } ?: return@awaitEachGesture
                        val rect = bounds.getValue(hit.id)
                        drag = WidgetDrag(hit, point, point + viewport.topLeft - rect.topLeft, rect.size)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            while (true) {
                                // Consume before child buttons see release. Ordinary taps and
                                // pager swipes are untouched before the long-press timeout.
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val pointer = event.changes.firstOrNull { it.id == heldPointer.id }
                                event.changes.forEach { it.consume() }
                                if (pointer == null || event.changes.any { it.id != heldPointer.id && it.pressed }) break
                                drag = drag?.copy(position = pointer.position)
                                if (pointer.pressed) continue
                                drag?.let { held ->
                                    if (held.position.y in 0f..viewport.height &&
                                        held.position.x in -edgeWidth..(viewport.width + edgeWidth)) {
                                        val source = latestWidgets.indexOfFirst { it.id == held.widget.id }
                                        val target = dropIndex(held)
                                        if (source >= 0 && source != target) {
                                            latestOnChange(HomeWidgets.move(latestWidgets, held.widget.id, target - source))
                                            latestOnAnchor(target)
                                        }
                                    }
                                }
                                break
                            }
                        } finally {
                            drag = null
                        }
                    }
                } finally {
                    drag = null
                }
            }) {
            HorizontalPager(pager, Modifier.fillMaxSize(), pageSpacing = 8.dp, userScrollEnabled = drag == null) { page ->
                val start = page * capacity
                val visible = widgets.drop(start).take(capacity)
                HomeWidgetGrid(visible, Modifier.fillMaxSize(),
                    empty = { HomeAddWidgetSlot(onAdd, it) }) { widget, tile ->
                    key(widget.id) {
                        DisposableEffect(widget.id) { onDispose { bounds.remove(widget.id) } }
                        val index = widgets.indexOfFirst { it.id == widget.id }
                        fun moveTo(target: Int): Boolean {
                            val current = latestWidgets.indexOfFirst { it.id == widget.id }
                            if (current < 0 || target == current) return false
                            latestOnChange(HomeWidgets.move(latestWidgets, widget.id, target - current))
                            latestOnAnchor(target)
                            return true
                        }
                        val actions = buildList {
                            if (index > 0) add(CustomAccessibilityAction(earlier) { moveTo(index - 1) })
                            if (index < widgets.lastIndex) add(CustomAccessibilityAction(later) { moveTo(index + 1) })
                            if (page > 0) add(CustomAccessibilityAction(previousPage) {
                                moveTo(HomeWidgetDragPolicy.dropIndex(latestWidgets.size, capacity, page - 1, index % capacity))
                            })
                            if (page + 1 < count) add(CustomAccessibilityAction(nextPage) {
                                moveTo(HomeWidgetDragPolicy.dropIndex(latestWidgets.size, capacity, page + 1, index % capacity))
                            })
                        }
                        Box(tile.onGloballyPositioned { bounds[widget.id] = it.boundsInRoot() }
                            .graphicsLayer { alpha = if (drag?.widget?.id == widget.id) .18f else 1f }
                            .semantics { customActions = actions }) {
                            content(widget, Modifier.fillMaxSize())
                            if (drag != null && targetIndex == index) Box(Modifier.matchParentSize()
                                .border(2.dp, AetherColors.Accent, RoundedCornerShape(16.dp)))
                        }
                    }
                }
            }
            // The lifted card is independent of the page being disposed/recreated underneath it.
            drag?.let { held ->
                Box(Modifier.offset { IntOffset((held.position.x - held.grabOffset.x).roundToInt(),
                    (held.position.y - held.grabOffset.y).roundToInt()) }
                    .size(with(density) { held.size.width.toDp() }, with(density) { held.size.height.toDp() })
                    .graphicsLayer {
                        scaleX = dragScale; scaleY = dragScale
                        // Scale around the grabbed point so the miniature stays under the finger.
                        transformOrigin = TransformOrigin(
                            (held.grabOffset.x / held.size.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                            (held.grabOffset.y / held.size.height.coerceAtLeast(1f)).coerceIn(0f, 1f))
                        shadowElevation = 8.dp.toPx()
                    }
                    .border(2.dp, AetherColors.Accent, RoundedCornerShape(16.dp))) {
                    content(held.widget, Modifier.fillMaxSize())
                }
                if (direction != 0 && HomeWidgetDragPolicy.targetPage(pager.currentPage, count, direction) != pager.currentPage) {
                    Box(Modifier.align(if (direction < 0) Alignment.CenterStart else Alignment.CenterEnd)
                        .width(6.dp).fillMaxHeight(.6f).background(AetherColors.Accent, CircleShape))
                }
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(max = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            PageText(stringResource(if (drag != null) R.string.home_widget_drag_hint else if (count > 1)
                R.string.home_widget_swipe_hint else R.string.home_widget_hold_hint),
                Modifier.weight(1f).height(20.dp), maxFontSize = 12f, color = AetherColors.TextSecondary)
            if (count > 1) {
                repeat(count) { index -> Box(Modifier.size(if (index == pager.currentPage) 7.dp else 5.dp)
                    .background(if (index == pager.currentPage) AetherColors.Accent else AetherColors.Muted, CircleShape)) }
                PageText(stringResource(R.string.home_widget_page, pager.currentPage + 1, count),
                    Modifier.width(42.dp).height(20.dp), maxFontSize = 12f, color = AetherColors.TextSecondary)
            }
        }
    }
}
