package com.aether.hmi.ui.shell

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aether.hmi.R
import com.aether.hmi.ui.navigation.AetherTab
import com.aether.hmi.ui.theme.AetherColors
import java.time.ZonedDateTime

@Composable
internal fun AetherNavigationRail(
    selectedTab: AetherTab,
    onTabSelected: (AetherTab) -> Unit,
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(),
) {
    val fontScale = LocalDensity.current.fontScale
    val railWidth = (92f + (fontScale - 1f).coerceAtLeast(0f) * 40f).coerceAtMost(132f).dp
    val navigationLabel = stringResource(R.string.navigation_label)

    BoxWithConstraints(
        modifier = modifier
            .width(railWidth)
            .fillMaxHeight()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF01070B), AetherColors.Rail, Color(0xFF000B12)),
                ),
            )
            .border(0.8.dp, AetherColors.RailBorder, RoundedCornerShape(22.dp)),
    ) {
        val compact = maxHeight < 380.dp
        val labelSize = if (compact) 13.sp else 15.sp
        val iconSize = if (compact) 24.dp else 28.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 5.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RailClock(
                now = now,
                modifier = Modifier.padding(bottom = if (compact) 8.dp else 16.dp),
                compact = compact,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .selectableGroup()
                    .semantics { contentDescription = navigationLabel },
                verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 6.dp),
            ) {
                AetherTab.entries.forEach { tab ->
                    val selected = selectedTab == tab
                    val tint = if (selected) AetherColors.Accent else AetherColors.TextSecondary
                    val label = stringResource(tab.labelRes)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(if (selected) Modifier.selectedTabSurface() else Modifier)
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = { onTabSelected(tab) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(if (compact) 3.dp else 5.dp),
                        ) {
                            Icon(
                                painter = painterResource(tab.iconRes),
                                contentDescription = null,
                                tint = tint,
                                modifier = Modifier.size(iconSize),
                            )
                            Text(
                                text = label,
                                color = tint,
                                fontSize = labelSize,
                                lineHeight = if (compact) 16.sp else 19.sp,
                                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            // A small breathing space echoes the reference without consuming phone height.
            Box(Modifier.weight(if (compact) 0.035f else 0.08f))
        }
    }
}

private fun Modifier.selectedTabSurface(): Modifier =
    background(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF003047), Color(0xFF001B2B), Color(0xFF00121F)),
        ),
    )
        .border(0.8.dp, AetherColors.Accent.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
        .drawWithCache {
            val glow = Brush.radialGradient(
                colors = listOf(AetherColors.Accent.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.42f, size.height * 0.42f),
                radius = size.width * 0.65f,
            )
            val barWidth = 2.5.dp.toPx()
            onDrawBehind {
                drawRect(glow)
                drawRoundRect(
                    color = AetherColors.Accent,
                    topLeft = Offset(0f, 9.dp.toPx()),
                    size = Size(barWidth, (size.height - 18.dp.toPx()).coerceAtLeast(0f)),
                    cornerRadius = CornerRadius(barWidth),
                )
            }
        }

@Composable
private fun RailClock(now: ZonedDateTime, modifier: Modifier = Modifier, compact: Boolean) {
    val context = LocalContext.current
    Text(
        text = DateFormat.format(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm", now.toInstant().toEpochMilli()).toString(),
        color = AetherColors.TextPrimary,
        fontSize = if (compact) 17.sp else 19.sp,
        fontWeight = FontWeight.Normal,
        maxLines = 1,
        modifier = modifier,
    )
}
