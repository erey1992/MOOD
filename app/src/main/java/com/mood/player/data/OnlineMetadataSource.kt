package com.mood.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/** Busca metadatos de canciones en la API pública de iTunes (no requiere clave). */
object OnlineMetadataSource {

    suspend fun search(query: String): List<MetadataCandidate> = withContext(Dispatchers.IO) {
        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        val results = searchIn(query, country)
        if (results.isEmpty() && country != "US") searchIn(query, "US") else results
    }

    private fun searchIn(query: String, country: String): List<MetadataCandidate> {
        val term = URLEncoder.encode(query, "UTF-8")
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=20&country=$country&term=$term"
        val json = JSONObject(String(Http.get(url), Charsets.UTF_8))
        val items = json.optJSONArray("results") ?: return emptyList()

        return (0 until items.length()).map { items.getJSONObject(it) }.map { item ->
            val trackNumber = item.optInt("trackNumber", 0)
            val trackCount = item.optInt("trackCount", 0)
            val thumbnail = item.optString("artworkUrl100").takeIf { it.isNotBlank() }
            MetadataCandidate(
                tags = TrackTags(
                    title = item.optString("trackName").takeIf { it.isNotBlank() },
                    artist = item.optString("artistName").takeIf { it.isNotBlank() },
                    album = item.optString("collectionName").takeIf { it.isNotBlank() },
                    year = item.optString("releaseDate").take(4).takeIf { it.length == 4 },
                    genre = item.optString("primaryGenreName").takeIf { it.isNotBlank() },
                    trackNumber = when {
                        trackNumber <= 0 -> null
                        trackCount > 0 -> "$trackNumber/$trackCount"
                        else -> "$trackNumber"
                    },
                ),
                durationMs = item.optLong("trackTimeMillis", 0).takeIf { it > 0 },
                artworkUrl = thumbnail?.replace("100x100bb", "600x600bb"),
                thumbnailUrl = thumbnail,
            )
        }
    }
}
