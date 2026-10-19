package com.mood.player.ui

import android.net.Uri
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mood.player.data.Track
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Comprueba el ▶ al pasar el ratón sobre una canción y el ⏸ funcional mientras suena. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TrackRowHoverTest {

    @get:Rule
    val rule = createComposeRule()

    private val track = Track(
        id = 1,
        uri = Uri.parse("content://media/external/audio/media/999999999"),
        title = "Canción de prueba",
        artist = "Artista",
        album = null,
        durationMs = 1000,
    )

    private fun showRow(isCurrent: Boolean, isPlaying: Boolean, onArtClick: () -> Unit = {}) {
        rule.setContent {
            MoodTheme {
                TrackRow(
                    track = track,
                    index = 0,
                    isCurrent = isCurrent,
                    isPlaying = isPlaying,
                    onArtClick = onArtClick,
                    selectionMode = false,
                    isSelected = false,
                    onClick = {},
                    onLongClick = {},
                    onRename = {},
                    onMove = {},
                    onDelete = {},
                    onAddToMood = {},
                    onRemoveFromMood = null,
                )
            }
        }
    }

    @Test
    fun hoverSobreLaFilaMuestraPlay() {
        showRow(isCurrent = false, isPlaying = false)
        rule.onNodeWithContentDescription("Reproducir").assertDoesNotExist()

        rule.onNodeWithText("Canción de prueba").performMouseInput { enter(center) }
        rule.onNodeWithContentDescription("Reproducir").assertExists()

        rule.onNodeWithText("Canción de prueba").performMouseInput { exit() }
        rule.waitForIdle()
        rule.onNodeWithContentDescription("Reproducir").assertDoesNotExist()
    }

    @Test
    fun mientrasSuenaMuestraPausaYAlTocarlaPausa() {
        var clicks = 0
        showRow(isCurrent = true, isPlaying = true, onArtClick = { clicks++ })
        rule.onNodeWithContentDescription("Pausar").assertExists().performClick()
        assertEquals(1, clicks)
    }
}
