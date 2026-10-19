package com.mood.player.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.documentfile.provider.DocumentFile
import com.mood.player.ui.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class AudioDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: YoutubeDownloadEngine
    private val workers = mutableMapOf<String, Job>()
    private val decisions = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val attempts = ConcurrentHashMap<String, String>()
    private var shuttingDown = false
    private var resolver: Job? = null
    private var resolverId: String? = null
    @Volatile private var foreground = false
    private var lastStartId = 0
    private var lastNotification = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        DownloadStatus.initialize(this)
        engine = YoutubeDownloadEngine(this)
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Descargas de MP3", NotificationManager.IMPORTANCE_LOW))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            SKIP, REPLACE -> intent.getStringExtra("request_id")?.let { decisions[it]?.complete(intent.action == REPLACE) }
            CANCEL -> cancelAll()
            CLEAR -> { cancelAll(); DownloadStatus.clearAll() }
            CANCEL_ITEM -> intent.getStringExtra("item_id")?.let(::cancelItem)
            RETRY -> { showForeground(); intent.getStringExtra("item_id")?.let(::retry) }
            else -> {
                val link = intent?.getStringExtra("url")?.let(::youtubeLink)
                val folder = intent?.getStringExtra("folder")
                if (link != null && folder != null) { showForeground(); addInput(link, folder) }
            }
        }
        stopIfIdle()
        return START_NOT_STICKY
    }

    private fun addInput(link: YoutubeLink, folder: String) {
        if (resolver?.isActive == true) return
        val inputId = UUID.randomUUID().toString()
        resolverId = inputId
        DownloadStatus.mutable.update { it.copy(resolving = true, message = if (link.isPlaylist) "Leyendo lista de reproducción…" else "Añadiendo canción…", inputError = null, failedInputUrl = null, failedInputFolder = null) }
        resolver = scope.launch {
            try {
                val (name, playlist) = withContext(Dispatchers.IO) {
                    val tree = DocumentFile.fromTreeUri(this@AudioDownloadService, Uri.parse(folder))
                        ?.takeIf { it.isDirectory && it.canWrite() } ?: throw IOException("No se puede escribir en la carpeta elegida.")
                    (tree.name ?: "Carpeta elegida") to if (link.isPlaylist) engine.playlist(link.url, inputId)
                    else {
                        val song = try { engine.song(link.url, inputId) }
                        catch (e: CancellationException) { throw e }
                        catch (_: Exception) { SongMetadata(link.url, null, null, null) }
                        PlaylistMetadata(null, listOf(song), 0)
                    }
                }
                DownloadStatus.mutable.update { state ->
                    val active = state.items.filter { it.phase.active }.map { it.targetKey }.toSet()
                    val additions = playlist.songs.map { song -> DownloadItem(url = song.url, folderUri = folder, folderName = name,
                        title = song.title, author = song.author, durationMs = song.durationMs) }.filter { it.targetKey !in active }
                    state.copy(items = state.items + additions,
                        message = if (additions.isEmpty()) "Estas canciones ya están en la cola."
                        else "${playlist.title?.let { "$it · " }.orEmpty()}" +
                            (if (additions.size == 1) "1 canción añadida." else "${additions.size} canciones añadidas.") +
                            if (playlist.unavailable > 0) " ${playlist.unavailable} entradas no disponibles." else "")
                }
                DownloadStatus.save()
                schedule()
            } catch (_: CancellationException) {
                DownloadStatus.mutable.update { it.copy(message = "Lectura del enlace cancelada.") }
            } catch (e: Exception) {
                DownloadStatus.mutable.update { it.copy(message = "No se pudo leer el enlace.", inputError = errorMessage(e), failedInputUrl = link.url, failedInputFolder = folder) }
            } finally {
                resolver = null
                resolverId = null
                DownloadStatus.mutable.update { it.copy(resolving = false) }
                notifyQueue(force = true)
                stopIfIdle()
            }
        }
    }

    private fun schedule() {
        if (shuttingDown) return
        while (workers.size < MAX_SIMULTANEOUS) {
            val state = DownloadStatus.state.value
            val runningTargets = state.items.filter { it.id in workers }.map { it.targetKey }.toSet()
            val item = state.items.firstOrNull { it.phase == DownloadPhase.PENDING && it.id !in workers && it.targetKey !in runningTargets } ?: break
            DownloadStatus.changeItem(item.id, persist = false) { it.copy(phase = DownloadPhase.METADATA, message = "Consultando datos…") }
            val attempt = UUID.randomUUID().toString()
            attempts[item.id] = attempt
            workers[item.id] = scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        engine.download(item,
                            processId = attempt,
                            metadata = { metadata -> if (attempts[item.id] == attempt) DownloadStatus.changeItem(item.id) { it.copy(title = metadata.title ?: it.title, author = metadata.author ?: it.author, durationMs = metadata.durationMs ?: it.durationMs) } },
                            phase = { phase, progress, message ->
                                if (attempts[item.id] == attempt) DownloadStatus.changeItem(item.id, persist = false) {
                                    if (it.phase.active) it.copy(phase = phase, progress = progress, message = message, conflict = null) else it
                                }
                                notifyQueue()
                            },
                            chooseReplacement = { original -> choose(item.id, original) },
                            saved = { uri -> DownloadStatus.completed(item.id, uri.toString()) },
                        )
                    }
                } catch (_: DownloadSkippedException) {
                    DownloadStatus.changeItem(item.id) { it.copy(phase = DownloadPhase.SKIPPED, progress = null, message = "Se conserva el archivo existente.", conflict = null) }
                } catch (_: CancellationException) {
                    if (!isCompleted(item.id)) DownloadStatus.changeItem(item.id) { it.copy(phase = DownloadPhase.CANCELLED, progress = null, message = "Descarga cancelada.", conflict = null) }
                } catch (e: Exception) {
                    if (!isCompleted(item.id)) DownloadStatus.changeItem(item.id) { it.copy(phase = DownloadPhase.FAILED, progress = null, message = errorMessage(e), conflict = null) }
                } finally {
                    attempts.remove(item.id, attempt)
                    workers.remove(item.id)
                    notifyQueue(force = true)
                    schedule()
                    stopIfIdle()
                }
            }
        }
        notifyQueue(force = true)
    }

    private fun isCompleted(id: String) = DownloadStatus.state.value.items.firstOrNull { it.id == id }?.phase == DownloadPhase.COMPLETED

    private suspend fun choose(itemId: String, original: DocumentFile): Boolean {
        val conflict = DownloadConflict(UUID.randomUUID().toString(), original.name ?: "MP3")
        val deferred = CompletableDeferred<Boolean>()
        decisions[conflict.requestId] = deferred
        DownloadStatus.changeItem(itemId, persist = false) { it.copy(phase = DownloadPhase.WAITING_CHOICE, progress = null, conflict = conflict, message = "Este MP3 ya existe en la carpeta elegida.") }
        notifyQueue(force = true)
        return try { deferred.await() } finally {
            decisions.remove(conflict.requestId)
            DownloadStatus.changeItem(itemId, persist = false) { it.copy(conflict = null) }
        }
    }

    private fun retry(id: String) {
        val item = DownloadStatus.state.value.items.firstOrNull { it.id == id && it.phase.retryable } ?: return
        if (DownloadStatus.state.value.items.any { it.id != id && it.phase.active && it.targetKey == item.targetKey }) return
        DownloadStatus.changeItem(id) { it.copy(phase = DownloadPhase.PENDING, message = null, progress = null, conflict = null) }
        schedule()
    }

    private fun cancelItem(id: String) {
        val item = DownloadStatus.state.value.items.firstOrNull { it.id == id } ?: return
        if (!item.phase.active) return
        if (id in workers) {
            val attempt = attempts[id]
            workers[id]?.cancel()
            if (attempt != null) scope.launch(Dispatchers.IO) { engine.cancel(attempt) }
        }
        else DownloadStatus.changeItem(id) { it.copy(phase = DownloadPhase.CANCELLED, progress = null, message = "Descarga cancelada.", conflict = null) }
    }

    private fun cancelAll() {
        resolver?.cancel()
        resolverId?.let { id -> scope.launch(Dispatchers.IO) { engine.cancel(id) } }
        DownloadStatus.mutable.update { state -> state.copy(items = state.items.map {
            if (it.phase == DownloadPhase.PENDING) it.copy(phase = DownloadPhase.CANCELLED, message = "Descarga cancelada.") else it
        }) }
        workers.keys.toList().forEach(::cancelItem)
        DownloadStatus.save()
    }

    private fun showForeground() {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(), if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
        foreground = true
    }

    private fun stopIfIdle() {
        if (workers.isEmpty() && resolver?.isActive != true && !DownloadStatus.state.value.items.any { it.phase == DownloadPhase.PENDING }) {
            if (foreground) { stopForeground(STOP_FOREGROUND_REMOVE); foreground = false }
            stopSelf(lastStartId)
        }
    }

    @Synchronized
    private fun notifyQueue(force: Boolean = false) {
        if (!foreground) return
        val now = System.currentTimeMillis()
        if (force || now - lastNotification > 1_000) {
            lastNotification = now
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
        }
    }

    private fun notification(): Notification {
        val state = DownloadStatus.state.value
        val finished = state.items.count { !it.phase.active }
        val downloading = state.items.count { it.phase == DownloadPhase.DOWNLOADING }
        val waiting = state.items.count { it.phase == DownloadPhase.WAITING_CHOICE }
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).putExtra("show_downloads", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val cancel = PendingIntent.getService(this, 1, Intent(this, AudioDownloadService::class.java).setAction(CANCEL), PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("MOOD · $finished de ${state.items.size} procesadas")
            .setContentText(when { waiting > 0 -> "$waiting MP3 existentes: abre MOOD para omitir o reemplazar."
                state.resolving -> "Leyendo enlace… · $downloading descargando"
                else -> "$downloading descargando · hasta $MAX_SIMULTANEOUS simultáneas" })
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setProgress(state.items.size.coerceAtLeast(1), finished, state.resolving && state.items.isEmpty())
            .addAction(0, "Cancelar todas", cancel).build()
    }

    override fun onTimeout(startId: Int, fgsType: Int) { shuttingDown = true; cancelAll(); stopForeground(STOP_FOREGROUND_REMOVE); foreground = false; stopSelf() }
    override fun onDestroy() {
        shuttingDown = true
        scope.cancel()
        DownloadStatus.mutable.update { state -> state.copy(resolving = false, items = state.items.map {
            if (it.phase.active) it.copy(phase = DownloadPhase.FAILED, conflict = null, progress = null, message = "La descarga se interrumpió. Pulsa Reintentar.") else it
        }) }
        DownloadStatus.save()
        super.onDestroy()
    }

    private fun errorMessage(error: Exception): String = if (error is SecurityException) "Sin acceso a la carpeta. Vuelve a elegirla."
        else error.message?.lineSequence()?.lastOrNull { it.isNotBlank() }?.take(350) ?: "Comprueba la conexión y vuelve a intentarlo."

    companion object {
        const val CANCEL = "com.mood.player.CANCEL_DOWNLOAD"
        const val CLEAR = "com.mood.player.CLEAR_DOWNLOAD_QUEUE"
        const val CANCEL_ITEM = "com.mood.player.CANCEL_DOWNLOAD_ITEM"
        const val RETRY = "com.mood.player.RETRY_DOWNLOAD_ITEM"
        const val SKIP = "com.mood.player.SKIP_DOWNLOAD"
        const val REPLACE = "com.mood.player.REPLACE_DOWNLOAD"
        const val MAX_SIMULTANEOUS = 3
        private const val CHANNEL = "mp3_downloads"
        private const val NOTIFICATION_ID = 42
    }
}
