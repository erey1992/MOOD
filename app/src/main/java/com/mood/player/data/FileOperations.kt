package com.mood.player.data

import android.content.ContentUris
import android.content.ContentValues
import android.media.MediaMetadataRetriever
import android.content.Context
import android.content.IntentSender
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** Error con un mensaje apto para mostrar al usuario. */
class FileOperationException(message: String) : Exception(message)

/** Solo las pistas indexadas por MediaStore (con ruta conocida) se pueden borrar, renombrar o mover. */
val Track.isManageable: Boolean
    get() = uri.authority == MediaStore.AUTHORITY && path != null

class MoveResult(val moved: Map<Uri, Uri>, val failed: List<Pair<Track, String>>)

/** Datos de un fichero para comparar dos versiones de una canción. */
data class FileDetails(
    val uri: Uri?,
    val path: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val durationMs: Long?,
    val sizeBytes: Long,
    val bitrateKbps: Int?,
    val modifiedMs: Long,
)

/** Al mover [track], ya existe un fichero con el mismo nombre en la carpeta de destino. */
data class MoveConflict(val track: Track, val incoming: FileDetails, val existing: FileDetails)

/**
 * Borrado, renombrado y movimiento de ficheros de audio.
 *
 * - Android 11+: a través de MediaStore. El sistema pide al usuario permiso para modificar o borrar
 *   ficheros que no creó la app (ver [deleteRequest] / [writeRequest]); la URI de la pista no cambia.
 * - Android 10 o anterior: operaciones directas sobre el fichero y nuevo escaneo; la URI sí cambia.
 */
class FileOperations(private val context: Context) {

    private val resolver get() = context.contentResolver

    /** Android 11+: diálogo del sistema que confirma y ejecuta el borrado. null en versiones anteriores. */
    fun deleteRequest(uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(resolver, uris).intentSender
        } else {
            null
        }

