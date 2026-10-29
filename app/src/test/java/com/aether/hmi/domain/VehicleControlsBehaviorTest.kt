package com.aether.hmi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleControlsBehaviorTest {
    private val initial = VehicleControlsState(available = true)

    @Test fun `opening a part affects only that part and only lockable panels unlock the vehicle`() {
        VehiclePart.entries.forEach { part ->
            val opened = initial.toggledPart(part)
            val staysLocked = part == VehiclePart.Sunroof || part == VehiclePart.ChargePort
            assertEquals(staysLocked, opened.locked)
            assertEquals(setOf(part), opened.openParts)
            val closed = opened.toggledPart(part)
            assertTrue(closed.openParts.isEmpty())
            assertEquals(staysLocked, closed.locked)
        }
    }

    @Test fun `locking closes doors trunk and bonnet while preserving sunroof and charge port`() {
        val opened = VehiclePart.entries.fold(initial) { state, part -> state.toggledPart(part) }
        assertEquals(VehiclePart.entries.toSet(), opened.openParts)
        val trunkClosed = opened.toggledPart(VehiclePart.Trunk)
        assertEquals(VehiclePart.entries.toSet() - VehiclePart.Trunk, trunkClosed.openParts)
        val locked = trunkClosed.withLocked(true)
        assertTrue(locked.locked)
        assertEquals(setOf(VehiclePart.Sunroof, VehiclePart.ChargePort), locked.openParts)
        val unlocked = locked.withLocked(false)
        assertFalse(unlocked.locked)
        assertEquals(locked.openParts, unlocked.openParts)
    }

    @Test fun `locking leaves independently controlled windows and parking brake unchanged`() {
        val changed = initial.withWindowPosition(VehicleWindow.RearRight, 75).toggledParkingBrake()
            .toggledPart(VehiclePart.FrontLeftDoor)
        val locked = changed.withLocked(true)
        assertEquals(75, locked.windowOpenPercent[VehicleWindow.RearRight])
        assertEquals(changed.parkingBrakeEngaged, locked.parkingBrakeEngaged)
        assertTrue(locked.openParts.isEmpty())
    }

    @Test fun `headlight Auto follows configured demo lighting independently of previous mode`() {
        val on = initial.withHeadlightMode(HeadlightMode.On, demoAutoHeadlightsOn = false)
        assertTrue(on.headlightsOn)
        assertEquals(HeadlightMode.On, on.headlightMode)
        val daylight = on.withHeadlightMode(HeadlightMode.Auto, demoAutoHeadlightsOn = false)
        assertFalse(daylight.headlightsOn)
        assertEquals(HeadlightMode.Auto, daylight.headlightMode)
        val night = daylight.withHeadlightMode(HeadlightMode.Auto, demoAutoHeadlightsOn = true)
        assertTrue(night.headlightsOn)
        val off = night.withHeadlightMode(HeadlightMode.Off, demoAutoHeadlightsOn = true)
        assertFalse(off.headlightsOn)
    }

    @Test fun `windows clamp extreme commands and preserve all other window positions`() {
        VehicleWindow.entries.forEach { window ->
            val open = initial.withWindowPosition(window, Int.MAX_VALUE)
            assertEquals(100, open.windowOpenPercent[window])
            VehicleWindow.entries.filterNot { it == window }.forEach { other ->
                assertEquals(0, open.windowOpenPercent[other])
            }
            assertEquals(0, open.withWindowPosition(window, Int.MIN_VALUE).windowOpenPercent[window])
            assertEquals(35, open.withWindowPosition(window, 35).windowOpenPercent[window])
        }
    }

    @Test fun `parking brake toggles without changing lock lights or open parts`() {
        val changed = initial.toggledPart(VehiclePart.Trunk).withHeadlightMode(HeadlightMode.On, false)
        val released = changed.toggledParkingBrake()
        assertFalse(released.parkingBrakeEngaged)
        assertEquals(changed.locked, released.locked)
        assertEquals(changed.openParts, released.openParts)
        assertEquals(changed.headlightsOn, released.headlightsOn)
        assertEquals(changed, released.toggledParkingBrake())
    }

    @Test fun `unavailable controls reject every command`() {
        val unavailable = VehicleControlsState()
        VehiclePart.entries.forEach { assertEquals(unavailable, unavailable.toggledPart(it)) }
        assertEquals(unavailable, unavailable.withLocked(false))
        HeadlightMode.entries.forEach { assertEquals(unavailable, unavailable.withHeadlightMode(it, true)) }
        VehicleWindow.entries.forEach { assertEquals(unavailable, unavailable.withWindowPosition(it, 100)) }
        assertEquals(unavailable, unavailable.toggledParkingBrake())
        TurnSignal.entries.forEach { assertEquals(unavailable, unavailable.withTurnSignal(it)) }
        VehicleGear.entries.forEach { assertEquals(unavailable, unavailable.withGear(it)) }
        assertEquals(unavailable, unavailable.withBrakeLights(true))
        assertEquals(unavailable, unavailable.withAmbientLights(true))
        assertFalse(unavailable.reverseLightsOn)
        VehicleGear.entries.forEach { gear ->
            assertEquals(TurnSignal.Off, unavailable.copy(gear = gear, turnSignal = TurnSignal.Hazard).effectiveTurnSignal)
        }
    }

    @Test fun `signals are mutually exclusive and hazards do not select a gear`() {
        var controls = initial.withGear(VehicleGear.Drive)
        TurnSignal.entries.forEach { signal ->
            controls = controls.withTurnSignal(signal)
            assertEquals(signal, controls.turnSignal)
            assertEquals(signal, controls.effectiveTurnSignal)
            assertEquals(VehicleGear.Drive, controls.gear)
            assertFalse(controls.reverseLightsOn)
        }
        val hazards = controls.withTurnSignal(TurnSignal.Hazard)
        assertEquals(TurnSignal.Hazard, hazards.withGear(VehicleGear.Park).turnSignal)
        assertEquals(TurnSignal.Off, hazards.withTurnSignal(TurnSignal.Off).turnSignal)
    }

    @Test fun `Park uses automatic hazards without losing the manual signal chosen for other gears`() {
        TurnSignal.entries.forEach { manual ->
            val driving = initial.withGear(VehicleGear.Drive).withTurnSignal(manual)
            val parked = driving.withGear(VehicleGear.Park)
            assertEquals(manual, parked.turnSignal)
            assertEquals(TurnSignal.Hazard, parked.effectiveTurnSignal)
            TurnSignal.entries.forEach { assertEquals(parked, parked.withTurnSignal(it)) }
            for (gear in listOf(VehicleGear.Drive, VehicleGear.Reverse)) {
                assertEquals(manual, parked.withGear(gear).effectiveTurnSignal)
            }
        }
    }

    @Test fun `reverse lamps follow Reverse only and Park hazards never enable white reversing lamps`() {
        val reverse = initial.withGear(VehicleGear.Reverse)
        assertTrue(reverse.reverseLightsOn)
        assertEquals(TurnSignal.Off, reverse.turnSignal)
        assertFalse(reverse.withGear(VehicleGear.Drive).reverseLightsOn)
        val parked = reverse.withGear(VehicleGear.Park)
        assertFalse(parked.reverseLightsOn)
        assertEquals(TurnSignal.Off, parked.turnSignal)
        assertEquals(TurnSignal.Hazard, parked.effectiveTurnSignal)
    }

    @Test fun `brake and ambient lighting are independent from headlights signals and gear`() {
        val base = initial.withHeadlightMode(HeadlightMode.On, false)
            .withGear(VehicleGear.Reverse).withTurnSignal(TurnSignal.Left)
        val lit = base.withBrakeLights(true).withAmbientLights(true)
        assertTrue(lit.brakeLightsOn)
        assertTrue(lit.ambientLightsOn)
        assertTrue(lit.headlightsOn)
        assertTrue(lit.reverseLightsOn)
        assertEquals(TurnSignal.Left, lit.turnSignal)
        assertEquals(base, lit.withBrakeLights(false).withAmbientLights(false))
    }

    @Test fun `charging opens the port and prevents selecting a drive gear or closing the connector`() {
        val snapshot = VehicleSnapshot(
            vehicle = VehicleState(source = DataSource.Demo, charging = false, parked = true),
            controls = initial,
        )
        val charging = snapshot.withCharging(true)
        assertEquals(true, charging.vehicle.charging)
        assertTrue(VehiclePart.ChargePort in charging.controls.openParts)
        assertTrue(charging.controls.locked)
        assertEquals(charging, charging.withToggledVehiclePart(VehiclePart.ChargePort))
        assertEquals(charging, charging.withVehicleGear(VehicleGear.Drive))
        assertEquals(charging, charging.withVehicleGear(VehicleGear.Reverse))
        assertTrue(VehiclePart.ChargePort in charging.controls.withLocked(true).openParts)
        val stopped = charging.withCharging(false)
        assertEquals(false, stopped.vehicle.charging)
        assertFalse(VehiclePart.ChargePort in stopped.controls.openParts)
        val reverse = stopped.withVehicleGear(VehicleGear.Reverse)
        assertTrue(reverse.controls.reverseLightsOn)
        assertEquals(false, reverse.vehicle.parked)
        assertEquals(reverse, reverse.withCharging(true))
        assertEquals(true, reverse.withVehicleGear(VehicleGear.Park).withCharging(true).vehicle.charging)
    }

    @Test fun `manual flap control and charging stop preserve unrelated panels and lamps`() {
        val snapshot = VehicleSnapshot(vehicle = VehicleState(charging = false),
            controls = initial.toggledPart(VehiclePart.Sunroof).withAmbientLights(true))
        val manual = snapshot.withToggledVehiclePart(VehiclePart.ChargePort)
        assertTrue(VehiclePart.ChargePort in manual.controls.openParts)
        assertTrue(manual.controls.locked)
        val stopped = manual.withCharging(true).withCharging(false)
        assertEquals(setOf(VehiclePart.Sunroof), stopped.controls.openParts)
        assertTrue(stopped.controls.ambientLightsOn)
        assertTrue(stopped.controls.locked)
    }

    @Test fun `snapshot commands do not invent unavailable charging or parked telemetry`() {
        val unavailable = VehicleSnapshot()
        assertEquals(unavailable, unavailable.withCharging(true))
        assertEquals(unavailable, unavailable.withVehicleGear(VehicleGear.Reverse))
        assertEquals(unavailable, unavailable.withToggledVehiclePart(VehiclePart.Bonnet))
        val unsupportedCharging = VehicleSnapshot(controls = initial)
        assertEquals(unsupportedCharging, unsupportedCharging.withCharging(true))
        assertEquals(null, unsupportedCharging.withVehicleGear(VehicleGear.Reverse).vehicle.parked)
    }
}
