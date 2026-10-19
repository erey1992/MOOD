package com.mood.player.ui

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mood.player.data.HttpException
import com.mood.player.data.LyricLine
import com.mood.player.data.Lyrics
import com.mood.player.data.LyricsRepository
import com.mood.player.data.LyricsResult
import com.mood.player.data.LyricsVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------------------------------------------------------------------------------------------
// Estado
// ---------------------------------------------------------------------------------------------

data class LyricsUiState(
    val mediaId: String? = null,
    val loading: Boolean = false,
    val result: LyricsResult? = null,
    val error: Boolean = false,
    /** El servidor de letras sigue limitando las peticiones aunque se ha esperado y reintentado. */
    val serverBusy: Boolean = false,
) {
    val lyrics: Lyrics? get() = result?.lyrics
}

data class VersionsUiState(
    val loading: Boolean = false,
    val versions: List<LyricsVersion> = emptyList(),
    val error: Boolean = false,
)

class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = LyricsRepository(application)
    private val _state = MutableStateFlow(LyricsUiState())
    val state: StateFlow<LyricsUiState> = _state.asStateFlow()
    private val _versions = MutableStateFlow(VersionsUiState())
    val versions: StateFlow<VersionsUiState> = _versions.asStateFlow()
    private var job: Job? = null
    private var offsetJob: Job? = null

    /** Datos de la canción actual, para buscar otras versiones. */
    private var song: Triple<String, String?, Long>? = null

    /** Descarga (o lee de la caché) la letra de la canción. [force] ignora la copia guardada. */
    fun load(mediaId: String, title: String, artist: String?, durationMs: Long, force: Boolean = false) {
        song = Triple(title, artist, durationMs)
        val current = _state.value
        if (!force && current.mediaId == mediaId && (current.loading || current.result != null)) return
        job?.cancel()
        if (force) repository.forget(mediaId)
        _versions.value = VersionsUiState()
        _state.value = LyricsUiState(mediaId = mediaId, loading = true)
        job = viewModelScope.launch {
            _state.value = try {
                LyricsUiState(mediaId = mediaId, result = repository.find(mediaId, title, artist, durationMs))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("MOOD", "No se pudo descargar la letra", e)
                LyricsUiState(mediaId = mediaId, error = true, serverBusy = e is HttpException && e.code in setOf(429, 503))
            }
        }
    }

    /** Desplaza toda la letra [deltaMs] (positivo = más tarde) y lo guarda para esta canción. */
    fun changeOffset(deltaMs: Long) = setOffset { it + deltaMs }

    fun resetOffset() = setOffset { 0 }

    private fun setOffset(change: (Long) -> Long) {
        val mediaId = _state.value.mediaId ?: return
        val result = _state.value.result ?: return
        val updated = result.copy(offsetMs = change(result.offsetMs).coerceIn(-60_000, 60_000))
        _state.update { it.copy(result = updated) }
        // Se guarda tras una pausa breve, para no escribir en cada pulsación seguida.
        offsetJob?.cancel()
        offsetJob = viewModelScope.launch {
            delay(600)
            repository.setOffset(mediaId, updated)
        }
    }

    fun loadVersions() {
        val (title, artist, duration) = song ?: return
        _versions.value = VersionsUiState(loading = true)
        viewModelScope.launch {
            _versions.value = try {
                VersionsUiState(versions = repository.versions(title, artist, duration))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                VersionsUiState(error = true)
            }
        }
    }

    fun chooseVersion(version: LyricsVersion) {
        val mediaId = _state.value.mediaId ?: return
        viewModelScope.launch {
            val result = repository.choose(mediaId, version)
            _state.update { it.copy(result = result, error = false) }
        }
    }

    fun saveManual(lines: List<LyricLine>) {
        val mediaId = _state.value.mediaId ?: return
        viewModelScope.launch {
            val result = repository.saveManual(mediaId, lines)
            _state.update { it.copy(result = result, error = false) }
        }
    }
}

/** Posición interpolada fotograma a fotograma a partir de la última lectura del reproductor. */
@Composable
private fun rememberSmoothPosition(state: PlayerUiState): State<Long> =
    produceState(state.positionMs, state.positionMs, state.positionUpdatedAt, state.isPlaying) {
        value = state.positionMs
        if (!state.isPlaying) return@produceState
        while (true) {
            withFrameMillis { }
            value = state.positionMs + (SystemClock.elapsedRealtime() - state.positionUpdatedAt)
        }
    }

