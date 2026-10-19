package com.mood.player.ui

import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.LruCache
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Info
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import java.text.Normalizer
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import com.mood.player.data.ArtworkLoader
import com.mood.player.data.GroupBy
import com.mood.player.data.LibraryGrouping
import com.mood.player.data.Mood
import com.mood.player.data.MusicCollection
import com.mood.player.data.Track
import com.mood.player.data.isManageable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Collections

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodApp(viewModel: PlayerViewModel, sharedUrl: String? = null, openDownloads: Boolean = false, onDownloadIntentConsumed: () -> Unit = {}) {
    val collections by viewModel.collections.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val state by viewModel.playerState.collectAsState()
    val artwork by viewModel.artwork.collectAsState()
    val colors = LocalMoodColors.current

    var openPath by rememberSaveable { mutableStateOf<String?>(null) }
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showDownloads by rememberSaveable { mutableStateOf(false) }
    var editingMediaId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(openDownloads, sharedUrl) {
        if (openDownloads || sharedUrl != null) {
            editingMediaId = null
            showPlayer = false
            openPath = null
            showDownloads = true
        }
    }

    if (showDownloads) {
        DownloadScreen(sharedUrl, onDownloadIntentConsumed, onShowCollections = {
            openPath = null
            showPlayer = false
            editingMediaId = null
            showDownloads = false
            onDownloadIntentConsumed()
        }, onClose = {
            showDownloads = false
            onDownloadIntentConsumed()
        })
        return
    }

    val moodPlaylists by viewModel.moodPlaylists.collectAsState()
    val moodStore = viewModel.moodStore
    val context = LocalContext.current
    var moodDialog by remember { mutableStateOf<MoodDialog?>(null) }
    when (val d = moodDialog) {
        is MoodDialog.AddTracks -> AddToMoodDialog(
            moodPlaylists,
            d.uris,
            onToggle = { mood, add ->
                if (add) moodStore.addTracks(mood.id, d.uris) else moodStore.removeTracks(mood.id, d.uris)
            },
            onCreate = { moodDialog = MoodDialog.Edit(null, addUris = d.uris) },
            onDismiss = { moodDialog = null },
        )
        is MoodDialog.Edit -> MoodEditorDialog(
            d.mood,
            onSave = { name, icon ->
                if (d.mood == null) {
                    val id = moodStore.create(name, icon)
                    if (d.addUris.isNotEmpty()) moodStore.addTracks(id, d.addUris)
                } else {
                    moodStore.update(d.mood.id, name, icon)
                }
                moodDialog = null
            },
            onDismiss = { moodDialog = null },
        )
        is MoodDialog.Delete -> DeleteMoodDialog(
            d.mood,
            onConfirm = {
                moodStore.delete(d.mood.id)
                if (openPath == "mood:${d.mood.id}") openPath = null
                moodDialog = null
            },
            onDismiss = { moodDialog = null },
        )
        null -> Unit
    }

    editingMediaId?.let { mediaId ->
        TagEditorScreen(mediaId, player = viewModel, onClose = { editingMediaId = null })
        return
    }

    val groups by viewModel.groups.collectAsState()
    var tab by rememberSaveable { mutableStateOf(0) } // 0..3 = GroupBy, 4 = MOODS

    /** Grupo (artista o álbum) que contiene la canción que suena, para abrirlo desde el reproductor. */
    fun currentGroup(by: GroupBy): MusicCollection? = state.mediaId?.let { id ->
        groups[by]?.firstOrNull { group -> !group.path.endsWith(":?") && group.tracks.any { it.uri.toString() == id } }
    }
    fun openGroup(by: GroupBy, group: MusicCollection) {
        tab = by.ordinal // al volver atrás, se queda en la pestaña de Artistas o Álbumes
        openPath = group.path
        showPlayer = false
    }
    fun openGroupFromPlayer(by: GroupBy): (() -> Unit)? = currentGroup(by)?.let { group -> { openGroup(by, group) } }

    /** Un enlace por cada artista de la canción (las colaboraciones están en el grupo de cada uno). */
    fun artistLinksFromPlayer(): List<Pair<String, () -> Unit>> = state.mediaId?.let { id ->
        groups[GroupBy.ARTIST].orEmpty()
            .filter { group -> !group.path.endsWith(":?") && group.tracks.any { it.uri.toString() == id } }
            .map { group -> group.name to { openGroup(GroupBy.ARTIST, group) } }
    }.orEmpty()

    val playingFrom by viewModel.playingFrom.collectAsState()

    /** Lista desde la que suena la canción; si no se sabe (p. ej. tras reiniciar la app), su carpeta. */
    fun playingListPath(): String? {
        val known = playingFrom?.takeIf { path ->
            collections.any { it.path == path } ||
                groups.values.any { list -> list.any { it.path == path } } ||
                moodPlaylists.any { it.collection.path == path }
        }
        return known ?: state.mediaId?.let { id ->
            collections.firstOrNull { folder -> folder.tracks.any { it.uri.toString() == id } }?.path
        }
    }

    /** Canción a la que desplazar la lista al abrirla desde el reproductor. */
    var scrollToTrack by remember { mutableStateOf<String?>(null) }

    // Buscador de las pestañas Artistas, Álbumes y Géneros; cada una recuerda su texto.
    var searchQueries by rememberSaveable { mutableStateOf(listOf("", "", "", "")) }

    if (showPlayer && state.hasMedia) {
        BackHandler { showPlayer = false }
        PlayerScreen(
            state,
            artwork,
            viewModel,
            onClose = { showPlayer = false },
            onEditTags = { editingMediaId = state.mediaId },
            onAddToMood = { state.mediaId?.let { moodDialog = MoodDialog.AddTracks(listOf(it)) } },
            favoriteMoods = moodPlaylists.map { it.mood }.filter { it.favorite },
            artistLinks = artistLinksFromPlayer(),
            onOpenPlayingList = playingListPath()?.let { path ->
                {
                    openPath = path
                    scrollToTrack = state.mediaId
                    showPlayer = false
                }
            },
            onOpenAlbum = openGroupFromPlayer(GroupBy.ALBUM),
            onToggleMood = { mood ->
                state.mediaId?.let { uri ->
                    val message = if (uri in mood.trackUris) {
                        moodStore.removeTracks(mood.id, listOf(uri))
                        "Quitada de ${mood.name}"
                    } else {
                        moodStore.addTracks(mood.id, listOf(uri))
                        "Añadida a ${mood.name}"
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
        )
        return
    }

    val currentMood = moodPlaylists.firstOrNull { it.collection.path == openPath }
    val openCollection = collections.firstOrNull { it.path == openPath }
        ?: groups.values.firstNotNullOfOrNull { list -> list.firstOrNull { it.path == openPath } }
        ?: currentMood?.collection
    BackHandler(enabled = openCollection != null) { openPath = null }

    // Filtro de canciones de la lista abierta; si está activo, la reproducción se hace con lo filtrado.
    var trackFilter by rememberSaveable(openPath) { mutableStateOf("") }
    val visibleCollection = openCollection?.let { collection ->
        if (trackFilter.isBlank()) collection
        else collection.copy(tracks = collection.tracks.filter { it.matchesFilter(trackFilter) })
    }
    val isAlbumView = openPath?.startsWith("album:") == true && !openPath!!.endsWith(":?") &&
        openPath != LibraryGrouping.SINGLES_PATH
    var showAlbumInfo by remember(openPath) { mutableStateOf(false) }
    if (showAlbumInfo && openCollection != null) {
        AlbumInfoDialog(
            openCollection,
            artist = openCollection.tracks.mapNotNull { it.albumArtist ?: it.artist }.distinct().singleOrNull(),
            onDismiss = { showAlbumInfo = false },
        )
    }

    val library: LibraryViewModel = viewModel()
    val libraryState by library.state.collectAsState()
    var selectedIds by remember(openPath) { mutableStateOf(emptySet<Long>()) }
    val selectedTracks = openCollection?.tracks?.filter { it.id in selectedIds }.orEmpty()
    val selectionMode = selectedTracks.isNotEmpty()
    var dialog by remember { mutableStateOf<LibraryDialog?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    BackHandler(enabled = selectionMode) { selectedIds = emptySet() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> library.onPermissionResult(result.resultCode == Activity.RESULT_OK) }
    LaunchedEffect(libraryState.pendingPermission) {
        libraryState.pendingPermission?.let { permissionLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LaunchedEffect(libraryState.message) {
        libraryState.message?.let {
            snackbarHost.showSnackbar(it)
            library.messageShown()
        }
    }

    fun finishAction() {
        dialog = null
        selectedIds = emptySet()
    }
    fun requestDelete(tracks: List<Track>) {
        // En Android 11+ el diálogo del sistema ya pide confirmación.
        if (library.needsOwnDeleteConfirmation) {
            dialog = LibraryDialog.Delete(tracks)
        } else {
            library.delete(tracks, viewModel)
            finishAction()
        }
    }

    when (val d = dialog) {
        is LibraryDialog.Rename -> RenameDialog(
            d.track,
            onConfirm = { name ->
                library.rename(d.track, name, viewModel)
                finishAction()
            },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.Move -> MoveDialog(
            count = d.tracks.size,
            destinations = collections.filter { !it.path.startsWith("content:") && it.path != openPath },
            onMove = { path, name ->
                library.move(d.tracks, path, name, viewModel)
                finishAction()
            },
            onNewFolder = { name ->
                library.move(d.tracks, library.newMusicFolder(name), name.trim(), viewModel)
                finishAction()
            },
            onDismiss = { dialog = null },
        )
        is LibraryDialog.Delete -> DeleteConfirmDialog(
            d.tracks,
            onConfirm = {
                library.delete(d.tracks, viewModel)
                finishAction()
            },
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
    libraryState.conflicts?.let { session ->
        MoveConflictDialog(
            session,
            onResolve = { replace -> library.resolveConflicts(replace, viewModel) },
            onCancel = library::cancelConflicts,
        )
    }

    MoodBackground {
        Scaffold(
            containerColor = Color.Transparent,
            snackbarHost = { SnackbarHost(snackbarHost) },
            topBar = {
                if (selectionMode && openCollection != null) {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = colors.textPrimary,
                            navigationIconContentColor = colors.textPrimary,
                            actionIconContentColor = colors.textPrimary,
                        ),
                        title = { Text(if (selectedTracks.size == 1) "1 seleccionada" else "${selectedTracks.size} seleccionadas") },
                        navigationIcon = {
                            IconButton(onClick = { selectedIds = emptySet() }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Cancelar selección")
                            }
                        },
                        actions = {
                            IconButton(onClick = { selectedIds = (visibleCollection ?: openCollection).tracks.map { it.id }.toSet() }) {
                                Icon(Icons.Rounded.SelectAll, contentDescription = "Seleccionar todas")
                            }
                            if (selectedTracks.size == 1) {
                                IconButton(onClick = { dialog = LibraryDialog.Rename(selectedTracks.single()) }) {
                                    Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = "Cambiar nombre")
                                }
                            }
                            IconButton(onClick = { moodDialog = MoodDialog.AddTracks(selectedTracks.map { it.uri.toString() }) }) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Añadir a MOOD")
                            }
                            currentMood?.let { mood ->
                                IconButton(onClick = {
                                    moodStore.removeTracks(mood.mood.id, selectedTracks.map { it.uri.toString() })
                                    selectedIds = emptySet()
                                }) {
                                    Icon(Icons.Rounded.PlaylistRemove, contentDescription = "Quitar del MOOD")
                                }
                            }
                            IconButton(onClick = { dialog = LibraryDialog.Move(selectedTracks) }) {
                                Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = "Mover")
                            }
                            IconButton(onClick = { requestDelete(selectedTracks) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Borrar del dispositivo")
                            }
                        },
                    )
                } else {
                    MoodTopBar(
                        title = openCollection?.name,
                        onBack = if (openCollection != null) ({ openPath = null }) else null,
                    ) {
                        RoundGlassButton(Icons.Rounded.Download, "Descargar MP3 de YouTube", onClick = { showDownloads = true })
                        RoundGlassButton(Icons.Rounded.Refresh, "Volver a escanear", onClick = viewModel::loadMusic)
                    }
                }
            },
            bottomBar = {
                if (state.hasMedia) {
                    MiniPlayer(
                        state = state,
                        artwork = artwork,
                        onOpen = { showPlayer = true },
                        onPlayPause = viewModel::playPause,
                        onNext = viewModel::next,
                    )
                }
            },
        ) { padding ->
            Box(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when {
                    isLoading && collections.isEmpty() -> Text(
                        "Buscando música…",
                        Modifier.align(Alignment.Center),
                        color = colors.textSecondary,
                    )
                    openCollection != null && visibleCollection != null -> TrackList(
                        collection = visibleCollection,
                        filter = trackFilter,
                        onFilterChange = { trackFilter = it },
                        showTrackNumbers = isAlbumView,
                        onAlbumInfo = if (isAlbumView) ({ showAlbumInfo = true }) else null,
                        scrollToMediaId = scrollToTrack,
                        onScrolled = { scrollToTrack = null },
                        currentMediaId = state.mediaId,
                        isPlaying = state.isPlaying,
                        onPause = viewModel::playPause,
                        onResume = viewModel::playPause,
                        selectedIds = selectedIds,
                        onTrackClick = { index, track ->
                            if (selectionMode) selectedIds = selectedIds.toggle(track.id)
                            else viewModel.playCollection(visibleCollection, index, shuffle = false)
                        },
                        onTrackLongClick = { track -> selectedIds = selectedIds.toggle(track.id) },
                        onPlayAll = { viewModel.playCollection(visibleCollection, 0, shuffle = false) },
                        onShuffle = { viewModel.playCollection(visibleCollection, shuffle = true) },
                        onRename = { dialog = LibraryDialog.Rename(it) },
                        onMove = { dialog = LibraryDialog.Move(listOf(it)) },
                        onDelete = { requestDelete(listOf(it)) },
                        onAddToMood = { moodDialog = MoodDialog.AddTracks(listOf(it.uri.toString())) },
                        onRemoveFromMood = currentMood?.let { mood ->
                            { track: Track -> moodStore.removeTracks(mood.mood.id, listOf(track.uri.toString())) }
                        },
                        youtubeQuery = when {
                            openPath == null || openPath!!.endsWith(":?") || openPath == LibraryGrouping.SINGLES_PATH -> null
                            openPath!!.startsWith("artist:") -> openCollection.name
                            openPath!!.startsWith("album:") -> albumQuery(
                                openCollection.name,
                                openCollection.tracks.mapNotNull { it.albumArtist ?: it.artist }.distinct().singleOrNull(),
                            )
                            else -> null
                        },
                        emptyText = if (trackFilter.isNotBlank()) {
                            "Ninguna canción coincide con «${trackFilter.trim()}»."
                        } else if (currentMood != null) {
                            "Este MOOD está vacío. Añade canciones desde el menú ⋮ de cualquier canción o desde el reproductor."
                        } else null,
                    )
                    collections.isEmpty() -> Text(
                        "No se encontraron archivos MP3 en el dispositivo.",
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        textAlign = TextAlign.Center,
                        color = colors.textSecondary,
                    )
                    else -> LibraryTabs(
                        selected = tab,
                        onSelect = { tab = it },
                        query = searchQueries.getOrElse(tab) { "" },
                        onQueryChange = { text ->
                            if (tab in searchQueries.indices) {
                                searchQueries = searchQueries.toMutableList().also { it[tab] = text }
                            }
                        },
                        groupsFor = { by -> groups[by] ?: if (by == GroupBy.FOLDER) collections else emptyList() },
                        onOpen = { openPath = it.path },
                        moods = {
                            MoodGrid(
                                moodPlaylists,
                                onOpen = { openPath = it.collection.path },
                                onEdit = { moodDialog = MoodDialog.Edit(it) },
                                onDelete = { moodDialog = MoodDialog.Delete(it) },
                                onToggleFavorite = { moodStore.setFavorite(it.id, !it.favorite) },
                                onCreate = { moodDialog = MoodDialog.Edit(null) },
                            )
                        },
                    )
                }
                if (libraryState.busy) {
                    LinearProgressIndicator(
                        Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = colors.accent,
                        trackColor = colors.track,
                    )
                }
            }
        }
    }
}

/** Cabecera: "MOOD" (o el nombre de la colección abierta) y botones redondos de cristal. */
@Composable
private fun MoodTopBar(title: String?, onBack: (() -> Unit)?, actions: @Composable () -> Unit) {
    val colors = LocalMoodColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            RoundGlassButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver", onClick = onBack, size = 44.dp)
            Spacer(Modifier.width(12.dp))
        }
        if (title == null) {
            Text(
                "MOOD",
                Modifier.weight(1f),
                color = colors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                style = MaterialTheme.typography.headlineMedium,
            )
        } else {
            Text(
                title,
                Modifier.weight(1f),
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TopActions { actions() }
    }
}

@Composable
fun PermissionScreen(onRequest: () -> Unit) {
    val colors = LocalMoodColors.current
    MoodBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoStatic()
            Spacer(Modifier.height(32.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    OrbIcon(OrbPalette[3], Icons.Rounded.MusicNote, 72.dp)
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "MOOD necesita acceso a tus archivos de audio para encontrar tus colecciones.",
                        textAlign = TextAlign.Center,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(24.dp))
                    PillButton("Conceder permiso", Icons.Rounded.Folder, onRequest, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTabs(
    selected: Int,
    onSelect: (Int) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    groupsFor: (GroupBy) -> List<MusicCollection>,
    onOpen: (MusicCollection) -> Unit,
    moods: @Composable () -> Unit,
) {
    val colors = LocalMoodColors.current
    val labels = GroupBy.entries.map { it.label } + "MOODS"
    Column(Modifier.fillMaxSize()) {
        // Fila fija: las cinco pestañas caben a lo ancho, como en el diseño.
        PrimaryTabRow(
            selectedTabIndex = selected,
            containerColor = Color.Transparent,
            contentColor = colors.textPrimary,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffset(selected, matchContentSize = true),
                    width = Dp.Unspecified,
                    height = 3.dp,
                    color = colors.accent,
                    shape = RoundedCornerShape(3.dp),
                )
            },
            divider = { HorizontalDivider(color = colors.textSecondary.copy(alpha = 0.18f)) },
        ) {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    selectedContentColor = colors.accent,
                    unselectedContentColor = colors.textSecondary,
                ) {
                    Text(
                        label,
                        Modifier.padding(vertical = 14.dp),
                        maxLines = 1,
                        softWrap = false,
                        fontSize = 15.sp,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
        // Cada pestaña conserva su propia posición de scroll.
        key(selected) {
            val by = GroupBy.entries.getOrNull(selected)
            if (by != null) GroupList(groupsFor(by), by, onOpen, query, onQueryChange) else moods()
        }
    }
}

@Composable
private fun GroupList(
    groups: List<MusicCollection>,
    by: GroupBy,
    onOpen: (MusicCollection) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val colors = LocalMoodColors.current
    val searchable = by != GroupBy.FOLDER
    // Se conserva el índice original para que cada grupo mantenga el color de su orbe al filtrar.
    val visible = remember(groups, query, by) {
        groups.withIndex().filter { (_, group) -> !searchable || group.matches(query, by) }
    }
    Column(Modifier.fillMaxSize()) {
        if (searchable) {
            LibrarySearchField(
                query,
                onQueryChange,
                placeholder = when (by) {
                    GroupBy.ARTIST -> "Buscar artista"
                    GroupBy.ALBUM -> "Buscar álbum o artista"
                    else -> "Buscar género"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp),
            )
        }
        if (visible.isEmpty() && query.isNotBlank()) {
            Text(
                "Sin resultados para «${query.trim()}»",
                Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(visible, key = { it.value.path }) { (index, group) ->
                GroupRow(group, by, index, onClick = { onOpen(group) })
            }
        }
    }
}

/** Coincide si todas las palabras buscadas aparecen en el nombre (y, en álbumes, también en el artista). */
private fun MusicCollection.matches(query: String, by: GroupBy): Boolean {
    val words = query.searchKey().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return true
    val haystack = (if (by == GroupBy.ALBUM) "$name ${subtitle.orEmpty()}" else name).searchKey()
    return words.all { it in haystack }
}

/** Coincide si todas las palabras del filtro aparecen en el título, el artista o el nombre del fichero. */
private fun Track.matchesFilter(filter: String): Boolean {
    val words = filter.searchKey().split(' ').filter { it.isNotBlank() }
    val haystack = listOfNotNull(title, artist, fileName).joinToString(" ").searchKey()
    return words.all { it in haystack }
}

/** Minúsculas y sin tildes: "Ávila" encuentra "avila" y al revés. */
private fun String.searchKey(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

@Composable
private fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .height(50.dp)
            .clip(shape)
            .background(colors.glass)
            .border(1.dp, colors.glassBorder, shape)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, contentDescription = null, tint = colors.iconMuted)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(placeholder, color = colors.textSecondary, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Rounded.Close, contentDescription = "Borrar la búsqueda", tint = colors.iconMuted)
            }
        }
    }
}

@Composable
private fun GroupRow(group: MusicCollection, by: GroupBy, index: Int, onClick: () -> Unit) {
    val colors = LocalMoodColors.current
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (by) {
                GroupBy.FOLDER -> OrbIcon(orbColor(index), Icons.Rounded.Folder, 56.dp)
                GroupBy.ARTIST -> OrbIcon(orbColor(index), Icons.Rounded.Person, 56.dp)
                GroupBy.ALBUM -> TrackArt(group.tracks.firstOrNull()?.uri, orbColor(index + 1), 56.dp)
                GroupBy.GENRE -> genreStyle(group.name).let { (icon, color) -> OrbIcon(color, icon, 56.dp) }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    group.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val count = group.tracks.size
                Text(
                    group.subtitle ?: if (count == 1) "1 pista" else "$count pistas",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.iconMuted)
        }
    }
}

/** Carátula de una pista (cacheada) o un orbe si no tiene. */
@Composable
internal fun TrackArt(uri: Uri?, fallback: Color, size: Dp, corner: Dp = 14.dp) {
    val context = LocalContext.current
    val version by ThumbnailCache.version.collectAsState()
    val image by produceState(ThumbnailCache.get(uri), uri, version) {
        value = ThumbnailCache.get(uri)
        if (uri != null && value == null && !ThumbnailCache.isMissing(uri)) {
            value = withContext(Dispatchers.IO) { ThumbnailCache.load(context, uri) }
        }
    }
    ArtOrOrb(image, fallback, size, corner = corner)
}


@Composable
private fun TrackList(
    collection: MusicCollection,
    filter: String,
    onFilterChange: (String) -> Unit,
    showTrackNumbers: Boolean,
    onAlbumInfo: (() -> Unit)?,
    scrollToMediaId: String?,
    onScrolled: () -> Unit,
    currentMediaId: String?,
    isPlaying: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    selectedIds: Set<Long>,
    onTrackClick: (Int, Track) -> Unit,
    onTrackLongClick: (Track) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRename: (Track) -> Unit,
    onMove: (Track) -> Unit,
    onDelete: (Track) -> Unit,
    onAddToMood: (Track) -> Unit,
    onRemoveFromMood: ((Track) -> Unit)?,
    youtubeQuery: String?,
    emptyText: String?,
) {
    val colors = LocalMoodColors.current
    val selectionMode = selectedIds.isNotEmpty()
    val listState = rememberLazyListState()
    val hasLinks = youtubeQuery != null || onAlbumInfo != null
    val showEmpty = collection.tracks.isEmpty() && emptyText != null
    // Elementos antes de la primera canción: botones, filtro, enlaces y aviso de lista vacía.
    val headerCount = 2 + (if (hasLinks) 1 else 0) + (if (showEmpty) 1 else 0)
    LaunchedEffect(scrollToMediaId, collection) {
        val target = scrollToMediaId ?: return@LaunchedEffect
        val index = collection.tracks.indexOfFirst { it.uri.toString() == target }
        if (index >= 0) listState.scrollToItem(headerCount + index, scrollOffset = -240)
        onScrolled()
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PillButton("Reproducir", Icons.Rounded.PlayArrow, onPlayAll, Modifier.weight(1f), enabled = collection.tracks.isNotEmpty())
                PillButton("Aleatorio", Icons.Rounded.Shuffle, onShuffle, Modifier.weight(1f), primary = false, enabled = collection.tracks.isNotEmpty())
            }
        }
        item {
            LibrarySearchField(filter, onFilterChange, placeholder = "Buscar canción en esta lista", modifier = Modifier.fillMaxWidth())
        }
        if (hasLinks) {
            item {
                val context = LocalContext.current
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    onAlbumInfo?.let {
                        PillButton("Info del álbum", Icons.Rounded.Info, it, Modifier.weight(1f), primary = false)
                    }
                    youtubeQuery?.let { query ->
                        PillButton(
                            "YouTube Music",
                            Icons.AutoMirrored.Rounded.OpenInNew,
                            onClick = { openYouTubeMusic(context, query) },
                            modifier = Modifier.weight(1f),
                            primary = false,
                        )
                    }
                }
            }
        }
        if (showEmpty) {
            item {
                Text(
                    emptyText,
                    Modifier.padding(32.dp),
                    textAlign = TextAlign.Center,
                    color = colors.textSecondary,
                )
            }
        }
        itemsIndexed(collection.tracks, key = { _, track -> track.id }) { index, track ->
            TrackRow(
                track = track,
                index = index,
                isCurrent = track.uri.toString() == currentMediaId,
                isPlaying = isPlaying && track.uri.toString() == currentMediaId,
                number = if (showTrackNumbers) track.trackNumber?.rem(1000)?.takeIf { it > 0 }?.toString() ?: "–" else null,
                onArtClick = {
                    val isCurrent = track.uri.toString() == currentMediaId
                    when {
                        isCurrent && isPlaying -> onPause()
                        isCurrent -> onResume()
                        else -> onTrackClick(index, track)
                    }
                },
                selectionMode = selectionMode,
                isSelected = track.id in selectedIds,
                onClick = { onTrackClick(index, track) },
                onLongClick = { onTrackLongClick(track) },
                onRename = { onRename(track) },
                onMove = { onMove(track) },
                onDelete = { onDelete(track) },
                onAddToMood = { onAddToMood(track) },
                onRemoveFromMood = onRemoveFromMood?.let { remove -> { remove(track) } },
            )
        }
    }
}

@Composable
internal fun TrackRow(
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    number: String? = null,
    onArtClick: () -> Unit,
    selectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onAddToMood: () -> Unit,
    onRemoveFromMood: (() -> Unit)?,
) {
    val colors = LocalMoodColors.current
    val rowInteraction = remember { MutableInteractionSource() }
    val rowHovered by rowInteraction.collectIsHoveredAsState()
    val rowPressed by rowInteraction.collectIsPressedAsState()
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tint = if (isSelected || isCurrent) colors.accent else null,
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = rowInteraction,
    ) {
        Row(
            Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // En los álbumes, el número de pista.
            number?.let {
                Text(
                    it,
                    Modifier.width(26.dp),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.width(4.dp))
            }
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = colors.accent, uncheckedColor = colors.iconMuted),
                )
            } else {
                TrackPlayButton(
                    uri = track.uri,
                    color = orbColor(index),
                    isPlaying = isPlaying,
                    highlighted = rowHovered || rowPressed,
                    onClick = onArtClick,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (isCurrent) colors.accent else colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                track.artist?.let {
                    Text(
                        it,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (isPlaying) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Sonando", tint = colors.accent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(formatTime(track.durationMs), color = colors.textSecondary, style = MaterialTheme.typography.labelMedium)
            if (!selectionMode) {
                TrackMenu(
                    onAddToMood = onAddToMood,
                    onRemoveFromMood = onRemoveFromMood,
                    fileActions = track.isManageable,
                    onRename = onRename,
                    onMove = onMove,
                    onDelete = onDelete,
                )
            } else {
                Spacer(Modifier.width(12.dp))
            }
        }
    }
}

/**
 * Círculo de la canción: muestra ▶ al pasar el puntero (o al pulsar la fila) y ⏸ mientras suena.
 * Tocarlo reproduce la canción o, si ya está sonando, la pausa.
 */
@Composable
private fun TrackPlayButton(
    uri: Uri,
    color: Color,
    isPlaying: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val showOverlay = isPlaying || highlighted || hovered || pressed
    val overlayAlpha by animateFloatAsState(if (showOverlay) 1f else 0f, label = "trackOverlay")
    val label = if (isPlaying) "Pausar" else "Reproducir"
    Box(
        Modifier
            .size(44.dp)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Siempre redonda, para que el ▶/■ quede centrado sobre un círculo también con carátulas reales.
        TrackArt(uri, color, 44.dp, corner = 22.dp)
        if (overlayAlpha > 0f) Box(
            Modifier
                .size(44.dp)
                .graphicsLayer { alpha = overlayAlpha }
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = if (pressed) 0.45f else 0.32f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun TrackMenu(
    onAddToMood: () -> Unit,
    onRemoveFromMood: (() -> Unit)?,
    fileActions: Boolean,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Más opciones", tint = LocalMoodColors.current.iconMuted)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Añadir a MOOD…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) },
                onClick = { expanded = false; onAddToMood() },
            )
            onRemoveFromMood?.let { remove ->
                DropdownMenuItem(
                    text = { Text("Quitar de este MOOD") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistRemove, contentDescription = null) },
                    onClick = { expanded = false; remove() },
                )
            }
            if (!fileActions) return@DropdownMenu
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Cambiar nombre") },
                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                onClick = { expanded = false; onRename() },
            )
            DropdownMenuItem(
                text = { Text("Mover a…") },
                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                onClick = { expanded = false; onMove() },
            )
            DropdownMenuItem(
                text = { Text("Borrar del dispositivo", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = { expanded = false; onDelete() },
            )
        }
    }
}

private fun Set<Long>.toggle(id: Long) = if (id in this) this - id else this + id

/** Mini reproductor flotante: tarjeta de cristal con la barra de progreso en el borde superior. */
@Composable
private fun MiniPlayer(
    state: PlayerUiState,
    artwork: ImageBitmap?,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val colors = LocalMoodColors.current
    Box(
        Modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
    ) {
        GlassCard(
            Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            shape = RoundedCornerShape(26.dp),
            strong = true,
            onClick = onOpen,
        ) {
            Row(
                Modifier.padding(start = 14.dp, end = 6.dp, top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ArtOrOrb(artwork, OrbPalette[0], 54.dp, corner = 27.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        state.title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.artist?.let {
                        Text(
                            it,
                            color = colors.textSecondary,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
                IconButton(onClick = onNext, enabled = state.hasNext) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = if (state.hasNext) colors.iconMuted else colors.iconMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
        GradientSeekBar(
            progressOf(state),
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
                .align(Alignment.TopCenter),
            thumbSize = 20.dp,
            trackHeight = 5.dp,
        )
    }
}

@Composable
private fun PlayerScreen(
    state: PlayerUiState,
    artwork: ImageBitmap?,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onEditTags: () -> Unit,
    onAddToMood: () -> Unit,
    favoriteMoods: List<Mood>,
    artistLinks: List<Pair<String, () -> Unit>>,
    onOpenAlbum: (() -> Unit)?,
    onOpenPlayingList: (() -> Unit)?,
    onToggleMood: (Mood) -> Unit,
) {
    val colors = LocalMoodColors.current
    // Página 0: karaoke (se descubre deslizando hacia la derecha). Página 1: reproductor.
    val pager = rememberPagerState(initialPage = 1) { 2 }
    val scope = rememberCoroutineScope()
    fun showPage(page: Int) {
        scope.launch { pager.animateScrollToPage(page) }
    }
    BackHandler(enabled = pager.currentPage == 0) { showPage(1) }

    MoodBackground {
      HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
        if (page == 0) {
            KaraokePage(state, viewModel, active = pager.currentPage == 0, onBack = { showPage(1) })
            return@HorizontalPager
        }
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Cerrar", tint = colors.textPrimary, modifier = Modifier.size(32.dp))
                }
                Row {
                    IconButton(onClick = { showPage(0) }) {
                        Icon(Icons.Rounded.Mic, contentDescription = "Karaoke", tint = colors.textPrimary)
                    }
                    IconButton(onClick = onAddToMood) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = "Añadir a MOOD", tint = colors.textPrimary)
                    }
                    IconButton(onClick = onEditTags) {
                        Icon(Icons.Rounded.TravelExplore, contentDescription = "Buscar datos en internet", tint = colors.textPrimary)
                    }
                    if (onOpenPlayingList != null) {
                        IconButton(onClick = onOpenPlayingList) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = "Ir a la lista en reproducción", tint = colors.textPrimary)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .aspectRatio(1f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                if (artwork != null) {
                    Image(artwork, contentDescription = "Carátula", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    ArtworkPlaceholder(Modifier.fillMaxSize())
                }
            }
            Spacer(Modifier.height(28.dp))

            Text(
                state.title,
                color = colors.textPrimary,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            ArtistAlbumLinks(state.artist, artistLinks, state.album, onOpenAlbum)

            if (favoriteMoods.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                MoodQuickBar(favoriteMoods, state.mediaId, onToggleMood, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(24.dp))
            ProgressBar(state, onSeek = viewModel::seekTo)
            Spacer(Modifier.height(20.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = viewModel::toggleShuffle) {
                    Icon(
                        Icons.Rounded.Shuffle,
                        contentDescription = if (state.shuffleEnabled) "Aleatorio activado" else "Aleatorio",
                        tint = toggleTint(state.shuffleEnabled),
                        modifier = Modifier.size(28.dp),
                    )
                }
                IconButton(onClick = viewModel::previous, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", tint = colors.textPrimary, modifier = Modifier.size(44.dp))
                }
                PlayCircleButton(state.isPlaying, onClick = viewModel::playPause)
                IconButton(onClick = viewModel::next, enabled = state.hasNext, modifier = Modifier.size(56.dp)) {
                    Icon(
                        Icons.Rounded.SkipNext,
                        contentDescription = "Siguiente",
                        tint = if (state.hasNext) colors.textPrimary else colors.iconMuted.copy(alpha = 0.4f),
                        modifier = Modifier.size(44.dp),
                    )
                }
                IconButton(onClick = viewModel::cycleRepeatMode) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "Repetir pista"
                            Player.REPEAT_MODE_ALL -> "Repetir colección"
                            else -> "Sin repetición"
                        },
                        tint = toggleTint(state.repeatMode != Player.REPEAT_MODE_OFF),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }
      }
        PageDots(
            count = 2,
            selected = pager.currentPage,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 22.dp),
        )
    }
}

/**
 * Artista y álbum de la canción. Cada uno lleva a sus canciones dentro de la app (el álbum, al de ese artista);
 * desde esas vistas se puede seguir a YouTube Music. Si la canción no está en la biblioteca, son texto normal.
 */
@Composable
private fun ArtistAlbumLinks(
    artist: String?,
    artistLinks: List<Pair<String, () -> Unit>>,
    album: String?,
    onOpenAlbum: (() -> Unit)?,
) {
    val colors = LocalMoodColors.current
    if (artist == null && album == null) {
        Text("Artista desconocido", color = colors.textSecondary, style = MaterialTheme.typography.titleMedium)
        return
    }
    // Cada enlace ocupa como mucho su parte del ancho y se recorta con "…" si no cabe.
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (artistLinks.isNotEmpty()) {
            artistLinks.forEachIndexed { index, (name, open) ->
                if (index > 0) Text(", ", color = colors.textSecondary, style = MaterialTheme.typography.titleMedium)
                MusicLink(name, "Ver las canciones de $name", open, Modifier.weight(1f, fill = false))
            }
        } else if (artist != null) {
            MusicLink(artist, artist, onClick = null, modifier = Modifier.weight(1f, fill = false))
        }
        if (artist != null && album != null) {
            Text(" · ", color = colors.textSecondary, style = MaterialTheme.typography.titleMedium)
        }
        album?.let {
            MusicLink(it, "Ver las canciones del álbum $it", onOpenAlbum, Modifier.weight(1f, fill = false))
        }
    }
}

@Composable
private fun MusicLink(text: String, label: String, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = label, onClick = onClick) else Modifier)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            Modifier.weight(1f, fill = false),
            color = colors.textSecondary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onClick != null) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = colors.iconMuted, modifier = Modifier.size(18.dp))
        }
    }
}

