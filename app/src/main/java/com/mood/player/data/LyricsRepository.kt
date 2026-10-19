package com.mood.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.security.MessageDigest
import kotlin.math.abs

/** Una línea de letra sincronizada: el momento en que empieza a cantarse y su texto. */
data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    /** Letra sin marcas de tiempo: se muestra entera, sin seguimiento. */
    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/** Una versión de la letra en LRCLIB (la misma canción puede tener varias, de grabaciones distintas). */
data class LyricsVersion(
    val id: Long,
    val track: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val synced: String?,
    val plain: String?,
    val instrumental: Boolean,
)

/**
 * Letra de una canción con los ajustes del usuario: [offsetMs] desplaza todas las líneas (positivo = la letra
 * aparece más tarde), [versionId] es la versión de LRCLIB elegida y [manual] indica que la sincronizó el usuario.
 */
data class LyricsResult(
    val lyrics: Lyrics,
    val offsetMs: Long = 0,
    val versionId: Long? = null,
    val manual: Boolean = false,
)

/**
 * Busca letras en LRCLIB (lrclib.net, base de datos abierta, sin clave) y las guarda en la memoria
 * interna de la app junto con los ajustes del usuario.
 */
class LyricsRepository(context: Context) {

    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    suspend fun find(mediaId: String, title: String, artist: String?, durationMs: Long): LyricsResult =
        withContext(Dispatchers.IO) {
            val cache = cacheFile(mediaId)
            readCache(cache)?.let { return@withContext it }
            val result = fetch(title, artist, durationMs)
            // Las búsquedas sin resultado no se guardan: quizá más adelante haya letra o mejores datos.
            if (result.lyrics != Lyrics.NotFound) writeCache(cache, result)
            result
        }

    /** Olvida la letra guardada de una canción (para "buscar de nuevo"). */
    fun forget(mediaId: String) {
        cacheFile(mediaId).delete()
    }

    /** Todas las versiones conocidas de la canción, de la duración más parecida a la menos. */
    suspend fun versions(title: String, artist: String?, durationMs: Long): List<LyricsVersion> =
        withContext(Dispatchers.IO) {
            val all = LinkedHashMap<Long, LyricsVersion>()
            queries(title, artist).forEach { query -> search(query).forEach { all.putIfAbsent(it.id, it) } }
            sortByCloseness(all.values.toList(), durationMs)
        }

    /** Usa [version] para esta canción (sin desplazamiento). */
    suspend fun choose(mediaId: String, version: LyricsVersion): LyricsResult = withContext(Dispatchers.IO) {
        val lyrics = version.toLyrics() ?: Lyrics.NotFound
        val result = LyricsResult(lyrics, versionId = version.id)
        if (lyrics != Lyrics.NotFound) writeCache(cacheFile(mediaId), result)
        result
    }

    /** Guarda el desplazamiento elegido por el usuario. */
    suspend fun setOffset(mediaId: String, result: LyricsResult) = withContext(Dispatchers.IO) {
        writeCache(cacheFile(mediaId), result)
    }

    /** Guarda una sincronización hecha a mano por el usuario. */
    suspend fun saveManual(mediaId: String, lines: List<LyricLine>): LyricsResult = withContext(Dispatchers.IO) {
        val result = LyricsResult(Lyrics.Synced(lines), manual = true)
        writeCache(cacheFile(mediaId), result)
        result
    }

    private fun fetch(title: String, artist: String?, durationMs: Long): LyricsResult {
        val seen = LinkedHashMap<Long, LyricsVersion>()
        for (query in queries(title, artist)) {
            search(query).forEach { seen.putIfAbsent(it.id, it) }
            // En cuanto hay una versión sincronizada que encaja, no hace falta seguir preguntando.
            pickBest(seen.values.toList(), durationMs)?.takeIf { it.lyrics is Lyrics.Synced }?.let { return it }
        }
        return pickBest(seen.values.toList(), durationMs)
            // Ninguna versión coincide en duración: mejor la letra sin tiempos que una sincronización equivocada.
            ?: seen.values.firstNotNullOfOrNull { it.plain }?.let { LyricsResult(Lyrics.Plain(it)) }
            ?: LyricsResult(Lyrics.NotFound)
    }

    private fun queries(title: String, artist: String?): List<String> {
        val cleanTitle = cleanTitle(title)
        return buildList {
            if (artist != null) add("track_name=${enc(cleanTitle)}&artist_name=${enc(artist)}")
            // Títulos tipo "Artista - Canción" (habituales en descargas).
            cleanTitle.split(" - ", limit = 2).takeIf { it.size == 2 }?.let { (a, t) ->
                add("track_name=${enc(t.trim())}&artist_name=${enc(a.trim())}")
            }
            add("q=${enc(listOfNotNull(artist, cleanTitle).joinToString(" "))}")
        }.distinct()
    }

    private fun search(query: String): List<LyricsVersion> {
        // LRCLIB limita las peticiones (429 + Retry-After); Http.get ya espera y reintenta.
        val array = JSONArray(String(Http.get("https://lrclib.net/api/search?$query"), Charsets.UTF_8))
        return (0 until array.length()).map { array.getJSONObject(it) }.map { json ->
            LyricsVersion(
                id = json.optLong("id"),
                track = json.optString("trackName"),
                artist = json.optString("artistName"),
                album = json.optString("albumName").takeIf { it.isNotBlank() && it != "null" },
                durationMs = json.optDouble("duration", -1.0).takeIf { it > 0 }?.let { (it * 1000).toLong() },
                synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" },
                plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" },
                instrumental = json.optBoolean("instrumental"),
            )
        }
    }

