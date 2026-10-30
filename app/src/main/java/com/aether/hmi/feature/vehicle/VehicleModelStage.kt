package com.aether.hmi.feature.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aether.hmi.R
import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehiclePart
import com.aether.hmi.feature.vehicle.renderer.FilamentVehicleRenderer
import com.aether.hmi.feature.vehicle.renderer.VehicleModelStatus
import com.aether.hmi.ui.theme.AetherColors
import com.aether.hmi.ui.theme.LocalAetherPalette
import androidx.compose.ui.graphics.toArgb

/** Native scene ownership belongs to the Activity; navigation only attaches a viewport. */
@Composable
fun VehicleModelStage(controls: VehicleControlsState, onPartTap: (VehiclePart) -> Unit, modifier: Modifier = Modifier,
    focus: VehicleSceneFocus = VehicleSceneFocus.Overview, charging: Boolean = false, batteryPercent: Int? = null,
    contentLeftFraction: Float = 0f, contentWidthFraction: Float = 1f) {
    if (LocalInspectionMode.current) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.vehicle_model_preview), color = AetherColors.TextSecondary, fontSize = 11.sp)
        }
        return
    }
    val context = LocalContext.current
    val activity = remember(context) { context.vehicleSceneActivity() }
    val sceneModel: VehicleSceneViewModel = viewModel(viewModelStoreOwner = activity)
    val status by sceneModel.status.collectAsStateWithLifecycle()
    val generation by sceneModel.generation.collectAsStateWithLifecycle()
    key(generation) { VehicleModelSurface(controls, onPartTap, sceneModel, status, modifier, focus, charging, batteryPercent, contentLeftFraction, contentWidthFraction) }
}

