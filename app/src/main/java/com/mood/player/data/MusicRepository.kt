package com.mood.player.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import com.mood.player.download.DownloadStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class MusicRepository(private val context: Context) {

    /** "uri@fecha" -> etiqueta de álbum real del fichero ("" si no tiene). */
    private val albumTagCache = ConcurrentHashMap<String, String>()

    /** Busca todos los MP3 del dispositivo y los agrupa por la carpeta que los contiene. */
    @Suppress("DEPRECATION") // MediaStore.Audio.Media.DATA sigue siendo legible y da la ruta real
    suspend fun loadCollections(): List<MusicCollection> = withContext(Dispatchers.IO) {
        val contentUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            "album_artist", // MediaStore.Audio.Media.ALBUM_ARTIST; la columna existe antes de Android 11
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_MODIFIED,
        ) + if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) arrayOf(MediaStore.Audio.Media.GENRE) else emptyArray()
        val selection =
            "(${MediaStore.Audio.Media.MIME_TYPE} = ? OR ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?)"
        val selectionArgs = arrayOf("audio/mpeg", "%.mp3")
        val sortOrder = "${MediaStore.Audio.Media.DATA} COLLATE NOCASE ASC"

        val byFolder = LinkedHashMap<String, MutableList<Track>>()
        val modifiedById = HashMap<Long, Long>()

        context.contentResolver.query(contentUri, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val albumArtistCol = cursor.getColumnIndexOrThrow("album_artist")
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val genreCol = cursor.getColumnIndex("genre")
                // Antes de Android 11 el género no está en la tabla de audio, sino en la de géneros.
                val legacyGenres = if (genreCol < 0) loadGenresLegacy() else emptyMap()

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol) ?: continue
                    val folder = File(path).parent ?: continue
                    val displayName = cursor.getString(nameCol) ?: File(path).name

                    val track = Track(
                        id = id,
                        uri = ContentUris.withAppendedId(contentUri, id),
                        title = cursor.getString(titleCol).cleanTag()
                            ?: displayName.substringBeforeLast('.'),
                        artist = cursor.getString(artistCol).cleanTag(),
                        album = cursor.getString(albumCol).cleanTag(),
                        durationMs = cursor.getLong(durationCol),
                        fileName = displayName,
                        path = path,
                        genre = (if (genreCol >= 0) cursor.getString(genreCol) else legacyGenres[id]).cleanTag(),
                        albumArtist = cursor.getString(albumArtistCol).cleanTag(),
                        trackNumber = cursor.getInt(trackCol).takeIf { it > 0 },
                        year = cursor.getInt(yearCol).takeIf { it > 0 },
                    )
                    modifiedById[id] = cursor.getLong(modifiedCol)
                    byFolder.getOrPut(folder) { mutableListOf() }.add(track)
                }
            }

        val downloads = DownloadStore(context).collections()
        val downloadedMetadata = downloads.flatMap { it.tracks }.mapNotNull { track ->
            val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { MediaStore.getMediaUri(context, track.uri) }.getOrNull()
            } else null
            mediaUri?.lastPathSegment?.let { it to track }
        }.toMap()
        val folderNames = byFolder.keys.map { File(it).name.lowercase() }.toSet()
        byFolder.values.forEach { tracks ->
            tracks.replaceAll { withRealAlbum(it, modifiedById[it.id] ?: 0L, folderNames) }
        }

        val scanned = byFolder.map { (folder, tracks) ->
            // MediaStore can expose a newly created document before its tags have been scanned.
            val updated = tracks.map { track ->
                downloadedMetadata[track.uri.lastPathSegment]?.let { fresh ->
                    track.copy(title = fresh.title, artist = fresh.artist, album = fresh.album, durationMs = fresh.durationMs)
                } ?: track
            }
            MusicCollection(path = folder, name = File(folder).name, tracks = updated)
        }
        val scannedUris = scanned.flatMap { it.tracks }.map { it.uri.lastPathSegment }.toSet()
        val downloaded = downloads.mapNotNull { collection ->
            val tracks = collection.tracks.filter { track ->
                val mediaUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    runCatching { MediaStore.getMediaUri(context, track.uri) }.getOrNull()
                } else null
                mediaUri == null || mediaUri.lastPathSegment !in scannedUris
            }
            collection.copy(tracks = tracks).takeIf { tracks.isNotEmpty() }
        }
        (scanned + downloaded).sortedBy { it.name.lowercase() }
    }

    /**
     * Si un MP3 no tiene etiqueta de álbum, MediaStore usa el nombre de su carpeta como álbum. Cuando el
     * álbum coincide con el nombre de alguna carpeta se comprueba la etiqueta real (solo esos casos, con caché).
     */
    private fun withRealAlbum(track: Track, modified: Long, folderNames: Set<String>): Track {
        val album = track.album ?: return track
        if (album.lowercase() !in folderNames) return track
        val realAlbum = albumTagCache.getOrPut("${track.uri}@$modified") { readAlbumTag(track) }
        return if (realAlbum.isEmpty()) track.copy(album = null) else track
    }

    private fun readAlbumTag(track: Track): String {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, track.uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.trim().orEmpty()
        } catch (e: Exception) {
            track.album.orEmpty() // si no se puede leer, se respeta lo que diga MediaStore
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** id de pista -> nombre de género, para Android 10 o anterior. */
    private fun loadGenresLegacy(): Map<Long, String> {
        val result = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null, null,
            )?.use { genres ->
                while (genres.moveToNext()) {
                    val genreId = genres.getLong(0)
                    val name = genres.getString(1) ?: continue
                    context.contentResolver.query(
                        MediaStore.Audio.Genres.Members.getContentUri("external", genreId),
                        arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID),
                        null, null, null,
                    )?.use { members ->
                        while (members.moveToNext()) result.putIfAbsent(members.getLong(0), name)
                    }
                }
            }
        }
        return result
    }

    private fun String?.cleanTag(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
}
