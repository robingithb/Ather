package com.aether.hmi.domain

import java.time.ZonedDateTime
import kotlin.math.round
import kotlinx.coroutines.flow.StateFlow

enum class DataSource { Demo, Live, Unavailable }
enum class VehicleHealth { Healthy, Attention, Unavailable }
enum class CabinAir { Clean, Unknown }
enum class NetworkTransport { Wifi, Mobile, Ethernet, Other, None }
enum class Greeting { Morning, Afternoon, Evening, Night }
enum class ClimateControl { Auto, Ac, Sync, FrontDefrost, RearDefrost, SeatHeating }

data class VehicleState(
    val source: DataSource = DataSource.Unavailable,
    val batteryPercent: Int? = null,
    val rangeKm: Int? = null,
    val health: VehicleHealth = VehicleHealth.Unavailable,
    val charging: Boolean? = null,
    val ecoMode: Boolean? = null,
    val parked: Boolean? = null,
    val outsideCelsius: Double? = null,
)

data class ClimateState(
    val available: Boolean = false,
    val driverCelsius: Float? = null,
    val minCelsius: Float = 16f,
    val maxCelsius: Float = 30f,
    val stepCelsius: Float = 0.5f,
    val auto: Boolean = false,
    val ac: Boolean = false,
    val sync: Boolean = false,
    val frontDefrost: Boolean = false,
    val rearDefrost: Boolean = false,
    val seatHeating: Boolean = false,
    val cabinAir: CabinAir = CabinAir.Unknown,
) {
    fun adjustedTemperature(steps: Int): Float? {
        val current = driverCelsius ?: return null
        if (!available || stepCelsius <= 0f) return current
        val next = current + steps * stepCelsius
        return (minCelsius + round((next - minCelsius) / stepCelsius) * stepCelsius)
            .coerceIn(minCelsius, maxCelsius)
    }

    fun isEnabled(control: ClimateControl): Boolean = when (control) {
        ClimateControl.Auto -> auto
        ClimateControl.Ac -> ac
        ClimateControl.Sync -> sync
        ClimateControl.FrontDefrost -> frontDefrost
        ClimateControl.RearDefrost -> rearDefrost
        ClimateControl.SeatHeating -> seatHeating
    }
}

data class RoutePreview(val destination: String, val durationMinutes: Long, val distanceKm: Double) {
    fun arrival(now: ZonedDateTime): ZonedDateTime = now.plusMinutes(durationMinutes)
}

data class VehicleSnapshot(
    val vehicle: VehicleState = VehicleState(),
    val climate: ClimateState = ClimateState(),
    val route: RoutePreview? = null,
)

data class ConnectivityState(
    val online: Boolean = false,
    val transport: NetworkTransport = NetworkTransport.None,
)

fun greetingAt(time: ZonedDateTime): Greeting = when (time.hour) {
    in 5..11 -> Greeting.Morning
    in 12..16 -> Greeting.Afternoon
    in 17..21 -> Greeting.Evening
    else -> Greeting.Night
}

interface VehicleRepository {
    val state: StateFlow<VehicleSnapshot>
    suspend fun adjustDriverTemperature(steps: Int)
    suspend fun toggle(control: ClimateControl)
}
