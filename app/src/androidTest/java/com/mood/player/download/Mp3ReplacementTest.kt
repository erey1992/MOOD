package com.mood.player.download

import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import java.util.UUID

class Mp3ReplacementTest {
    private fun withFiles(test: (Context, File, File, File) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dir = File(context.cacheDir, "replacement-test-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val original = File(dir, "Mi canción.mp3").apply { writeText("original intacto") }
            val source = File(context.cacheDir, "source-test-${UUID.randomUUID()}").apply { writeText("MP3 nuevo") }
            try { test(context, dir, original, source) } finally { source.delete() }
        } finally { dir.deleteRecursively() }
    }

    @Test fun replacementKeepsNameAndRegistersOnlyCompletedFile() = withFiles { context, dir, original, source ->
        runBlocking {
            var registered = false
            val result = replaceMp3(context, DocumentFile.fromFile(dir), DocumentFile.fromFile(original), source) {
                assertEquals("MP3 nuevo", File(it.uri.path!!).readText())
                registered = true
            }
            assertTrue(registered)
            assertEquals(original.name, result.name)
            assertEquals("MP3 nuevo", original.readText())
            assertEquals(listOf(original.name), dir.listFiles()!!.map { it.name })
        }
    }

    @Test fun failedRegistrationRestoresOriginalAndRemovesTemporaryFiles() = withFiles { context, dir, original, source ->
        runBlocking {
            try {
                replaceMp3(context, DocumentFile.fromFile(dir), DocumentFile.fromFile(original), source) {
                    throw IOException("Fallo al registrar")
                }
                fail("Debió fallar el registro")
            } catch (_: IOException) { }
            assertEquals("original intacto", original.readText())
            assertEquals(listOf(original.name), dir.listFiles()!!.map { it.name })
        }
    }

    @Test fun missingSourceLeavesOriginalUntouched() = withFiles { context, dir, original, source ->
        source.delete()
        runBlocking {
            try {
                replaceMp3(context, DocumentFile.fromFile(dir), DocumentFile.fromFile(original), source) { fail("No debe registrarse") }
                fail("Debió fallar la lectura")
            } catch (_: IOException) { }
            assertEquals("original intacto", original.readText())
            assertEquals(listOf(original.name), dir.listFiles()!!.map { it.name })
        }
    }
}
