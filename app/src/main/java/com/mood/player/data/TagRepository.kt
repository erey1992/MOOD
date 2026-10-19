package com.mood.player.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.IOException

class TagRepository(private val context: Context) {

    class FileInfo(
        val tags: TrackTags,
        val artwork: ByteArray?,
        val durationMs: Long?,
        val displayName: String?,
    )

    /** Lee las etiquetas que hay realmente escritas en el fichero (no los valores deducidos por MediaStore). */
    fun read(uri: Uri): FileInfo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun key(k: Int) = retriever.extractMetadata(k)?.trim()?.takeIf { it.isNotEmpty() }
            return FileInfo(
                tags = TrackTags(
                    title = key(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    artist = key(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    album = key(MediaMetadataRetriever.METADATA_KEY_ALBUM),
                    year = key(MediaMetadataRetriever.METADATA_KEY_YEAR)
                        ?: key(MediaMetadataRetriever.METADATA_KEY_DATE)?.take(4),
                    genre = key(MediaMetadataRetriever.METADATA_KEY_GENRE),
                    trackNumber = key(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER),
                ),
                artwork = retriever.embeddedPicture,
                durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(),
                displayName = queryString(uri, MediaStore.Audio.Media.DISPLAY_NAME),
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Reescribe la etiqueta ID3 del fichero. Lanza SecurityException si la app no tiene permiso
     * de escritura sobre él (en ese caso el fichero no se ha tocado).
     */
    fun write(uri: Uri, text: Map<TagField, String>, artwork: ByteArray?) {
        val resolver = context.contentResolver
        val original = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IOException("No se pudo leer el fichero")
        val updated = Id3Writer.rewrite(original, text, artwork)

        try {
            overwrite(uri, updated)
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            // Si algo falló a mitad de la escritura, intentamos dejar el fichero como estaba.
            runCatching { overwrite(uri, original) }
            throw e
        }

        @Suppress("DEPRECATION")
        queryString(uri, MediaStore.Audio.Media.DATA)?.let { path ->
            MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("audio/mpeg"), null)
        }
    }

    private fun overwrite(uri: Uri, bytes: ByteArray) {
        val pfd = context.contentResolver.openFileDescriptor(uri, "rw")
            ?: throw IOException("No se pudo abrir el fichero para escritura")
        ParcelFileDescriptor.AutoCloseOutputStream(pfd).use { out ->
            out.write(bytes)
            out.channel.truncate(bytes.size.toLong())
            out.fd.sync()
        }
    }

    private fun queryString(uri: Uri, column: String): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
}
