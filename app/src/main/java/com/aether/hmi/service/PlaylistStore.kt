package com.aether.hmi.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.aether.hmi.feature.media.DemoMedia
import com.aether.hmi.feature.media.DocumentMediaReader
import com.aether.hmi.feature.media.MediaQueuePolicy
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

internal data class SavedPlaylist(
    val items: List<MediaItem> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
)

internal class PlaylistStore(private val context: Context) {
    private val preferences = context.getSharedPreferences("media_queue", Context.MODE_PRIVATE)

    fun save(player: Player) {
        val items = JSONArray()
        val retainedIndices = mutableListOf<Int>()
        for (index in 0 until player.mediaItemCount) {
            val item = player.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri ?: continue
            // Document grants and our own bundled originals are the only restorable sources.
            if (uri.scheme != "content" && !DemoMedia.isDemoUri(uri)) continue
            retainedIndices += index
            items.put(JSONObject().put("uri", uri.toString()))
        }
        val position = MediaQueuePolicy.restorePosition(player.currentMediaItemIndex,
            player.currentPosition, retainedIndices)
        preferences.edit().putString("items", items.toString())
            .putInt("index", position.index).putLong("position", position.positionMs)
            .putBoolean("shuffle", player.shuffleModeEnabled).putInt("repeat", player.repeatMode).apply()
    }

    /** Run on IO: providers may be disconnected, grants revoked, or metadata changed since the last session. */
    fun restore(): SavedPlaylist = try {
        val entries = JSONArray(preferences.getString("items", "[]"))
        val retainedIndices = mutableListOf<Int>()
        val demo = DemoMedia.items(context).associateBy { it.localConfiguration?.uri }
        val items = (0 until entries.length()).mapNotNull { index ->
            try {
                val uri = Uri.parse(entries.getJSONObject(index).getString("uri"))
                val item = if (DemoMedia.isDemoUri(uri)) demo[uri] else DocumentMediaReader.read(context, uri)
                item?.also { retainedIndices += index }
            } catch (_: JSONException) {
                null
            }
        }
        val position = MediaQueuePolicy.restorePosition(preferences.getInt("index", 0),
            preferences.getLong("position", 0), retainedIndices)
        SavedPlaylist(items, position.index, position.positionMs,
            preferences.getBoolean("shuffle", false), preferences.getInt("repeat", Player.REPEAT_MODE_OFF)
                .takeIf { it in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL } ?: Player.REPEAT_MODE_OFF)
    } catch (_: JSONException) {
        SavedPlaylist()
    }
}
