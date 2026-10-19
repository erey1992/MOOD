package com.mood.player.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import com.mood.player.download.DownloadConflict
import com.mood.player.download.DownloadItem
import com.mood.player.download.DownloadPhase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DownloadTaskCardTest {
    @get:Rule val rule = createComposeRule()
    private val song = DownloadItem(url = "https://www.youtube.com/watch?v=jNQXAC9IVRw", folderUri = "content://folder", folderName = "Música", title = "Mi canción", author = "Mi artista", durationMs = 185000)

    @Test fun clearIconWorksEvenWhenAllRowsFailed() {
        var clears = 0
        rule.setContent { MoodTheme { DownloadQueueHeading(2, false) { clears++ } } }
        rule.onNodeWithContentDescription("Limpiar lista").performClick()
        assertEquals(1, clears)
    }

    @Test fun clearIconIsDisabledForEmptyList() {
        rule.setContent { MoodTheme { DownloadQueueHeading(0, false) {} } }
        rule.onNodeWithContentDescription("Limpiar lista").assertIsNotEnabled()
    }

    @Test fun showsSongDetailsAndRealProgress() {
        rule.setContent { MoodTheme { DownloadTaskCard(song.copy(phase = DownloadPhase.DOWNLOADING, progress = 0.42f), 1, {}, {}, {}) } }
        rule.onNodeWithText("Mi canción").assertExists()
        rule.onNodeWithText("Mi artista").assertExists()
        rule.onNodeWithText("Duración: 3:05").assertExists()
        rule.onNodeWithText("1 · Descargando · 42 %").assertExists()
    }

    @Test fun failedSongCanBeRetriedIndividually() {
        var retries = 0
        rule.setContent { MoodTheme { DownloadTaskCard(song.copy(phase = DownloadPhase.FAILED, message = "Sin conexión"), 2, { retries++ }, {}, {}) } }
        rule.onNodeWithText("Sin conexión").assertExists()
        rule.onNodeWithText("Reintentar").performClick()
        assertEquals(1, retries)
    }

    @Test fun existingFileOffersBothChoicesOnItsOwnCard() {
        var choice: Boolean? = null
        rule.setContent { MoodTheme { DownloadTaskCard(song.copy(phase = DownloadPhase.WAITING_CHOICE, conflict = DownloadConflict("1", "Mi canción.mp3")), 3, {}, {}, { choice = it }) } }
        rule.onNodeWithText("Omitir").performClick()
        assertEquals(false, choice)
        rule.onNodeWithText("Reemplazar").performClick()
        assertEquals(true, choice)
    }
}