/** Se resalta la línea un poco antes de que empiece, para que dé tiempo a leerla. */
private const val LEAD_MS = 250L

/** Paso de los botones de ajuste. */
private const val OFFSET_STEP_MS = 500L

/** Se toca un poco después de oír la línea; se descuenta ese retraso al marcar. */
private const val REACTION_MS = 150L

// ---------------------------------------------------------------------------------------------
// Pantalla
// ---------------------------------------------------------------------------------------------

/**
 * Página de karaoke del reproductor. Al abrirse ([active]) descarga la letra de la canción actual;
 * la sincronizada se desplaza sola y rellena de color la línea que se está cantando.
 * Si el ritmo no cuadra se puede desplazar, elegir otra versión o sincronizar tocando.
 */
@Composable
fun KaraokePage(
    state: PlayerUiState,
    player: PlayerViewModel,
    active: Boolean,
    onBack: () -> Unit,
) {
    val colors = LocalMoodColors.current
    val lyricsViewModel: LyricsViewModel = viewModel()
    val lyricsState by lyricsViewModel.state.collectAsState()
    val mediaId = state.mediaId
    var syncing by remember(mediaId) { mutableStateOf(false) }
    var showVersions by remember(mediaId) { mutableStateOf(false) }

    fun load(force: Boolean = false) {
        if (mediaId != null) lyricsViewModel.load(mediaId, state.title, state.artist, state.durationMs, force)
    }
    // La duración llega un instante después de cambiar de canción; se espera a tenerla para elegir la versión correcta.
    LaunchedEffect(active, mediaId, state.durationMs > 0) {
        if (active && mediaId != null) {
            if (state.durationMs <= 0) delay(1_500)
            load()
        }
    }
    BackHandler(enabled = syncing) { syncing = false }

    val ready = lyricsState.mediaId == mediaId && !lyricsState.loading
    val lyrics = lyricsState.lyrics.takeIf { ready }
    // Líneas que se pueden sincronizar a mano: las de cualquier letra que tengamos.
    val syncSource = when (lyrics) {
        is Lyrics.Synced -> lyrics.lines.map { it.text }.filter { it.isNotBlank() }
        is Lyrics.Plain -> lyrics.text.lines().map { it.trim() }.filter { it.isNotBlank() }
        else -> emptyList()
    }

    if (showVersions) {
        VersionsDialog(
            viewModel = lyricsViewModel,
            currentVersionId = lyricsState.result?.versionId,
            durationMs = state.durationMs,
            onChoose = {
                lyricsViewModel.chooseVersion(it)
                showVersions = false
            },
            onDismiss = { showVersions = false },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 40.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OrbIcon(OrbPalette[3], Icons.Rounded.Mic, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (syncing) "SINCRONIZAR" else "KARAOKE",
                    color = colors.accent,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                Text(
                    state.title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.artist?.let {
                    Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!syncing) {
                KaraokeMenu(
                    canSync = syncSource.isNotEmpty(),
                    manual = lyricsState.result?.manual == true,
                    onVersions = {
                        lyricsViewModel.loadVersions()
                        showVersions = true
                    },
                    onSync = { syncing = true },
                    onSearchAgain = { load(force = true) },
                )
            }
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Volver al reproductor", tint = colors.textPrimary)
            }
        }

        if (!syncing && lyrics is Lyrics.Synced) {
            OffsetBar(
                offsetMs = lyricsState.result?.offsetMs ?: 0,
                manual = lyricsState.result?.manual == true,
                onEarlier = { lyricsViewModel.changeOffset(-OFFSET_STEP_MS) },
                onLater = { lyricsViewModel.changeOffset(OFFSET_STEP_MS) },
                onReset = lyricsViewModel::resetOffset,
            )
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                syncing -> TapSync(
                    lines = syncSource,
                    state = state,
                    player = player,
                    onCancel = { syncing = false },
                    onSave = { lines ->
                        lyricsViewModel.saveManual(lines)
                        syncing = false
                    },
                )
                !ready -> Message(
                    title = "Buscando la letra…",
                    icon = null,
                )
                lyricsState.error -> Message(
                    icon = Icons.Rounded.WifiOff,
                    title = "No se pudo descargar la letra",
                    detail = if (lyricsState.serverBusy) {
                        "El servidor de letras está recibiendo demasiadas peticiones. Prueba de nuevo en unos segundos."
                    } else {
                        "Revisa tu conexión a internet."
                    },
                    action = "Reintentar" to { load(force = true) },
                )
                lyrics is Lyrics.Synced -> {
                    val offset = lyricsState.result?.offsetMs ?: 0
                    val shifted = remember(lyrics, offset) {
                        lyrics.lines.map { it.copy(timeMs = (it.timeMs + offset).coerceAtLeast(0)) }
                    }
                    SyncedLyrics(shifted, state, onSeek = player::seekTo)
                }
                lyrics is Lyrics.Plain -> PlainLyrics(lyrics.text, onSync = { syncing = true })
                lyrics == Lyrics.Instrumental -> Message(
                    icon = Icons.Rounded.MusicNote,
                    title = "Canción instrumental",
                    detail = "No tiene letra que cantar. ¡Disfrútala!",
                )
                else -> Message(
                    icon = Icons.Rounded.SearchOff,
                    title = "No se encontró la letra",
                    detail = "Si el título o el artista no son correctos, complétalos con «Buscar datos en internet» y vuelve a intentarlo.",
                    action = "Buscar de nuevo" to { load(force = true) },
                )
            }
        }

        // Controles mínimos para no tener que volver al reproductor.
        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            GradientSeekBar(
                if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f,
                Modifier.fillMaxWidth(),
                thumbSize = 18.dp,
                trackHeight = 5.dp,
            )
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                PlayCircleButton(state.isPlaying, onClick = player::playPause, size = 60.dp)
            }
        }
    }
}

