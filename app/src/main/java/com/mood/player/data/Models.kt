package com.mood.player.data

import android.net.Uri

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    /** Nombre y ruta del fichero; solo se conocen para las pistas indexadas por MediaStore. */
    val fileName: String? = null,
    val path: String? = null,
    val genre: String? = null,
    /** Etiqueta «artista del álbum»: distingue discos homónimos de artistas distintos. */
    val albumArtist: String? = null,
    /** Formato MediaStore: disco * 1000 + pista (p. ej. 1003 = disco 1, pista 3). */
    val trackNumber: Int? = null,
    val year: Int? = null,
)

/** Una colección es una carpeta física del dispositivo que contiene archivos MP3. */
data class MusicCollection(
    val path: String,
    val name: String,
    val tracks: List<Track>,
    /** Texto secundario para la lista (p. ej. "12 canciones · 3 álbumes"). */
    val subtitle: String? = null,
)
