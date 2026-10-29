package com.aether.hmi.feature.home

import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehicleGear
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.domain.VehicleState
import com.aether.hmi.domain.withCharging
import com.aether.hmi.domain.withVehicleGear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStatusChipPolicyTest {
    private val parked = VehicleSnapshot(
        vehicle = VehicleState(charging = false, parked = false),
        controls = VehicleControlsState(available = true, gear = VehicleGear.Park),
    )

    @Test fun `Park chip follows gear transitions rather than stale parked telemetry or manual selection`() {
        var snapshot = parked
        for (gear in listOf(VehicleGear.Park, VehicleGear.Drive, VehicleGear.Reverse, VehicleGear.Park)) {
            snapshot = snapshot.withVehicleGear(gear)
            for (manual in listOf(0, -1)) {
                val chip = state(snapshot, manual, HomeStatusChipPolicy.ParkedIndex)
                assertEquals(gear == VehicleGear.Park, chip.selected)
                assertTrue(chip.readOnly)
                assertTrue(chip.available)
            }
        }
        assertTrue(state(parked, 0, HomeStatusChipPolicy.ParkedIndex).selected)
    }

    @Test fun `Charging chip follows start and stop and Park remains active while charging`() {
        val charging = parked.withCharging(true)
        for (manual in listOf(0, -1)) {
            assertFalse(state(parked, manual, HomeStatusChipPolicy.ChargingIndex).selected)
            assertTrue(state(charging, manual, HomeStatusChipPolicy.ChargingIndex).selected)
            assertTrue(state(charging, manual, HomeStatusChipPolicy.ParkedIndex).selected)
            assertFalse(state(charging.withCharging(false), manual, HomeStatusChipPolicy.ChargingIndex).selected)
            assertTrue(state(charging.withCharging(false), manual, HomeStatusChipPolicy.ParkedIndex).selected)
        }
    }

    @Test fun `unknown charging is neutral and cannot be selected by a persisted manual bit`() {
        val unknown = parked.copy(vehicle = parked.vehicle.copy(charging = null))
        val chip = state(unknown, -1, HomeStatusChipPolicy.ChargingIndex)
        assertFalse(chip.selected)
        assertFalse(chip.available)
        assertTrue(chip.readOnly)
        assertTrue(state(unknown, 0, HomeStatusChipPolicy.ParkedIndex).selected)
    }

    @Test fun `unavailable controls suppress stale Park and charging values and saved selection bits`() {
        val unavailable = parked.withCharging(true).copy(controls = parked.controls.copy(available = false))
        for (index in listOf(HomeStatusChipPolicy.ChargingIndex, HomeStatusChipPolicy.ParkedIndex)) {
            val chip = state(unavailable, -1, index)
            assertFalse(chip.selected)
            assertFalse(chip.available)
            assertTrue(chip.readOnly)
        }
    }

    @Test fun `the other five chips preserve independent manual selections without issuing vehicle commands`() {
        for (index in listOf(0, 1, 2, 3, 5)) {
            assertFalse(state(parked, 0, index).selected)
            val selected = state(parked, 1 shl index, index)
            assertTrue(selected.selected)
            assertFalse(selected.readOnly)
            assertTrue(selected.available)
            val unavailable = parked.copy(controls = VehicleControlsState())
            assertEquals(selected, state(unavailable, 1 shl index, index))
            for (other in listOf(0, 1, 2, 3, 5).filterNot { it == index }) {
                assertFalse(state(parked, 1 shl index, other).selected)
            }
        }
    }

    private fun state(snapshot: VehicleSnapshot, manual: Int, index: Int) = HomeStatusChipPolicy.state(snapshot, manual, index)
}
