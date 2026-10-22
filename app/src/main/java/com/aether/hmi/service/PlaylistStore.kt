package com.aether.hmi.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class SavedPlaylist(val items: List<MediaItem> = emptyList(), val index: Int = 0, val positionMs: Long = 0)

internal class PlaylistStore(context: Context) {
    private val preferences = context.getSharedPreferences("media_queue", Context.MODE_PRIVATE)

    fun save(player: Player) {
        val items = JSONArray()
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri ?: continue
            // Only user-granted document URIs are persisted. No remote samples or fixed tracks.
            if (uri.scheme != "content") continue
            items.put(JSONObject().put("uri", uri.toString())
                .put("title", item.mediaMetadata.title?.toString().orEmpty())
                .put("artist", item.mediaMetadata.artist?.toString().orEmpty()))
        }
        preferences.edit().putString("items", items.toString())
            .putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
            .putLong("position", player.currentPosition.coerceAtLeast(0)).apply()
    }

    fun restore(): SavedPlaylist = try {
        val items = JSONArray(preferences.getString("items", "[]"))
        SavedPlaylist(
            items = (0 until items.length()).mapNotNull { index ->
                val value = items.getJSONObject(index)
                val uri = android.net.Uri.parse(value.getString("uri"))
                if (uri.scheme != "content") null else MediaItem.Builder().setUri(uri).setMediaId(uri.toString())
                    .setMediaMetadata(MediaMetadata.Builder().setTitle(value.optString("title"))
                        .setArtist(value.optString("artist")).build()).build()
            },
            index = preferences.getInt("index", 0),
            positionMs = preferences.getLong("position", 0).coerceAtLeast(0),
        )
    } catch (_: JSONException) {
        SavedPlaylist()
    }
}
