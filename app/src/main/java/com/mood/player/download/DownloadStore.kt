package com.mood.player.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mood.player.data.MusicCollection
import com.mood.player.data.TagRepository
import com.mood.player.data.Track
import org.json.JSONArray
import org.json.JSONObject

class DownloadStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("audio_downloads", Context.MODE_PRIVATE)

    var folder: Uri?
        get() = prefs.getString("folder", null)?.let(Uri::parse)
        set(value) { prefs.edit().putString("folder", value?.toString()).apply() }

    var lastUpdate: Long
        get() = prefs.getLong("last_update", 0)
        set(value) { prefs.edit().putLong("last_update", value).apply() }

    fun remember(uri: Uri, tree: Uri, name: String, replacedUri: Uri? = null) = synchronized(registrationLock) {
        val previous = entries()
        val entries = JSONArray()
        for (i in 0 until previous.length()) {
            val entry = previous.getJSONObject(i)
            if (entry.optString("uri") !in setOf(uri.toString(), replacedUri?.toString())) entries.put(entry)
        }
        entries.put(JSONObject().put("uri", uri.toString()).put("tree", tree.toString()).put("name", name))
        check(prefs.edit().putString("files", entries.toString()).commit()) {
            "No se pudo registrar el MP3 en la biblioteca."
        }
    }

    companion object {
        // All workers share the same registry, including workers using different store instances.
        private val registrationLock = Any()
    }

    private fun entries() = runCatching { JSONArray(prefs.getString("files", "[]")) }
        .getOrElse { JSONArray() }

    /** SAF files may live on SD cards or providers that MediaStore does not index. */
    fun collections(): List<MusicCollection> {
        val entries = entries()
        val folders = linkedMapOf<String, MutableList<Track>>()
        for (i in 0 until entries.length()) {
            runCatching {
                val entry = entries.getJSONObject(i)
                val uri = Uri.parse(entry.getString("uri"))
                val info = TagRepository(context).read(uri)
                val track = Track(
                    id = -1L - (uri.toString().hashCode().toLong() and 0xffffffffL),
                    uri = uri,
                    title = info.tags.title ?: entry.getString("name").removeSuffix(".mp3"),
                    artist = info.tags.artist,
                    album = info.tags.album,
                    durationMs = info.durationMs ?: 0L,
                )
                folders.getOrPut(entry.getString("tree")) { mutableListOf() }.add(track)
            }
        }
        return folders.map { (tree, tracks) ->
            val name = runCatching { DocumentFile.fromTreeUri(context, Uri.parse(tree))?.name }.getOrNull()
            MusicCollection(tree, name ?: "Descargas de MOOD", tracks)
        }
    }
}
