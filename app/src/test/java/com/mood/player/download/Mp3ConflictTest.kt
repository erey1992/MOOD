package com.mood.player.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mp3ConflictTest {
    @Test fun recognizesExistingVideoDespiteTitleChangeAndNumberedCopies() {
        assertTrue(isMp3ForVideo("Título anterior [jNQXAC9IVRw].mp3", "jNQXAC9IVRw"))
        assertTrue(isMp3ForVideo("Título [jNQXAC9IVRw] (2).MP3", "jNQXAC9IVRw"))
    }

    @Test fun doesNotMatchOtherVideosOrOtherFormats() {
        assertFalse(isMp3ForVideo("Título [BaW_jenozKc].mp3", "jNQXAC9IVRw"))
        assertFalse(isMp3ForVideo("Título [jNQXAC9IVRw].webm", "jNQXAC9IVRw"))
        assertFalse(isMp3ForVideo("jNQXAC9IVRw.mp3", "jNQXAC9IVRw"))
        assertFalse(isMp3ForVideo("Título [jnQXAC9IVRw].mp3", "jNQXAC9IVRw"))
    }
}
