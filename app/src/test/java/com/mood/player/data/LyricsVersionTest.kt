package com.mood.player.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsVersionTest {

    private fun version(id: Long, seconds: Long?, synced: Boolean = true, plain: Boolean = true) = LyricsVersion(
        id = id,
        track = "Ave María",
        artist = "David Bisbal",
        album = null,
        durationMs = seconds?.times(1000),
        synced = if (synced) "[00:01.00]Línea $id" else null,
        plain = if (plain) "Línea $id" else null,
        instrumental = false,
    )

    @Test
    fun eligeLaDeDuracionMasParecidaNoLaPrimera() {
        // El fichero dura 3:29 (209 s): la primera de la lista (214 s) también entra en el margen,
        // pero la de 210 s se parece más.
        val versions = listOf(version(1, 214), version(2, 369), version(3, 210))
        assertEquals(3L, LyricsRepository.pickBest(versions, 209_000)?.versionId)
    }

    @Test
    fun prefiereLaSincronizadaAunqueOtraSinTiemposCoincidaMejor() {
        val versions = listOf(version(1, 209, synced = false), version(2, 211))
        val best = LyricsRepository.pickBest(versions, 209_000)
        assertEquals(2L, best?.versionId)
        assertTrue(best?.lyrics is Lyrics.Synced)
    }

    @Test
    fun ningunaEncajaSiLaDuracionEsMuyDistinta() {
        assertNull(LyricsRepository.pickBest(listOf(version(1, 369)), 209_000))
    }

    @Test
    fun ordenaLasVersionesPorParecidoYDejaAlFinalLasSinDuracion() {
        val sorted = LyricsRepository.sortByCloseness(listOf(version(1, null), version(2, 300), version(3, 208)), 209_000)
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }
}