/** Indicador de páginas: la actual es una píldora morada. */
@Composable
private fun PageDots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { index ->
            val width by animateDpAsState(if (index == selected) 18.dp else 6.dp, label = "pageDot")
            Box(
                Modifier
                    .size(width = width, height = 6.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) colors.accent else colors.iconMuted.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun ProgressBar(state: PlayerUiState, onSeek: (Long) -> Unit) {
    val colors = LocalMoodColors.current
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val fraction = dragFraction ?: progressOf(state)
    Column(Modifier.fillMaxWidth()) {
        GradientSeekBar(
            fraction,
            Modifier.fillMaxWidth(),
            onValueChange = if (state.durationMs > 0) ({ dragFraction = it }) else null,
            onValueChangeFinished = {
                dragFraction?.let { onSeek((it * state.durationMs).toLong()) }
                dragFraction = null
            },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val shownPosition = dragFraction?.let { (it * state.durationMs).toLong() } ?: state.positionMs
            Text(formatTime(shownPosition), color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
            Text(formatTime(state.durationMs), color = colors.textSecondary, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun toggleTint(active: Boolean) =
    if (active) LocalMoodColors.current.accent else LocalMoodColors.current.iconMuted

private fun progressOf(state: PlayerUiState): Float =
    if (state.durationMs <= 0) 0f else (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}
