package com.aether.hmi.feature.vehicle

import com.aether.hmi.domain.TurnSignal
import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehiclePart

/** Visibility of authored LED and lens meshes, independent of Filament and frame timing. */
object VehicleLampPolicy {
    private val mixedRearOverlays = setOf("REAR_BRAKE_ON", "REAR_BRAKE_GLOW_ON", "REAR_TAIL_ON")
    private val leftSignalMeshes = setOf(
        "FRONT_INDICATOR_L_ON", "FENDER_INDICATOR_L_ON", "MIRROR_INDICATOR_L_ON",
    ) + (1..6).map { "REAR_TURN_L_ON_$it" }
    private val rightSignalMeshes = setOf(
        "FRONT_INDICATOR_R_ON", "FENDER_INDICATOR_R_ON", "MIRROR_INDICATOR_R_ON",
    ) + (1..6).map { "REAR_TURN_R_ON_$it" }
    private val nativeLampNodes = setOf("HEADLAMP_L_LIGHT", "HEADLAMP_R_LIGHT", "REAR_BRAKE_LIGHT")

    /** Actual amber LED geometry, excluding the authored billboard glows and unpowered lenses. */
    fun isIndicatorNode(nodeName: String): Boolean = nodeName in leftSignalMeshes || nodeName in rightSignalMeshes

    /** gltfio uses emissive alpha 0, bypassing camera exposure; these gains need no EV15 compensation. */
    fun emissionGain(nodeName: String): Float = when {
        isIndicatorNode(nodeName) -> 24_000f
        // Match the upper stop's per-area emissive luminance on the small white projectors.
        // Keep the much wider DRL separate so only the headlamp apertures gain a halo.
        nodeName == "HEADLAMP_L_ON" || nodeName == "HEADLAMP_R_ON" -> 4_096f
        // Twenty percent above the previously verified reverse gain of 4.
        nodeName == "REAR_REVERSE_ON_L" || nodeName == "REAR_REVERSE_ON_R" -> 4.8f
        nodeName == "DRL_BAR" -> 2f
        nodeName == "REAR_TAIL_ON" || nodeName == "REAR_BRAKE_ON" || nodeName == "HIGH_STOP_ON" -> 18_000f
        nodeName.startsWith("AMBIENT_STRIP_ON_") -> 1_200f
        nodeName == "CHARGE_PORT_RING_ON_BLUE" || nodeName == "CHARGE_PORT_RING_ON_WHITE" ||
            nodeName == "CHARGE_PORT_RING_ON_GREEN" -> 12_000f
        else -> 1f
    }

    fun visible(
        nodeName: String,
        controls: VehicleControlsState,
        charging: Boolean,
        blinkOn: Boolean,
        trunkOpen: Boolean,
    ): Boolean {
        // These authored glow billboards look like solid cards from other orbit angles.
        if (nodeName.contains("_GLOW_ON")) return false
        // The original rear overlays span fixed body and tailgate geometry.
        if (trunkOpen && nodeName in mixedRearOverlays) return false
        // The battery view preserves the real hazard LEDs while other luminous exterior
        // overlays stay hidden. The connector ring remains blue during charging.
        val lightingOverlay = nodeName == "DRL_BAR" || nodeName.contains("_ON") ||
            nodeName.contains("_OFF") || nodeName in nativeLampNodes
        if (charging && lightingOverlay && !isIndicatorNode(nodeName)) return nodeName == "CHARGE_PORT_RING_ON_BLUE"

        val headlights = controls.available && controls.headlightsOn
        val brake = controls.available && controls.brakeLightsOn
        val signal = controls.effectiveTurnSignal
        val left = blinkOn && (signal == TurnSignal.Left || signal == TurnSignal.Hazard)
        val right = blinkOn && (signal == TurnSignal.Right || signal == TurnSignal.Hazard)
        val portOpen = controls.available && VehiclePart.ChargePort in controls.openParts
        return when {
            nodeName in leftSignalMeshes -> left
            nodeName in rightSignalMeshes -> right
            nodeName == "FRONT_INDICATOR_L" || nodeName == "MIRROR_INDICATOR_L_OFF" -> !left
            nodeName == "FRONT_INDICATOR_R" || nodeName == "MIRROR_INDICATOR_R_OFF" -> !right
            nodeName == "REAR_TURN_OFF_L" || nodeName == "REAR_TURN_INNER_OFF_L" -> !left
            nodeName == "REAR_TURN_OFF_R" || nodeName == "REAR_TURN_INNER_OFF_R" -> !right
            nodeName == "DRL_BAR" || nodeName == "HEADLAMP_L_ON" || nodeName == "HEADLAMP_R_ON" ||
                nodeName == "HEADLAMP_L_LIGHT" || nodeName == "HEADLAMP_R_LIGHT" -> headlights
            nodeName == "HEADLAMP_L_OFF" || nodeName == "HEADLAMP_R_OFF" -> !headlights
            nodeName == "REAR_TAIL_ON" -> headlights
            nodeName == "REAR_BRAKE_ON" || nodeName == "HIGH_STOP_ON" || nodeName == "REAR_BRAKE_LIGHT" -> brake
            nodeName == "HIGH_STOP_OFF" -> !brake
            nodeName == "REAR_REVERSE_ON_L" || nodeName == "REAR_REVERSE_ON_R" -> controls.reverseLightsOn
            nodeName == "REAR_REVERSE_OFF_L" || nodeName == "REAR_REVERSE_OFF_R" -> !controls.reverseLightsOn
            nodeName.startsWith("AMBIENT_STRIP_ON_") -> controls.available && controls.ambientLightsOn
            nodeName == "CHARGE_PORT_RING_ON_BLUE" -> charging
            nodeName == "CHARGE_PORT_RING_ON_WHITE" -> portOpen && !charging
            nodeName == "CHARGE_PORT_RING_ON_GREEN" -> false
            nodeName == "CHARGE_PORT_RING_OFF" -> !portOpen && !charging
            lightingOverlay -> false // Unrecognised optional effect nodes stay off.
            else -> true
        }
    }

    /** A 1.2-second indicator cycle, with no platform clock or persistent animation state. */
    fun blinkOn(elapsedSeconds: Double): Boolean =
        elapsedSeconds.isFinite() && elapsedSeconds.coerceAtLeast(0.0) % 1.2 < 0.6
}
