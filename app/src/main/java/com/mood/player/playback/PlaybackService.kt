package com.mood.player.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import com.mood.player.R
import com.mood.player.ui.MainActivity

/**
 * Servicio en primer plano que mantiene la reproducción aunque la app esté minimizada.
 * Media3 publica automáticamente la notificación multimedia con play/pausa, anterior y siguiente.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.addListener(EndlessShuffle(player))

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .setCallback(SessionCallback())
            .build()

        // Icono de MOOD (orbe con play) en la barra de estado mientras suena.
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply { setSmallIcon(R.drawable.ic_notification) }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            // Si no se está reproduciendo nada, no tiene sentido seguir vivos.
            stopSelf()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    /**
     * El orden aleatorio de ExoPlayer se fija al cargar la lista, y la canción por la que se empieza puede caer
     * cerca del final: sonaban unas pocas y se acababa. Aquí el orden siempre empieza por la canción actual y,
     * al terminar la vuelta, se baraja de nuevo y sigue sonando: el aleatorio no se acaba nunca.
     */
    @OptIn(UnstableApi::class)
    private class EndlessShuffle(private val player: ExoPlayer) : Player.Listener {
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            if (shuffleModeEnabled) reshuffle(first = player.currentMediaItemIndex)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && player.shuffleModeEnabled) {
                reshuffle(first = player.currentMediaItemIndex)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState != Player.STATE_ENDED || !player.shuffleModeEnabled || player.mediaItemCount == 0) return
            // Nueva vuelta sin repetir de entrada la canción que acaba de sonar.
            val last = player.currentMediaItemIndex
            val first = (0 until player.mediaItemCount).filter { it != last }.randomOrNull() ?: last
            reshuffle(first)
            player.seekTo(first, 0L)
            player.play()
        }

        private fun reshuffle(first: Int) {
            val count = player.mediaItemCount
            if (count == 0 || first !in 0 until count) return
            val order = listOf(first) + (0 until count).filter { it != first }.shuffled()
            player.setShuffleOrder(ShuffleOrder.DefaultShuffleOrder(order.toIntArray(), System.nanoTime()))
        }
    }

    private class SessionCallback : MediaSession.Callback {
        // El mediaId de cada pista es su content:// URI; lo usamos para reconstruir el item.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(
                mediaItems.map { it.buildUpon().setUri(it.mediaId).build() }.toMutableList()
            )
    }
}
