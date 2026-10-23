package com.aether.hmi.data

import android.content.Context
import com.aether.hmi.domain.AirflowMode
import com.aether.hmi.domain.CabinAir
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.domain.ClimateZone
import com.aether.hmi.domain.DataSource
import com.aether.hmi.domain.RoutePreview
import com.aether.hmi.domain.SeatComfort
import com.aether.hmi.domain.SeatZone
import com.aether.hmi.domain.VehicleHealth
import com.aether.hmi.domain.VehicleRepository
import com.aether.hmi.domain.VehicleSnapshot
import com.aether.hmi.domain.VehicleState
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
        return VehicleSnapshot(
            vehicle = VehicleState(
                source = DataSource.Demo,
                batteryPercent = vehicle.optInt("batteryPercent", -1).takeIf { it in 0..100 },
                rangeKm = vehicle.optInt("rangeKm", -1).takeIf { it >= 0 },
                health = when (vehicle.optString("health")) {
                    "healthy" -> VehicleHealth.Healthy
                    "attention" -> VehicleHealth.Attention
                    else -> VehicleHealth.Unavailable
                },
                charging = vehicle.optionalBoolean("charging"),
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
        )
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

    private suspend fun update(transform: (ClimateState) -> ClimateState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = mutableState.value
            if (!previous.climate.available) return@withLock
            val next = transform(previous.climate)
            if (next == previous.climate) return@withLock
            val editor = preferences.edit()
                .putBoolean("auto", next.auto).putBoolean("ac", next.ac).putBoolean("sync", next.sync)
                .putBoolean("frontDefrost", next.frontDefrost).putBoolean("rearDefrost", next.rearDefrost)
                .putBoolean("seatHeating", next.seatHeating)
                .putInt("fanSpeed", next.fanSpeed).putString("airflowMode", next.airflowMode.name)
                .putInt("driverHeatingLevel", next.driverHeatingLevel)
                .putInt("driverCoolingLevel", next.driverCoolingLevel)
                .putInt("passengerHeatingLevel", next.passengerHeatingLevel)
                .putInt("passengerCoolingLevel", next.passengerCoolingLevel)
            next.driverCelsius?.let { editor.putFloat("temperature", it) } ?: editor.remove("temperature")
            next.passengerCelsius?.let { editor.putFloat("passengerTemperature", it) }
                ?: editor.remove("passengerTemperature")
            val saved = editor.commit()
            if (!saved) throw IOException("Unable to persist climate state")
            mutableState.value = previous.copy(climate = next)
        }
    }

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (isNull(key) || !has(key)) null else getBoolean(key)
}
