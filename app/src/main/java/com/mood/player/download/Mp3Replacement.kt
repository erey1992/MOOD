package com.mood.player.download

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

/** Recognize previous MOOD downloads even if the video title changed or a numbered copy exists. */
fun isMp3ForVideo(name: String, videoId: String): Boolean =
    Regex(".*\\[${Regex.escape(videoId)}]( \\(\\d+\\))?\\.(?i:mp3)").matches(name)

/** Stage the entire new MP3 before touching the original, and roll back a failed commit. */
internal suspend fun replaceMp3(
    context: Context,
    folder: DocumentFile,
    original: DocumentFile,
    source: File,
    register: (DocumentFile) -> Unit,
): DocumentFile {
    if (!original.isFile) throw IOException("El destino existente no es un archivo MP3.")
    val originalName = original.name ?: throw IOException("No se pudo leer el nombre del MP3 existente.")
    val token = UUID.randomUUID().toString()
    val stage = folder.createFile("audio/mpeg", ".mood-new-$token.mp3")
        ?: throw IOException("No se pudo preparar el MP3 nuevo. El original se conserva.")
    var backedUp = false
    var committed = false
    try {
        val coroutine = currentCoroutineContext()
        context.contentResolver.openOutputStream(stage.uri, "w")?.use { output ->
            source.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    coroutine.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            }
            output.flush()
        } ?: throw IOException("No se pudo escribir el MP3 nuevo. El original se conserva.")
        coroutine.ensureActive()
        // Once the old file moves, finish the short commit/rollback even if cancellation arrives.
        withContext(NonCancellable) {
            if (!original.isFile || !original.renameTo(".mood-backup-$token.mp3")) {
                throw IOException("Esta carpeta no permite reemplazar el archivo. El original se conserva.")
            }
            backedUp = true
            if (!stage.renameTo(originalName) || stage.name != originalName) {
                throw IOException("No se pudo asignar el nombre al MP3 nuevo.")
            }
            register(stage)
            committed = true
            // If the provider refuses cleanup, keep the backup rather than fail a completed save.
            runCatching { original.delete() }
        }
        return stage
    } finally {
        if (!committed) {
            runCatching { stage.delete() }
            if (backedUp && !original.renameTo(originalName)) {
                throw IOException("No se pudo restaurar el nombre original. Tu MP3 anterior se conserva como ${original.name} en la misma carpeta.")
            }
        }
    }
}
