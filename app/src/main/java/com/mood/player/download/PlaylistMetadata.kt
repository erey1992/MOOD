package com.mood.player.download

import org.json.JSONObject

data class SongMetadata(val url: String, val title: String?, val author: String?, val durationMs: Long?)
data class PlaylistMetadata(val title: String?, val songs: List<SongMetadata>, val unavailable: Int)

fun parseSongMetadata(info: JSONObject, fallbackUrl: String? = null): SongMetadata? {
    val id = info.text("id")?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
    val url = id?.let { "https://www.youtube.com/watch?v=$it" }
        ?: sequenceOf(info.text("webpage_url"), info.text("url"), fallbackUrl).filterNotNull().mapNotNull(::youtubeVideoUrl).firstOrNull()
        ?: return null
    val duration = info.optDouble("duration", Double.NaN)
    return SongMetadata(
        url, info.text("track") ?: info.text("title"),
        sequenceOf("artist", "creator", "uploader", "channel").mapNotNull(info::text).firstOrNull(),
        if (duration.isFinite() && duration >= 0) (duration * 1000).toLong() else null,
    )
}

fun parsePlaylistMetadata(json: String): PlaylistMetadata {
    val info = JSONObject(json)
    val entries = info.optJSONArray("entries") ?: throw IllegalArgumentException("El enlace no devolvió una lista de reproducción.")
    val songs = (0 until entries.length()).mapNotNull { index ->
        entries.optJSONObject(index)?.let { parseSongMetadata(it) }
    }
    return PlaylistMetadata(info.text("title"), songs.distinctBy { it.url }, entries.length() - songs.size)
}
