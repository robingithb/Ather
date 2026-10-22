package com.aether.hmi.data

import android.content.Context
import com.aether.hmi.domain.CabinAir
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.domain.DataSource
import com.aether.hmi.domain.RoutePreview
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

/** The AAOS adapter can implement the same contract without changing Home. */
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
        val defaultTemp = climate.getDouble("driverCelsius").toFloat().coerceIn(min, max)
        require(defaultTemp.isFinite())
        val savedTemp = preferences.getFloat("temperature", defaultTemp)
        val temperature = if (savedTemp.isFinite()) savedTemp.coerceIn(min, max) else defaultTemp
        fun saved(key: String) = preferences.getBoolean(key, climate.getBoolean(key))
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
                outsideCelsius = if (vehicle.isNull("outsideCelsius")) null else vehicle.getDouble("outsideCelsius"),
            ),
            climate = ClimateState(
                available = true, driverCelsius = temperature, minCelsius = min, maxCelsius = max,
                stepCelsius = step, auto = saved("auto"), ac = saved("ac"), sync = saved("sync"),
                frontDefrost = saved("frontDefrost"), rearDefrost = saved("rearDefrost"),
                seatHeating = saved("seatHeating"),
                cabinAir = if (climate.optString("cabinAir") == "clean") CabinAir.Clean else CabinAir.Unknown,
            ),
            route = route,
        )
    }

    override suspend fun adjustDriverTemperature(steps: Int) = update { current ->
        current.copy(driverCelsius = current.adjustedTemperature(steps))
    }

    override suspend fun toggle(control: ClimateControl) = update { current ->
        when (control) {
            ClimateControl.Auto -> current.copy(auto = !current.auto)
            ClimateControl.Ac -> current.copy(ac = !current.ac)
            ClimateControl.Sync -> current.copy(sync = !current.sync)
            ClimateControl.FrontDefrost -> current.copy(frontDefrost = !current.frontDefrost)
            ClimateControl.RearDefrost -> current.copy(rearDefrost = !current.rearDefrost)
            ClimateControl.SeatHeating -> current.copy(seatHeating = !current.seatHeating)
        }
    }

    private suspend fun update(transform: (ClimateState) -> ClimateState) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val previous = mutableState.value
            if (!previous.climate.available) return@withLock
            val next = transform(previous.climate)
            val saved = preferences.edit()
                .putFloat("temperature", requireNotNull(next.driverCelsius))
                .putBoolean("auto", next.auto).putBoolean("ac", next.ac).putBoolean("sync", next.sync)
                .putBoolean("frontDefrost", next.frontDefrost).putBoolean("rearDefrost", next.rearDefrost)
                .putBoolean("seatHeating", next.seatHeating).commit()
            if (!saved) throw IOException("Unable to persist climate state")
            mutableState.value = previous.copy(climate = next)
        }
    }

    private fun JSONObject.optionalBoolean(key: String): Boolean? =
        if (isNull(key) || !has(key)) null else getBoolean(key)
}
