package com.aether.hmi.feature.vehicle

import android.app.Application
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import com.aether.hmi.feature.vehicle.renderer.FilamentVehicleRenderer
import com.aether.hmi.feature.vehicle.renderer.VehicleModelStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Retains the expensive GLB/native scene across navigation and Activity recreation. */
internal class VehicleSceneViewModel(application: Application) : AndroidViewModel(application) {
    private val mutableStatus = MutableStateFlow<VehicleModelStatus>(VehicleModelStatus.Loading())
    val status = mutableStatus.asStateFlow()
    private val mutableGeneration = MutableStateFlow(0L)
    val generation = mutableGeneration.asStateFlow()
    private val mutableCameraPose = MutableStateFlow<VehicleCameraPose?>(null)
    val cameraPose = mutableCameraPose.asStateFlow()
    private var scene: FilamentVehicleRenderer? = null

    fun obtainScene(): FilamentVehicleRenderer {
        checkMainThread()
        scene?.let { return it }
        lateinit var created: FilamentVehicleRenderer
        created = FilamentVehicleRenderer(getApplication<Application>().applicationContext,
            onStatus = { next -> if (scene === created) mutableStatus.value = next },
            onCameraPose = { next -> if (scene === created) mutableCameraPose.value = next })
        scene = created
        created.initialize()
        return created
    }

    fun retry() {
        checkMainThread()
        val old = scene
        scene = null
        old?.dispose()
        mutableStatus.value = VehicleModelStatus.Loading()
        mutableCameraPose.value = null
        mutableGeneration.value++
    }

    override fun onCleared() {
        checkMainThread()
        val old = scene
        scene = null
        old?.dispose()
        super.onCleared()
    }

    private fun checkMainThread() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Vehicle scene owner must run on the main thread" }
    }
}
