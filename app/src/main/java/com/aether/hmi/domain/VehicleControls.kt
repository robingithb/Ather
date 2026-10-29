package com.aether.hmi.domain

enum class VehiclePart { FrontLeftDoor, FrontRightDoor, RearLeftDoor, RearRightDoor, Trunk, Bonnet, Sunroof, ChargePort }
enum class HeadlightMode { Off, Auto, On }
enum class VehicleWindow { FrontLeft, FrontRight, RearLeft, RearRight }
enum class TurnSignal { Off, Left, Right, Hazard }
enum class VehicleGear { Park, Drive, Reverse }

/** Command state supplied by the vehicle adapter; the mock implementation simulates actuation. */
data class VehicleControlsState(
    val available: Boolean = false,
    val locked: Boolean = true,
    val openParts: Set<VehiclePart> = emptySet(),
    val headlightMode: HeadlightMode = HeadlightMode.Auto,
    val headlightsOn: Boolean = false,
    val parkingBrakeEngaged: Boolean = true,
    val windowOpenPercent: Map<VehicleWindow, Int> = VehicleWindow.entries.associateWith { 0 },
    val turnSignal: TurnSignal = TurnSignal.Off,
    val gear: VehicleGear = VehicleGear.Park,
    val brakeLightsOn: Boolean = false,
    val ambientLightsOn: Boolean = false,
) {
    val reverseLightsOn: Boolean get() = available && gear == VehicleGear.Reverse

    /** Park hazards are a demo presentation rule; retain the manual choice for other gears. */
    val effectiveTurnSignal: TurnSignal get() = when {
        !available -> TurnSignal.Off
        gear == VehicleGear.Park -> TurnSignal.Hazard
        else -> turnSignal
    }

    fun toggledPart(part: VehiclePart): VehicleControlsState {
        if (!available) return this
        val opening = part !in openParts
        return copy(
            locked = if (opening && part.isLockablePanel()) false else locked,
            openParts = if (opening) openParts + part else openParts - part,
        )
    }

    fun withLocked(locked: Boolean): VehicleControlsState = if (!available) this else copy(
        locked = locked,
        openParts = if (locked) openParts.filterNot { it.isLockablePanel() }.toSet() else openParts,
    )

    fun withHeadlightMode(mode: HeadlightMode, demoAutoHeadlightsOn: Boolean): VehicleControlsState =
        if (!available) this else copy(
            headlightMode = mode,
            headlightsOn = when (mode) {
                HeadlightMode.Off -> false
                HeadlightMode.Auto -> demoAutoHeadlightsOn
                HeadlightMode.On -> true
            },
        )

    fun withWindowPosition(window: VehicleWindow, percent: Int): VehicleControlsState =
        if (!available) this else copy(windowOpenPercent = windowOpenPercent + (window to percent.coerceIn(0, 100)))

    fun toggledParkingBrake(): VehicleControlsState =
        if (!available) this else copy(parkingBrakeEngaged = !parkingBrakeEngaged)

    fun withTurnSignal(signal: TurnSignal): VehicleControlsState =
        if (!available || gear == VehicleGear.Park) this else copy(turnSignal = signal)

    fun withGear(gear: VehicleGear): VehicleControlsState =
        if (!available) this else copy(gear = gear)

    fun withBrakeLights(enabled: Boolean): VehicleControlsState =
        if (!available) this else copy(brakeLightsOn = enabled)

    fun withAmbientLights(enabled: Boolean): VehicleControlsState =
        if (!available) this else copy(ambientLightsOn = enabled)
}

private fun VehiclePart.isLockablePanel(): Boolean = this != VehiclePart.Sunroof && this != VehiclePart.ChargePort

/** Park-only mock charging keeps its connector flap and shared Home telemetry consistent. */
fun VehicleSnapshot.withCharging(enabled: Boolean): VehicleSnapshot {
    if (!controls.available || vehicle.charging == null || (enabled && controls.gear != VehicleGear.Park)) return this
    val parts = if (enabled) controls.openParts + VehiclePart.ChargePort else controls.openParts - VehiclePart.ChargePort
    return copy(vehicle = vehicle.copy(charging = enabled), controls = controls.copy(openParts = parts))
}

fun VehicleSnapshot.withVehicleGear(gear: VehicleGear): VehicleSnapshot {
    if (!controls.available || (vehicle.charging == true && gear != VehicleGear.Park)) return this
    return copy(
        controls = controls.withGear(gear),
        vehicle = vehicle.copy(parked = vehicle.parked?.let { gear == VehicleGear.Park }),
    )
}

fun VehicleSnapshot.withToggledVehiclePart(part: VehiclePart): VehicleSnapshot {
    if (!controls.available || (part == VehiclePart.ChargePort && vehicle.charging == true && part in controls.openParts)) return this
    return copy(controls = controls.toggledPart(part))
}
