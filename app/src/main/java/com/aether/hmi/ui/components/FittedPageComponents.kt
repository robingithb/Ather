package com.aether.hmi.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.aether.hmi.ui.theme.AetherColors

/** Native text fitting inside a finite page slot; the interface itself is never canvas-scaled. */
@Composable
fun PageText(text: String, modifier: Modifier = Modifier, color: Color = AetherColors.TextPrimary,
    maxFontSize: Float = 18f, minFontSize: Float = 11f, maxLines: Int = 1,
    fontWeight: FontWeight = FontWeight.Normal, textAlign: TextAlign = TextAlign.Start) {
    Box(modifier, contentAlignment = Alignment.CenterStart) {
        Text(text, Modifier.fillMaxWidth(), color = color, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
            textAlign = textAlign, style = TextStyle(fontSize = maxFontSize.sp, lineHeight = 1.12.em, fontWeight = fontWeight),
            autoSize = TextAutoSize.StepBased(minFontSize.coerceAtMost(maxFontSize).sp, maxFontSize.sp, .5.sp))
    }
}

/** A finite page action: the parent owns its height, so no hidden minimum can overflow a row. */
@Composable
fun PageAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    enabled: Boolean = true, selected: Boolean = false, glyph: Glyph? = null) {
    Row(modifier.clip(RoundedCornerShape(10.dp))
        .background(if (selected) AetherColors.AccentSoft else AetherColors.Panel)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (glyph != null) AetherGlyph(glyph, size = 20.dp, color = if (enabled) AetherColors.Accent else AetherColors.Muted)
        PageText(label, Modifier.weight(1f).fillMaxHeight(),
            color = if (!enabled) AetherColors.Muted else if (selected) AetherColors.Accent else AetherColors.TextPrimary,
            maxFontSize = 18f, minFontSize = 11f, maxLines = 2, textAlign = TextAlign.Center)
    }
}
