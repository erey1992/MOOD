package com.mood.player.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executors

/** Lista de reproducción del usuario asociada a un estado de ánimo. Las canciones se guardan por URI. */
data class Mood(
    val id: String,
    val name: String,
    val icon: String,
    val color: Long,
    val trackUris: List<String> = emptyList(),
    /** Los favoritos aparecen como acceso rápido en el reproductor. */
    val favorite: Boolean = false,
)

/** Un MOOD con sus canciones ya resueltas contra la biblioteca actual. */
data class MoodPlaylist(val mood: Mood, val collection: MusicCollection)

/** Guarda los MOODS en un JSON privado de la app. Los cambios se publican al instante y se escriben en segundo plano. */
class MoodRepository private constructor(context: Context) {

    private val file = AtomicFile(File(context.filesDir, "moods.json"))
    private val writer = Executors.newSingleThreadExecutor()

    private val _moods = MutableStateFlow(load())
    val moods: StateFlow<List<Mood>> = _moods.asStateFlow()

    /** Devuelve el id del MOOD creado. */
    fun create(name: String, icon: String): String {
        val id = UUID.randomUUID().toString()
        edit { moods -> moods + Mood(id, name.trim(), icon, PALETTE[moods.size % PALETTE.size]) }
        return id
    }

    fun update(id: String, name: String, icon: String) = editMood(id) { it.copy(name = name.trim(), icon = icon) }

    fun setFavorite(id: String, favorite: Boolean) = editMood(id) { it.copy(favorite = favorite) }

    fun delete(id: String) = edit { moods -> moods.filterNot { it.id == id } }

    fun addTracks(id: String, uris: List<String>) = editMood(id) { it.copy(trackUris = (it.trackUris + uris).distinct()) }

    fun removeTracks(id: String, uris: Collection<String>) = editMood(id) { mood ->
        val removed = uris.toSet()
        mood.copy(trackUris = mood.trackUris.filterNot { it in removed })
    }

    /** Las canciones borradas del dispositivo desaparecen de todos los MOODS. */
    fun forgetTracks(uris: Set<String>) = edit { moods ->
        moods.map { mood -> mood.copy(trackUris = mood.trackUris.filterNot { it in uris }) }
    }

    /** Ficheros que cambiaron de URI al moverlos o renombrarlos (Android 10 o anterior). */
    fun replaceTracks(newUris: Map<String, String>) = edit { moods ->
        moods.map { mood -> mood.copy(trackUris = mood.trackUris.map { newUris[it] ?: it }.distinct()) }
    }

    private fun editMood(id: String, change: (Mood) -> Mood) = edit { moods ->
        moods.map { if (it.id == id) change(it) else it }
    }

    private fun edit(change: (List<Mood>) -> List<Mood>) {
        val updated = _moods.updateAndGet(change)
        writer.execute { save(updated) }
    }

    private fun load(): List<Mood> {
        if (!file.baseFile.exists()) return DEFAULTS
        return runCatching {
            val array = JSONArray(String(file.readFully(), Charsets.UTF_8))
            (0 until array.length()).map { i ->
                val json = array.getJSONObject(i)
                val uris = json.getJSONArray("tracks")
                Mood(
                    id = json.getString("id"),
                    name = json.getString("name"),
                    icon = json.getString("icon"),
                    color = json.getLong("color"),
                    trackUris = (0 until uris.length()).map { uris.getString(it) },
                    favorite = json.optBoolean("favorite", false),
                )
            }
        }.getOrDefault(DEFAULTS)
    }

    private fun save(moods: List<Mood>) {
        val array = JSONArray()
        moods.forEach { mood ->
            array.put(
                JSONObject()
                    .put("id", mood.id)
                    .put("name", mood.name)
                    .put("icon", mood.icon)
                    .put("color", mood.color)
                    .put("tracks", JSONArray(mood.trackUris))
                    .put("favorite", mood.favorite)
            )
        }
        val out = file.startWrite()
        try {
            out.write(array.toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: IOException) {
            file.failWrite(out)
        }
    }

    companion object {
        /** Colores para los MOODS que crea el usuario. */
        val PALETTE = listOf(
            0xFFF9A825, 0xFF5C6BC0, 0xFFE91E63, 0xFF8E24AA, 0xFFE53935, 0xFF00897B,
            0xFF1E88E5, 0xFF3949AB, 0xFFFB8C00, 0xFF43A047, 0xFF6D4C41, 0xFF00ACC1,
        )

        private val DEFAULTS = listOf(
            Mood("happy", "HAPPY", "happy", 0xFFF9A825),
            Mood("sad", "SAD", "sad", 0xFF5C6BC0),
            Mood("romantic", "ROMANTIC", "romantic", 0xFFE91E63),
            Mood("dance", "DANCE", "dance", 0xFF8E24AA),
            Mood("workout", "WORK OUT", "workout", 0xFFE53935),
            Mood("chill", "CHILL", "chill", 0xFF00897B),
            Mood("focus", "FOCUS", "focus", 0xFF1E88E5),
            Mood("sleep", "SLEEP", "sleep", 0xFF3949AB),
            Mood("roadtrip", "ROAD TRIP", "roadtrip", 0xFFFB8C00),
        )

        @Volatile
        private var instance: MoodRepository? = null

        fun get(context: Context): MoodRepository =
            instance ?: synchronized(this) {
                instance ?: MoodRepository(context.applicationContext).also { instance = it }
            }
    }
}
