package com.mood.player.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.lerp
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Nightlife
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mood.player.data.Mood
import com.mood.player.data.MoodPlaylist

/** Iconos disponibles para los MOODS, identificados por la clave que se guarda en el JSON. */
object MoodIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "happy" to Icons.Rounded.SentimentVerySatisfied,
        "sad" to Icons.Rounded.SentimentDissatisfied,
        "romantic" to Icons.Rounded.Favorite,
        "dance" to Icons.Rounded.Nightlife,
        "workout" to Icons.Rounded.FitnessCenter,
        "chill" to Icons.Rounded.Spa,
        "focus" to Icons.Rounded.Psychology,
        "sleep" to Icons.Rounded.Bedtime,
        "roadtrip" to Icons.Rounded.DirectionsCar,
        "party" to Icons.Rounded.Celebration,
        "fire" to Icons.Rounded.LocalFireDepartment,
        "coffee" to Icons.Rounded.LocalCafe,
        "beach" to Icons.Rounded.BeachAccess,
        "rain" to Icons.Rounded.Umbrella,
        "nature" to Icons.Rounded.Park,
        "game" to Icons.Rounded.SportsEsports,
        "star" to Icons.Rounded.Star,
        "music" to Icons.Rounded.MusicNote,
    )

    fun of(key: String): ImageVector = all.firstOrNull { it.first == key }?.second ?: Icons.Rounded.MusicNote
}

/** Diálogos relacionados con los MOODS. */
sealed interface MoodDialog {
    data class AddTracks(val uris: List<String>) : MoodDialog
    /** [mood] null = crear uno nuevo; [addUris] se añaden al MOOD recién creado. */
    data class Edit(val mood: Mood?, val addUris: List<String> = emptyList()) : MoodDialog
    data class Delete(val mood: Mood) : MoodDialog
}

@Composable
fun MoodGrid(
    playlists: List<MoodPlaylist>,
    onOpen: (MoodPlaylist) -> Unit,
    onEdit: (Mood) -> Unit,
    onDelete: (Mood) -> Unit,
    onToggleFavorite: (Mood) -> Unit,
    onCreate: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(140.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(playlists, key = { it.mood.id }) { playlist ->
            MoodTile(
                playlist,
                onOpen = { onOpen(playlist) },
                onEdit = { onEdit(playlist.mood) },
                onDelete = { onDelete(playlist.mood) },
                onToggleFavorite = { onToggleFavorite(playlist.mood) },
            )
        }
        item(key = "new") { NewMoodTile(onCreate) }
    }
}

