package com.aether.hmi.feature.media

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.aether.hmi.R
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import java.util.Collections

/** Original, locally synthesized audio. These are playable demos, not a simulated catalogue. */
internal object DemoMedia {
    private data class Original(val key: String, val title: String, val artwork: Int)

    // Keep the first three IDs and asset paths stable for previously saved queues.
    private val tracks = listOf(
        Original("mountain_drive", "Mountain Drive (Demo)", R.drawable.home_album_placeholder),
        Original("coastal_road", "Coastal Road (Demo)", R.drawable.navigation_road),
        Original("evening_lights", "Evening Lights (Demo)", R.drawable.theme_sport),
        Original("ocean_air", "Ocean Air (Demo)", R.drawable.theme_arctic),
        Original("midnight_city", "Midnight City (Demo)", R.drawable.theme_minimal),
        Original("golden_hour", "Golden Hour (Demo)", R.drawable.home_album_placeholder),
    )

    fun items(context: Context): List<MediaItem> = tracks.mapIndexed { index, track ->
        MediaItem.Builder().setMediaId("aether-demo:${track.key}")
            .setUri("asset:///media/${track.key}.wav")
            .setMediaMetadata(MediaMetadata.Builder().setTitle(track.title)
                .setArtist("Aether Originals · bundled demo").setAlbumTitle("Aether Demo Sessions")
                .setDurationMs(24_000L).setTrackNumber(index + 1)
                .setArtworkUri("android.resource://${context.packageName}/${track.artwork}".toUri())
                .build()).build()
    }

    /** Recommendations share the metadata of the actual playable catalogue. */
    fun queueTracks(context: Context): List<QueueTrack> = Collections.unmodifiableList(items(context).map { item ->
        val metadata = item.mediaMetadata
        QueueTrack(id = item.mediaId, title = metadata.title?.toString(), artist = metadata.artist?.toString(),
            album = metadata.albumTitle?.toString(), artwork = metadata.artworkData,
            artworkUri = metadata.artworkUri, durationMs = metadata.durationMs ?: 0L, source = MediaSource.Demo)
    })

    fun isDemoId(id: String): Boolean = tracks.any { id == "aether-demo:${it.key}" }

    fun isDemoUri(uri: Uri): Boolean = tracks.any { uri.toString() == "asset:///media/${it.key}.wav" }
}
