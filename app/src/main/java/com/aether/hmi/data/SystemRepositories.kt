package com.aether.hmi.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.ContextCompat
import com.aether.hmi.domain.ConnectivityState
import com.aether.hmi.domain.NetworkTransport
import java.time.ZonedDateTime
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

class SystemTimeRepository(private val context: Context) {
    /** Re-read the default zone each time: a cached Clock would retain an old timezone. */
    val time: Flow<ZonedDateTime> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                trySend(ZonedDateTime.now())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_LOCALE_CHANGED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(ZonedDateTime.now())
        awaitClose { context.unregisterReceiver(receiver) }
    }.conflate()
}

class ConnectivityRepository(context: Context) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)

    val state: Flow<ConnectivityState> = callbackFlow {
        fun publish() {
            val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
            val transport = when {
                capabilities == null -> NetworkTransport.None
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransport.Wifi
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransport.Mobile
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransport.Ethernet
                else -> NetworkTransport.Other
            }
            trySend(ConnectivityState(
                online = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
                transport = transport,
            ))
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = publish()
            override fun onLost(network: Network) = publish()
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = publish()
        }
        if (manager != null) manager.registerDefaultNetworkCallback(callback)
        publish()
        awaitClose { if (manager != null) manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
