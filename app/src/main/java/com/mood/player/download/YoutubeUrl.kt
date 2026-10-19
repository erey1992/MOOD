package com.mood.player.download

import java.net.URI
import java.net.URLDecoder

data class YoutubeLink(val url: String, val isPlaylist: Boolean)

/** A watch link remains one song; an explicit /playlist link queues the whole list. */
fun youtubeLink(input: String): YoutubeLink? {
    youtubeVideoUrl(input)?.let { return YoutubeLink(it, false) }
    val candidate = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE).find(input.trim())?.value ?: return null
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    if (uri.host?.lowercase() !in setOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com") || uri.userInfo != null || uri.port != -1) return null
    if (uri.path !in setOf("/playlist", "/embed/videoseries", "/watch")) return null
    if (uri.path == "/watch" && uri.rawQuery.orEmpty().split('&').any { it.substringBefore('=') == "v" }) return null
    val list = runCatching {
        uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "list" }
            ?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }
    }.getOrNull()?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{2,200}")) } ?: return null
    return YoutubeLink("https://www.youtube.com/playlist?list=$list", true)
}

/** Accept individual videos and discard playlist/tracking parameters before invoking yt-dlp. */
fun youtubeVideoUrl(input: String): String? {
    val candidate = Regex("https?://[^\\s<>]+", RegexOption.IGNORE_CASE)
        .find(input.trim())?.value ?: return null
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (uri.userInfo != null || uri.port != -1) return null
    val segments = uri.path.orEmpty().trim('/').split('/')
    val id = when (host) {
        "youtu.be" -> segments.singleOrNull()
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com" -> when {
            uri.path == "/watch" -> runCatching {
                uri.rawQuery.orEmpty().split('&').firstOrNull { it.substringBefore('=') == "v" }
                    ?.substringAfter('=', "")?.let { URLDecoder.decode(it, "UTF-8") }
            }.getOrNull()
            uri.path == "/embed/videoseries" -> null
            segments.size == 2 && segments[0] in listOf("shorts", "live", "embed") -> segments[1]
            else -> null
        }
        else -> null
    }
    return id?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        ?.let { "https://www.youtube.com/watch?v=$it" }
}
