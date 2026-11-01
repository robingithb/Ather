package com.aether.hmi.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aether.hmi.ui.components.PageText
import com.aether.hmi.R
import com.aether.hmi.ui.navigation.AetherTab
import com.aether.hmi.ui.theme.AetherColors
import java.time.ZonedDateTime

/** Stable side navigation for landscape infotainment demo displays. */
@Composable
internal fun AetherNavigationRail(
    selectedTab: AetherTab,
    onTabSelected: (AetherTab) -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") now: ZonedDateTime = ZonedDateTime.now(),
) {
    val navigationLabel = stringResource(R.string.navigation_label)
    BoxWithConstraints(modifier.fillMaxHeight()) {
        val roomy = maxHeight >= 440.dp
        val railWidth = if (roomy) 88.dp else 72.dp
        val gap = if (roomy) 8.dp else 4.dp
        Column(Modifier.width(railWidth).fillMaxHeight().clip(RoundedCornerShape(16.dp)).background(AetherColors.Rail)
            .padding(4.dp).selectableGroup().semantics { contentDescription = navigationLabel },
            verticalArrangement = Arrangement.spacedBy(gap)) {
            AetherTab.entries.forEach { tab ->
                NavigationItem(tab, selectedTab == tab, { onTabSelected(tab) },
                    Modifier.fillMaxWidth().weight(1f), roomy)
            }
        }
    }
}

@Composable
private fun NavigationItem(tab: AetherTab, selected: Boolean, onClick: () -> Unit, modifier: Modifier, roomy: Boolean) {
    val tint = if (selected) AetherColors.Accent else AetherColors.TextSecondary
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) AetherColors.AccentSoft else AetherColors.Rail)
        .selectable(selected, role = Role.Tab, onClick = onClick).padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
        Icon(painterResource(if (tab == AetherTab.Climate && selected) R.drawable.ic_climate_filled else tab.iconRes),
            null, Modifier.size(if (roomy) 28.dp else 21.dp), tint = tint)
        PageText(stringResource(tab.labelRes), Modifier.fillMaxWidth().weight(1f), color = tint,
            maxFontSize = if (roomy) 16f else 13f, minFontSize = 10f,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
