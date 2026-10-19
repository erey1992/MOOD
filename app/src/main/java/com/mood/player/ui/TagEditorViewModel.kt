package com.mood.player.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mood.player.data.ArtworkLoader
import com.mood.player.data.Http
import com.mood.player.data.MetadataCandidate
import com.mood.player.data.OnlineMetadataSource
import com.mood.player.data.TagField
import com.mood.player.data.TagRepository
import com.mood.player.data.TrackTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TagEditorState(
    val mediaId: String? = null,
    val current: TrackTags = TrackTags(),
    val currentArtwork: ImageBitmap? = null,
    val currentDurationMs: Long? = null,
    val query: String = "",
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val results: List<MetadataCandidate> = emptyList(),
    val selected: MetadataCandidate? = null,
    val proposedArtwork: ImageBitmap? = null,
    val isLoadingArtwork: Boolean = false,
    val choices: Set<TagField> = emptySet(),
    val isSaving: Boolean = false,
    val pendingPermission: IntentSender? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

class TagEditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TagRepository(application)

    private val _state = MutableStateFlow(TagEditorState())
    val state: StateFlow<TagEditorState> = _state.asStateFlow()

    private var proposedArtworkBytes: ByteArray? = null
    private var writeGranted = false
    private var artworkJob: Job? = null

    fun open(mediaId: String) {
        val s = _state.value
        if (s.mediaId == mediaId && !s.saved) return
        _state.value = TagEditorState(mediaId = mediaId)
        proposedArtworkBytes = null
        writeGranted = false

        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                runCatching { repository.read(Uri.parse(mediaId)) }.getOrNull()
            }
            if (info == null) {
                _state.update { it.copy(error = "No se pudo leer el fichero.") }
                return@launch
            }
            val artwork = withContext(Dispatchers.Default) {
                info.artwork?.let { ArtworkLoader.decodeSampled(it, 400)?.asImageBitmap() }
            }
            _state.update {
                it.copy(
                    current = info.tags,
                    currentArtwork = artwork,
                    currentDurationMs = info.durationMs,
                    query = buildQuery(info.tags, info.displayName),
                )
            }
            search()
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun search() {
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(isSearching = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { OnlineMetadataSource.search(query) }
            _state.update {
                it.copy(
                    isSearching = false,
                    hasSearched = true,
                    results = result.getOrDefault(emptyList()),
                    error = if (result.isFailure) "No se pudo conectar. Revisa tu conexión a internet." else null,
                )
            }
        }
    }

    fun select(candidate: MetadataCandidate) {
        val s = _state.value
        // Por defecto solo se marcan los datos que faltan; el resto el usuario decide si sobrescribirlos.
        val defaults = TrackTags.TEXT_FIELDS.filter { field ->
            s.current[field].isNullOrBlank() && !candidate.tags[field].isNullOrBlank()
        }.toMutableSet()
        if (s.currentArtwork == null && candidate.artworkUrl != null) defaults += TagField.ARTWORK

        _state.update {
            it.copy(selected = candidate, choices = defaults, proposedArtwork = null, error = null)
        }
        proposedArtworkBytes = null
        artworkJob?.cancel()
        val url = candidate.artworkUrl ?: return
        _state.update { it.copy(isLoadingArtwork = true) }
        artworkJob = viewModelScope.launch {
            val bytes = withContext(Dispatchers.IO) { runCatching { Http.get(url) }.getOrNull() }
            val bitmap = bytes?.let {
                withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(it, 0, it.size) }
            }
            proposedArtworkBytes = bytes.takeIf { bitmap != null }
            _state.update {
                it.copy(
                    proposedArtwork = bitmap?.asImageBitmap(),
                    isLoadingArtwork = false,
                    choices = if (bitmap == null) it.choices - TagField.ARTWORK else it.choices,
                )
            }
        }
    }

    fun backToResults() {
        artworkJob?.cancel()
        _state.update { it.copy(selected = null, choices = emptySet(), proposedArtwork = null, isLoadingArtwork = false) }
    }

    fun toggle(field: TagField) {
        _state.update {
            it.copy(choices = if (field in it.choices) it.choices - field else it.choices + field)
        }
    }

    fun save(player: PlayerViewModel) {
        val s = _state.value
        val mediaId = s.mediaId ?: return
        if (s.selected == null || s.choices.isEmpty() || s.isSaving) return

        // Android 11+: el sistema pide al usuario que autorice modificar un fichero que no creó la app.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !writeGranted && Uri.parse(mediaId).authority == MediaStore.AUTHORITY) {
            val request = MediaStore.createWriteRequest(
                getApplication<Application>().contentResolver,
                listOf(Uri.parse(mediaId)),
            )
            _state.update { it.copy(pendingPermission = request.intentSender) }
            return
        }
        performSave(player, mediaId)
    }

    fun onPermissionResult(granted: Boolean, player: PlayerViewModel) {
        _state.update { it.copy(pendingPermission = null) }
        if (granted) {
            writeGranted = true
            _state.value.mediaId?.let { performSave(player, it) }
        } else {
            _state.update { it.copy(error = "Permiso denegado: el fichero no se ha modificado.") }
        }
    }

    private fun performSave(player: PlayerViewModel, mediaId: String) {
        val s = _state.value
        val candidate = s.selected ?: return
        val text = s.choices
            .filter { it != TagField.ARTWORK }
            .mapNotNull { field -> candidate.tags[field]?.let { field to it } }
            .toMap()
        val artwork = if (TagField.ARTWORK in s.choices) proposedArtworkBytes else null
        val newTags = text.entries.fold(s.current) { tags, (field, value) -> tags.with(field, value) }

        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            try {
                player.rewriteTrack(mediaId, newTags) {
                    withContext(Dispatchers.IO) { repository.write(Uri.parse(mediaId), text, artwork) }
                }
                _state.update { it.copy(isSaving = false, saved = true) }
            } catch (e: SecurityException) {
                // Android 10: el permiso se solicita a través de la excepción.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    _state.update {
                        it.copy(isSaving = false, pendingPermission = e.userAction.actionIntent.intentSender)
                    }
                } else {
                    _state.update { it.copy(isSaving = false, error = "Sin permiso para modificar el fichero.") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isSaving = false, error = "No se pudo guardar: ${e.message}") }
            }
        }
    }

    /** Usa título y artista si existen; si no, el nombre del fichero sin número de pista ni extensión. */
    private fun buildQuery(tags: TrackTags, displayName: String?): String {
        if (!tags.title.isNullOrBlank()) return listOfNotNull(tags.title, tags.artist).joinToString(" ")
        return displayName.orEmpty()
            .substringBeforeLast('.')
            .replace('_', ' ')
            .replace(Regex("^\\s*\\d{1,3}\\s*[-.)]?\\s*"), "")
            .trim()
    }
}
