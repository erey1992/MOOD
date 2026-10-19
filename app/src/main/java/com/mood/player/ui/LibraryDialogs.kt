package com.mood.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mood.player.data.MusicCollection
import com.mood.player.data.Track
import java.io.File

/** Diálogo abierto sobre la lista de canciones. */
sealed interface LibraryDialog {
    data class Rename(val track: Track) : LibraryDialog
    data class Move(val tracks: List<Track>) : LibraryDialog
    data class Delete(val tracks: List<Track>) : LibraryDialog
}

@Composable
fun RenameDialog(track: Track, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val initial = (track.fileName ?: track.title).substringBeforeLast('.')
    var value by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(initial, selection = TextRange(0, initial.length)))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar nombre") },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("Nombre del fichero") },
                    suffix = { Text(".mp3") },
                    singleLine = true,
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    "Cambia el nombre del fichero. El título que se muestra viene de las etiquetas; " +
                        "puedes editarlo desde el reproductor con «Buscar datos en internet».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text("Renombrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun MoveDialog(
    count: Int,
    destinations: List<MusicCollection>,
    onMove: (path: String, name: String) -> Unit,
    onNewFolder: (name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newFolder by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (count == 1) "Mover canción a…" else "Mover $count canciones a…") },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(destinations, key = { it.path }) { folder ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onMove(folder.path, folder.name) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Folder, contentDescription = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    File(folder.path).parent.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(Modifier.padding(4.dp))
                OutlinedTextField(
                    value = newFolder,
                    onValueChange = { newFolder = it },
                    label = { Text("Nueva carpeta en Music") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = { onNewFolder(newFolder) }, enabled = newFolder.isNotBlank()) {
                            Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Crear y mover")
                        }
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
fun DeleteConfirmDialog(tracks: List<Track>, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (tracks.size == 1) "¿Borrar canción?" else "¿Borrar ${tracks.size} canciones?") },
        text = {
            Text(
                if (tracks.size == 1) "«${tracks.first().title}» se borrará del dispositivo. No se puede deshacer."
                else "Los ficheros se borrarán del dispositivo. No se puede deshacer."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
