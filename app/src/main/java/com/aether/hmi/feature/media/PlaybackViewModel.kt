package com.aether.hmi.feature.media

import android.content.ComponentName
import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aether.hmi.service.AetherPlaybackService
import java.io.IOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QueueTrack(val id: String, val title: String?)
data class PlaybackState(
    val connected: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val artwork: ByteArray? = null,
    val hasMedia: Boolean = false,
    val playing: Boolean = false,
    val buffering: Boolean = false,
    val durationMs: Long = 0,
    val canPrevious: Boolean = false,
    val canNext: Boolean = false,
    val canSeek: Boolean = false,
    val favorite: Boolean = false,
    val error: Boolean = false,
    val queue: List<QueueTrack> = emptyList(),
)

class PlaybackViewModel(private val context: Application) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaybackState())
    val state = mutableState.asStateFlow()
    private val preferences = context.getSharedPreferences("media_favorites", Context.MODE_PRIVATE)
    private val future = MediaController.Builder(context,
        SessionToken(context, ComponentName(context, AetherPlaybackService::class.java))).buildAsync()
    private var controller: MediaController? = null
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    // Only the subscribed progress widgets tick; the hero and cards do not recompose each second.
    val position = flow {
        while (currentCoroutineContext().isActive) {
            emit(controller?.currentPosition?.coerceAtLeast(0) ?: 0)
            delay(1_000)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), 0L)

    init {
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                refresh()
            } catch (_: ExecutionException) {
                mutableState.value = PlaybackState(error = true)
            } catch (_: CancellationException) {
                // The owning ViewModel was cleared before the connection completed.
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun refresh() {
        val player = controller ?: return
        val metadata = player.mediaMetadata
        val id = player.currentMediaItem?.mediaId
        mutableState.value = PlaybackState(
            connected = true, title = metadata.title?.toString()?.takeIf { it.isNotBlank() },
            artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() }, artwork = metadata.artworkData,
            hasMedia = player.mediaItemCount > 0, playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0,
            canPrevious = player.hasPreviousMediaItem(), canNext = player.hasNextMediaItem(),
            canSeek = player.isCurrentMediaItemSeekable,
            favorite = id != null && preferences.getStringSet("ids", emptySet()).orEmpty().contains(id),
            error = player.playerError != null,
            queue = (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index).let { QueueTrack(it.mediaId, it.mediaMetadata.title?.toString()) }
            },
        )
    }

    fun playDocuments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val items = withContext(Dispatchers.IO) {
                uris.filter { it.scheme == "content" }.mapNotNull { uri ->
                    try {
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                            ?.use { if (it.moveToFirst()) it.getString(0) else null }
                        val retriever = MediaMetadataRetriever()
                        val metadata = try {
                            retriever.setDataSource(context, uri)
                            MediaMetadata.Builder()
                                .setTitle(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: name)
                                .setArtist(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)).build()
                        } finally { retriever.release() }
                        MediaItem.Builder().setUri(uri).setMediaId(uri.toString()).setMediaMetadata(metadata).build()
                    } catch (_: IOException) { null
                    } catch (_: SecurityException) { null
                    } catch (_: IllegalArgumentException) { null
                    } catch (_: RuntimeException) {
                        // Native metadata parsing can reject corrupt media this way.
                        // Rejected selections produce the visible playback error below.
                        null
                    }
                }
            }
            if (items.isEmpty()) {
                mutableState.value = mutableState.value.copy(error = true)
                return@launch
            }
            controller?.apply { setMediaItems(items); prepare(); play() }
        }
    }

    fun togglePlayback() {
        controller?.apply {
            if (isPlaying || playWhenReady) pause() else {
                if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
                if (playbackState == Player.STATE_IDLE) prepare()
                play()
            }
        }
    }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun next() { controller?.seekToNextMediaItem() }
    fun seek(positionMs: Long) { controller?.seekTo(positionMs.coerceIn(0, state.value.durationMs)) }
    fun playIndex(index: Int) {
        controller?.apply { if (index in 0 until mediaItemCount) { seekToDefaultPosition(index); play() } }
    }
    fun toggleFavorite() {
        val id = controller?.currentMediaItem?.mediaId ?: return
        val favorites = preferences.getStringSet("ids", emptySet()).orEmpty().toMutableSet()
        if (!favorites.add(id)) favorites.remove(id)
        preferences.edit().putStringSet("ids", favorites).apply()
        refresh()
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        MediaController.releaseFuture(future)
        controller = null
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlaybackViewModel(context.applicationContext as Application) }
        }
    }
}
