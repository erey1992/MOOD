package com.mood.player.download

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.mood.player.data.Http
import com.mood.player.data.TagRepository
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class YoutubeDownloadEngine(private val context: Context) {
    private val initialization = Mutex()
    private val conversionSlots = Semaphore(1)
    private var ready = false

    suspend fun ensureReady() = initialization.withLock {
        if (ready) return@withLock
        runInterruptible { YoutubeDL.getInstance().init(context) }
        val store = DownloadStore(context)
        if (System.currentTimeMillis() - store.lastUpdate > 86_400_000L) {
            val release = File(context.cacheDir, "release-${UUID.randomUUID()}.json")
            try {
                runInterruptible {
                    release.writeBytes(Http.get("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest"))
                    YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel(release.toURI().toString()))
                }
                store.lastUpdate = System.currentTimeMillis()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Use the installed engine if the update fails. */ }
            finally { release.delete() }
        }
        currentCoroutineContext().ensureActive()
        ready = true
    }

    private fun request(url: String) = YoutubeDLRequest(url).apply {
        addOption("--no-config"); addOption("--socket-timeout", 20)
        addOption("--retries", 2); addOption("--no-colors")
    }

    suspend fun playlist(url: String, processId: String): PlaylistMetadata {
        ensureReady()
        val request = request(url).apply {
            addOption("--yes-playlist"); addOption("--flat-playlist")
            addOption("--dump-single-json"); addOption("--ignore-errors")
        }
        val response = runInterruptible { YoutubeDL.getInstance().execute(request, "$processId-list", null) }
        return parsePlaylistMetadata(response.out).also {
            if (it.songs.isEmpty()) throw IOException("No se encontraron canciones accesibles en la lista.")
        }
    }

    fun cancel(id: String) {
        for (suffix in listOf("list", "metadata", "audio")) YoutubeDL.getInstance().destroyProcessById("$id-$suffix")
    }

    suspend fun song(url: String, processId: String): SongMetadata {
        ensureReady()
        val infoRequest = request(url).apply { addOption("--no-playlist"); addOption("--dump-single-json") }
        val response = runInterruptible { YoutubeDL.getInstance().execute(infoRequest, "$processId-metadata", null) }
        return parseSongMetadata(JSONObject(response.out), url) ?: throw IOException("No se pudieron consultar los datos de la canción.")
    }

    suspend fun download(
        item: DownloadItem,
        processId: String,
        metadata: (SongMetadata) -> Unit,
        phase: (DownloadPhase, Float?, String?) -> Unit,
        chooseReplacement: suspend (DocumentFile) -> Boolean,
        saved: (Uri) -> Unit,
    ) {
        val tree = Uri.parse(item.folderUri)
        val folder = DocumentFile.fromTreeUri(context, tree)?.takeIf { it.isDirectory && it.canWrite() }
            ?: throw IOException("No se puede escribir en la carpeta. Vuelve a elegirla para conceder acceso.")
        val videoId = item.url.substringAfterLast('=')
        var replacement = folder.listFiles().filter { it.isFile && isMp3ForVideo(it.name.orEmpty(), videoId) }
            .sortedBy { if (it.name.orEmpty().endsWith("[$videoId].mp3")) 0 else 1 }.firstOrNull()
        var song = SongMetadata(item.url, item.title, item.author, item.durationMs)
        replacement?.let { existing ->
            runCatching { TagRepository(context).read(existing.uri) }.getOrNull()?.let { local ->
                song = song.copy(title = song.title ?: local.tags.title ?: existing.name,
                    author = song.author ?: local.tags.artist, durationMs = song.durationMs ?: local.durationMs)
                metadata(song)
            }
            if (!chooseReplacement(existing)) throw DownloadSkippedException()
        }
        val temp = File(context.cacheDir, "mp3-${item.id}-${UUID.randomUUID()}")
        check(temp.mkdirs()) { "No se pudo crear el archivo temporal. Comprueba el espacio disponible." }
        var destination: DocumentFile? = null
        var committed = false
        try {
            phase(DownloadPhase.METADATA, null, "Preparando descarga…")
            ensureReady()
            if (song.title == null || song.durationMs == null) {
                song = this.song(item.url, processId)
                metadata(song)
            }
            val audioRequest = request(item.url).apply {
                addOption("--no-playlist"); addOption("-f", "bestaudio/best")
                addOption("--fixup", "never"); addOption("--write-info-json")
                addOption("--no-mtime"); addOption("--newline")
                addOption("-o", "${temp.absolutePath}/source.%(ext)s")
            }
            phase(DownloadPhase.DOWNLOADING, 0f, null)
            runInterruptible {
                YoutubeDL.getInstance().execute(audioRequest, "$processId-audio") { progress, _, _ ->
                    phase(DownloadPhase.DOWNLOADING, progress.takeIf { it.isFinite() && it >= 0 }?.let { (it / 100).coerceIn(0f, 1f) }, null)
                }
            }
            currentCoroutineContext().ensureActive()
            val source = temp.listFiles()?.singleOrNull {
                it.name.startsWith("source.") && it.extension !in listOf("json", "part", "ytdl") && it.length() > 0
            } ?: throw IOException("No se pudo encontrar el audio descargado.")
            parseSongMetadata(JSONObject(File(temp, "source.info.json").readText()), item.url)?.let { song = it; metadata(it) }
            val mp3 = File(temp, "converted.mp3")
            phase(DownloadPhase.CONVERTING, null, "Esperando conversión…")
            conversionSlots.withPermit {
                phase(DownloadPhase.CONVERTING, null, "Convirtiendo a MP3…")
                convert(source, mp3, song.title ?: "Audio de YouTube", song.author)
            }
            check(mp3.length() > 0) { "La conversión no produjo un MP3." }
            currentCoroutineContext().ensureActive()
            val title = (song.title ?: "Audio").replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
                .take(140).trim().trimEnd('.', ' ').ifBlank { "Audio" }
            val name = "$title [$videoId].mp3"
            phase(DownloadPhase.SAVING, null, null)
            if (replacement?.exists() != true) {
                replacement = folder.findFile(name)
                if (replacement != null && !replacement.isFile) throw IOException("Ya existe una carpeta con el nombre del MP3.")
                if (replacement != null && !chooseReplacement(replacement)) throw DownloadSkippedException()
            }
            val store = DownloadStore(context)
            replacement?.let { original ->
                val originalUri = original.uri
                phase(DownloadPhase.SAVING, null, "Reemplazando el MP3…")
                replaceMp3(context, folder, original, mp3) { file ->
                    store.remember(file.uri, tree, file.name ?: name, originalUri)
                    saved(file.uri)
                }
                return
            }
            destination = folder.createFile("audio/mpeg", name) ?: throw IOException("No se pudo crear el MP3 en la carpeta elegida.")
            val coroutine = currentCoroutineContext()
            context.contentResolver.openOutputStream(destination.uri, "w")?.use { output ->
                mp3.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutine.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                    }
                }
                output.flush()
            } ?: throw IOException("No se pudo escribir el MP3.")
            coroutine.ensureActive()
            store.remember(destination.uri, tree, destination.name ?: name)
            committed = true
            saved(destination.uri)
        } finally {
            if (!committed) runCatching { destination?.delete() }
            temp.deleteRecursively()
        }
    }

    private suspend fun convert(source: File, output: File, title: String, artist: String?) {
        val arguments = buildList {
            addAll(listOf("-nostdin", "-y", "-i", source.absolutePath, "-vn", "-c:a", "libmp3lame", "-q:a", "0", "-id3v2_version", "3", "-metadata", "title=$title"))
            if (artist != null) addAll(listOf("-metadata", "artist=$artist"))
            add(output.absolutePath)
        }.toTypedArray()
        suspendCancellableCoroutine<Unit> { continuation ->
            val session = FFmpegKit.executeWithArgumentsAsync(arguments) { result ->
                if (continuation.isActive) {
                    if (ReturnCode.isSuccess(result.returnCode)) continuation.resume(Unit)
                    else continuation.resumeWithException(IOException("No se pudo convertir el audio a MP3."))
                }
            }
            continuation.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
        }
    }
}

internal class DownloadSkippedException : Exception()
