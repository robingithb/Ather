package com.aether.hmi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateBehaviorTest {
    private val initial = ClimateState(
        available = true,
        driverCelsius = 21f,
        passengerCelsius = 20.5f,
        minCelsius = 16f,
        maxCelsius = 28f,
        stepCelsius = 0.5f,
        fanSpeed = 6,
        maxFanSpeed = 8,
        maxSeatLevel = 3,
        auto = true,
    )

    @Test fun `zones adjust independently and clamp to configured limits`() {
        val passenger = initial.withTemperatureAdjustment(ClimateZone.Passenger, -100)
        assertEquals(16f, passenger.passengerCelsius!!, 0.001f)
        assertEquals(initial.driverCelsius, passenger.driverCelsius)
        val driver = passenger.withTemperatureAdjustment(ClimateZone.Driver, 100)
        assertEquals(28f, driver.driverCelsius!!, 0.001f)
        assertEquals(passenger.passengerCelsius, driver.passengerCelsius)
    }

    @Test fun `sync immediately copies driver then follows driver changes and locks passenger input`() {
        val synced = initial.withToggledControl(ClimateControl.Sync)
        assertTrue(synced.sync)
        assertEquals(synced.driverCelsius, synced.passengerCelsius)
        val adjusted = synced.withTemperatureAdjustment(ClimateZone.Driver, 3)
        assertEquals(22.5f, adjusted.driverCelsius!!, 0.001f)
        assertEquals(adjusted.driverCelsius, adjusted.passengerCelsius)
        assertEquals(adjusted, adjusted.withTemperatureAdjustment(ClimateZone.Passenger, -2))
        val independent = adjusted.withToggledControl(ClimateControl.Sync)
            .withTemperatureAdjustment(ClimateZone.Passenger, -1)
        assertFalse(independent.sync)
        assertEquals(22f, independent.passengerCelsius!!, 0.001f)
        assertEquals(22.5f, independent.driverCelsius!!, 0.001f)
    }

    @Test fun `sync does not invent a value for an unsupported passenger zone`() {
        val unsupported = initial.copy(passengerCelsius = null)
        assertEquals(unsupported, unsupported.withToggledControl(ClimateControl.Sync))
        assertNull(unsupported.withTemperatureAdjustment(ClimateZone.Passenger, 1).passengerCelsius)
    }

    @Test fun `manual fan adjustments disable automatic mode and support zero without overflow`() {
        val off = initial.withFanAdjustment(Int.MIN_VALUE)
        assertEquals(0, off.fanSpeed)
        assertFalse(off.auto)
        val maximum = off.withFanAdjustment(Int.MAX_VALUE)
        assertEquals(maximum.maxFanSpeed, maximum.fanSpeed)
        assertFalse(maximum.auto)
        assertEquals(initial, initial.withFanAdjustment(0))
        assertTrue(maximum.withToggledControl(ClimateControl.Auto).auto)
    }

    @Test fun `selecting an airflow mode replaces the previous selection and preserves other controls`() {
        var state = initial
        AirflowMode.entries.forEach { mode ->
            state = state.withAirflowMode(mode)
            assertEquals(mode, state.airflowMode)
            assertEquals(initial.fanSpeed, state.fanSpeed)
            assertEquals(initial.auto, state.auto)
            assertEquals(initial.frontDefrost, state.frontDefrost)
        }
    }

    @Test fun `seat levels cycle through supported steps then off independently by zone`() {
        var state = initial
        repeat(initial.maxSeatLevel) { index ->
            state = state.withCycledSeatLevel(SeatZone.Driver, SeatComfort.Heating)
            assertEquals(index + 1, state.driverHeatingLevel)
            assertTrue(state.isEnabled(ClimateControl.SeatHeating))
            assertEquals(0, state.passengerHeatingLevel)
        }
        state = state.withCycledSeatLevel(SeatZone.Driver, SeatComfort.Heating)
        assertEquals(0, state.driverHeatingLevel)
        assertFalse(state.isEnabled(ClimateControl.SeatHeating))
        state = state.withCycledSeatLevel(SeatZone.Passenger, SeatComfort.Cooling)
        assertEquals(1, state.passengerCoolingLevel)
        assertEquals(0, state.driverCoolingLevel)
    }

    @Test fun `seat heating and cooling exclude each other and Home seat toggle uses driver heating`() {
        val warm = initial.withToggledControl(ClimateControl.SeatHeating)
        assertEquals(1, warm.driverHeatingLevel)
        assertTrue(warm.seatHeating)
        val cool = warm.withCycledSeatLevel(SeatZone.Driver, SeatComfort.Cooling)
        assertEquals(0, cool.driverHeatingLevel)
        assertEquals(1, cool.driverCoolingLevel)
        assertFalse(cool.seatHeating)
        val warmAgain = cool.withCycledSeatLevel(SeatZone.Driver, SeatComfort.Heating)
        assertEquals(1, warmAgain.driverHeatingLevel)
        assertEquals(0, warmAgain.driverCoolingLevel)
        assertTrue(warmAgain.seatHeating)
    }

    @Test fun `unavailable climate data rejects all commands and exposes no air sensor readings`() {
        val unavailable = ClimateState()
        assertEquals(unavailable, unavailable.withTemperatureAdjustment(ClimateZone.Driver, 1))
        assertEquals(unavailable, unavailable.withTemperatureAdjustment(ClimateZone.Passenger, 1))
        assertEquals(unavailable, unavailable.withFanAdjustment(1))
        assertEquals(unavailable, unavailable.withAirflowMode(AirflowMode.Defrost))
        assertEquals(unavailable, unavailable.withToggledControl(ClimateControl.Ac))
        assertEquals(unavailable, unavailable.withCycledSeatLevel(SeatZone.Driver, SeatComfort.Heating))
        assertNull(unavailable.cabinAirQualityPercent)
        assertNull(unavailable.cabinFilterHealthy)
    }
}
