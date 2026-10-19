package com.mood.player.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import android.os.SystemClock
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.mood.player.data.ArtworkLoader
import com.mood.player.data.MusicCollection
import com.mood.player.data.MusicRepository
import com.mood.player.data.Track
import com.mood.player.data.TrackTags
import com.mood.player.playback.PlaybackService
import com.mood.player.download.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import com.mood.player.data.GroupBy
import com.mood.player.data.LibraryGrouping
import com.mood.player.data.MoodPlaylist
import com.mood.player.data.MoodRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val hasMedia: Boolean = false,
    val mediaId: String? = null,
    val title: String = "",
    val artist: String? = null,
    val album: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    /** Momento (SystemClock.elapsedRealtime) en que se leyó [positionMs]; permite interpolar la posición. */
    val positionUpdatedAt: Long = 0,
    val durationMs: Long = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
)

@androidx.annotation.OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _collections = MutableStateFlow<List<MusicCollection>>(emptyList())
    val collections: StateFlow<List<MusicCollection>> = _collections.asStateFlow()

    /** La biblioteca agrupada por carpeta, artista, álbum y género; se recalcula al reescanear. */
    val groups: StateFlow<Map<GroupBy, List<MusicCollection>>> = _collections
        .map { folders -> GroupBy.entries.associateWith { LibraryGrouping.group(folders, it) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val moodStore = MoodRepository.get(application)

    /** Los MOODS con sus canciones resueltas; las que ya no existen en el dispositivo no se muestran. */
    val moodPlaylists: StateFlow<List<MoodPlaylist>> = combine(_collections, moodStore.moods) { folders, moods ->
        val byUri = folders.flatMap { it.tracks }.associateBy { it.uri.toString() }
        moods.map { mood ->
            MoodPlaylist(mood, MusicCollection("mood:${mood.id}", mood.name, mood.trackUris.mapNotNull(byUri::get)))
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _playerState = MutableStateFlow(PlayerUiState())
    val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

    private val _artwork = MutableStateFlow<ImageBitmap?>(null)
    val artwork: StateFlow<ImageBitmap?> = _artwork.asStateFlow()

    private val controllerFuture: ListenableFuture<MediaController>
    private var controller: MediaController? = null
    private var hasLoaded = false
    private var musicLoadJob: Job? = null
    private var artworkJob: Job? = null
    private var artworkMediaId: String? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = refreshState()
    }

    init {
        // Keep the library current even when the download screen is closed or the app is behind YouTube.
        viewModelScope.launch {
            DownloadStatus.state.map { it.completionVersion }.distinctUntilChanged().collect { version ->
                if (version > 0 && hasLoaded) loadMusic()
            }
        }
        val token = SessionToken(application, ComponentName(application, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(application, token).buildAsync()
        controllerFuture.addListener({
            val c = runCatching { controllerFuture.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(playerListener)
            refreshState()
        }, ContextCompat.getMainExecutor(application))

        // La posición no genera eventos: la consultamos periódicamente mientras suena.
        viewModelScope.launch {
            while (isActive) {
                controller?.takeIf { it.isPlaying }?.let { c ->
                    _playerState.update {
                        it.copy(positionMs = c.currentPosition.coerceAtLeast(0), positionUpdatedAt = SystemClock.elapsedRealtime())
                    }
                }
                delay(500)
            }
        }
    }

    fun loadMusicIfNeeded() {
        if (!hasLoaded) loadMusic()
    }

    fun loadMusic() {
        hasLoaded = true
        musicLoadJob?.cancel()
        musicLoadJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                _collections.value = repository.loadCollections()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Retain the current collections if a provider is temporarily unavailable.
            } finally {
                if (isActive) _isLoading.value = false
            }
        }
    }

    private val _playingFrom = MutableStateFlow<String?>(null)

    /** Lista (carpeta, artista, álbum, MOOD…) desde la que se empezó a reproducir. */
    val playingFrom: StateFlow<String?> = _playingFrom.asStateFlow()

    fun playCollection(collection: MusicCollection, startIndex: Int = 0, shuffle: Boolean? = null) {
        val c = controller ?: return
        if (collection.tracks.isEmpty()) return
        _playingFrom.value = collection.path
        shuffle?.let { c.shuffleModeEnabled = it }
        val index = if (shuffle == true) collection.tracks.indices.random() else startIndex
        c.setMediaItems(collection.tracks.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun playPause() {
        controller?.let { Util.handlePlayPauseButtonAction(it) }
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        controller?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        _playerState.update { it.copy(positionMs = positionMs, positionUpdatedAt = SystemClock.elapsedRealtime()) }
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    /** Sin repetición -> repetir colección -> repetir pista. */
    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Quita de la cola de reproducción las pistas cuyos ficheros se han borrado. */
    fun removeFromQueue(mediaIds: Set<String>) {
        val c = controller ?: return
        for (i in c.mediaItemCount - 1 downTo 0) {
            if (c.getMediaItemAt(i).mediaId in mediaIds) c.removeMediaItem(i)
        }
    }

    /**
     * Actualiza la cola cuando un fichero cambia de URI (solo ocurre en Android 10 o anterior).
     * La pista que suena se deja como está: sigue leyendo el fichero ya abierto.
     */
    fun replaceInQueue(newUris: Map<String, Uri>) {
        val c = controller ?: return
        if (newUris.isEmpty()) return
        for (i in 0 until c.mediaItemCount) {
            if (i == c.currentMediaItemIndex) continue
            val item = c.getMediaItemAt(i)
            val newUri = newUris[item.mediaId] ?: continue
            c.replaceMediaItem(i, item.buildUpon().setMediaId(newUri.toString()).setUri(newUri).build())
        }
    }

    /**
     * Ejecuta [write] (que reescribe el fichero de [mediaId]) con el reproductor detenido para que no
     * esté leyendo el fichero, y después lo recarga en la misma posición con los nuevos datos.
     */
    suspend fun rewriteTrack(mediaId: String, newTags: TrackTags, write: suspend () -> Unit) {
        val c = controller
        if (c == null || c.currentMediaItem?.mediaId != mediaId) {
            write()
            ThumbnailCache.invalidate(Uri.parse(mediaId))
            loadMusic()
            return
        }

        val index = c.currentMediaItemIndex
        val position = c.currentPosition
        val playWhenReady = c.playWhenReady
        c.stop()
        try {
            write()
            val old = c.getMediaItemAt(index)
            val metadata = old.mediaMetadata.buildUpon()
                .setTitle(newTags.title ?: old.mediaMetadata.title)
                .setArtist(newTags.artist ?: old.mediaMetadata.artist)
                .setAlbumTitle(newTags.album ?: old.mediaMetadata.albumTitle)
                .build()
            c.replaceMediaItem(index, old.buildUpon().setMediaMetadata(metadata).build())
        } finally {
            c.seekTo(index, position)
            c.prepare()
            c.playWhenReady = playWhenReady
            artworkMediaId = null // fuerza recargar la carátula
            ThumbnailCache.invalidate(Uri.parse(mediaId))
            refreshState()
            loadMusic()
        }
    }

    private fun refreshState() {
        val c = controller ?: return
        val item = c.currentMediaItem
        val metadata = c.mediaMetadata
        _playerState.value = PlayerUiState(
            hasMedia = item != null,
            mediaId = item?.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString()?.takeIf { it.isNotBlank() },
            album = metadata.albumTitle?.toString()?.takeIf { it.isNotBlank() },
            isPlaying = !Util.shouldShowPlayButton(c),
            positionMs = c.currentPosition.coerceAtLeast(0),
            positionUpdatedAt = SystemClock.elapsedRealtime(),
            durationMs = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasNext = c.hasNextMediaItem(),
            hasPrevious = item != null,
        )
        if (item?.mediaId != artworkMediaId) loadArtwork(item?.mediaId)
    }

    private fun loadArtwork(mediaId: String?) {
        artworkMediaId = mediaId
        artworkJob?.cancel()
        _artwork.value = null
        if (mediaId == null) return
        artworkJob = viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                ArtworkLoader.load(getApplication(), Uri.parse(mediaId))
            }
            _artwork.value = bitmap?.asImageBitmap()
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        MediaController.releaseFuture(controllerFuture)
        controller = null
        super.onCleared()
    }
}

private fun Track.toMediaItem(): MediaItem =
    MediaItem.Builder()
        .setMediaId(uri.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .build()
        )
        .build()
