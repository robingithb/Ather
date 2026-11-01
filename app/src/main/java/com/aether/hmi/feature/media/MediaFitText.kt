package com.aether.hmi.feature.media

import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.em
import com.aether.hmi.ui.theme.AetherColors

/** Fit within the allocated slot and center the text vertically against neighboring icons. */
@Composable
internal fun MediaFitText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = AetherColors.TextPrimary,
    maxFontSize: Float = 14f,
    minFontSize: Float = 8f,
    maxLines: Int = 1,
    fontWeight: FontWeight = FontWeight.Normal,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(text, modifier = modifier.wrapContentHeight(Alignment.CenterVertically), color = color, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, textAlign = textAlign,
        style = TextStyle(fontSize = maxFontSize.sp, lineHeight = 1.1.em, fontWeight = fontWeight),
        autoSize = TextAutoSize.StepBased(minFontSize.coerceAtMost(maxFontSize).sp, maxFontSize.sp, .5.sp))
}
