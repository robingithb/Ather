package com.aether.hmi.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aether.hmi.ui.theme.AetherColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardSurface(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier.clip(shape)
            .background(Brush.linearGradient(listOf(AetherColors.Panel.copy(alpha = 0.92f), Color(0xFF000D17))))
            .border(0.7.dp, AetherColors.Border, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun GlyphButton(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = AetherColors.TextPrimary,
) {
    IconButton(onClick = onClick, enabled = enabled,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = label }) {
        AetherGlyph(glyph, color = if (enabled) color else AetherColors.Muted.copy(alpha = 0.4f))
    }
}

@Composable
fun MediaArtwork(data: ByteArray?, modifier: Modifier = Modifier, @androidx.annotation.DrawableRes fallback: Int? = null) {
    // ID3 artwork is decoded off the main thread and sampled to a small thumbnail.
    val bitmap by produceState<ImageBitmap?>(null, data) {
        value = withContext(Dispatchers.IO) {
            if (data == null) return@withContext null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null
            var sample = 1
            while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
            BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        }
    }
    Box(modifier.clip(RoundedCornerShape(7.dp))
        .background(Brush.linearGradient(listOf(Color(0xFF124864), AetherColors.Panel)))
        .border(0.6.dp, AetherColors.Border, RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else if (fallback != null) Image(painterResource(fallback), null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize())
        else AetherGlyph(Glyph.Music, color = AetherColors.TextSecondary)
    }
}
