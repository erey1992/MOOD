package com.mood.player.data

/** Campos editables de una pista y los frames ID3v2 que los representan (el primero es el que se escribe). */
enum class TagField(val label: String, val frameIds: List<String>) {
    TITLE("Título", listOf("TIT2")),
    ARTIST("Artista", listOf("TPE1")),
    ALBUM("Álbum", listOf("TALB")),
    YEAR("Año", listOf("TYER", "TDRC")),
    GENRE("Género", listOf("TCON")),
    TRACK("Nº de pista", listOf("TRCK")),
    ARTWORK("Carátula", listOf("APIC")),
}

data class TrackTags(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val trackNumber: String? = null,
) {
    operator fun get(field: TagField): String? = when (field) {
        TagField.TITLE -> title
        TagField.ARTIST -> artist
        TagField.ALBUM -> album
        TagField.YEAR -> year
        TagField.GENRE -> genre
        TagField.TRACK -> trackNumber
        TagField.ARTWORK -> null
    }

    fun with(field: TagField, value: String?): TrackTags = when (field) {
        TagField.TITLE -> copy(title = value)
        TagField.ARTIST -> copy(artist = value)
        TagField.ALBUM -> copy(album = value)
        TagField.YEAR -> copy(year = value)
        TagField.GENRE -> copy(genre = value)
        TagField.TRACK -> copy(trackNumber = value)
        TagField.ARTWORK -> this
    }

    companion object {
        val TEXT_FIELDS = TagField.entries.filter { it != TagField.ARTWORK }
    }
}

/** Un resultado de la búsqueda en internet. */
data class MetadataCandidate(
    val tags: TrackTags,
    val durationMs: Long?,
    val artworkUrl: String?,
    val thumbnailUrl: String?,
)
