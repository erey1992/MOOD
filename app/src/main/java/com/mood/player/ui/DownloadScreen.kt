package com.mood.player.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mood.player.download.AudioDownloadService
import com.mood.player.download.DownloadItem
import com.mood.player.download.DownloadPhase
import com.mood.player.download.DownloadStatus
import com.mood.player.download.DownloadStore
import com.mood.player.download.youtubeLink
import kotlinx.coroutines.flow.update

class DownloadViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val store = DownloadStore(application)
    val url = saved.getStateFlow("url", "")
    var folder by mutableStateOf(store.folder)
        private set
    var folderName by mutableStateOf(folder?.let { folderLabel(it) }.orEmpty())
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init { DownloadStatus.initialize(application) }
    fun setUrl(value: String) { saved["url"] = value; error = null }
    fun receiveSharedUrl(value: String) = setUrl(value)
    private fun folderLabel(uri: Uri): String = runCatching { DocumentFile.fromTreeUri(getApplication(), uri)?.name }.getOrNull() ?: uri.lastPathSegment.orEmpty()

    fun selectFolder(uri: Uri) {
        try {
            getApplication<Application>().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            folder = uri; store.folder = uri; folderName = folderLabel(uri); error = null
        } catch (_: Exception) { error = "No se pudo conservar el acceso a esa carpeta. Elige otra carpeta." }
    }

    fun start() {
        val link = youtubeLink(url.value)
        if (link == null) { error = "Introduce un enlace de una canción o lista de YouTube / YouTube Music."; return }
        val destination = folder
        if (destination == null) { error = "Elige dónde guardar los MP3."; return }
        error = null
        add(link.url, destination.toString())
    }

    private fun add(link: String, destination: String) {
        if (DownloadStatus.state.value.resolving) return
        DownloadStatus.mutable.update { it.copy(resolving = true, inputError = null, message = "Leyendo enlace…") }
        try {
            ContextCompat.startForegroundService(getApplication(), Intent(getApplication(), AudioDownloadService::class.java).putExtra("url", link).putExtra("folder", destination))
        } catch (_: Exception) {
            DownloadStatus.mutable.update { it.copy(resolving = false, inputError = "No se pudo iniciar la cola. Vuelve a intentarlo con MOOD abierta.", failedInputUrl = link, failedInputFolder = destination) }
        }
    }

    fun retryInput() {
        val state = DownloadStatus.state.value
        state.failedInputUrl?.let { link -> state.failedInputFolder?.let { add(link, it) } }
    }

    private fun command(action: String, itemId: String? = null) {
        try {
            val intent = Intent(getApplication(), AudioDownloadService::class.java).setAction(action).putExtra("item_id", itemId)
            if (action == AudioDownloadService.RETRY) ContextCompat.startForegroundService(getApplication(), intent)
            else getApplication<Application>().startService(intent)
        } catch (_: Exception) { error = "No se pudo realizar la acción. Vuelve a intentarlo con MOOD abierta." }
    }

    fun cancelAll() = command(AudioDownloadService.CANCEL)
    fun clearList() = command(AudioDownloadService.CLEAR)
    fun cancelItem(id: String) = command(AudioDownloadService.CANCEL_ITEM, id)
    fun retry(id: String) = command(AudioDownloadService.RETRY, id)
    fun resolveConflict(requestId: String, replace: Boolean) {
        getApplication<Application>().startService(Intent(getApplication(), AudioDownloadService::class.java)
            .setAction(if (replace) AudioDownloadService.REPLACE else AudioDownloadService.SKIP).putExtra("request_id", requestId))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(sharedUrl: String?, onSharedUrlConsumed: () -> Unit, onShowCollections: () -> Unit, onClose: () -> Unit) {
    val model: DownloadViewModel = viewModel()
    val url by model.url.collectAsState()
    val state by DownloadStatus.state.collectAsState()
    val colors = LocalMoodColors.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) model.selectFolder(it) }
    LaunchedEffect(sharedUrl) { if (sharedUrl != null) model.receiveSharedUrl(sharedUrl); onSharedUrlConsumed() }
    BackHandler(onBack = onClose)
    MoodBackground {
        Scaffold(containerColor = Color.Transparent, topBar = {
            TopAppBar(
                title = { Column(Modifier.padding(start = 12.dp)) {
                    Text("Descargas", color = colors.textPrimary, fontWeight = FontWeight.Bold)
                    Text("TU MÚSICA EN MP3", color = colors.textSecondary, style = MaterialTheme.typography.labelSmall)
                } },
                navigationIcon = { RoundGlassButton(Icons.AutoMirrored.Filled.ArrowBack, "Volver", onClose) },
                actions = { RoundGlassButton(Icons.Rounded.LibraryMusic, "Ver colecciones", onShowCollections) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }) { padding ->
            LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DownloadEmblem(Icons.Rounded.AddLink, colors.accent)
                                Column(Modifier.weight(1f)) {
                                    Text("Añade tu música", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                                    Text("Una canción o una lista de YouTube Music", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                }
                            }
                            OutlinedTextField(
                                value = url, onValueChange = model::setUrl, label = { Text("Enlace de canción o lista") },
                                placeholder = { Text("Pega aquí el enlace") }, modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp), singleLine = true, enabled = !state.resolving,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = colors.accent, unfocusedBorderColor = colors.textSecondary.copy(alpha = 0.35f),
                                    focusedTextColor = colors.textPrimary, unfocusedTextColor = colors.textPrimary,
                                    focusedLabelColor = colors.accent, unfocusedLabelColor = colors.textSecondary,
                                ),
                            )
                            GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                                Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Icon(Icons.Rounded.FolderOpen, null, tint = colors.accent)
                                    Column(Modifier.weight(1f)) {
                                        Text("Guardar en", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                                        Text(model.folderName.ifEmpty { "Elige una carpeta" }, style = MaterialTheme.typography.titleSmall,
                                            color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    TextButton(onClick = { picker.launch(model.folder) }, enabled = !state.resolving) {
                                        Text(if (model.folder == null) "Elegir" else "Cambiar")
                                    }
                                }
                            }
                            Text("Recordaremos esta carpeta para tus próximas descargas.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            model.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                            PillButton("Añadir a descargas", Icons.Rounded.Download, model::start,
                                Modifier.fillMaxWidth(), enabled = url.isNotBlank() && model.folder != null && !state.resolving)
                            if (state.resolving) {
                                LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.track)
                                Text(state.message, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                                TextButton(onClick = model::cancelAll) { Text("Cancelar lectura del enlace") }
                            } else if (state.message.isNotEmpty()) {
                                Text(state.message, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                            }
                            state.inputError?.let {
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = model::retryInput, enabled = !state.resolving) { Text("Reintentar enlace") }
                            }
                        }
                    }
                }
                item {
                    DownloadQueueHeading(state.items.size, state.items.any { it.phase.active }, model::clearList)
                }
                if (state.items.isEmpty()) {
                    item {
                        GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                            Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                DownloadEmblem(Icons.AutoMirrored.Rounded.QueueMusic, colors.accent)
                                Text("Tu lista está vacía", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium)
                                Text("Añade un enlace y verás aquí el progreso de cada canción.", color = colors.textSecondary,
                                    style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                } else {
                    item {
                        val completed = state.items.count { it.phase == DownloadPhase.COMPLETED }
                        val pending = state.items.count { it.phase == DownloadPhase.PENDING }
                        val failed = state.items.count { it.phase == DownloadPhase.FAILED }
                        val active = state.items.count { it.phase.active && it.phase != DownloadPhase.PENDING }
                        Text("$completed guardadas · $active en proceso\n$pending pendientes · $failed con error",
                            Modifier.padding(horizontal = 24.dp), color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, item ->
                    DownloadTaskCard(item, index + 1, onRetry = { model.retry(item.id) }, onCancel = { model.cancelItem(item.id) },
                        onResolve = { replace -> item.conflict?.let { model.resolveConflict(it.requestId, replace) } },
                        retryEnabled = state.items.none { it.id != item.id && it.phase.active && it.targetKey == item.targetKey })
                }
                item {
                    Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Info, null, tint = colors.iconMuted, modifier = Modifier.size(16.dp))
                        Text("Hasta ${AudioDownloadService.MAX_SIMULTANEOUS} descargas a la vez. Continúan al salir de esta pantalla.",
                            color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DownloadQueueHeading(count: Int, hasActive: Boolean, onClear: () -> Unit) {
    val colors = LocalMoodColors.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Tu lista", color = colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(if (count == 1) "1 canción" else "$count canciones", color = colors.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), state = rememberTooltipState(),
            tooltip = { PlainTooltip { Text(if (hasActive) "Limpiar lista y cancelar descargas" else "Limpiar lista") } }) {
            IconButton(onClick = onClear, enabled = count > 0,
                modifier = Modifier.background(colors.glass, CircleShape)) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Limpiar lista",
                    tint = if (count > 0) colors.textPrimary else colors.iconMuted.copy(alpha = 0.45f))
            }
        }
    }
}

@Composable
private fun DownloadEmblem(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color) {
    Box(Modifier.size(44.dp).background(tint.copy(alpha = 0.13f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(24.dp))
    }
}

@Composable
internal fun DownloadTaskCard(item: DownloadItem, number: Int, onRetry: () -> Unit, onCancel: () -> Unit, onResolve: (Boolean) -> Unit, retryEnabled: Boolean = true) {
    val colors = LocalMoodColors.current
    val statusColor = if (item.phase == DownloadPhase.FAILED) MaterialTheme.colorScheme.error else colors.accent
    val statusIcon = when (item.phase) {
        DownloadPhase.COMPLETED -> Icons.Rounded.CheckCircle
        DownloadPhase.FAILED -> Icons.Rounded.ErrorOutline
        DownloadPhase.PENDING -> Icons.Rounded.Schedule
        DownloadPhase.WAITING_CHOICE -> Icons.Rounded.FileCopy
        DownloadPhase.CANCELLED, DownloadPhase.SKIPPED -> Icons.Rounded.DoNotDisturb
        else -> Icons.Rounded.MusicNote
    }
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp), tint = if (item.phase == DownloadPhase.FAILED) statusColor else null) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val status = when (item.phase) {
                DownloadPhase.PENDING -> "Pendiente de descargar"
                DownloadPhase.METADATA -> "Consultando datos…"
                DownloadPhase.WAITING_CHOICE -> "El MP3 ya existe"
                DownloadPhase.DOWNLOADING -> "Descargando" + (item.progress?.let { " · ${(it * 100).toInt()} %" } ?: "…")
                DownloadPhase.CONVERTING -> "Convirtiendo a MP3"
                DownloadPhase.SAVING -> "Guardando MP3"
                DownloadPhase.COMPLETED -> "Descargada"
                DownloadPhase.SKIPPED -> "Omitida"
                DownloadPhase.FAILED -> "Error de descarga"
                DownloadPhase.CANCELLED -> "Cancelada"
            }
            Text("$number · $status", style = MaterialTheme.typography.labelLarge, color = statusColor)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                DownloadEmblem(statusIcon, statusColor)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(item.title ?: "Video ${item.url.substringAfterLast('=')}", style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary, fontWeight = FontWeight.SemiBold)
                    item.author?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary) }
                }
            }
            Text("Duración: ${item.durationMs?.let(::downloadDuration) ?: "no disponible"}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FolderOpen, null, tint = colors.iconMuted, modifier = Modifier.size(16.dp))
                Text("Carpeta: ${item.folderName}", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
            if (item.phase in setOf(DownloadPhase.METADATA, DownloadPhase.DOWNLOADING, DownloadPhase.CONVERTING, DownloadPhase.SAVING)) {
                val progress = item.progress
                if (item.phase == DownloadPhase.DOWNLOADING && progress != null) {
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.track)
                } else LinearProgressIndicator(Modifier.fillMaxWidth(), color = colors.accent, trackColor = colors.track)
            }
            item.message?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (item.phase == DownloadPhase.FAILED) statusColor else colors.textSecondary) }
            item.conflict?.let { conflict ->
                Text("«${conflict.fileName}»", style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onResolve(false) }) { Text("Omitir") }
                    Button(onClick = { onResolve(true) }) { Text("Reemplazar") }
                }
            }
            if (item.phase.retryable) PillButton("Reintentar", Icons.Rounded.Refresh, onRetry, Modifier.fillMaxWidth(), enabled = retryEnabled)
            if (item.phase.active) TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancelar") }
        }
    }
}

private fun downloadDuration(ms: Long): String {
    val seconds = ms / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}
