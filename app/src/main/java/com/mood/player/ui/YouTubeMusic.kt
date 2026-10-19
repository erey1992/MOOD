package com.mood.player.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/**
 * Abre YouTube Music buscando [query]. Si la app está instalada abre ese enlace directamente;
 * si no, el navegador.
 */
fun openYouTubeMusic(context: Context, query: String) {
    val uri = Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(query.trim()))
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No hay ninguna app para abrir YouTube Music.", Toast.LENGTH_SHORT).show()
    }
}

/** Búsqueda de un álbum concreto: el nombre del álbum junto a su artista, si se conoce. */
fun albumQuery(album: String, artist: String?): String = listOfNotNull(album, artist).joinToString(" ")
