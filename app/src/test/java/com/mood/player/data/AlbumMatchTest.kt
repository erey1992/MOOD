package com.mood.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumMatchTest {

    @Test
    fun normalizaTitulos() {
        assertEquals("vuelvo a ti", AlbumInfoSource.titleKey("Vuelvo A Tí"))
        assertEquals("ave maria", AlbumInfoSource.titleKey("Ave María (Remasterizado)"))
        assertEquals("dimelo", AlbumInfoSource.titleKey("Dímelo - Remastered 2011"))
        assertEquals("si tu la quieres", AlbumInfoSource.titleKey("Si Tú la Quieres feat. Aitana"))
    }

    @Test
    fun emparejaLasQueTienesYMarcaLasQueFaltan() {
        val official = listOf("Ave María", "Dígale", "Vuelvo A Tí", "Corazón Latino")
        val local = listOf("Corazón latino", "Ave Maria (Radio Edit)", "Otra canción")
        assertEquals(listOf(1, null, null, 0), AlbumInfoSource.matchTitles(official, local))
    }

    @Test
    fun cadaCancionTuyaSeUsaUnaSolaVez() {
        val official = listOf("Intro", "Intro")
        val local = listOf("Intro")
        assertEquals(listOf(0, null), AlbumInfoSource.matchTitles(official, local))
    }
}
