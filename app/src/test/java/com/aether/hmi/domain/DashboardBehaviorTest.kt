package com.aether.hmi.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardBehaviorTest {
    @Test fun `greeting changes at each local boundary including midnight`() {
        val examples = mapOf(
            "00:00" to Greeting.Night, "04:59" to Greeting.Night,
            "05:00" to Greeting.Morning, "11:59" to Greeting.Morning,
            "12:00" to Greeting.Afternoon, "16:59" to Greeting.Afternoon,
            "17:00" to Greeting.Evening, "21:59" to Greeting.Evening,
            "22:00" to Greeting.Night, "23:59" to Greeting.Night,
        )
        examples.forEach { (time, expected) ->
            assertEquals(time, expected, greetingAt(ZonedDateTime.parse("2026-10-22T$time:00+05:30[Asia/Kolkata]")))
        }
    }

    @Test fun `the same instant gets the greeting for the current device timezone`() {
        val instant = Instant.parse("2026-10-22T00:30:00Z")
        assertEquals(Greeting.Morning, greetingAt(instant.atZone(ZoneId.of("Asia/Kolkata"))))
        assertEquals(Greeting.Night, greetingAt(instant.atZone(ZoneId.of("Europe/London"))))
    }

    @Test fun `demo ETA advances with time and crosses the date boundary`() {
        val route = RoutePreview("Destination", 12, 8.4)
        val start = ZonedDateTime.parse("2026-10-22T23:55:00+05:30[Asia/Kolkata]")
        assertEquals("2026-10-23T00:07+05:30[Asia/Kolkata]", route.arrival(start).toString())
        assertEquals(route.arrival(start).plusMinutes(1), route.arrival(start.plusMinutes(1)))
    }

    @Test fun `ETA represents elapsed duration across a timezone offset transition`() {
        val route = RoutePreview("Destination", 12, 8.4)
        val start = Instant.parse("2026-10-25T00:55:00Z").atZone(ZoneId.of("Europe/London"))
        assertEquals(start.toInstant().plusSeconds(720), route.arrival(start).toInstant())
    }

    @Test fun `temperature uses repository supplied resolution rather than a fixed UI increment`() {
        val climate = ClimateState(available = true, driverCelsius = 20f, minCelsius = 18f, maxCelsius = 25f, stepCelsius = 0.25f)
        assertEquals(20.25f, climate.adjustedTemperature(1)!!, 0.001f)
        assertEquals(19.75f, climate.adjustedTemperature(-1)!!, 0.001f)
    }

    @Test fun `rapid temperature adjustments cannot exceed supported limits`() {
        var climate = ClimateState(available = true, driverCelsius = 21f)
        repeat(100) { climate = climate.copy(driverCelsius = climate.adjustedTemperature(1)) }
        assertEquals(climate.maxCelsius, climate.driverCelsius!!, 0.001f)
        repeat(100) { climate = climate.copy(driverCelsius = climate.adjustedTemperature(-1)) }
        assertEquals(climate.minCelsius, climate.driverCelsius!!, 0.001f)
    }

    @Test fun `unavailable vehicle data has no fabricated telemetry or health result`() {
        val state = VehicleSnapshot()
        assertEquals(DataSource.Unavailable, state.vehicle.source)
        assertEquals(VehicleHealth.Unavailable, state.vehicle.health)
        assertNull(state.vehicle.batteryPercent)
        assertNull(state.vehicle.rangeKm)
        assertNull(state.vehicle.outsideCelsius)
        assertNull(state.climate.adjustedTemperature(1))
        assertNull(state.route)
    }

    @Test fun `a disconnected climate adapter does not change its last known temperature`() {
        val climate = ClimateState(available = false, driverCelsius = 21f)
        assertEquals(21f, climate.adjustedTemperature(1)!!, 0.001f)
    }
}
