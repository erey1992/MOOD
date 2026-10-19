package com.mood.player.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mood.player.data.MetadataCandidate
import com.mood.player.data.RemoteImages
import com.mood.player.data.TagField
import com.mood.player.data.TrackTags
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagEditorScreen(
    mediaId: String,
    player: PlayerViewModel,
    onClose: () -> Unit,
    viewModel: TagEditorViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(mediaId) { viewModel.open(mediaId) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onPermissionResult(result.resultCode == Activity.RESULT_OK, player)
    }
    LaunchedEffect(state.pendingPermission) {
        state.pendingPermission?.let { permissionLauncher.launch(IntentSenderRequest.Builder(it).build()) }
    }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, "Datos guardados en el fichero", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    val selected = state.selected
    BackHandler { if (selected != null) viewModel.backToResults() else onClose() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selected == null) "Buscar datos en internet" else "Revisar cambios") },
                navigationIcon = {
                    IconButton(onClick = { if (selected != null) viewModel.backToResults() else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (selected == null) {
                SearchContent(state, viewModel)
            } else {
                ReviewContent(state, selected, viewModel, onSave = { viewModel.save(player) })
            }
        }
    }
}

@Composable
private fun SearchContent(state: TagEditorState, viewModel: TagEditorViewModel) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                CurrentTagsCard(state)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text("Búsqueda") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { viewModel.search() }),
                    trailingIcon = {
                        IconButton(onClick = viewModel::search) {
                            Icon(Icons.Rounded.Search, contentDescription = "Buscar")
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                if (state.isSearching) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.error?.let { ErrorText(it) }
                if (state.hasSearched && !state.isSearching && state.results.isEmpty() && state.error == null) {
                    Text(
                        "Sin resultados. Prueba a cambiar el texto de búsqueda.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.results.isNotEmpty()) {
                    Text(
                        "Elige el resultado que corresponde a esta canción:",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        items(state.results) { candidate ->
            CandidateRow(candidate, state.currentDurationMs, onClick = { viewModel.select(candidate) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun CurrentTagsCard(state: TagEditorState) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(state.currentArtwork, size = 64.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Datos actuales del fichero", style = MaterialTheme.typography.labelMedium)
                TrackTags.TEXT_FIELDS.take(3).forEach { field ->
                    Text(
                        "${field.label}: ${state.current[field] ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CandidateRow(candidate: MetadataCandidate, currentDurationMs: Long?, onClick: () -> Unit) {
    val durationMatches = candidate.durationMs != null && currentDurationMs != null &&
        abs(candidate.durationMs - currentDurationMs) < 3_000
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteCover(candidate.thumbnailUrl, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                candidate.tags.title.orEmpty(),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(candidate.tags.artist.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(candidate.tags.album, candidate.tags.year).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            candidate.durationMs?.let { Text(formatDuration(it), style = MaterialTheme.typography.labelMedium) }
            if (durationMatches) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = "La duración coincide",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewContent(
    state: TagEditorState,
    candidate: MetadataCandidate,
    viewModel: TagEditorViewModel,
    onSave: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                Text(
                    "Marca los datos que quieres guardar en el fichero. Por defecto solo se marcan los que faltan.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                val canChange = state.proposedArtwork != null
                FieldRow(
                    label = TagField.ARTWORK.label,
                    checked = TagField.ARTWORK in state.choices,
                    enabled = canChange,
                    onToggle = { viewModel.toggle(TagField.ARTWORK) },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LabeledCover("Actual", state.currentArtwork)
                        if (state.isLoadingArtwork) {
                            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LabeledCover("Nueva", state.proposedArtwork)
                        }
                    }
                }
                HorizontalDivider()
            }
            items(TrackTags.TEXT_FIELDS) { field ->
                val current = state.current[field]
                val proposed = candidate.tags[field]
                val canChange = !proposed.isNullOrBlank() && proposed != current
                FieldRow(
                    label = field.label,
                    checked = field in state.choices,
                    enabled = canChange,
                    onToggle = { viewModel.toggle(field) },
                ) {
                    Text(
                        "Actual: ${current ?: "—"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Nuevo: ${proposed ?: "—"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (canChange) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
            }
        }

        Surface(tonalElevation = 3.dp) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp)
            ) {
                state.error?.let { ErrorText(it) }
                Button(
                    onClick = onSave,
                    enabled = state.choices.isNotEmpty() && !state.isSaving && !state.isLoadingArtwork,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Guardando…")
                    } else {
                        val n = state.choices.size
                        Text(if (n == 1) "Guardar 1 cambio en el fichero" else "Guardar $n cambios en el fichero")
                    }
                }
            }
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            content()
        }
    }
}

@Composable
private fun LabeledCover(label: String, image: ImageBitmap?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Cover(image, size = 96.dp)
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun RemoteCover(url: String?, size: Dp) {
    val image by produceState<ImageBitmap?>(null, url) {
        value = url?.let { withContext(Dispatchers.IO) { RemoteImages.load(it)?.asImageBitmap() } }
    }
    Cover(image, size)
}

@Composable
private fun Cover(image: ImageBitmap?, size: Dp) {
    val modifier = Modifier
        .size(size)
        .clip(RoundedCornerShape(8.dp))
        .background(MaterialTheme.colorScheme.surfaceVariant)
    if (image != null) {
        Image(image, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