@Composable
private fun KaraokeMenu(
    canSync: Boolean,
    manual: Boolean,
    onVersions: () -> Unit,
    onSync: () -> Unit,
    onSearchAgain: () -> Unit,
) {
    val colors = LocalMoodColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones de la letra", tint = colors.iconMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Otras versiones de la letra") },
                leadingIcon = { Icon(Icons.Rounded.LibraryMusic, contentDescription = null) },
                onClick = { open = false; onVersions() },
            )
            DropdownMenuItem(
                text = { Text("Sincronizar tocando") },
                leadingIcon = { Icon(Icons.Rounded.TouchApp, contentDescription = null) },
                enabled = canSync,
                onClick = { open = false; onSync() },
            )
            DropdownMenuItem(
                text = { Text(if (manual) "Buscar de nuevo (borra tu sincronización)" else "Buscar de nuevo") },
                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                onClick = { open = false; onSearchAgain() },
            )
        }
    }
}

/** Ajuste fino: si la letra va tarde se adelanta (−) y si va antes se retrasa (+). Se guarda por canción. */
@Composable
private fun OffsetBar(offsetMs: Long, manual: Boolean, onEarlier: () -> Unit, onLater: () -> Unit, onReset: () -> Unit) {
    val colors = LocalMoodColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        StepButton("−0,5 s", "Adelantar la letra medio segundo", onEarlier)
        Column(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .clickable(enabled = offsetMs != 0L, onClickLabel = "Quitar el ajuste", onClick = onReset)
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                when {
                    offsetMs == 0L -> if (manual) "Sincronizada por ti" else "Sincronía original"
                    else -> "Letra %+.1f s".format(offsetMs / 1000.0).replace('.', ',')
                },
                color = if (offsetMs == 0L) colors.textSecondary else colors.accent,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (offsetMs == 0L) "¿Va desfasada? Ajústala" else "Toca para quitar el ajuste",
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        StepButton("+0,5 s", "Retrasar la letra medio segundo", onLater)
    }
}