    /** Android 11+: diálogo del sistema para autorizar la modificación. null en versiones anteriores. */
    fun writeRequest(uris: List<Uri>): IntentSender? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(resolver, uris).intentSender
        } else {
            null
        }

    /** Android 10 o anterior (en 11+ el borrado lo hace el sistema con [deleteRequest]). */
    suspend fun deleteLegacy(tracks: List<Track>) = withContext(Dispatchers.IO) {
        tracks.forEach { track ->
            track.path?.let { File(it) }?.takeIf { it.exists() }?.let { file ->
                if (!file.delete()) throw FileOperationException("No se pudo borrar «${file.name}».")
            }
            resolver.delete(track.uri, null, null)
        }
    }

    /** Cambia el nombre del fichero conservando la extensión. Devuelve la URI resultante. */
    suspend fun rename(track: Track, newBaseName: String): Uri = withContext(Dispatchers.IO) {
        val source = File(track.path ?: throw FileOperationException("Fichero no disponible."))
        val extension = source.extension.ifEmpty { "mp3" }
        val target = File(source.parentFile, "$newBaseName.$extension")
        if (target.path == source.path) return@withContext track.uri
        // Permite cambiar solo mayúsculas/minúsculas aunque el sistema de ficheros no las distinga.
        if (target.exists() && !target.path.equals(source.path, ignoreCase = true)) {
            throw FileOperationException("Ya existe «${target.name}» en esta carpeta.")
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            update(track.uri, ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, target.name) })
            track.uri
        } else {
            moveLegacy(source, target)
        }
    }

    /** Mueve las pistas a [targetDir] (ruta absoluta). Sigue con las demás si alguna falla. */
    suspend fun move(tracks: List<Track>, targetDir: String): MoveResult = withContext(Dispatchers.IO) {
        val target = File(targetDir)
        val moved = linkedMapOf<Uri, Uri>()
        val failed = mutableListOf<Pair<Track, String>>()
        for (track in tracks) {
            try {
                val source = File(track.path ?: throw FileOperationException("Fichero no disponible."))
                if (source.parentFile?.path == target.path) continue
                val destination = File(target, source.name)
                if (destination.exists()) throw FileOperationException("Ya existe «${source.name}» en ${target.name}.")
                moved[track.uri] = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    moveViaMediaStore(track.uri, source, target)
                    track.uri
                } else {
                    moveLegacy(source, destination)
                }
            } catch (e: FileOperationException) {
                failed += track to (e.message ?: "Error")
            }
        }
        MoveResult(moved, failed)
    }

    /** Canciones de [tracks] que chocarían con un fichero del mismo nombre en [targetDir]. */
    suspend fun findConflicts(tracks: List<Track>, targetDir: String): List<MoveConflict> = withContext(Dispatchers.IO) {
        val target = File(targetDir)
        tracks.mapNotNull { track ->
            val source = File(track.path ?: return@mapNotNull null)
            if (source.parentFile?.path == target.path) return@mapNotNull null
            val destination = File(target, source.name)
            if (destination.exists()) MoveConflict(track, details(source), details(destination)) else null
        }
    }

    /**
     * Borra los ficheros que se van a sustituir. En Android 11+ requiere el permiso de escritura concedido con
     * [writeRequest] para sus URIs.
     */
    suspend fun deleteForReplace(targets: List<FileDetails>) = withContext(Dispatchers.IO) {
        targets.forEach { target ->
            val file = File(target.path)
            val deleted = try {
                if (target.uri != null) resolver.delete(target.uri, null, null) > 0 || !file.exists() else file.delete()
            } catch (e: SecurityException) {
                false
            }
            if (!deleted || file.exists()) throw FileOperationException("No se pudo reemplazar «${file.name}».")
        }
    }

    /** Lee lo que MediaStore sabe del fichero y completa con el propio fichero (calidad, tamaño). */
    @Suppress("DEPRECATION")
    private fun details(file: File): FileDetails {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        var uri: Uri? = null
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var duration: Long? = null
        runCatching {
            resolver.query(collection, projection, "${MediaStore.Audio.Media.DATA} = ?", arrayOf(file.path), null)?.use { c ->
                if (c.moveToFirst()) {
                    uri = ContentUris.withAppendedId(collection, c.getLong(0))
                    title = c.getString(1).clean()
                    artist = c.getString(2).clean()
                    album = c.getString(3).clean()
                    duration = c.getLong(4).takeIf { it > 0 }
                }
            }
        }
        var bitrate: Int? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.div(1000)
            if (duration == null) duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (title == null) title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).clean()
            if (artist == null) artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).clean()
            if (album == null) album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).clean()
        } catch (e: Exception) {
            // Sin acceso al contenido: se compara con lo que haya.
        } finally {
            runCatching { retriever.release() }
        }
        return FileDetails(uri, file.path, title, artist, album, duration, file.length(), bitrate, file.lastModified())
    }

    private fun String?.clean() = this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }

    /** Carpeta nueva dentro de Music, el directorio estándar donde Android admite audio. */
    @Suppress("DEPRECATION")
    fun newMusicFolder(name: String): String =
        File(File(Environment.getExternalStorageDirectory(), Environment.DIRECTORY_MUSIC), name).path

    @RequiresApi(Build.VERSION_CODES.R)
    private fun moveViaMediaStore(uri: Uri, source: File, target: File) {
        val storage = context.getSystemService(StorageManager::class.java)
        val targetRoot = storage.getStorageVolume(target)?.directory
            ?: throw FileOperationException("Carpeta de destino no válida.")
        if (storage.getStorageVolume(source)?.directory != targetRoot) {
            throw FileOperationException("No se puede mover entre almacenamientos distintos (memoria interna / tarjeta SD).")
        }
        val relativePath = target.path.removePrefix(targetRoot.path).trim('/') + "/"
        try {
            update(uri, ContentValues().apply { put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) })
        } catch (e: IllegalArgumentException) {
            // MediaProvider solo admite audio en carpetas estándar (Music, Download, Podcasts…).
            throw FileOperationException(
                "Android no permite guardar audio en «$relativePath». Elige una carpeta dentro de Music, Download o Podcasts."
            )
        }
    }

    private fun update(uri: Uri, values: ContentValues) {
        if (resolver.update(uri, values, null, null) == 0) {
            throw FileOperationException("El sistema no pudo modificar el fichero.")
        }
    }

    private suspend fun moveLegacy(source: File, target: File): Uri {
        target.parentFile?.mkdirs()
        if (!source.renameTo(target)) throw FileOperationException("No se pudo mover «${source.name}».")
        scan(source.path) // elimina la entrada antigua de MediaStore
        return scan(target.path) ?: throw FileOperationException("El sistema no indexó «${target.name}».")
    }

    private suspend fun scan(path: String): Uri? = suspendCancellableCoroutine { cont ->
        MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, uri ->
            if (cont.isActive) cont.resume(uri)
        }
    }
}
