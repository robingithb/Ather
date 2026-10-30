package com.aether.hmi.feature.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.style.TextOverflow
import com.aether.hmi.R
import com.aether.hmi.ui.theme.AetherColors
import kotlin.math.roundToInt

/** Native measurements adapt to the available viewport; the screen is never a scaled bitmap. */
internal data class SettingsMetrics(val scale: Float = 1f) {
    val compact: Boolean get() = scale < .82f
    fun dp(value: Float): Dp = (value * scale).dp
    fun font(value: Float): TextUnit = (value * scale).coerceAtLeast(7f).sp
    val gap: Dp get() = dp(6f)
    val padding: Dp get() = dp(8f)
    val radius: Dp get() = dp(10f)
}

internal val LocalSettingsMetrics = staticCompositionLocalOf { SettingsMetrics() }

internal enum class SettingsIcon(val path: String) {
    Wifi("M2,8 Q12,0 22,8 M5,12 Q12,6 19,12 M8,16 Q12,12 16,16 M12,20 L12,20.1"),
    Bluetooth("M11,2 L19,8 L5,20 M11,2 L11,22 L19,16 L5,4"),
    Link("M9,15 L15,9 M9,7 L11,5 Q16,0 21,5 Q24,9 20,13 L17,16 M7,9 L4,12 Q0,16 4,20 Q8,24 12,20 L15,17"),
    Sun("M17,12 A5,5 0,1 1,7,12 A5,5 0,1 1,17,12 M12,1 L12,4 M12,20 L12,23 M1,12 L4,12 M20,12 L23,12 M4,4 L6,6 M18,18 L20,20 M4,20 L6,18 M18,6 L20,4"),
    Sound("M2,9 L7,9 L13,3 L13,21 L7,15 L2,15 Z M17,7 Q22,12 17,17 M20,3 Q28,12 20,21"),
    Navigation("M3,10 L22,2 L14,22 L11,13 Z"),
    Update("M7,17 L4,17 Q0,13 4,10 Q3,4 9,3 Q15,0 18,7 Q24,6 23,13 Q23,17 18,17 M12,10 L12,23 M8,19 L12,23 L16,19"),
    Storage("M3,5 C3,0 21,0 21,5 C21,10 3,10 3,5 M3,5 L3,19 C3,24 21,24 21,19 L21,5 M3,12 C3,17 21,17 21,12"),
    Health("M12,21 C8,17 2,14 2,8 C2,2 9,1 12,6 C15,1 22,2 22,8 C22,14 16,17 12,21 M2,12 L7,12 L9,8 L12,17 L15,10 L17,12 L22,12"),
    Car("M4,9 L7,3 L18,3 L21,9 M2,10 Q12,7 23,10 L23,19 L2,19 Z M5,19 L5,23 M20,19 L20,23 M5,13 L8,13 M17,13 L20,13"),
    User("M16,6 A4,4 0,1 1,8,6 A4,4 0,1 1,16,6 M3,23 L3,19 Q3,12 12,12 Q21,12 21,19 L21,23 Z"),
    Route("M4,22 L9,2 M20,22 L15,2 M12,3 L12,7 M12,11 L12,15 M12,19 L12,22"),
    Music("M9,5 L21,2 L21,17 Q21,22 16,21 Q12,18 18,16 L18,7 L12,9 L12,20 Q12,24 7,23 Q3,20 9,19 Z"),
}

