package com.mood.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistSplitTest {

    private val library = listOf(
        "David Bisbal",
        "David Bisbal, Aitana",
        "Simon & Garfunkel",
        "AC/DC",
        "Shakira",
        "Bad Bunny",
    )
    private val known = LibraryGrouping.knownArtists(library)

    private fun split(artist: String?, title: String? = null) = LibraryGrouping.artistsOf(artist, title, known)

    @Test
    fun separaComasYFeat() {
        assertEquals(listOf("David Bisbal", "Aitana"), split("David Bisbal, Aitana"))
        assertEquals(listOf("Shakira", "Bad Bunny"), split("Shakira feat. Bad Bunny"))
        assertEquals(listOf("Shakira", "Bad Bunny"), split("Shakira (ft. Bad Bunny)"))
        assertEquals(listOf("Shakira", "Bad Bunny"), split("Shakira / Bad Bunny"))
    }

    @Test
    fun separadoresAmbiguosSoloSiAlgunaParteEsArtistaConocido() {
        // Aitana aparece como artista en otra canción, así que "&" separa.
        assertEquals(listOf("David Bisbal", "Aitana"), split("David Bisbal & Aitana"))
        assertEquals(listOf("Shakira", "Desconocido"), split("Shakira y Desconocido"))
        // Nadie llamado "Simon" ni "Garfunkel" por separado: es un dúo.
        assertEquals(listOf("Simon & Garfunkel"), split("Simon & Garfunkel"))
    }

    @Test
    fun noRompeNombresConBarra() {
        assertEquals(listOf("AC/DC"), split("AC/DC"))
    }

    @Test
    fun anadeInvitadosDelTitulo() {
        assertEquals(listOf("David Bisbal", "Aitana"), split("David Bisbal", "Si Tú La Quieres (feat. Aitana)"))
    }

    @Test
    fun sinArtistaNoDevuelveNada() {
        assertEquals(emptyList<String>(), split(null))
        assertEquals(emptyList<String>(), split("   "))
    }

    @Test
    fun detectaSencillos() {
        assertEquals(true, LibraryGrouping.isSingle("Si Tú la Quieres - Single"))
        assertEquals(true, LibraryGrouping.isSingle("Despacito (Single)"))
        assertEquals(true, LibraryGrouping.isSingle("SINGLE"))
        assertEquals(false, LibraryGrouping.isSingle("Corazón Latino"))
        // "Singles" o "singleton" no son la palabra "single".
        assertEquals(false, LibraryGrouping.isSingle("The Singles Collection"))
        assertEquals(false, LibraryGrouping.isSingle(null))
    }

    @Test
    fun noRepiteElMismoArtista() {
        assertEquals(listOf("Shakira"), split("Shakira, shakira"))
    }
}
