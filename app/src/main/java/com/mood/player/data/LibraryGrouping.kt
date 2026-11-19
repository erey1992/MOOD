package com.mood.player.data

enum class GroupBy(val label: String) {
    FOLDER("Carpetas"),
    ARTIST("Artistas"),
    ALBUM("Álbumes"),
    GENRE("Géneros"),
}

/**
 * Agrupa todas las canciones de la biblioteca según sus metadatos. Cada grupo se devuelve como una
 * [MusicCollection] con un path sintético (`artist:…`, `album:…`, `genre:…`) para poder abrirlo y
 * reproducirlo igual que una carpeta. Los grupos "desconocidos" van al final.
 */
object LibraryGrouping {

    const val UNKNOWN_ARTIST = "Artista desconocido"
    const val UNKNOWN_ALBUM = "Álbum desconocido"
    const val UNKNOWN_GENRE = "Sin género"
    const val SINGLES = "Singles"

    /** Grupo donde van las canciones publicadas como sencillo ("Canción - Single"): no son un álbum. */
    const val SINGLES_PATH = "album:singles"

    private val SINGLE_WORD = Regex("\\bsingle\\b", RegexOption.IGNORE_CASE)

    /** Un "álbum" cuyo nombre contiene la palabra "single" es en realidad un sencillo. */
    fun isSingle(album: String?): Boolean = album != null && SINGLE_WORD.containsMatchIn(album)

    fun group(folders: List<MusicCollection>, by: GroupBy): List<MusicCollection> {
        if (by == GroupBy.FOLDER) return folders
        val tracks = folders.flatMap { it.tracks }.distinctBy { it.uri }
        return when (by) {
            GroupBy.FOLDER -> folders
            GroupBy.ARTIST -> byArtist(tracks)
            GroupBy.ALBUM -> byAlbum(tracks)
            GroupBy.GENRE -> byGenre(tracks)
        }
    }

    /**
     * Una canción con varios artistas ("A, B", "A feat. B"…) aparece en el grupo de cada uno,
     * en lugar de crear un grupo "A, B".
     */
    private fun byArtist(tracks: List<Track>): List<MusicCollection> {
        val known = knownArtists(tracks.map { it.artist })
        val byKey = LinkedHashMap<String?, MutableList<Pair<String, Track>>>()
        tracks.forEach { track ->
            val names = artistsOf(track.artist, track.title, known)
            if (names.isEmpty()) {
                byKey.getOrPut(null) { mutableListOf() } += UNKNOWN_ARTIST to track
            } else {
                names.forEach { name -> byKey.getOrPut(name.lowercase()) { mutableListOf() } += name to track }
            }
        }
        return byKey.map { (key, entries) ->
            val items = entries.map { it.second }.distinctBy { it.uri }
            // Se muestra la grafía más frecuente del nombre.
            val name = if (key == null) UNKNOWN_ARTIST else entries.groupingBy { it.first }.eachCount().maxBy { it.value }.key
            val albums = items.mapNotNull { it.album?.takeUnless(::isSingle)?.lowercase() }.distinct().size
            MusicCollection(
                path = "artist:${key ?: "?"}",
                name = name,
                tracks = items.sortedWith(compareBy({ it.album?.lowercase() }, { it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() })),
                subtitle = listOfNotNull(countLabel(items.size), albums.takeIf { it > 0 }?.let { plural(it, "álbum", "álbumes") })
                    .joinToString(" · "),
            ) to (key == null)
        }.sortedWith(compareBy({ it.second }, { it.first.name.lowercase() })).map { it.first }
    }

    /** Separadores que siempre indican varios artistas. La barra solo con espacios: "AC/DC" es un grupo. */
    private val STRONG_SEPARATORS = Regex("""\s*(?:[,;]|\s/\s|\s(?:feat\.?|ft\.?|featuring)\s)\s*""", RegexOption.IGNORE_CASE)

    /** Separadores ambiguos: "Simon & Garfunkel" es un dúo, "David Bisbal & Aitana" son dos artistas. */
    private val WEAK_SEPARATORS = Regex("""\s+(?:&|y|x|and|e)\s+""", RegexOption.IGNORE_CASE)