@SuppressLint("ClickableViewAccessibility") // Named accessibility actions accompany picking and camera gestures.
@Composable
private fun VehicleModelSurface(
    controls: VehicleControlsState,
    onPartTap: (VehiclePart) -> Unit,
    sceneModel: VehicleSceneViewModel,
    status: VehicleModelStatus,
    modifier: Modifier,
    focus: VehicleSceneFocus,
    charging: Boolean,
    batteryPercent: Int?,
    contentLeftFraction: Float,
    contentWidthFraction: Float,
) {
    val colors = LocalAetherPalette.current
    // Reserve only the gesture hint now that direct camera buttons have been removed.
    val stageBottomInset = with(LocalDensity.current) { 20.dp.toPx() }
    val owner = LocalLifecycleOwner.current
    val latestOnPartTap by rememberUpdatedState(onPartTap)
    val holder = remember { SceneHolder() }
    var reducedMotion by remember { mutableStateOf(false) }
    val description = stringResource(R.string.vehicle_model_description)
    val partActions = VehiclePart.entries.map { part ->
        val label = stringResource(when (part) {
            VehiclePart.FrontLeftDoor -> R.string.vehicle_front_left
            VehiclePart.FrontRightDoor -> R.string.vehicle_front_right
            VehiclePart.RearLeftDoor -> R.string.vehicle_rear_left
            VehiclePart.RearRightDoor -> R.string.vehicle_rear_right
            VehiclePart.Trunk -> R.string.vehicle_trunk
            VehiclePart.Bonnet -> R.string.vehicle_bonnet
            VehiclePart.Sunroof -> R.string.vehicle_sunroof
            VehiclePart.ChargePort -> R.string.vehicle_charge_port
        })
        CustomAccessibilityAction(stringResource(if (part in controls.openParts) R.string.vehicle_close_part else R.string.vehicle_open_part, label)) {
            if (controls.available && !(part == VehiclePart.ChargePort && charging)) { latestOnPartTap(part); true } else false
        }
    }
    val cameraActions = listOf(
        CustomAccessibilityAction(stringResource(R.string.vehicle_rotate_left)) {
            holder.renderer?.let { it.rotate(-15.0); true } ?: false
        },
        CustomAccessibilityAction(stringResource(R.string.vehicle_rotate_right)) {
            holder.renderer?.let { it.rotate(15.0); true } ?: false
        },
        CustomAccessibilityAction(stringResource(R.string.vehicle_zoom_in)) {
            holder.renderer?.let { it.changeZoom(.93); true } ?: false
        },
        CustomAccessibilityAction(stringResource(R.string.vehicle_zoom_out)) {
            holder.renderer?.let { it.changeZoom(1.07); true } ?: false
        },
        CustomAccessibilityAction(stringResource(R.string.vehicle_reset_camera)) {
            holder.renderer?.let { it.resetCamera(); true } ?: false
        },
    )
    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]
        snapshotFlow { (durationScale?.scaleFactor ?: 1f) == 0f }.collect { reducedMotion = it }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> holder.renderer?.setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            holder.detach()
        }
    }
    BoxWithConstraints(modifier) {
        val contentOffset = maxWidth*contentLeftFraction
        val contentWidth = maxWidth*contentWidthFraction
        AndroidView(factory = { context ->
            TextureView(context).apply {
                isOpaque = false
                contentDescription = description
                val attached = this
                val renderer = sceneModel.obtainScene()
                holder.renderer = renderer
                holder.texture = attached
                renderer.attach(attached) { part ->
                    if (holder.renderer === renderer && holder.texture === attached) latestOnPartTap(part)
                }
                renderer.updateStageViewport(contentLeftFraction, contentWidthFraction, stageBottomInset)
                renderer.updateControls(controls)
                renderer.updatePresentation(focus, charging, batteryPercent)
                renderer.updateTheme(colors.Accent.toArgb(), colors.Background.toArgb(), colors.isLight)
                renderer.setReducedMotion(reducedMotion)
                renderer.setActive(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            }
        }, update = { attached ->
            if (holder.texture === attached) {
                holder.renderer?.updateStageViewport(contentLeftFraction, contentWidthFraction, stageBottomInset)
                holder.renderer?.updateControls(controls)
                holder.renderer?.updatePresentation(focus, charging, batteryPercent)
                holder.renderer?.updateTheme(colors.Accent.toArgb(), colors.Background.toArgb(), colors.isLight)
                holder.renderer?.setReducedMotion(reducedMotion)
            }
        }, onRelease = { attached ->
            holder.detach(attached)
        }, modifier = Modifier.fillMaxSize().semantics { contentDescription = description; customActions = partActions + cameraActions })
        // Fade the shared scene into the existing shell rather than showing a rectangular image card.
        Canvas(Modifier.fillMaxSize()) {
            val base = colors.Background
            drawRect(Brush.horizontalGradient(0f to base, .15f to base.copy(alpha=.6f), .28f to Color.Transparent,
                .70f to Color.Transparent, 1f to base.copy(alpha=.82f)))
            drawRect(Brush.verticalGradient(0f to base.copy(alpha=.75f), .15f to Color.Transparent,
                .78f to Color.Transparent, 1f to base))
        }
        Box(Modifier.offset(x=contentOffset).width(contentWidth).fillMaxSize()) {
        when (val current = status) {
            is VehicleModelStatus.Loading -> Column(Modifier.align(Alignment.Center).background(colors.Background.copy(alpha = .85f), RoundedCornerShape(12.dp)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (current.percent == null) CircularProgressIndicator(Modifier.size(32.dp), color = AetherColors.Accent, strokeWidth = 3.dp)
                else CircularProgressIndicator(progress = { current.percent / 100f }, modifier = Modifier.size(32.dp), color = AetherColors.Accent,
                    trackColor = AetherColors.Border, strokeWidth = 3.dp)
                Text(stringResource(R.string.vehicle_model_loading), color = AetherColors.TextPrimary, fontSize = 11.sp)
            }
            VehicleModelStatus.Failed -> Column(Modifier.align(Alignment.Center).background(colors.Background.copy(alpha = .93f), RoundedCornerShape(10.dp)).padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.vehicle_model_failed), color = AetherColors.Warning, fontSize = 11.sp)
                TextButton(onClick = sceneModel::retry) { Text(stringResource(R.string.vehicle_model_retry)) }
            }
            VehicleModelStatus.Ready -> Unit
        }
        }
    }
}

private class SceneHolder {
    var renderer: FilamentVehicleRenderer? = null
    var texture: TextureView? = null

    fun detach(attached: TextureView? = texture) {
        if (attached == null) return
        renderer?.detach(attached)
        if (texture === attached) {
            texture = null
            renderer = null
        }
    }
}

private fun Context.vehicleSceneActivity(): ComponentActivity {
    var candidate = this
    while (candidate is ContextWrapper) {
        if (candidate is ComponentActivity) return candidate
        candidate = candidate.baseContext
    }
    error("Vehicle scene requires a ComponentActivity owner")
}
