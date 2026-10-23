package com.aether.hmi.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aether.hmi.AppContainer
import com.aether.hmi.domain.AirflowMode
import com.aether.hmi.domain.ClimateControl
import com.aether.hmi.domain.ConnectivityState
import com.aether.hmi.domain.SeatComfort
import com.aether.hmi.domain.SeatZone
import com.aether.hmi.domain.VehicleSnapshot
import java.io.IOException
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardState(
    val snapshot: VehicleSnapshot = VehicleSnapshot(),
    val connectivity: ConnectivityState = ConnectivityState(),
    val now: ZonedDateTime = ZonedDateTime.now(),
)

enum class ClimateUpdate { Idle, Pending, Confirmed, Failed }

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    val state = combine(container.vehicle.state, container.connectivity.state, container.time.time) { snapshot, network, now ->
        DashboardState(snapshot, network, now)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), DashboardState())

    private val mutableUpdate = MutableStateFlow(ClimateUpdate.Idle)
    val climateUpdate = mutableUpdate.asStateFlow()
    private val pending = AtomicInteger()
    private val climateActions = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        // One consumer awaits each repository write before starting the next tap's command.
        viewModelScope.launch {
            for (action in climateActions) {
                try {
                    action()
                    if (pending.get() == 1) mutableUpdate.value = ClimateUpdate.Confirmed
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    mutableUpdate.value = ClimateUpdate.Failed
                } finally {
                    pending.decrementAndGet()
                }
            }
        }
    }

    fun adjustTemperature(steps: Int) = updateClimate { container.vehicle.adjustDriverTemperature(steps) }
    fun adjustPassengerTemperature(steps: Int) = updateClimate { container.vehicle.adjustPassengerTemperature(steps) }
    fun adjustFanSpeed(steps: Int) = updateClimate { container.vehicle.adjustFanSpeed(steps) }
    fun setAirflowMode(mode: AirflowMode) = updateClimate { container.vehicle.setAirflowMode(mode) }
    fun cycleSeatLevel(zone: SeatZone, comfort: SeatComfort) = updateClimate { container.vehicle.cycleSeatLevel(zone, comfort) }
    fun toggle(control: ClimateControl) = updateClimate { container.vehicle.toggle(control) }

    private fun updateClimate(action: suspend () -> Unit) {
        pending.incrementAndGet()
        mutableUpdate.value = ClimateUpdate.Pending
        if (climateActions.trySend(action).isFailure) {
            pending.decrementAndGet()
            mutableUpdate.value = ClimateUpdate.Failed
        }
    }

    override fun onCleared() {
        climateActions.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { DashboardViewModel(container) }
        }
    }
}