    /** Artistas invitados indicados en el título: "Canción (feat. X)", "Canción ft. X". */
    private val TITLE_FEATURING = Regex("""(?:\(|\[|\s)(?:feat\.?|ft\.?|featuring)\s+([^)\]]+)""", RegexOption.IGNORE_CASE)

    /** Nombres que aparecen como artista propio en algún sitio de la biblioteca (en minúsculas). */
    /**
     * Posibles canciones repetidas: mismo título (sin "(Remastered)", "[id]", "(1)", "feat."…) y mismo artista
     * principal. Las que no tienen artista se juntan con las de su título si todas son del mismo artista.
     * Devuelve grupos de 2 o más, ordenados por título.
     */
    fun <T> duplicateGroups(items: List<T>, title: (T) -> String, artist: (T) -> String?): List<List<T>> {
        fun artistKey(item: T): String? = artist(item)
            ?.split(STRONG_SEPARATORS)?.firstOrNull()
            ?.let(AlbumInfoSource::titleKey)
            ?.takeIf { it.isNotEmpty() }

        val result = mutableListOf<Pair<String, List<T>>>()
        items.groupBy { AlbumInfoSource.titleKey(title(it)) }
            .filterKeys { it.isNotEmpty() }
            .forEach { (key, sameTitle) ->
                if (sameTitle.size < 2) return@forEach
                val (known, unknown) = sameTitle.partition { artistKey(it) != null }
                val byArtist = known.groupBy { artistKey(it)!! }
                if (byArtist.size <= 1) {
                    result += key to sameTitle
                } else {
                    // Mismo título de artistas distintos: no son la misma canción.
                    byArtist.values.filter { it.size >= 2 }.forEach { result += key to it }
                    if (unknown.size >= 2) result += key to unknown
                }
            }
        return result.sortedBy { it.first }.map { it.second }
    }

    internal fun knownArtists(artists: List<String?>): Set<String> =
        artists.flatMap { raw -> raw.orEmpty().replace('(', ' ').replace(')', ' ').split(STRONG_SEPARATORS) }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()

