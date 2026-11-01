package com.aether.hmi.feature.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode

internal enum class MediaOutputKind { Bluetooth, Usb, Wired, BuiltIn, Other }

internal data class MediaOutput(val kind: MediaOutputKind, val name: String)

internal data class MediaOutputs(val devices: List<MediaOutput> = emptyList(), val available: Boolean = true)

/** Connected output capabilities, not a claim about the system's currently selected route. */
@Composable
internal fun rememberMediaOutputs(): MediaOutputs {
    if (LocalInspectionMode.current) return MediaOutputs(available = false)
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    var outputs by remember(manager) { mutableStateOf(readMediaOutputs(manager)) }
    DisposableEffect(manager) {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                outputs = readMediaOutputs(manager)
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                outputs = readMediaOutputs(manager)
            }
        }
        val registered = try {
            manager?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
            manager != null
        } catch (_: SecurityException) {
            outputs = MediaOutputs(available = false)
            false
        } catch (_: IllegalStateException) {
            outputs = MediaOutputs(available = false)
            false
        }
        outputs = readMediaOutputs(manager)
        onDispose {
            if (registered) {
                try {
                    manager?.unregisterAudioDeviceCallback(callback)
                } catch (_: SecurityException) {
                    // An output may disappear while the screen is being disposed.
                } catch (_: IllegalStateException) {
                    // The audio service can restart independently of the activity.
                }
            }
        }
    }
    return outputs
}

private fun readMediaOutputs(manager: AudioManager?): MediaOutputs {
    if (manager == null) return MediaOutputs(available = false)
    return try {
        MediaOutputs(manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { device ->
            val kind = when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_BLE_BROADCAST -> MediaOutputKind.Bluetooth
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_USB_HEADSET -> MediaOutputKind.Usb
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL,
                AudioDeviceInfo.TYPE_AUX_LINE -> MediaOutputKind.Wired
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> MediaOutputKind.BuiltIn
                else -> MediaOutputKind.Other
            }
            MediaOutput(kind, device.productName?.toString().orEmpty())
        }.distinct())
    } catch (_: SecurityException) {
        MediaOutputs(available = false)
    } catch (_: IllegalStateException) {
        MediaOutputs(available = false)
    }
}
