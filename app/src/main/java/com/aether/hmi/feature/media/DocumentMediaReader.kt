package com.aether.hmi.feature.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.graphics.scale
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.io.ByteArrayOutputStream
import java.io.IOException

/** Reads only documents selected through Android's picker and its persisted URI grants. */
internal object DocumentMediaReader {
    fun read(context: Context, uri: Uri): MediaItem? {
        if (uri.scheme != "content") return null
        return try {
            val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            val retriever = MediaMetadataRetriever()
            val metadata = try {
                retriever.setDataSource(context, uri)
                MediaMetadata.Builder()
                    .setTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() } ?: displayName ?: "Audio document")
                    .setArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST))
                    .setAlbumTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM))
                    .setDurationMs(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull())
                    .setArtworkData(thumbnail(retriever.embeddedPicture), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                    .build()
            } finally {
                retriever.release()
            }
            MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).setMediaMetadata(metadata).build()
        } catch (_: IOException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            // Corrupt or unsupported native metadata can throw IllegalArgumentException or other runtime errors.
            null
        }
    }

    /** Small covers fit safely in session metadata even when the source contains a full-size scan. */
    private fun thumbnail(bytes: ByteArray?): ByteArray? {
        if (bytes == null || bytes.size > 12 * 1024 * 1024) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            val options = BitmapFactory.Options().apply {
                inSampleSize = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / inSampleSize > 512) inSampleSize *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
            try {
                val scale = (256f / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
                val small = if (scale < 1f) bitmap.scale(
                    (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true) else bitmap
                try {
                    ByteArrayOutputStream().use { output ->
                        small.compress(Bitmap.CompressFormat.JPEG, 82, output)
                        output.toByteArray().takeIf { it.size <= 64 * 1024 }
                    }
                } finally {
                    if (small !== bitmap) small.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        } catch (_: RuntimeException) {
            // A broken cover must not hide otherwise playable audio.
            null
        }
    }
}