    /**
     * Artistas de una canción. Los separadores ambiguos ("&", "y", "x", "and") solo separan si alguna de las
     * partes ya existe como artista por sí misma en la biblioteca.
     */
    internal fun artistsOf(artist: String?, title: String?, known: Set<String>): List<String> {
        val featured = title?.let { TITLE_FEATURING.find(it)?.groupValues?.get(1) }
        return listOfNotNull(artist, featured)
            .flatMap { it.replace('(', ' ').replace(')', ' ').split(STRONG_SEPARATORS) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .flatMap { part ->
                val pieces = part.split(WEAK_SEPARATORS).map { it.trim() }.filter { it.isNotEmpty() }
                if (pieces.size > 1 && pieces.any { it.lowercase() in known }) pieces else listOf(part)
            }
            .distinctBy { it.lowercase() }
    }

    /** Resultado de quitar artistas a una canción: nuevas etiquetas de artista (null = sin artista) y título. */
    data class ArtistEdit(val artist: String?, val title: String)

    /** "Canción (feat. X)", "Canción [ft. X]" o "Canción feat. X" al final del título. */
    private val TITLE_FEATURING_PART = Regex(
        """\s*(?:[(\[]\s*(?:feat\.?|ft\.?|featuring)\s+[^)\]]+[)\]]|\s(?:feat\.?|ft\.?|featuring)\s+[^(\[]+$)""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Deja en la canción solo los artistas de [keep] (de los que da [artistsOf], más los nuevos que se añadan).
     * Si se quita algún invitado del título ("(feat. X)"), ese trozo desaparece del título y los invitados
     * que se conservan pasan a la etiqueta de artista. Si no cambia nada, se respeta el texto original.
     */
    fun editArtists(artist: String?, title: String, keep: List<String>, known: Set<String>): ArtistEdit {
        val kept = keep.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
        val keptKeys = kept.map { it.lowercase() }.toSet()
        val tagArtists = artistsOf(artist, null, known)
        val featured = TITLE_FEATURING.find(title)?.groupValues?.get(1)
        val titleArtists = artistsOf(featured, null, known)
        val dropTitlePart = titleArtists.any { it.lowercase() !in keptKeys }

        val newTitle = if (dropTitlePart) title.replace(TITLE_FEATURING_PART, "").trim().ifEmpty { title } else title
        val inTitle = if (dropTitlePart) emptySet() else titleArtists.map { it.lowercase() }.toSet()
        val parts = kept.filter { it.lowercase() !in inTitle }
        val unchanged = parts.map { it.lowercase() } == tagArtists.map { it.lowercase() }
        return ArtistEdit(
            artist = if (unchanged) artist else parts.joinToString(", ").ifEmpty { null },
            title = newTitle,
        )
    }

    /** Quita un artista de la canción (comparando sin mayúsculas) y conserva el resto. */
    fun removeArtist(artist: String?, title: String, name: String, known: Set<String>): ArtistEdit {
        val keep = artistsOf(artist, title, known).filterNot { it.equals(name.trim(), ignoreCase = true) }
        return editArtists(artist, title, keep, known)
    }

    private fun byAlbum(tracks: List<Track>): List<MusicCollection> {
        // Nombre del álbum + artista del álbum (si la etiqueta existe). No se usa ALBUM_ID de MediaStore
        // porque separa un mismo álbum cuando sus canciones están en carpetas distintas.
        val groups = tracks.groupBy { track ->
            track.album?.let { album ->
                if (isSingle(album)) SINGLES_KEY
                else listOfNotNull(album.trim().lowercase(), track.albumArtist?.trim()?.lowercase()).joinToString("|")
            }
        }
        return groups.map { (key, items) ->
            if (key == SINGLES_KEY) {
                return@map MusicCollection(
                    path = SINGLES_PATH,
                    name = SINGLES,
                    tracks = items.sortedWith(compareBy({ it.artist?.lowercase() }, { it.title.lowercase() })),
                    subtitle = countLabel(items.size),
                ) to 1
            }
            val artists = items.mapNotNull { it.albumArtist ?: it.artist }.distinctBy { it.trim().lowercase() }
            val artist = when (artists.size) {
                0 -> null
                1 -> artists.first()
                else -> "Varios artistas"
            }
            MusicCollection(
                path = "album:${key ?: "?"}",
                name = items.firstNotNullOfOrNull { it.album } ?: UNKNOWN_ALBUM,
                tracks = items.sortedWith(compareBy({ it.trackNumber ?: Int.MAX_VALUE }, { it.title.lowercase() })),
                subtitle = listOfNotNull(artist, items.mapNotNull { it.year }.maxOrNull()?.toString(), countLabel(items.size))
                    .joinToString(" · "),
            ) to (if (key == null) 2 else 0)
        }
            // Álbumes primero, luego los sencillos y al final las canciones sin álbum.
            .sortedWith(compareBy({ it.second }, { it.first.name.lowercase() }))
            .map { it.first }
    }

    private fun byGenre(tracks: List<Track>) =
        build(tracks, prefix = "genre", unknown = UNKNOWN_GENRE, label = { it.genre }) { name, items ->
            val artists = items.mapNotNull { it.artist?.lowercase() }.distinct().size
            MusicCollection(
                path = "",
                name = name,
                tracks = items.sortedWith(compareBy({ it.artist?.lowercase() }, { it.album?.lowercase() }, { it.trackNumber ?: Int.MAX_VALUE })),
                subtitle = listOfNotNull(countLabel(items.size), artists.takeIf { it > 0 }?.let { plural(it, "artista", "artistas") })
                    .joinToString(" · "),
            )
        }

    /** Agrupa sin distinguir mayúsculas y muestra la grafía más frecuente. */
    private fun build(
        tracks: List<Track>,
        prefix: String,
        unknown: String,
        label: (Track) -> String?,
        create: (name: String, items: List<Track>) -> MusicCollection,
    ): List<MusicCollection> =
        tracks.groupBy { label(it)?.trim()?.lowercase() }
            .map { (key, items) ->
                val name = if (key == null) unknown else items.mapNotNull(label).groupingBy { it.trim() }.eachCount().maxBy { it.value }.key
                create(name, items).copy(path = "$prefix:${key ?: "?"}") to (key == null)
            }
            .sortedWith(compareBy({ it.second }, { it.first.name.lowercase() }))
            .map { it.first }

    private const val SINGLES_KEY = "\u0000singles"

    private fun countLabel(n: Int) = plural(n, "canción", "canciones")

    private fun plural(n: Int, singular: String, pluralForm: String) =
        if (n == 1) "1 $singular" else "$n $pluralForm"
}
