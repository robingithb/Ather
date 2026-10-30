package com.aether.hmi.data

import android.content.Context
import androidx.core.content.edit
import com.aether.hmi.domain.AppSettings
import com.aether.hmi.domain.AppTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One application-owned repository publishes preferences immediately and persists them across launches. */
class SettingsRepository(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aether_settings", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = mutableState.asStateFlow()

    @Synchronized
    fun update(settings: AppSettings) {
        val next = settings.normalized()
        mutableState.value = next
        preferences.edit {
            putString("theme", next.theme.name)
            putFloat("brightness", next.brightness)
            putBoolean("auto_brightness", next.autoBrightness)
            putFloat("media_volume", next.mediaVolume)
            putFloat("navigation_volume", next.navigationVolume)
            putString("route_type", next.routeType)
            putBoolean("avoid_tolls", next.avoidTolls)
            putBoolean("avoid_ferries", next.avoidFerries)
            putBoolean("map_3d", next.map3D)
            putLong("ambient_color", next.ambientColor)
            if (next.accentColor == null) remove("accent_color") else putLong("accent_color", next.accentColor)
        }
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        // Preferences can contain legacy values of the wrong type. Read each independently so one
        // malformed field does not discard all of the user's valid preferences.
        fun string(key: String, fallback: String) = runCatching { preferences.getString(key, fallback) ?: fallback }.getOrDefault(fallback)
        fun float(key: String, fallback: Float) = runCatching { preferences.getFloat(key, fallback) }.getOrDefault(fallback)
        fun boolean(key: String, fallback: Boolean) = runCatching { preferences.getBoolean(key, fallback) }.getOrDefault(fallback)
        fun long(key: String, fallback: Long) = runCatching { preferences.getLong(key, fallback) }.getOrDefault(fallback)
        return AppSettings(
            theme = AppTheme.entries.firstOrNull { it.name == string("theme", defaults.theme.name) } ?: defaults.theme,
            brightness = float("brightness", defaults.brightness),
            autoBrightness = boolean("auto_brightness", defaults.autoBrightness),
            mediaVolume = float("media_volume", defaults.mediaVolume),
            navigationVolume = float("navigation_volume", defaults.navigationVolume),
            routeType = string("route_type", defaults.routeType),
            avoidTolls = boolean("avoid_tolls", defaults.avoidTolls),
            avoidFerries = boolean("avoid_ferries", defaults.avoidFerries),
            map3D = boolean("map_3d", defaults.map3D),
            ambientColor = long("ambient_color", defaults.ambientColor),
            accentColor = if (preferences.contains("accent_color")) long("accent_color", 0L) else null,
        ).normalized()
    }
}
