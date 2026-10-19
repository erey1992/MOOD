package com.mood.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class AlbumTrackInfo(val disc: Int, val number: Int, val title: String, val durationMs: Long?)

/** Ficha oficial de un álbum según iTunes. */
data class AlbumInfo(
    val name: String,
    val artist: String,
    val year: String?,
    val artworkUrl: String?,
    val tracks: List<AlbumTrackInfo>,
)

/** Busca la lista oficial de canciones de un álbum en la API pública de iTunes (sin clave). */
object AlbumInfoSource {

    private val cache = ConcurrentHashMap<String, AlbumInfo>()

    /** null si no se encuentra un álbum que coincida con el nombre. */
    suspend fun find(album: String, artist: String?): AlbumInfo? = withContext(Dispatchers.IO) {
        val cacheKey = "${titleKey(album)}|${artist?.let(::titleKey).orEmpty()}"
        cache[cacheKey]?.let { return@withContext it }

        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        val countries = listOf(country, "US").distinct()
        for (store in countries) {
            val best = searchAlbums(album, artist, store) ?: continue
            val info = lookupTracks(best, store)
            if (info.tracks.isNotEmpty()) {
                cache[cacheKey] = info
                return@withContext info
            }
        }
        null
    }

    private fun searchAlbums(album: String, artist: String?, country: String): JSONObject? {
        val term = enc(listOfNotNull(album, artist).joinToString(" "))
        val json = JSONObject(String(Http.get("https://itunes.apple.com/search?media=music&entity=album&limit=15&country=$country&term=$term"), Charsets.UTF_8))
        val results = json.optJSONArray("results") ?: return null
        val wantedName = titleKey(album)
        val wantedArtist = artist?.let(::titleKey)
        // El nombre tiene que coincidir; el artista desempata entre ediciones homónimas.
        return (0 until results.length()).map { results.getJSONObject(it) }
            .map { item ->
                val name = titleKey(item.optString("collectionName"))
                val nameScore = when {
                    name == wantedName -> 3
                    name.contains(wantedName) || wantedName.contains(name) -> 2
                    else -> 0
                }
                val itemArtist = titleKey(item.optString("artistName"))
                val artistScore = if (wantedArtist != null && (itemArtist.contains(wantedArtist) || wantedArtist.contains(itemArtist))) 1 else 0
                item to (if (nameScore == 0) 0 else nameScore + artistScore)
            }
            .filter { it.second > 0 }
            .maxByOrNull { it.second }
            ?.first
    }

    private fun lookupTracks(album: JSONObject, country: String): AlbumInfo {
        val id = album.getLong("collectionId")
        val json = JSONObject(String(Http.get("https://itunes.apple.com/lookup?id=$id&entity=song&country=$country"), Charsets.UTF_8))
        val results = json.optJSONArray("results")
        val tracks = (0 until (results?.length() ?: 0)).map { results!!.getJSONObject(it) }
            .filter { it.optString("wrapperType") == "track" }
            .map {
                AlbumTrackInfo(
                    disc = it.optInt("discNumber", 1),
                    number = it.optInt("trackNumber", 0),
                    title = it.optString("trackName"),
                    durationMs = it.optLong("trackTimeMillis", 0).takeIf { ms -> ms > 0 },
                )
            }
            .sortedWith(compareBy({ it.disc }, { it.number }))
        return AlbumInfo(
            name = album.optString("collectionName"),
            artist = album.optString("artistName"),
            year = album.optString("releaseDate").take(4).takeIf { it.length == 4 },
            artworkUrl = album.optString("artworkUrl100").takeIf { it.isNotBlank() }?.replace("100x100bb", "300x300bb"),
            tracks = tracks,
        )
    }

    /**
     * Empareja cada canción oficial con una de las tuyas por título. Devuelve, para cada índice de [official],
     * el índice de [local] que le corresponde (o null si falta). Cada canción tuya se usa una sola vez.
     */
    fun matchTitles(official: List<String>, local: List<String>): List<Int?> {
        val localKeys = local.map(::titleKey)
        val used = BooleanArray(local.size)
        fun take(predicate: (String) -> Boolean): Int? =
            localKeys.indices.firstOrNull { !used[it] && predicate(localKeys[it]) }?.also { used[it] = true }
        val officialKeys = official.map(::titleKey)
        // Primero las coincidencias exactas; después las parciales ("Vuelvo a ti" ⊂ "Vuelvo a ti (Remasterizado)").
        val result = officialKeys.map { key -> take { it == key } }.toMutableList()
        officialKeys.forEachIndexed { i, key ->
            if (result[i] == null && key.length >= 4) {
                result[i] = take { it.length >= 4 && (it.contains(key) || key.contains(it)) }
            }
        }
        return result
    }

    /**
     * Clave para comparar títulos: sin tildes, en minúsculas, sin paréntesis ni corchetes, sin "feat." y sin
     * coletillas tras " - " (p. ej. "- Remastered 2011").
     */
    fun titleKey(title: String): String = Normalizer.normalize(title, Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .lowercase()
        .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), " ")
        .replace(Regex("\\s(feat\\.?|ft\\.?|featuring)\\s.*$"), " ")
        .replace(Regex("\\s-\\s.*$"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
}
