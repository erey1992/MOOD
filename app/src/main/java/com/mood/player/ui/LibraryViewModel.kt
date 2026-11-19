package com.mood.player.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.mood.player.data.FileOperations
import com.mood.player.data.LibraryGrouping
import com.mood.player.data.TagField
import com.mood.player.data.TagRepository
import com.mood.player.data.TrackTags
import com.mood.player.data.MoveConflict
import com.mood.player.data.MoodRepository
import com.mood.player.data.Track
import com.mood.player.data.isManageable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val busy: Boolean = false,
    val pendingPermission: IntentSender? = null,
    val message: String? = null,
    /** Movimiento en pausa hasta que el usuario decida qué hacer con los ficheros repetidos. */
    val conflicts: ConflictSession? = null,
)

data class ConflictSession(
    val tracks: List<Track>,
    val targetDir: String,
    val folderName: String,
    val conflicts: List<MoveConflict>,
)

/** Borrar, renombrar y mover canciones del dispositivo. */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val files = FileOperations(application)
    private val tags = TagRepository(application)
    private val moods = MoodRepository.get(application)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Acción a ejecutar cuando el usuario autorice el diálogo del sistema. Devuelve el mensaje final. */
    private var pendingAction: (suspend () -> String)? = null

    val needsOwnDeleteConfirmation: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    fun newMusicFolder(name: String): String = files.newMusicFolder(sanitize(name))

    fun delete(tracks: List<Track>, player: PlayerViewModel) {
        val managed = manageable(tracks) ?: return
        val uris = managed.map { it.uri }
        val afterDelete: suspend () -> String = {
            val deleted = uris.map { it.toString() }.toSet()
            player.removeTracksLocally(deleted)
            player.removeFromQueue(deleted)
            moods.forgetTracks(deleted)
            player.loadMusic()
            plural(managed.size, "canción borrada", "canciones borradas")
        }
        val request = files.deleteRequest(uris)
        if (request != null) {
            // Android 11+: el propio diálogo del sistema confirma y borra.
            awaitPermission(request, afterDelete)
        } else {
            execute {
                files.deleteLegacy(managed)
                afterDelete()
            }
        }
    }

    fun rename(track: Track, newName: String, player: PlayerViewModel) {
        val managed = manageable(listOf(track))?.single() ?: return
        val name = sanitize(newName)
        if (name.isEmpty()) {
            showMessage("El nombre no puede estar vacío.")
            return
        }
        withWritePermission(listOf(managed.uri)) {
            val newUri = files.rename(managed, name)
            if (newUri != managed.uri) {
                player.replaceInQueue(mapOf(managed.uri.toString() to newUri))
                moods.replaceTracks(mapOf(managed.uri.toString() to newUri.toString()))
            }
            player.loadMusic()
            "Renombrada a «$name»"
        }
    }

    /** Deja en la canción solo los artistas de [keep] (ver [LibraryGrouping.editArtists]). */
    fun editArtists(track: Track, keep: List<String>, known: Set<String>, player: PlayerViewModel) {
        val edit = LibraryGrouping.editArtists(track.artist, track.title, keep, known)
        if (edit.artist == track.artist && edit.title == track.title) return
        rewriteArtists(listOf(track to edit), player) { "Artistas actualizados" }
    }

    /** Borra el artista [name] de las etiquetas de todas las [tracks]; las canciones no se tocan más. */
    fun removeArtist(tracks: List<Track>, name: String, known: Set<String>, player: PlayerViewModel) {
        val edits = tracks.map { it to LibraryGrouping.removeArtist(it.artist, it.title, name, known) }
            .filter { (track, edit) -> edit.artist != track.artist || edit.title != track.title }
        if (edits.isEmpty()) {
            showMessage("«$name» solo aparece en el nombre de los ficheros: no hay etiqueta que borrar.")
            return
        }
        rewriteArtists(edits, player) { "«$name» quitado de ${plural(edits.size, "canción", "canciones")}" }
    }

    private fun rewriteArtists(
        edits: List<Pair<Track, LibraryGrouping.ArtistEdit>>,
        player: PlayerViewModel,
        message: () -> String,
    ) {
        // Las canciones que no indexa MediaStore (p. ej. descargas en curso) no admiten la petición de permiso.
        val uris = edits.map { it.first.uri }.filter { it.authority == MediaStore.AUTHORITY }
        withWritePermission(uris) {
            edits.forEach { (track, edit) ->
                val text = buildMap<TagField, String?> {
                    if (edit.artist != track.artist) put(TagField.ARTIST, edit.artist)
                    if (edit.title != track.title) put(TagField.TITLE, edit.title)
                }
                val newTags = TrackTags(title = edit.title, artist = edit.artist ?: "", album = track.album)
                player.rewriteTrack(track.uri.toString(), newTags, reload = false) {
                    withContext(Dispatchers.IO) { tags.write(track.uri, text, artwork = null) }
                }
            }
            player.loadMusic()
            message()
        }
    }

    /** Mueve las canciones; si alguna ya existe en el destino, primero pregunta (comparar, reemplazar u omitir). */
    fun move(tracks: List<Track>, targetDir: String, folderName: String, player: PlayerViewModel) {
        val managed = manageable(tracks) ?: return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val conflicts = runCatching { files.findConflicts(managed, targetDir) }.getOrDefault(emptyList())
            _state.update { it.copy(busy = false) }
            if (conflicts.isEmpty()) {
                performMove(managed, targetDir, folderName, replace = emptyList(), skipped = 0, player)
            } else {
                _state.update { it.copy(conflicts = ConflictSession(managed, targetDir, folderName, conflicts)) }
            }
        }
    }

    /** [replace]: URIs de las canciones (de las que se mueven) que deben sustituir al fichero existente. */
    fun resolveConflicts(replace: Set<Uri>, player: PlayerViewModel) {
        val session = _state.value.conflicts ?: return
        _state.update { it.copy(conflicts = null) }
        val skipped = session.conflicts.filter { it.track.uri !in replace }.map { it.track.uri }.toSet()
        val toMove = session.tracks.filter { it.uri !in skipped }
        if (toMove.isEmpty()) {
            showMessage("${plural(skipped.size, "canción omitida", "canciones omitidas")}: ya estaban en «${session.folderName}».")
            return
        }
        performMove(
            toMove,
            session.targetDir,
            session.folderName,
            replace = session.conflicts.filter { it.track.uri in replace },
            skipped = skipped.size,
            player = player,
        )
    }

    fun cancelConflicts() {
        _state.update { it.copy(conflicts = null) }
        showMessage("Operación cancelada.")
    }

    private fun performMove(
        tracks: List<Track>,
        targetDir: String,
        folderName: String,
        replace: List<MoveConflict>,
        skipped: Int,
        player: PlayerViewModel,
    ) {
        // El permiso de escritura incluye los ficheros que se van a sustituir.
        val uris = tracks.map { it.uri } + replace.mapNotNull { it.existing.uri }
        withWritePermission(uris) {
            if (replace.isNotEmpty()) {
                files.deleteForReplace(replace.map { it.existing })
                val replaced = replace.filter { it.existing.uri != null }
                player.removeFromQueue(replaced.map { it.existing.uri.toString() }.toSet())
                // Los MOODS que tenían la versión sustituida pasan a tener la nueva.
                moods.replaceTracks(replaced.associate { it.existing.uri.toString() to it.track.uri.toString() })
            }
            val result = files.move(tracks, targetDir)
            val changed = result.moved.filter { (old, new) -> old != new }
            player.replaceInQueue(changed.mapKeys { it.key.toString() })
            moods.replaceTracks(changed.entries.associate { (old, new) -> old.toString() to new.toString() })
            player.loadMusic()
            val extras = listOfNotNull(
                replace.size.takeIf { it > 0 }?.let { plural(it, "reemplazada", "reemplazadas") },
                skipped.takeIf { it > 0 }?.let { plural(it, "omitida", "omitidas") },
            ).joinToString(", ").let { if (it.isEmpty()) "" else " ($it)" }
            when {
                result.failed.isEmpty() ->
                    "${plural(result.moved.size, "canción movida", "canciones movidas")} a «$folderName»$extras"
                result.moved.isEmpty() -> result.failed.first().second
                else -> "Se movieron ${result.moved.size}; fallaron ${result.failed.size}: ${result.failed.first().second}"
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        val action = pendingAction
        pendingAction = null
        _state.update { it.copy(pendingPermission = null) }
        if (granted && action != null) execute(action) else showMessage("Operación cancelada.")
    }

    fun messageShown() {
        _state.update { it.copy(message = null) }
    }

    private fun withWritePermission(uris: List<Uri>, action: suspend () -> String) {
        val request = if (uris.isEmpty()) null else files.writeRequest(uris)
        if (request != null) awaitPermission(request, action) else execute(action)
    }

    private fun awaitPermission(request: IntentSender, action: suspend () -> String) {
        pendingAction = action
        _state.update { it.copy(pendingPermission = request) }
    }

    private fun execute(action: suspend () -> String) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                showMessage(action())
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                // Android 10: el permiso para tocar ficheros ajenos llega a través de la excepción.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && e is RecoverableSecurityException) {
                    awaitPermission(e.userAction.actionIntent.intentSender, action)
                } else {
                    showMessage("Sin permiso para modificar el fichero.")
                }
            } catch (e: Exception) {
                showMessage(e.message ?: "No se pudo completar la operación.")
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun manageable(tracks: List<Track>): List<Track>? {
        val managed = tracks.filter { it.isManageable }
        if (managed.isEmpty()) {
            showMessage("Esta canción no está indexada por Android; gestiónala desde el explorador de archivos.")
            return null
        }
        return managed
    }

    private fun showMessage(message: String) {
        _state.update { it.copy(message = message) }
    }

    private fun sanitize(name: String) =
        name.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").removeSuffix(".mp3").trim()

    private fun plural(n: Int, singular: String, pluralForm: String) =
        if (n == 1) "1 $singular" else "$n $pluralForm"
}
