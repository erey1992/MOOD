package com.mood.player.data

import com.mood.player.data.LibraryGrouping.ArtistEdit
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistEditTest {

    private val known = LibraryGrouping.knownArtists(listOf("Shakira", "Bad Bunny", "Aitana", "David Bisbal"))

    private fun edit(artist: String?, title: String, keep: List<String>) =
        LibraryGrouping.editArtists(artist, title, keep, known)

    private fun remove(artist: String?, title: String, name: String) =
        LibraryGrouping.removeArtist(artist, title, name, known)

    @Test
    fun dejaSoloLosElegidos() {
        assertEquals(ArtistEdit("Shakira", "Canción"), edit("Shakira, Bad Bunny, Aitana", "Canción", listOf("Shakira")))
        assertEquals(ArtistEdit("Shakira, Aitana", "Canción"), edit("Shakira feat. Bad Bunny & Aitana", "Canción", listOf("Shakira", "Aitana")))
    }

    @Test
    fun ningunoQuitaLaEtiqueta() {
        assertEquals(ArtistEdit(null, "Canción"), edit("Shakira, Bad Bunny", "Canción", emptyList()))
    }

    @Test
    fun sinCambiosRespetaElTextoOriginal() {
        assertEquals(ArtistEdit("Shakira feat. Bad Bunny", "Canción"), edit("Shakira feat. Bad Bunny", "Canción", listOf("Shakira", "Bad Bunny")))
    }

    @Test
    fun anadeArtistasNuevos() {
        assertEquals(ArtistEdit("Shakira, Karol G", "Canción"), edit("Shakira", "Canción", listOf("Shakira", "Karol G")))
    }

    @Test
    fun quitarUnInvitadoDelTituloLimpiaElTitulo() {
        assertEquals(ArtistEdit("Shakira", "Canción"), remove("Shakira", "Canción (feat. Bad Bunny)", "Bad Bunny"))
        assertEquals(ArtistEdit("Shakira", "Canción"), remove("Shakira", "Canción ft. Bad Bunny", "Bad Bunny"))
        // Los invitados que se quedan pasan a la etiqueta de artista.
        assertEquals(ArtistEdit("Shakira, Aitana", "Canción"), remove("Shakira", "Canción (feat. Bad Bunny, Aitana)", "Bad Bunny"))
    }

    @Test
    fun quitarElArtistaPrincipalNoTocaElTituloSiSeConservanLosInvitados() {
        assertEquals(ArtistEdit(null, "Canción (feat. Bad Bunny)"), remove("Shakira", "Canción (feat. Bad Bunny)", "shakira"))
    }
}