@Composable
private fun MoodTile(
    playlist: MoodPlaylist,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val colors = LocalMoodColors.current
    val color = Color(playlist.mood.color)
    var menuOpen by remember { mutableStateOf(false) }
    val count = playlist.collection.tracks.size

    GlassCard(
        Modifier.aspectRatio(0.92f),
        shape = RoundedCornerShape(26.dp),
        tint = color,
        onClick = onOpen,
        onLongClick = { menuOpen = true },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            OrbIcon(color, MoodIcons.of(playlist.mood.icon), 84.dp, iconScale = 0.5f)
            Column {
                if (playlist.mood.favorite) {
                    Icon(Icons.Rounded.Star, contentDescription = "Favorito", tint = color, modifier = Modifier.size(20.dp))
                }
                Text(
                    playlist.mood.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (count == 1) "1 canción" else "$count canciones",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Box(Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Opciones de ${playlist.mood.name}", tint = colors.textPrimary)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (playlist.mood.favorite) "Quitar de favoritos" else "Marcar como favorito") },
                    leadingIcon = {
                        Icon(
                            if (playlist.mood.favorite) Icons.Rounded.StarBorder else Icons.Rounded.Star,
                            contentDescription = null,
                        )
                    },
                    onClick = { menuOpen = false; onToggleFavorite() },
                )
                DropdownMenuItem(
                    text = { Text("Editar") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuOpen = false; onEdit() },
                )
                DropdownMenuItem(
                    text = { Text("Borrar MOOD", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/**
 * Accesos rápidos del reproductor: una píldora por MOOD favorito. Con color = la canción ya está en ese MOOD;
 * al tocarla se añade (o se quita si ya estaba).
 */
@Composable
fun MoodQuickBar(favorites: List<Mood>, mediaId: String?, onToggle: (Mood) -> Unit, modifier: Modifier = Modifier) {
    if (favorites.isEmpty() || mediaId == null) return
    val colors = LocalMoodColors.current
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
    ) {
        favorites.forEach { mood ->
            val color = Color(mood.color)
            val contained = mediaId in mood.trackUris
            val label = lerp(color, colors.textPrimary, 0.35f)
            val shape = CircleShape
            Row(
                Modifier
                    .clip(shape)
                    .background(if (contained) color.copy(alpha = 0.24f) else colors.glass)
                    .border(1.5.dp, color.copy(alpha = if (contained) 0.75f else 0.3f), shape)
                    .clickable(
                        onClickLabel = if (contained) "Quitar de ${mood.name}" else "Añadir a ${mood.name}",
                        onClick = { onToggle(mood) },
                    )
                    .padding(start = 6.dp, end = 18.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OrbIcon(
                    color,
                    MoodIcons.of(mood.icon),
                    40.dp,
                    Modifier.alpha(if (contained) 1f else 0.7f),
                    glow = contained,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    mood.name,
                    color = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                if (contained) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.Check, contentDescription = "${mood.name}: añadida", tint = label, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NewMoodTile(onCreate: () -> Unit) {
    val colors = LocalMoodColors.current
    GlassCard(Modifier.aspectRatio(0.92f), shape = RoundedCornerShape(26.dp), onClick = onCreate) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OrbIcon(colors.accentLight, Icons.Rounded.Add, 72.dp)
            Spacer(Modifier.height(14.dp))
            Text("Nuevo MOOD", color = colors.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Crear (mood == null) o editar un MOOD: nombre e icono. */
@Composable
fun MoodEditorDialog(mood: Mood?, onSave: (name: String, icon: String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(mood?.name.orEmpty()) }
    var icon by rememberSaveable { mutableStateOf(mood?.icon ?: MoodIcons.all.first().first) }
    val accent = mood?.let { Color(it.color) } ?: MaterialTheme.colorScheme.primary
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (mood == null) "Nuevo MOOD" else "Editar MOOD") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(24) },
                    label = { Text("Nombre") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Spacer(Modifier.height(16.dp))
                Text("Icono", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(48.dp),
                    modifier = Modifier.heightIn(max = 220.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(MoodIcons.all, key = { it.first }) { (key, vector) ->
                        val selected = key == icon
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                vector,
                                contentDescription = key,
                                tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, icon) }, enabled = name.isNotBlank()) {
                Text(if (mood == null) "Crear" else "Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Marca o desmarca los MOODS que contienen las canciones indicadas. Los cambios se aplican al instante. */
@Composable
fun AddToMoodDialog(
    playlists: List<MoodPlaylist>,
    uris: List<String>,
    onToggle: (mood: Mood, add: Boolean) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (uris.size == 1) "Añadir a MOOD" else "Añadir ${uris.size} canciones a MOOD") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(playlists, key = { it.mood.id }) { playlist ->
                    val mood = playlist.mood
                    val contained = uris.all { it in mood.trackUris }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(mood, !contained) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OrbIcon(Color(mood.color), MoodIcons.of(mood.icon), 40.dp, glow = false)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(mood.name, fontWeight = FontWeight.SemiBold)
                            val count = playlist.collection.tracks.size
                            Text(
                                if (count == 1) "1 canción" else "$count canciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Checkbox(checked = contained, onCheckedChange = { onToggle(mood, it) })
                    }
                }
                item {
                    TextButton(onClick = onCreate) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Nuevo MOOD")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } },
    )
}

@Composable
fun DeleteMoodDialog(mood: Mood, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Borrar el MOOD ${mood.name}?") },
        text = { Text("Solo se borra la lista. Las canciones siguen en el dispositivo.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
