package com.aether.hmi.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.aether.hmi.MainActivity
import com.aether.hmi.AetherApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One player and session in the app's default process, shared by every destination. */
class AetherPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var restoring = true
    private val settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        // App playback gain survives navigation, reconnection and process recreation.
        val preferences = (application as AetherApplication).container.settings
        player.volume = preferences.state.value.mediaVolume
        settingsScope.launch {
            preferences.state.map { it.mediaVolume }.distinctUntilChanged().collect { player.volume = it }
        }
        val activity = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        session = MediaSession.Builder(this, player)
            .setSessionActivity(activity)
            .setCallback(object : MediaSession.Callback {
                @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
                    return if (controller.isTrusted || controller.packageName == packageName) {
                        super.onConnect(session, controller)
                    } else MediaSession.ConnectionResult.reject()
                }
            }).build()
        val store = PlaylistStore(this)
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (!restoring && events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED)) store.save(player)
            }
        })
        settingsScope.launch {
            val saved = withContext(Dispatchers.IO) { store.restore() }
            // A user may start a demo or select a document while restoration is reading a provider.
            if (player.mediaItemCount == 0 && saved.items.isNotEmpty()) {
                player.shuffleModeEnabled = saved.shuffleEnabled
                player.repeatMode = saved.repeatMode
                player.setMediaItems(saved.items, saved.index.coerceIn(saved.items.indices), saved.positionMs)
                player.prepare()
            }
            restoring = false
            store.save(player)
        }
        settingsScope.launch {
            while (isActive) {
                delay(5_000)
                // Save the playhead between transitions, without doing work while paused.
                if (!restoring && player.isPlaying) store.save(player)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0 || player.playbackState == Player.STATE_ENDED) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        settingsScope.cancel()
        session?.let {
            if (!restoring || it.player.mediaItemCount > 0) PlaylistStore(this).save(it.player)
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
