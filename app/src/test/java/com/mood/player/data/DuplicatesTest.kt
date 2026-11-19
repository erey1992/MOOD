package com.mood.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DuplicatesTest {

    private fun groups(vararg songs: Pair<String, String?>) =
        LibraryGrouping.duplicateGroups(songs.toList(), { it.first }, { it.second })

    @Test
    fun agrupaVersionesDelMismoTituloYArtista() {
        val result = groups(
            "Ave María" to "David Bisbal",
            "Ave Maria (Remastered)" to "David Bisbal",
            "Dígale" to "David Bisbal",
        )
        assertEquals(1, result.size)
        assertEquals(2, result.first().size)
    }

    @Test
    fun copiasDeDescargasConNumeroEIdentificador() {
        val result = groups(
            "Me at the zoo [jNQXAC9IVRw]" to null,
            "Me at the zoo [jNQXAC9IVRw] (1)" to null,
            "Me at the zoo [jNQXAC9IVRw] (2)" to null,
        )
        assertEquals(listOf(3), result.map { it.size })
    }

    @Test
    fun mismoTituloDeArtistasDistintosNoEsLaMismaCancion() {
        assertEquals(emptyList<List<Pair<String, String?>>>(), groups("Ave María" to "David Bisbal", "Ave Maria" to "Schubert"))
    }

    @Test
    fun laCancionSinArtistaSeJuntaSiSoloHayUnArtista() {
        val result = groups("Ave María" to "David Bisbal", "Ave María" to null)
        assertEquals(listOf(2), result.map { it.size })
    }

    @Test
    fun cuentaElArtistaPrincipalDeUnaColaboracion() {
        val result = groups("Si Tú la Quieres" to "David Bisbal", "Si Tú la Quieres" to "David Bisbal, Aitana")
        assertEquals(listOf(2), result.map { it.size })
    }
}
