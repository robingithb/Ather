package com.aether.hmi.feature.climate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.aether.hmi.domain.CabinAir
import com.aether.hmi.domain.ClimateState
import com.aether.hmi.ui.theme.AetherColors
import com.aether.hmi.ui.theme.AetherTheme

@Preview(name = "Climate / landscape", widthDp = 960, heightDp = 540)
@Preview(name = "Climate / compact landscape", widthDp = 640, heightDp = 320)
@Preview(name = "Climate / phone landscape", widthDp = 780, heightDp = 360)
@Preview(name = "Climate / head unit", widthDp = 1280, heightDp = 720)
@Preview(name = "Climate / landscape large text", widthDp = 960, heightDp = 540, fontScale = 1.5f)
@Preview(name = "Climate / smallest content large text", widthDp = 532, heightDp = 270, fontScale = 1.5f)
@Composable
private fun ClimateResponsivePreview() {
    AetherTheme {
        ClimateScreen(
            state = ClimateState(available = true, driverCelsius = 21f, passengerCelsius = 21.5f,
                fanSpeed = 3, auto = true, cabinAir = CabinAir.Clean, cabinAirQualityPercent = 92, cabinFilterHealthy = true),
            actions = ClimateActions({}, {}, {}, {}, {}, { _, _ -> }),
            modifier = Modifier.fillMaxSize().background(AetherColors.Background).padding(16.dp),
            reducedMotion = true,
        )
    }
}
