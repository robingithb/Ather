package com.aether.hmi.data

import android.content.Context
import com.aether.hmi.domain.AirflowMode
import com.aether.hmi.domain.CabinAir
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.domain.ClimateZone
import com.aether.hmi.domain.DataSource
import com.aether.hmi.domain.HeadlightMode
import com.aether.hmi.domain.RoutePreview
import com.aether.hmi.domain.SeatComfort
import com.aether.hmi.domain.SeatZone
import com.aether.hmi.domain.VehicleHealth
import com.aether.hmi.domain.VehicleControlsState
import com.aether.hmi.domain.VehiclePart
import com.aether.hmi.domain.VehicleRepository
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.domain.VehicleState
import com.aether.hmi.domain.VehicleWindow
import com.aether.hmi.domain.VehicleGear
import com.aether.hmi.domain.TurnSignal
import com.aether.hmi.domain.withCharging
import com.aether.hmi.domain.withToggledVehiclePart
import com.aether.hmi.domain.withVehicleGear
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/** The AAOS adapter can implement the same contract without changing Home or Climate. */
class MockVehicleRepository(context: Context, scope: CoroutineScope) : VehicleRepository {
    private val preferences = context.getSharedPreferences("vehicle_demo", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(VehicleSnapshot())
    override val state = mutableState.asStateFlow()
    private val mutex = Mutex()
    private var demoAutoHeadlightsOn = false

    init {
        scope.launch(Dispatchers.IO) {
            val snapshot = try {
                val json = context.assets.open("demo_vehicle.json").bufferedReader().use {
                    JSONObject(it.readText())
                }
                parseSnapshot(json)
            } catch (_: IOException) {
                VehicleSnapshot()
            } catch (_: JSONException) {
                VehicleSnapshot()
            } catch (_: IllegalArgumentException) {
                VehicleSnapshot()
            }
            mutex.withLock { mutableState.value = snapshot }
        }
    }

    private fun parseSnapshot(json: JSONObject): VehicleSnapshot {
        val vehicle = json.getJSONObject("vehicle")
        val climate = json.getJSONObject("climate")
        val min = climate.getDouble("minCelsius").toFloat()
        val max = climate.getDouble("maxCelsius").toFloat()
        val step = climate.getDouble("stepCelsius").toFloat()
        require(min.isFinite() && max.isFinite() && min < max && step.isFinite() && step > 0)
        fun temperature(key: String, preferenceKey: String): Float? {
            if (climate.isNull(key) || !climate.has(key)) return null
            val default = climate.getDouble(key).toFloat()
            require(default.isFinite())
            val saved = preferences.getFloat(preferenceKey, default)
            return (if (saved.isFinite()) saved else default).coerceIn(min, max)
        }
        val driver = temperature("driverCelsius", "temperature")
        val passenger = temperature("passengerCelsius", "passengerTemperature")
        val maxFan = climate.getInt("maxFanSpeed").also { require(it in 1..32) }
        val maxSeat = climate.getInt("maxSeatLevel").also { require(it in 1..10) }
        fun saved(key: String) = preferences.getBoolean(key, climate.getBoolean(key))
        val sync = saved("sync") && driver != null && passenger != null
        fun seatLevel(key: String, legacyDefault: Int = 0): Int = preferences.getInt(
            key, climate.optInt(key, legacyDefault),
        ).coerceIn(0, maxSeat)
        val driverHeat = if (preferences.contains("driverHeatingLevel")) seatLevel("driverHeatingLevel")
            else if (preferences.getBoolean("seatHeating", climate.optBoolean("seatHeating"))) 1
            else seatLevel("driverHeatingLevel")
        val passengerHeat = seatLevel("passengerHeatingLevel")
        val defaultMode = AirflowMode.entries.firstOrNull {
            it.name.equals(climate.optString("airflowMode"), ignoreCase = true)
        } ?: throw IllegalArgumentException("Unsupported configured airflow mode")
        val mode = AirflowMode.entries.firstOrNull {
            it.name == preferences.getString("airflowMode", defaultMode.name)
        } ?: defaultMode
        val route = json.optJSONObject("route")?.let {
            RoutePreview(it.getString("destination"), it.getLong("durationMinutes"), it.getDouble("distanceKm"))
                .takeIf { value -> value.destination.isNotBlank() && value.durationMinutes > 0 && value.distanceKm > 0 }
        }
        val snapshot = VehicleSnapshot(
            vehicle = VehicleState(
                source = DataSource.Demo,
                batteryPercent = vehicle.optInt("batteryPercent", -1).takeIf { it in 0..100 },
                rangeKm = vehicle.optInt("rangeKm", -1).takeIf { it >= 0 },
                health = when (vehicle.optString("health")) {
                    "healthy" -> VehicleHealth.Healthy
                    "attention" -> VehicleHealth.Attention
                    else -> VehicleHealth.Unavailable
                },
                charging = vehicle.optionalBoolean("charging")?.let {
                    preferences.getBoolean("vehicleCharging", it)
                },
                ecoMode = vehicle.optionalBoolean("ecoMode"),
                parked = vehicle.optionalBoolean("parked"),
                outsideCelsius = vehicle.optDouble("outsideCelsius", Double.NaN).takeIf { it.isFinite() },
            ),
            climate = ClimateState(
                available = true, driverCelsius = driver, minCelsius = min, maxCelsius = max,
                stepCelsius = step, auto = saved("auto"), ac = saved("ac"), sync = sync,
                frontDefrost = saved("frontDefrost"), rearDefrost = saved("rearDefrost"),
                seatHeating = driverHeat > 0,
                cabinAir = if (climate.optString("cabinAir") == "clean") CabinAir.Clean else CabinAir.Unknown,
                passengerCelsius = if (sync) driver else passenger,
                fanSpeed = preferences.getInt("fanSpeed", climate.getInt("fanSpeed")).coerceIn(0, maxFan),
                maxFanSpeed = maxFan,
                airflowMode = mode,
                driverHeatingLevel = driverHeat,
                driverCoolingLevel = if (driverHeat > 0) 0 else seatLevel("driverCoolingLevel"),
                passengerHeatingLevel = passengerHeat,
                passengerCoolingLevel = if (passengerHeat > 0) 0 else seatLevel("passengerCoolingLevel"),
                maxSeatLevel = maxSeat,
                cabinAirQualityPercent = climate.optInt("cabinAirQualityPercent", -1).takeIf { it in 0..100 },
                cabinFilterHealthy = climate.optionalBoolean("cabinFilterHealthy"),
            ),
            route = route,
            controls = parseVehicleControls(json.optJSONObject("vehicleControls")),
        )
        // Older demo preferences have no connector flap or gear fields. Reconcile them once
        // when loading instead of showing charging with a closed flap or a selected drive gear.
        val coherent = if (snapshot.controls.available && snapshot.vehicle.charging == true) {
            snapshot.withCharging(snapshot.controls.gear == VehicleGear.Park)
        } else snapshot
        return coherent.copy(vehicle = coherent.vehicle.copy(
            parked = coherent.vehicle.parked?.let { if (coherent.controls.available) coherent.controls.gear == VehicleGear.Park else it },
        ))
    }

    private fun parseVehicleControls(json: JSONObject?): VehicleControlsState {
        if (json == null || !json.optBoolean("available", true)) return VehicleControlsState()
        demoAutoHeadlightsOn = json.optBoolean("demoAutoHeadlightsOn", false)
        val locked = preferences.getBoolean("vehicleLocked", json.optBoolean("locked", true))
        val configuredParts = json.optJSONArray("openParts")?.let { array ->
            (0 until array.length()).map { array.optString(it) }.toSet()
        }.orEmpty()
        val openParts = preferences.getStringSet("vehicleOpenParts", configuredParts).orEmpty().mapNotNull { name ->
            VehiclePart.entries.firstOrNull { it.name == name }
        }.toSet()
        val defaultMode = HeadlightMode.entries.firstOrNull {
            it.name.equals(json.optString("headlightMode"), ignoreCase = true)
        } ?: HeadlightMode.Auto
        val mode = HeadlightMode.entries.firstOrNull {
            it.name == preferences.getString("vehicleHeadlightMode", defaultMode.name)
        } ?: defaultMode
        val windowDefaults = json.optJSONObject("windowOpenPercent")
        val defaultSignal = TurnSignal.entries.firstOrNull { it.name.equals(json.optString("turnSignal"), true) } ?: TurnSignal.Off
        val signal = TurnSignal.entries.firstOrNull {
            it.name == preferences.getString("vehicleTurnSignal", defaultSignal.name)
        } ?: defaultSignal
        val defaultGear = VehicleGear.entries.firstOrNull { it.name.equals(json.optString("gear"), true) } ?: VehicleGear.Park
        val gear = VehicleGear.entries.firstOrNull {
            it.name == preferences.getString("vehicleGear", defaultGear.name)
        } ?: defaultGear
        return VehicleControlsState(
            available = true,
            locked = locked,
            openParts = openParts,
            parkingBrakeEngaged = preferences.getBoolean("vehicleParkingBrakeEngaged", json.optBoolean("parkingBrakeEngaged", true)),
            windowOpenPercent = VehicleWindow.entries.associateWith { window ->
                preferences.getInt("vehicleWindow${window.name}", windowDefaults?.optInt(window.name, 0) ?: 0)
                    .coerceIn(0, 100)
            },
            turnSignal = signal,
            gear = gear,
            brakeLightsOn = preferences.getBoolean("vehicleBrakeLightsOn", json.optBoolean("brakeLightsOn", false)),
            ambientLightsOn = preferences.getBoolean("vehicleAmbientLightsOn", json.optBoolean("ambientLightsOn", false)),
        ).withLocked(locked).withHeadlightMode(mode, demoAutoHeadlightsOn)
    }

    override suspend fun adjustDriverTemperature(steps: Int) = update { current ->
        current.withTemperatureAdjustment(ClimateZone.Driver, steps)
    }

    override suspend fun adjustPassengerTemperature(steps: Int) = update { current ->
        current.withTemperatureAdjustment(ClimateZone.Passenger, steps)
    }

    override suspend fun adjustFanSpeed(steps: Int) = update { it.withFanAdjustment(steps) }

    override suspend fun setAirflowMode(mode: AirflowMode) = update { it.withAirflowMode(mode) }

    override suspend fun cycleSeatLevel(zone: SeatZone, comfort: SeatComfort) =
        update { it.withCycledSeatLevel(zone, comfort) }

    override suspend fun toggle(control: ClimateControl) = update { it.withToggledControl(control) }

    override suspend fun toggleVehiclePart(part: VehiclePart) = updateSnapshot { it.withToggledVehiclePart(part) }

    override suspend fun setVehicleLocked(locked: Boolean) = updateControls { it.withLocked(locked) }

    override suspend fun setHeadlightMode(mode: HeadlightMode) =
        updateControls { it.withHeadlightMode(mode, demoAutoHeadlightsOn) }

    override suspend fun setWindowPosition(window: VehicleWindow, percent: Int) =
        updateControls { it.withWindowPosition(window, percent) }

    override suspend fun toggleParkingBrake() = updateControls { it.toggledParkingBrake() }

    override suspend fun setCharging(enabled: Boolean) = updateSnapshot { it.withCharging(enabled) }

    override suspend fun setTurnSignal(signal: TurnSignal) = updateControls { it.withTurnSignal(signal) }

    override suspend fun setVehicleGear(gear: VehicleGear) = updateSnapshot { it.withVehicleGear(gear) }

    override suspend fun setBrakeLights(enabled: Boolean) = updateControls { it.withBrakeLights(enabled) }

    override suspend fun setAmbientLights(enabled: Boolean) = updateControls { it.withAmbientLights(enabled) }

    private suspend fun update(transform: (ClimateState) -> ClimateState) = updateSnapshot { previous ->
        if (!previous.climate.available) previous else previous.copy(climate = transform(previous.climate))
    }

    private suspend fun updateControls(transform: (VehicleControlsState) -> VehicleControlsState) =
        updateSnapshot { previous -> previous.copy(controls = transform(previous.controls)) }

    private suspend fun updateSnapshot(transform: (VehicleSnapshot) -> VehicleSnapshot) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = mutableState.value
            val next = transform(previous)
            if (next == previous) return@withLock
            val editor = preferences.edit()
            if (next.climate != previous.climate) {
                val climate = next.climate
                editor.putBoolean("auto", climate.auto).putBoolean("ac", climate.ac).putBoolean("sync", climate.sync)
                    .putBoolean("frontDefrost", climate.frontDefrost).putBoolean("rearDefrost", climate.rearDefrost)
                    .putBoolean("seatHeating", climate.seatHeating)
                    .putInt("fanSpeed", climate.fanSpeed).putString("airflowMode", climate.airflowMode.name)
                    .putInt("driverHeatingLevel", climate.driverHeatingLevel)
                    .putInt("driverCoolingLevel", climate.driverCoolingLevel)
                    .putInt("passengerHeatingLevel", climate.passengerHeatingLevel)
                    .putInt("passengerCoolingLevel", climate.passengerCoolingLevel)
                climate.driverCelsius?.let { editor.putFloat("temperature", it) } ?: editor.remove("temperature")
                climate.passengerCelsius?.let { editor.putFloat("passengerTemperature", it) }
                    ?: editor.remove("passengerTemperature")
            }
            if (next.controls != previous.controls) {
                val controls = next.controls
                editor.putBoolean("vehicleLocked", controls.locked)
                    .putStringSet("vehicleOpenParts", controls.openParts.map { it.name }.toSet())
                    .putString("vehicleHeadlightMode", controls.headlightMode.name)
                    .putBoolean("vehicleParkingBrakeEngaged", controls.parkingBrakeEngaged)
                    .putString("vehicleTurnSignal", controls.turnSignal.name)
                    .putString("vehicleGear", controls.gear.name)
                    .putBoolean("vehicleBrakeLightsOn", controls.brakeLightsOn)
                    .putBoolean("vehicleAmbientLightsOn", controls.ambientLightsOn)
                VehicleWindow.entries.forEach { window ->
                    editor.putInt("vehicleWindow${window.name}", controls.windowOpenPercent[window] ?: 0)
                }
            }
            if (next.vehicle.charging != previous.vehicle.charging) {
                next.vehicle.charging?.let { editor.putBoolean("vehicleCharging", it) }
                    ?: editor.remove("vehicleCharging")
            }
            val saved = editor.commit()
            if (!saved) throw IOException("Unable to persist vehicle state")
            mutableState.value = next
        }
    }

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (isNull(key) || !has(key)) null else getBoolean(key)
}
