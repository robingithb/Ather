package com.aether.hmi.feature.media

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.aether.hmi.service.AetherPlaybackService
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

enum class MediaSource { Documents, Demo }

data class QueueTrack(
    val id: String,
    val title: String?,
    val artist: String? = null,
    val album: String? = null,
    val artwork: ByteArray? = null,
    val artworkUri: Uri? = null,
    val durationMs: Long = 0,
    val source: MediaSource = MediaSource.Documents,
)

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
    val album: String? = null,
    val artworkUri: Uri? = null,
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val favoriteIds: Set<String> = emptySet(),
    val upcomingIndices: List<Int> = emptyList(),
    val importing: Boolean = false,
    val errorMessage: String? = null,
    val source: MediaSource? = null,
) {
    val isDemo: Boolean get() = source == MediaSource.Demo
}

/** All destinations connect to the same Media3 session; navigation never creates another player. */
class PlaybackViewModel(private val context: Application) : ViewModel() {
    private val mutableState = MutableStateFlow(PlaybackState())
    val state = mutableState.asStateFlow()
    private val preferences = context.getSharedPreferences("media_favorites", Context.MODE_PRIVATE)
    private val future = MediaController.Builder(context,
        SessionToken(context, ComponentName(context, AetherPlaybackService::class.java))).buildAsync()
    private var controller: MediaController? = null
    private var importError: String? = null
    private var importing = false
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refresh()
    }

    // Only subscribed progress widgets tick; the hero, queue and cards keep stable state.
    val position = flow {
        while (currentCoroutineContext().isActive) {
            emit(controller?.currentPosition?.coerceAtLeast(0) ?: 0)
            delay(if (controller?.isPlaying == true) 100L else 500L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), 0L)

    init {
        future.addListener({
            try {
                controller = future.get().also { it.addListener(listener) }
                refresh()
            } catch (_: ExecutionException) {
                mutableState.value = PlaybackState(error = true, errorMessage = "Audio service could not connect. Reopen the app to retry.")
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
        val favorites = preferences.getStringSet("ids", emptySet()).orEmpty().toSet()
        val errorMessage = importError ?: player.playerError?.let {
            "This audio could not be played. Retry it, choose another track, or choose the file again if its access changed."
        }
        mutableState.value = PlaybackState(
            connected = true,
            title = metadata.title?.toString()?.takeIf { it.isNotBlank() },
            artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() },
            album = metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            artwork = metadata.artworkData, artworkUri = metadata.artworkUri,
            hasMedia = player.mediaItemCount > 0, playing = player.isPlaying,
            buffering = player.playbackState == Player.STATE_BUFFERING,
            durationMs = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: metadata.durationMs ?: 0,
            canPrevious = player.hasPreviousMediaItem(), canNext = player.hasNextMediaItem(),
            canSeek = player.isCurrentMediaItemSeekable,
            favorite = id != null && favorites.contains(id), favoriteIds = favorites,
            error = errorMessage != null, errorMessage = errorMessage, importing = importing,
            currentIndex = player.currentMediaItemIndex.takeIf { player.mediaItemCount > 0 } ?: -1,
            shuffleEnabled = player.shuffleModeEnabled, repeatMode = player.repeatMode,
            source = id?.let { if (DemoMedia.isDemoId(it)) MediaSource.Demo else MediaSource.Documents },
            upcomingIndices = MediaQueuePolicy.upcomingIndices(player.currentMediaItemIndex, player.mediaItemCount) { index ->
                player.currentTimeline.getNextWindowIndex(index, player.repeatMode, player.shuffleModeEnabled)
            },
            queue = (0 until player.mediaItemCount).map { index ->
                player.getMediaItemAt(index).let { item ->
                    val track = item.mediaMetadata
                    QueueTrack(item.mediaId, track.title?.toString(), track.artist?.toString(),
                        track.albumTitle?.toString(), track.artworkData, track.artworkUri,
                        track.durationMs ?: 0, if (DemoMedia.isDemoId(item.mediaId)) MediaSource.Demo else MediaSource.Documents)
                }
            },
        )
    }

    fun playDocuments(uris: List<Uri>) = importDocuments(uris, append = false)
    fun appendDocuments(uris: List<Uri>) = importDocuments(uris, append = true)

    private fun importDocuments(uris: List<Uri>, append: Boolean) {
        if (uris.isEmpty() || importing) return
        if (controller == null) {
            mutableState.value = mutableState.value.copy(error = true,
                errorMessage = "The audio service is unavailable. Reopen the app, then choose your files again.")
            return
        }
        viewModelScope.launch {
            importing = true
            importError = null
            refresh()
            val uniqueUris = uris.distinct()
            val items = withContext(Dispatchers.IO) {
                uniqueUris.mapNotNull { DocumentMediaReader.read(context, it) }
            }
            importing = false
            if (items.isEmpty()) {
                importError = "No playable audio was found. Choose supported audio files with access still available."
                refresh()
                return@launch
            }
            val player = controller
            if (player == null) {
                mutableState.value = mutableState.value.copy(connected = false, importing = false, error = true,
                    errorMessage = "The audio service disconnected. Reopen the app, then choose your files again.")
                return@launch
            }
            player.apply {
                if (append && mediaItemCount > 0) {
                    // Re-selecting a document does not create duplicate rows in the library.
                    val existing = (0 until mediaItemCount).map { getMediaItemAt(it).mediaId }.toSet()
                    addMediaItems(items.filterNot { it.mediaId in existing })
                } else {
                    setMediaItems(items)
                    prepare()
                    play()
                }
            }
            if (items.size < uniqueUris.size) importError = "Some selected files were unavailable or unsupported. The playable files were added."
            refresh()
        }
    }

    fun startDemo() {
        importError = null
        controller?.apply { setMediaItems(DemoMedia.items(context)); prepare(); play() }
        refresh()
    }

    /** A recommendation adds bundled originals without replacing selected documents. */
    fun playDemo(index: Int) {
        val player = controller ?: return
        val catalogue = DemoMedia.items(context)
        val existingIds = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
        val plan = DemoQueuePolicy.plan(existingIds, catalogue.map { it.mediaId }, index) ?: return
        importError = null
        if (existingIds.isEmpty()) {
            player.setMediaItems(catalogue, plan.playIndex, 0L)
        } else {
            val additions = plan.appendCatalogueIndices.map { catalogue[it] }
            if (additions.isNotEmpty()) player.addMediaItems(additions)
            player.seekToDefaultPosition(plan.playIndex)
        }
        if (player.playbackState == Player.STATE_IDLE || player.playerError != null) player.prepare()
        player.play()
        refresh()
    }

    fun togglePlayback() {
        importError = null
        controller?.apply {
            if (isPlaying || (playWhenReady && playerError == null)) pause() else {
                if (playbackState == Player.STATE_ENDED) seekToDefaultPosition()
                if (playbackState == Player.STATE_IDLE) prepare()
                play()
            }
        }
        refresh()
    }

    fun previous() {
        importError = null
        controller?.apply { seekToPreviousMediaItem(); if (playbackState == Player.STATE_IDLE) { prepare(); play() } }
        refresh()
    }

    fun next() {
        importError = null
        controller?.apply { seekToNextMediaItem(); if (playbackState == Player.STATE_IDLE) { prepare(); play() } }
        refresh()
    }

    fun seek(positionMs: Long) {
        controller?.takeIf { it.isCurrentMediaItemSeekable }?.seekTo(positionMs.coerceIn(0, state.value.durationMs))
    }

    fun playIndex(index: Int) {
        importError = null
        controller?.apply {
            if (index in 0 until mediaItemCount) {
                seekToDefaultPosition(index)
                if (playbackState == Player.STATE_IDLE) prepare()
                play()
            }
        }
        refresh()
    }

    fun toggleShuffle() { controller?.apply { shuffleModeEnabled = !shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.apply {
            repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun retry() {
        importError = null
        controller?.apply { if (mediaItemCount > 0) { prepare(); play() } }
        refresh()
    }

    fun dismissError() { importError = null; refresh() }

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