    private fun cacheFile(mediaId: String) = File(dir, sha1(mediaId) + ".json")

    private fun readCache(file: File): LyricsResult? = runCatching {
        if (!file.exists()) return null
        val json = JSONObject(file.readText())
        val lyrics = when {
            json.optBoolean("instrumental") -> Lyrics.Instrumental
            json.has("synced") -> Lyrics.Synced(parseLrc(json.getString("synced")))
            json.has("plain") -> Lyrics.Plain(json.getString("plain"))
            else -> return null
        }
        LyricsResult(
            lyrics,
            offsetMs = json.optLong("offsetMs", 0),
            versionId = json.optLong("versionId", -1).takeIf { it >= 0 },
            manual = json.optBoolean("manual", false),
        )
    }.getOrNull()

    private fun writeCache(file: File, result: LyricsResult) {
        val json = JSONObject()
        when (val lyrics = result.lyrics) {
            is Lyrics.Synced -> json.put("synced", lyrics.lines.joinToString("\n") { formatLrc(it) })
            is Lyrics.Plain -> json.put("plain", lyrics.text)
            Lyrics.Instrumental -> json.put("instrumental", true)
            Lyrics.NotFound -> return
        }
        json.put("offsetMs", result.offsetMs)
        result.versionId?.let { json.put("versionId", it) }
        json.put("manual", result.manual)
        runCatching { file.writeText(json.toString()) }
    }

    companion object {
        private const val DURATION_TOLERANCE_MS = 8_000

        private val TIME_TAG = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
        private val OFFSET_TAG = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

        /** De la duración más parecida a la del fichero a la menos; las que no indican duración, al final. */
        fun sortByCloseness(versions: List<LyricsVersion>, durationMs: Long): List<LyricsVersion> =
            versions.sortedWith(
                compareBy(
                    { v -> if (durationMs > 0 && v.durationMs != null) abs(v.durationMs - durationMs) else Long.MAX_VALUE },
                    { v -> if (v.synced != null) 0 else 1 },
                )
            )

        /**
         * Elige la versión cuya duración más se parece a la del fichero (dentro de un margen), prefiriendo
         * la sincronizada. null si ninguna encaja.
         */
        fun pickBest(versions: List<LyricsVersion>, durationMs: Long): LyricsResult? {
            val matching = if (durationMs <= 0) versions else sortByCloseness(versions, durationMs).filter {
                it.durationMs != null && abs(it.durationMs - durationMs) <= DURATION_TOLERANCE_MS
            }
            matching.firstOrNull { it.synced != null && parseLrc(it.synced).isNotEmpty() }?.let {
                return LyricsResult(Lyrics.Synced(parseLrc(it.synced!!)), versionId = it.id)
            }
            matching.firstOrNull { it.plain != null }?.let { return LyricsResult(Lyrics.Plain(it.plain!!), versionId = it.id) }
            if (matching.any { it.instrumental }) return LyricsResult(Lyrics.Instrumental)
            return null
        }

        private fun LyricsVersion.toLyrics(): Lyrics? = when {
            synced != null && parseLrc(synced).isNotEmpty() -> Lyrics.Synced(parseLrc(synced))
            plain != null -> Lyrics.Plain(plain)
            instrumental -> Lyrics.Instrumental
            else -> null
        }

        /** Interpreta el formato LRC: "[mm:ss.xx] texto", con varias marcas por línea y etiqueta [offset:]. */
        fun parseLrc(lrc: String): List<LyricLine> {
            var offset = 0L
            val lines = mutableListOf<LyricLine>()
            lrc.lineSequence().forEach { raw ->
                OFFSET_TAG.find(raw)?.let {
                    offset = it.groupValues[1].toLong()
                    return@forEach
                }
                val stamps = TIME_TAG.findAll(raw).toList()
                if (stamps.isEmpty()) return@forEach
                val text = raw.substring(stamps.last().range.last + 1).trim()
                stamps.forEach { m ->
                    val minutes = m.groupValues[1].toLong()
                    val seconds = m.groupValues[2].toLong()
                    val fraction = m.groupValues[3]
                    val millis = when (fraction.length) {
                        0 -> 0L
                        1 -> fraction.toLong() * 100
                        2 -> fraction.toLong() * 10
                        else -> fraction.take(3).toLong()
                    }
                    // Un offset positivo adelanta la letra.
                    lines += LyricLine((minutes * 60_000 + seconds * 1_000 + millis - offset).coerceAtLeast(0), text)
                }
            }
            return lines.sortedBy { it.timeMs }
        }

        private fun formatLrc(line: LyricLine): String {
            val minutes = line.timeMs / 60_000
            val seconds = (line.timeMs % 60_000) / 1_000
            val centis = (line.timeMs % 1_000) / 10
            return "[%02d:%02d.%02d]%s".format(minutes, seconds, centis, line.text)
        }

        /** Quita adornos habituales de los títulos descargados: "[id]", "(Official Video)", "(Letra)"… */
        fun cleanTitle(title: String): String = title
            .replace(Regex("""\[[^]]*]"""), "")
            .replace(Regex("""\((?:official|oficial|video|audio|lyric|letra|visualizer|remaster)[^)]*\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()

        private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

        private fun sha1(value: String) = MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
