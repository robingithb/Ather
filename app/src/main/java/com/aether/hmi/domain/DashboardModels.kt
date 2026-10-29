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
enum class AirflowMode { Face, FaceFeet, Feet, Defrost }
enum class ClimateZone { Driver, Passenger }
enum class SeatZone { Driver, Passenger }
enum class SeatComfort { Heating, Cooling }

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
    val passengerCelsius: Float? = null,
    val fanSpeed: Int = 0,
    val maxFanSpeed: Int = 8,
    val airflowMode: AirflowMode = AirflowMode.Face,
    val driverHeatingLevel: Int = 0,
    val driverCoolingLevel: Int = 0,
    val passengerHeatingLevel: Int = 0,
    val passengerCoolingLevel: Int = 0,
    val maxSeatLevel: Int = 3,
    val cabinAirQualityPercent: Int? = null,
    val cabinFilterHealthy: Boolean? = null,
) {
    fun adjustedTemperature(steps: Int, zone: ClimateZone = ClimateZone.Driver): Float? {
        val current = when (zone) {
            ClimateZone.Driver -> driverCelsius
            ClimateZone.Passenger -> passengerCelsius
        } ?: return null
        if (!available || !current.isFinite() || !stepCelsius.isFinite() || stepCelsius <= 0f ||
            !minCelsius.isFinite() || !maxCelsius.isFinite() || minCelsius > maxCelsius ||
            (zone == ClimateZone.Passenger && sync)
        ) return current
        val next = current + steps * stepCelsius
        return (minCelsius + round((next - minCelsius) / stepCelsius) * stepCelsius)
            .coerceIn(minCelsius, maxCelsius)
    }

    fun withTemperatureAdjustment(zone: ClimateZone, steps: Int): ClimateState {
        if (!available || steps == 0) return this
        return when (zone) {
            ClimateZone.Driver -> {
                val temperature = adjustedTemperature(steps) ?: return this
                copy(
                    driverCelsius = temperature,
                    passengerCelsius = if (sync && passengerCelsius != null) temperature else passengerCelsius,
                )
            }
            ClimateZone.Passenger -> if (sync) this else copy(
                passengerCelsius = adjustedTemperature(steps, zone),
            )
        }
    }

    fun withFanAdjustment(steps: Int): ClimateState {
        if (!available || maxFanSpeed <= 0 || steps == 0) return this
        // Use Long before clamping so even an unusually large command cannot overflow.
        val speed = (fanSpeed.toLong() + steps).coerceIn(0L, maxFanSpeed.toLong()).toInt()
        return copy(fanSpeed = speed, auto = false)
    }

    fun withAirflowMode(mode: AirflowMode): ClimateState =
        if (available) copy(airflowMode = mode) else this

    fun withToggledControl(control: ClimateControl): ClimateState {
        if (!available) return this
        return when (control) {
            ClimateControl.Auto -> copy(auto = !auto)
            ClimateControl.Ac -> copy(ac = !ac)
            ClimateControl.Sync -> if (!sync && (driverCelsius == null || passengerCelsius == null)) this else copy(
                sync = !sync,
                passengerCelsius = if (!sync) driverCelsius else passengerCelsius,
            )
            ClimateControl.FrontDefrost -> copy(frontDefrost = !frontDefrost)
            ClimateControl.RearDefrost -> copy(rearDefrost = !rearDefrost)
            ClimateControl.SeatHeating -> if (maxSeatLevel > 0) copy(
                seatHeating = !isEnabled(control),
                driverHeatingLevel = if (isEnabled(control)) 0 else 1,
                driverCoolingLevel = if (isEnabled(control)) driverCoolingLevel else 0,
            ) else this
        }
    }

    fun seatLevel(zone: SeatZone, comfort: SeatComfort): Int = when (zone) {
        SeatZone.Driver -> if (comfort == SeatComfort.Heating) driverHeatingLevel else driverCoolingLevel
        SeatZone.Passenger -> if (comfort == SeatComfort.Heating) passengerHeatingLevel else passengerCoolingLevel
    }

    fun withCycledSeatLevel(zone: SeatZone, comfort: SeatComfort): ClimateState {
        if (!available || maxSeatLevel <= 0) return this
        val current = seatLevel(zone, comfort).coerceIn(0, maxSeatLevel)
        val next = if (current >= maxSeatLevel) 0 else current + 1
        return when (zone) {
            SeatZone.Driver -> if (comfort == SeatComfort.Heating) copy(
                driverHeatingLevel = next, seatHeating = next > 0,
                driverCoolingLevel = if (next > 0) 0 else driverCoolingLevel,
            ) else copy(
                driverCoolingLevel = next,
                driverHeatingLevel = if (next > 0) 0 else driverHeatingLevel,
                seatHeating = if (next > 0) false else seatHeating,
            )
            SeatZone.Passenger -> if (comfort == SeatComfort.Heating) copy(
                passengerHeatingLevel = next,
                passengerCoolingLevel = if (next > 0) 0 else passengerCoolingLevel,
            ) else copy(
                passengerCoolingLevel = next,
                passengerHeatingLevel = if (next > 0) 0 else passengerHeatingLevel,
            )
        }
    }

    fun isEnabled(control: ClimateControl): Boolean = when (control) {
        ClimateControl.Auto -> auto
        ClimateControl.Ac -> ac
        ClimateControl.Sync -> sync
        ClimateControl.FrontDefrost -> frontDefrost
        ClimateControl.RearDefrost -> rearDefrost
        ClimateControl.SeatHeating -> seatHeating || driverHeatingLevel > 0
    }
}

data class RoutePreview(val destination: String, val durationMinutes: Long, val distanceKm: Double) {
    fun arrival(now: ZonedDateTime): ZonedDateTime = now.plusMinutes(durationMinutes)
}

data class VehicleSnapshot(
    val vehicle: VehicleState = VehicleState(),
    val climate: ClimateState = ClimateState(),
    val route: RoutePreview? = null,
    val controls: VehicleControlsState = VehicleControlsState(),
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
    suspend fun adjustPassengerTemperature(steps: Int)
    suspend fun adjustFanSpeed(steps: Int)
    suspend fun setAirflowMode(mode: AirflowMode)
    suspend fun cycleSeatLevel(zone: SeatZone, comfort: SeatComfort)
    suspend fun toggle(control: ClimateControl)
    suspend fun toggleVehiclePart(part: VehiclePart)
    suspend fun setVehicleLocked(locked: Boolean)
    suspend fun setHeadlightMode(mode: HeadlightMode)
    suspend fun setWindowPosition(window: VehicleWindow, percent: Int)
    suspend fun toggleParkingBrake()
    suspend fun setCharging(enabled: Boolean)
    suspend fun setTurnSignal(signal: TurnSignal)
    suspend fun setVehicleGear(gear: VehicleGear)
    suspend fun setBrakeLights(enabled: Boolean)
    suspend fun setAmbientLights(enabled: Boolean)
}
