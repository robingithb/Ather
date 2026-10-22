package com.aether.hmi.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.aether.hmi.R

enum class AetherTab(
    @param:StringRes val labelRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    Home(R.string.tab_home, R.drawable.ic_home),
    Vehicle(R.string.tab_vehicle, R.drawable.ic_vehicle),
    Climate(R.string.tab_climate, R.drawable.ic_climate),
    Media(R.string.tab_media, R.drawable.ic_media),
    Settings(R.string.tab_settings, R.drawable.ic_settings),
}
