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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** One player and session in the app's default process, shared by every destination. */
class AetherPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
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
        val saved = store.restore()
        if (saved.items.isNotEmpty()) {
            player.setMediaItems(saved.items, saved.index.coerceIn(saved.items.indices), saved.positionMs)
            player.prepare()
        }
        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(Player.EVENT_TIMELINE_CHANGED, Player.EVENT_MEDIA_METADATA_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_POSITION_DISCONTINUITY)) store.save(player)
            }
        })
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
            PlaylistStore(this).save(it.player)
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