@Composable
private fun StepButton(text: String, label: String, onClick: () -> Unit) {
    val colors = LocalMoodColors.current
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.glass)
            .border(1.dp, colors.glassBorder, RoundedCornerShape(50))
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(text, color = colors.textPrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun VersionsDialog(
    viewModel: LyricsViewModel,
    currentVersionId: Long?,
    durationMs: Long,
    onChoose: (LyricsVersion) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalMoodColors.current
    val state by viewModel.versions.collectAsState()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Otras versiones de la letra") },
        text = {
            when {
                state.loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
                state.error -> Text("No se pudieron consultar las versiones. Revisa tu conexión a internet.")
                state.versions.isEmpty() -> Text("No hay otras versiones de esta canción.")
                else -> Column {
                    Text(
                        "Elige la que dure lo mismo que tu canción (${formatTime(durationMs)}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(state.versions, key = { it.id }) { version ->
                            VersionRow(version, durationMs, selected = version.id == currentVersionId, onClick = { onChoose(version) })
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = if (state.error) {
            { TextButton(onClick = viewModel::loadVersions) { Text("Reintentar") } }
        } else null,
    )
}

@Composable
private fun VersionRow(version: LyricsVersion, durationMs: Long, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalMoodColors.current
    val diff = version.durationMs?.takeIf { durationMs > 0 }?.let { (it - durationMs) / 1000 }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${version.track} — ${version.artist}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    version.album,
                    version.durationMs?.let { formatTime(it) },
                    diff?.let { if (it == 0L) "misma duración" else "%+d s".format(it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (diff != null && abs(diff) <= 2) Color(0xFF10B981) else colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    version.synced != null -> "Con tiempos"
                    version.plain != null -> "Sin tiempos"
                    version.instrumental -> "Instrumental"
                    else -> "Sin letra"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (version.synced != null) colors.accent else colors.textSecondary,
            )
        }
        if (selected) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Versión actual", tint = colors.accent)
        }
    }
}

/**
 * Sincronizar tocando: suena la canción desde el principio y se toca «Marcar» cuando empieza cada línea.
 * Al terminar, la letra queda sincronizada y guardada para esta canción.
 */
@Composable
private fun TapSync(
    lines: List<String>,
    state: PlayerUiState,
    player: PlayerViewModel,
    onCancel: () -> Unit,
    onSave: (List<LyricLine>) -> Unit,
) {
    val colors = LocalMoodColors.current
    val position = rememberSmoothPosition(state)
    var marks by remember(lines) { mutableStateOf(listOf<Long>()) }
    var started by remember(lines) { mutableStateOf(false) }
    val next = marks.size
    val done = next >= lines.size

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            when {
                !started -> "La canción empezará desde el principio. Toca «Marcar» justo cuando empiece cada línea."
                done -> "¡Listo! Guarda la sincronización. Después podrás afinarla con −0,5 s / +0,5 s."
                else -> "Línea ${next + 1} de ${lines.size}"
            },
            color = colors.textSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.weight(1f))
        // La línea anterior (ya marcada), la que toca marcar y la siguiente.
        lines.getOrNull(next - 1)?.let {
            Text(it, color = colors.accent.copy(alpha = 0.5f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
        }
        Text(
            if (done) "♪" else lines[next],
            color = colors.textPrimary,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        lines.getOrNull(next + 1)?.let {
            Spacer(Modifier.height(16.dp))
            Text(it, color = colors.textSecondary.copy(alpha = 0.6f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))

        when {
            !started -> PillButton(
                "Empezar",
                Icons.Rounded.TouchApp,
                onClick = {
                    player.seekTo(0)
                    if (!state.isPlaying) player.playPause()
                    started = true
                },
                modifier = Modifier.fillMaxWidth(),
            )
            !done -> Box(
                Modifier
                    .size(120.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(colors.accentLight, colors.accentDeep)))
                    .clickable(onClickLabel = "Marcar el inicio de la línea") {
                        // Cada marca va después de la anterior; se descuenta el tiempo de reacción.
                        val time = (position.value - REACTION_MS).coerceAtLeast(marks.lastOrNull() ?: 0L)
                        marks = marks + time
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Marcar", color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            else -> PillButton(
                "Guardar sincronización",
                Icons.Rounded.CheckCircle,
                onClick = { onSave(lines.zip(marks) { text, time -> LyricLine(time, text) }) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onCancel) { Text("Cancelar") }
            if (marks.isNotEmpty()) {
                TextButton(onClick = {
                    // Deshace la última marca y vuelve unos segundos atrás para repetirla.
                    val previous = marks.dropLast(1)
                    marks = previous
                    player.seekTo(((previous.lastOrNull() ?: 0L) - 2_000).coerceAtLeast(0))
                }) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Deshacer")
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
private fun SyncedLyrics(lines: List<LyricLine>, state: PlayerUiState, onSeek: (Long) -> Unit) {
    val colors = LocalMoodColors.current
    val position = rememberSmoothPosition(state)
    val current by remember(lines) {
        derivedStateOf { lines.indexOfLast { it.timeMs <= position.value + LEAD_MS } }
    }
    val listState = rememberLazyListState()

    // Si el usuario desliza la letra, se deja de seguir la canción durante 3 segundos.
    val dragging by listState.interactionSource.collectIsDraggedAsState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(dragging) {
        if (dragging) {
            follow = false
        } else if (!follow) {
            delay(3_000)
            follow = true
        }
    }
    LaunchedEffect(current, follow) {
        if (follow) listState.animateScrollToItem(current.coerceAtLeast(0))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // El relleno superior coloca la línea actual a un tercio de la altura; el inferior deja llegar a la última.
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = maxHeight * 0.3f, bottom = maxHeight * 0.6f),
        ) {
            itemsIndexed(lines, key = { index, line -> "$index-${line.timeMs}" }) { index, line ->
                val isCurrent = index == current
                val isPast = index < current
                val scale by animateFloatAsState(if (isCurrent) 1.08f else 0.86f, label = "lyricScale")
                val alpha by animateFloatAsState(
                    when {
                        isCurrent -> 1f
                        isPast -> 0.45f
                        else -> 0.5f
                    },
                    label = "lyricAlpha",
                )
                val baseColor by animateColorAsState(if (isPast) colors.accent else colors.textPrimary, label = "lyricColor")
                val style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                val text = line.text.ifBlank { "♪" }
                val modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    }
                    .clickable(onClickLabel = "Ir a esta línea") { onSeek(line.timeMs) }
                    .padding(vertical = 10.dp)

                if (isCurrent) {
                    // Relleno tipo karaoke: encima del texto se dibuja la copia morada recortada hasta lo cantado.
                    // La posición se lee solo al dibujar, así que avanzar no recompone nada.
                    val end = lines.getOrNull(index + 1)?.timeMs ?: (line.timeMs + 4_000)
                    val duration = (end - line.timeMs).coerceAtLeast(1)
                    Box(modifier) {
                        Text(text, color = colors.textPrimary, style = style)
                        Text(
                            text,
                            Modifier.drawWithContent {
                                val fill = ((position.value - line.timeMs).toFloat() / duration).coerceIn(0f, 1f)
                                clipRect(right = size.width * fill) { this@drawWithContent.drawContent() }
                            },
                            style = style.copy(brush = Brush.horizontalGradient(listOf(colors.accentLight, colors.accent))),
                        )
                    }
                } else {
                    Text(text, modifier, color = baseColor, style = style)
                }
            }
        }

        if (!follow) {
            PillButton(
                "Volver a la canción",
                Icons.Rounded.Mic,
                onClick = { follow = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun PlainLyrics(text: String, onSync: () -> Unit) {
    val colors = LocalMoodColors.current
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        GlassCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MicOff, contentDescription = null, tint = colors.iconMuted)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Esta letra no tiene tiempos, así que no puede seguir la canción. Puedes sincronizarla tú tocando " +
                            "al ritmo, o probar otra versión desde el menú ⋮.",
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(10.dp))
                PillButton("Sincronizar tocando", Icons.Rounded.TouchApp, onSync, Modifier.fillMaxWidth())
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            color = colors.textPrimary,
            style = MaterialTheme.typography.titleLarge,
            lineHeight = 32.sp,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Message(
    title: String,
    icon: ImageVector?,
    detail: String? = null,
    action: Pair<String, () -> Unit>? = null,
) {
    val colors = LocalMoodColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            OrbIcon(OrbPalette[3], icon, 80.dp)
        } else {
            CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, color = colors.textPrimary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        detail?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        }
        action?.let { (label, onClick) ->
            Spacer(Modifier.height(20.dp))
            PillButton(label, Icons.Rounded.Refresh, onClick)
        }
    }
}

private fun formatTime(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
