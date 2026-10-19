package com.mood.player.download

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class DownloadQueueTest {
    @Test fun clearRemovesEveryPhaseAndLateCallbacksCannotRestoreRows() {
        val previous = DownloadStatus.state.value
        try {
            val songs = DownloadPhase.entries.map { phase -> DownloadItem(id = phase.name,
                url = "https://www.youtube.com/watch?v=jNQXAC9IVRw", folderUri = "content://folder", folderName = "Music", phase = phase) }
            DownloadStatus.mutable.value = DownloadState(items = songs, resolving = true, completionVersion = 7,
                inputError = "Error", failedInputUrl = "old link", failedInputFolder = "old folder")
            DownloadStatus.clearAll()
            DownloadStatus.changeItem(DownloadPhase.DOWNLOADING.name) { it.copy(progress = 0.9f) }
            DownloadStatus.changeItem(DownloadPhase.FAILED.name) { it.copy(phase = DownloadPhase.CANCELLED) }
            assertTrue(DownloadStatus.state.value.items.isEmpty())
            assertTrue(DownloadStatus.state.value.resolving)
            assertEquals(7L, DownloadStatus.state.value.completionVersion)
            assertNull(DownloadStatus.state.value.inputError)
            assertNull(DownloadStatus.state.value.failedInputUrl)
            assertNull(DownloadStatus.state.value.failedInputFolder)
        } finally { DownloadStatus.mutable.value = previous; DownloadStatus.save() }
    }

    @Test fun simultaneousRegistryUpdatesDoNotLoseSongs() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "registry-test-${UUID.randomUUID()}"
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
        }
        runBlocking {
            (1..12).map { index -> async(Dispatchers.IO) {
                DownloadStore(isolated).remember(android.net.Uri.parse("content://test/song/$index"), android.net.Uri.parse("content://test/folder"), "$index.mp3")
            } }.awaitAll()
        }
        val prefs = isolated.getSharedPreferences("audio_downloads", Context.MODE_PRIVATE)
        val entries = org.json.JSONArray(prefs.getString("files", "[]"))
        assertEquals(12, entries.length())
        prefs.edit().clear().commit()
    }

    @Test fun parallelCompletionsAndProgressRemainIndependent() {
        val previous = DownloadStatus.state.value
        try {
            val songs = (1..8).map { DownloadItem(id = "$it", url = "https://www.youtube.com/watch?v=jNQXAC9IVRw", folderUri = "content://folder/$it", folderName = "$it") }
            DownloadStatus.mutable.value = DownloadState(items = songs)
            runBlocking {
                songs.map { song -> async(Dispatchers.IO) {
                    DownloadStatus.changeItem(song.id, persist = false) { it.copy(phase = DownloadPhase.DOWNLOADING, progress = 0.5f) }
                    DownloadStatus.completed(song.id, "content://saved/${song.id}")
                } }.awaitAll()
            }
            assertEquals(8L, DownloadStatus.state.value.completionVersion)
            assertEquals(8, DownloadStatus.state.value.items.count { it.phase == DownloadPhase.COMPLETED })
            assertEquals(8, DownloadStatus.state.value.items.map { it.savedUri }.distinct().size)
        } finally { DownloadStatus.mutable.value = previous; DownloadStatus.save() }
    }
}
