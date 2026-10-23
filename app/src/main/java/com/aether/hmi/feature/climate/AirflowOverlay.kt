package com.aether.hmi.feature.climate

import android.content.res.Resources
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.aether.hmi.R
import com.aether.hmi.domain.AirflowMode
import com.aether.hmi.ui.theme.AetherColors
import kotlin.math.max
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.xmlpull.v1.XmlPullParser

/**
 * A decorative airflow layer for the supplied cabin image, drawn with
 * ContentScale.Crop and BiasAlignment(0f, -0.36f): horizontal fraction 0.5 and
 * vertical fraction 0.32. This upward bias keeps the dashboard vents in view.
 * It adds no interaction or independent HVAC state.
 * Geometry lives in res/xml so vent positions can be revised with the artwork.
 */
@Composable
fun CabinAirflowOverlay(
    mode: AirflowMode,
    fanSpeed: Int,
    maxFanSpeed: Int,
    available: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current
    val artwork = remember(resources) { readAirflowArtwork(resources) }
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val running = available && fanSpeed > 0 && maxFanSpeed > 0
    val strength = if (running) (fanSpeed.toFloat() / maxFanSpeed).coerceIn(0f, 1f) else 0f
    val phase = remember { Animatable(0f) }
    val routeOpacity = remember { AirflowRoute.entries.associateWith { Animatable(0f) } }

    // Snapshot observation also responds immediately if the system animator
    // duration scale changes while this screen is open. Values are read by the
    // drawing phase only; animation never recomposes the surrounding dashboard.
    LaunchedEffect(running, strength, resumed, reducedMotion) {
        if (!running || !resumed || reducedMotion) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        val motionScale = coroutineContext[MotionDurationScale]
        snapshotFlow { motionScale?.scaleFactor ?: 1f }.collectLatest { scale ->
            phase.snapTo(0f)
            if (scale > 0f) {
                phase.animateTo(
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(
                            durationMillis = (1800 - 900 * strength).toInt(),
                            easing = LinearEasing,
                        ),
                    ),
                )
            }
        }
    }

    LaunchedEffect(mode, running, resumed, reducedMotion) {
        val animate = resumed && !reducedMotion &&
            (coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f) > 0f
        coroutineScope {
            routeOpacity.forEach { (route, opacity) ->
                launch {
                    val target = if (running && route.selectedBy(mode)) 1f else 0f
                    if (animate) opacity.animateTo(target, tween(240)) else opacity.snapTo(target)
                }
            }
        }
    }

    Box(
        modifier.clipToBounds().drawWithCache {
            val scale = max(size.width / artwork.width, size.height / artwork.height)
            val offset = Offset(
                (size.width - artwork.width * scale) / 2f,
                (size.height - artwork.height * scale) * 0.32f,
            )
            fun project(point: Offset) = Offset(
                offset.x + point.x * artwork.width * scale,
                offset.y + point.y * artwork.height * scale,
            )

            val streams = artwork.streams.map { stream ->
                val start = project(stream.start)
                val first = project(stream.control1)
                val second = project(stream.control2)
                val end = project(stream.end)
                val path = Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(first.x, first.y, second.x, second.y, end.x, end.y)
                }
                DrawnStream(
                    route = stream.route,
                    path = path,
                    width = (stream.width * artwork.width * scale).coerceAtLeast(0.7.dp.toPx()),
                )
            }
            val dash = 17.dp.toPx()
            val gap = 43.dp.toPx()
            val dashIntervals = floatArrayOf(dash, gap)
            onDrawBehind {
                val dashEffect = PathEffect.dashPathEffect(
                    intervals = dashIntervals,
                    phase = -phase.value * (dash + gap),
                )
                val intensity = 0.44f + strength * 0.56f
                streams.forEach { stream ->
                    val opacity = routeOpacity.getValue(stream.route).value * intensity
                    if (opacity > 0.005f) {
                        // A quiet continuous trail gives the moving, brighter
                        // streaks direction; layered strokes avoid a bitmap blur.
                        drawPath(
                            path = stream.path,
                            color = AetherColors.Accent.copy(alpha = opacity * 0.055f),
                            style = Stroke(width = stream.width * 5f, cap = StrokeCap.Round),
                        )
                        drawPath(
                            path = stream.path,
                            color = AetherColors.Accent.copy(alpha = opacity * 0.20f),
                            style = Stroke(
                                width = stream.width * 2.9f,
                                cap = StrokeCap.Round,
                                pathEffect = dashEffect,
                            ),
                        )
                        drawPath(
                            path = stream.path,
                            color = Color(0xFFA2F5FF).copy(alpha = opacity * 0.76f),
                            style = Stroke(
                                width = stream.width * 0.65f,
                                cap = StrokeCap.Round,
                                pathEffect = dashEffect,
                            ),
                        )
                    }
                }
            }
        },
    )
}

private enum class AirflowRoute {
    Face, Feet, Defrost;

    fun selectedBy(mode: AirflowMode): Boolean = when (this) {
        Face -> mode == AirflowMode.Face || mode == AirflowMode.FaceFeet
        Feet -> mode == AirflowMode.Feet || mode == AirflowMode.FaceFeet
        Defrost -> mode == AirflowMode.Defrost
    }
}

private data class AirflowArtwork(
    val width: Float,
    val height: Float,
    val streams: List<AirflowStream>,
)

private data class AirflowStream(
    val route: AirflowRoute,
    val start: Offset,
    val control1: Offset,
    val control2: Offset,
    val end: Offset,
    val width: Float,
)

private data class DrawnStream(val route: AirflowRoute, val path: Path, val width: Float)

private fun readAirflowArtwork(resources: Resources): AirflowArtwork {
    resources.getXml(R.xml.climate_airflow_paths).use { parser ->
        var width = 0f
        var height = 0f
        val streams = mutableListOf<AirflowStream>()
        fun number(name: String): Float = requireNotNull(parser.getAttributeValue(null, name)).toFloat()
        fun point(prefix: String) = Offset(number("${prefix}X"), number("${prefix}Y"))
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "airflow" -> {
                        width = number("sourceWidth")
                        height = number("sourceHeight")
                    }
                    "stream" -> streams += AirflowStream(
                        route = when (parser.getAttributeValue(null, "route")) {
                            "face" -> AirflowRoute.Face
                            "feet" -> AirflowRoute.Feet
                            "defrost" -> AirflowRoute.Defrost
                            else -> error("Unknown airflow route in climate_airflow_paths")
                        },
                        start = point("start"),
                        control1 = point("control1"),
                        control2 = point("control2"),
                        end = point("end"),
                        width = number("width"),
                    )
                }
            }
            parser.next()
        }
        require(width > 0f && height > 0f && streams.isNotEmpty())
        return AirflowArtwork(width, height, streams)
    }
}
