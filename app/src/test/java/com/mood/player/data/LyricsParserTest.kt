package com.mood.player.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricsParserTest {

    @Test
    fun interpretaMarcasDeTiempoYVariasPorLinea() {
        val lines = LyricsRepository.parseLrc(
            """
            [ar:Artista]
            [00:12.34] Primera línea
            [00:05.5]Intro
            [01:02.00][02:10.123] Estribillo
            [00:20.00]
            """.trimIndent()
        )
        assertEquals(
            listOf(
                LyricLine(5_500, "Intro"),
                LyricLine(12_340, "Primera línea"),
                LyricLine(20_000, ""),
                LyricLine(62_000, "Estribillo"),
                LyricLine(130_123, "Estribillo"),
            ),
            lines,
        )
    }

    @Test
    fun aplicaElOffset() {
        val lines = LyricsRepository.parseLrc("[offset:+500]\n[00:10.00]Hola")
        assertEquals(listOf(LyricLine(9_500, "Hola")), lines)
    }

    @Test
    fun limpiaTitulosDeDescargas() {
        assertEquals("David Bisbal - Ave María", LyricsRepository.cleanTitle("David Bisbal - Ave María [gra-sIV1n4U]"))
        assertEquals("Despacito", LyricsRepository.cleanTitle("Despacito (Official Video)"))
    }
}