@Composable
internal fun SettingsGlyph(icon: SettingsIcon, modifier: Modifier = Modifier, color: Color = AetherColors.Accent) {
    val path = remember(icon) { PathParser().parsePathString(icon.path).toPath() }
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(1.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

@Composable
internal fun SettingsPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val m = LocalSettingsMetrics.current
    Column(modifier.clip(RoundedCornerShape(m.radius))
        .background(Brush.linearGradient(listOf(AetherColors.PanelRaised, AetherColors.PanelDeep)))
        .border(.7.dp, AetherColors.Border, RoundedCornerShape(m.radius)).padding(m.padding),
        verticalArrangement = Arrangement.spacedBy(m.gap), content = content)
}

@Composable
internal fun SettingsPanelHeader(title: String, detail: String, icon: SettingsIcon, modifier: Modifier = Modifier) {
    val m = LocalSettingsMetrics.current
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(m.dp(7f)), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(m.dp(if (m.compact) 27f else 32f)).clip(CircleShape).background(AetherColors.Accent.copy(alpha = .09f)), contentAlignment = Alignment.Center) {
            SettingsGlyph(icon, Modifier.size(m.dp(21f)), color = AetherColors.Accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(m.dp(2f))) {
            Text(title, color = AetherColors.TextPrimary, fontWeight = FontWeight.SemiBold,
                fontSize = m.font(14f), lineHeight = m.font(16f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!m.compact && detail.isNotEmpty()) Text(detail, color = AetherColors.TextSecondary,
                fontSize = m.font(10f), lineHeight = m.font(12f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun SettingsHint(text: String) {
    val m = LocalSettingsMetrics.current
    Text(text, color = AetherColors.Muted, fontSize = m.font(9f), lineHeight = m.font(11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** Compact, accessible slider without Material's fixed 48dp minimum visual footprint. */
@Composable
internal fun CompactSettingsSlider(label: String, value: Float, onChange: (Float) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true) {
    val m = LocalSettingsMetrics.current
    val currentChange by rememberUpdatedState(onChange)
    val fraction = value.coerceIn(0f, 1f)
    val insetPx = with(LocalDensity.current) { m.dp(5f).toPx() }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(m.dp(6f))) {
        Text(label, Modifier.weight(.9f), color = AetherColors.TextSecondary,
            fontSize = m.font(11f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        val accent = if (enabled) AetherColors.Accent else AetherColors.Muted
        val track = AetherColors.RailBorder
        Box(Modifier.weight(1.1f).fillMaxHeight().semantics {
            contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
            if (enabled) setProgress { currentChange(it.coerceIn(0f, 1f)); true }
            else disabled()
        }.pointerInput(enabled, insetPx) {
            if (enabled) detectTapGestures { currentChange(settingsSliderFraction(it.x, size.width.toFloat(), insetPx)) }
        }.pointerInput(enabled, insetPx) {
            if (enabled) detectHorizontalDragGestures { change, _ ->
                change.consume()
                currentChange(settingsSliderFraction(change.position.x, size.width.toFloat(), insetPx))
            }
        }.focusable(enabled)) {
            Canvas(Modifier.fillMaxSize()) {
                val inset = m.dp(5f).toPx()
                val end = (size.width - inset).coerceAtLeast(inset)
                val x = inset + (end - inset) * fraction
                drawLine(track, Offset(inset, size.height / 2), Offset(end, size.height / 2), m.dp(5f).toPx(), StrokeCap.Round)
                drawLine(accent, Offset(inset, size.height / 2), Offset(x, size.height / 2), m.dp(5f).toPx(), StrokeCap.Round)
                drawCircle(accent.copy(alpha = .15f), m.dp(8f).toPx(), Offset(x, size.height / 2))
                drawCircle(accent, m.dp(5f).toPx(), Offset(x, size.height / 2))
            }
        }
        Text(stringResource(R.string.settings_percent, (fraction * 100).roundToInt()),
            Modifier.width(m.dp(31f)), color = AetherColors.TextPrimary, fontSize = m.font(11f), maxLines = 1)
    }
}

/** Pointer and drawing coordinates share the same inset track, including its endpoints. */
internal fun settingsSliderFraction(x: Float, width: Float, inset: Float): Float {
    val length = width - inset * 2f
    return if (length > 0f) ((x - inset) / length).coerceIn(0f, 1f) else 0f
}

@Composable
internal fun CompactSettingsSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier) {
    val m = LocalSettingsMetrics.current
    Row(modifier.fillMaxWidth().toggleable(checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(m.dp(5f))) {
        Text(label, Modifier.weight(1f), color = AetherColors.TextPrimary, fontSize = m.font(11f),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.width(m.dp(31f)).height(m.dp(17f)).clip(CircleShape)
            .background(if (checked) AetherColors.Accent else AetherColors.RailBorder).padding(m.dp(2f)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(m.dp(13f)).clip(CircleShape).background(if (checked) AetherColors.OnAccent else AetherColors.TextSecondary))
        }
    }
}
