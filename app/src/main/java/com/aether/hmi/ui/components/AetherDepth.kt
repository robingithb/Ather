package com.aether.hmi.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aether.hmi.ui.theme.LocalAetherPalette
import com.aether.hmi.ui.theme.aetherDepthColors

/** Native shadow, shaded face and fine bevel; adds no padding or layout size. */
@Composable
fun Modifier.aetherRaised(
    shape: Shape = RoundedCornerShape(10.dp),
    selected: Boolean = false,
    enabled: Boolean = true,
    elevation: Dp = 3.dp,
    background: Color? = null,
    interactionSource: MutableInteractionSource? = null,
): Modifier {
    val palette = LocalAetherPalette.current
    val colors = remember(palette, selected, enabled, background) { aetherDepthColors(palette, selected, enabled, background) }
    val pressed = interactionSource?.collectIsPressedAsState()?.value == true && enabled
    val sink by animateFloatAsState(if (pressed) 1f else 0f, tween(90), label = "Control depression")
    return graphicsLayer {
        this.shape = shape
        shadowElevation = if (!enabled) 0f else (elevation.toPx() * (1f - sink * .78f)).coerceAtLeast(0f)
        ambientShadowColor = colors.shadow
        spotShadowColor = colors.shadow
        translationY = .8.dp.toPx() * sink
    }.clip(shape)
        .background(Brush.verticalGradient(listOf(colors.top, colors.bottom)))
        // Border is drawn over content, including image-backed cards.
        .border(1.dp, Brush.verticalGradient(listOf(colors.highlight, palette.Border.copy(alpha = .5f), colors.lowerEdge)), shape)
}

@Composable
fun Modifier.aetherRaisedClickable(
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(10.dp),
    selected: Boolean = false,
    enabled: Boolean = true,
    elevation: Dp = 5.dp,
    background: Color? = null,
    role: Role = Role.Button,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return aetherRaised(shape, selected, enabled, elevation, background, interaction)
        .clickable(interactionSource = interaction, indication = LocalIndication.current,
            enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun Modifier.aetherRaisedToggleable(
    value: Boolean,
    onValueChange: (Boolean) -> Unit,
    shape: Shape = RoundedCornerShape(10.dp),
    enabled: Boolean = true,
    elevation: Dp = 5.dp,
    background: Color? = null,
    role: Role = Role.Checkbox,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return aetherRaised(shape, value, enabled, elevation, background, interaction)
        .toggleable(value, interactionSource = interaction, indication = LocalIndication.current,
            enabled = enabled, role = role, onValueChange = onValueChange)
}

@Composable
fun Modifier.aetherRaisedSelectable(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape = RoundedCornerShape(10.dp),
    enabled: Boolean = true,
    elevation: Dp = 5.dp,
    background: Color? = null,
    role: Role = Role.RadioButton,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return aetherRaised(shape, selected, enabled, elevation, background, interaction)
        .selectable(selected, interactionSource = interaction, indication = LocalIndication.current,
            enabled = enabled, role = role, onClick = onClick)
}
