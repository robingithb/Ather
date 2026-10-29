package com.aether.hmi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleAttentionTest {
    private val healthy = VehicleSnapshot(
        vehicle = VehicleState(source = DataSource.Demo, health = VehicleHealth.Healthy, charging = false),
        controls = VehicleControlsState(available = true),
    )

    @Test fun `healthy closed vehicle has no notices for demo or live data`() {
        for (source in listOf(DataSource.Demo, DataSource.Live)) {
            val attention = healthy.copy(vehicle = healthy.vehicle.copy(source = source)).vehicleAttention()
            assertEquals(VehicleAttentionSeverity.Good, attention.severity)
            assertNull(attention.primaryIssue)
            assertEquals(0, attention.additionalIssueCount)
        }
    }

    @Test fun `each panel reports its own open notice and closing it clears shared attention`() {
        VehiclePart.entries.forEach { part ->
            val opened = healthy.withToggledVehiclePart(part)
            assertEquals(listOf(VehicleIssue.OpenPart(part)), opened.vehicleAttention().issues)
            assertEquals(VehicleAttentionSeverity.Attention, opened.vehicleAttention().severity)
            assertTrue(opened.withToggledVehiclePart(part).vehicleAttention().issues.isEmpty())
        }
    }

    @Test fun `part and window notices are stable across unordered input and clear independently`() {
        val changed = healthy.copy(controls = healthy.controls.copy(
            openParts = setOf(VehiclePart.Bonnet, VehiclePart.RearRightDoor, VehiclePart.FrontLeftDoor),
            windowOpenPercent = mapOf(VehicleWindow.RearRight to 100, VehicleWindow.FrontLeft to 1),
        ))
        val attention = changed.vehicleAttention()
        assertEquals(listOf(
            VehicleIssue.OpenPart(VehiclePart.FrontLeftDoor),
            VehicleIssue.OpenPart(VehiclePart.RearRightDoor),
            VehicleIssue.OpenPart(VehiclePart.Bonnet),
            VehicleIssue.OpenWindow(VehicleWindow.FrontLeft),
            VehicleIssue.OpenWindow(VehicleWindow.RearRight),
        ), attention.issues)
        assertEquals(4, attention.additionalIssueCount)
        val closedPanel = changed.withToggledVehiclePart(VehiclePart.FrontLeftDoor)
        assertEquals(VehicleIssue.OpenPart(VehiclePart.RearRightDoor), closedPanel.vehicleAttention().primaryIssue)
        val windowsClosed = closedPanel.copy(controls = closedPanel.controls
            .withWindowPosition(VehicleWindow.FrontLeft, 0).withWindowPosition(VehicleWindow.RearRight, 0))
        assertEquals(2, windowsClosed.vehicleAttention().issues.size)
    }

    @Test fun `every partially open window is reported and a closed window is omitted`() {
        VehicleWindow.entries.forEach { window ->
            val opened = healthy.copy(controls = healthy.controls.withWindowPosition(window, 1))
            assertEquals(listOf(VehicleIssue.OpenWindow(window)), opened.vehicleAttention().issues)
            assertTrue(opened.copy(controls = opened.controls.withWindowPosition(window, 0)).vehicleAttention().issues.isEmpty())
        }
    }

    @Test fun `service attention takes priority and survives closing a panel`() {
        val service = healthy.copy(vehicle = healthy.vehicle.copy(health = VehicleHealth.Attention))
        val opened = service.withToggledVehiclePart(VehiclePart.Trunk)
        assertEquals(VehicleIssue.ServiceAttention, opened.vehicleAttention().primaryIssue)
        assertEquals(1, opened.vehicleAttention().additionalIssueCount)
        assertEquals(listOf(VehicleIssue.ServiceAttention), opened.withToggledVehiclePart(VehiclePart.Trunk).vehicleAttention().issues)
    }

    @Test fun `unavailable telemetry overrides stale open panels and service attention`() {
        val stale = healthy.copy(vehicle = healthy.vehicle.copy(health = VehicleHealth.Attention),
            controls = healthy.controls.copy(openParts = VehiclePart.entries.toSet()))
        val cases = listOf(
            VehicleSnapshot(),
            stale.copy(controls = stale.controls.copy(available = false)),
            stale.copy(vehicle = stale.vehicle.copy(source = DataSource.Unavailable)),
            stale.copy(vehicle = stale.vehicle.copy(health = VehicleHealth.Unavailable)),
        )
        cases.forEach { snapshot ->
            val attention = snapshot.vehicleAttention()
            assertEquals(listOf(VehicleIssue.Unavailable), attention.issues)
            assertEquals(VehicleAttentionSeverity.Unavailable, attention.severity)
            assertEquals(0, attention.additionalIssueCount)
        }
    }

    @Test fun `charging treats only its open connector as expected and keeps unrelated notices`() {
        val charging = healthy.withCharging(true)
        assertTrue(VehiclePart.ChargePort in charging.controls.openParts)
        assertTrue(charging.vehicleAttention().issues.isEmpty())
        val doorOpen = charging.withToggledVehiclePart(VehiclePart.FrontRightDoor)
        assertEquals(listOf(VehicleIssue.OpenPart(VehiclePart.FrontRightDoor)), doorOpen.vehicleAttention().issues)
        assertTrue(charging.withCharging(false).vehicleAttention().issues.isEmpty())
        val idleOpen = healthy.withToggledVehiclePart(VehiclePart.ChargePort)
        assertEquals(listOf(VehicleIssue.OpenPart(VehiclePart.ChargePort)), idleOpen.vehicleAttention().issues)
    }
}
