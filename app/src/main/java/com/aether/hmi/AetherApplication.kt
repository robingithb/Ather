package com.aether.hmi

import android.app.Application
import android.content.Context
import com.aether.hmi.data.ConnectivityRepository
import com.aether.hmi.data.MockVehicleRepository
import com.aether.hmi.data.SystemTimeRepository
import com.aether.hmi.domain.VehicleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AetherApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val vehicle: VehicleRepository = MockVehicleRepository(context, applicationScope)
    val time = SystemTimeRepository(context)
    val connectivity = ConnectivityRepository(context)
}
