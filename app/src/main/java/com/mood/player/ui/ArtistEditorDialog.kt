package com.mood.player.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Ventana para elegir qué artistas se quedan en una canción: cada uno con su casilla, y un campo para
 * añadir otro. Si no queda ninguno, la canción se queda sin artista. [onConfirm] recibe los elegidos en orden.
 */
@Composable
fun ArtistEditorDialog(
    subtitle: String?,
    artists: List<String>,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val names = remember(artists) { mutableStateListOf(*artists.toTypedArray()) }
    val checked = remember(artists) { mutableStateListOf(*Array(artists.size) { true }) }
    var newName by remember { mutableStateOf("") }
    val kept = names.filterIndexed { index, _ -> checked[index] }

    fun addNew() {
        val name = newName.trim()
        if (name.isEmpty()) return
        val existing = names.indexOfFirst { it.equals(name, ignoreCase = true) }
        if (existing >= 0) {
            checked[existing] = true
        } else {
            names += name
            checked += true
        }
        newName = ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar artistas") },
        text = {
            Column {
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.padding(4.dp))
                }
                Text(
                    "Desmarca los artistas que no quieras en esta canción.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    itemsIndexed(names) { index, name ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { checked[index] = !checked[index] }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked[index], onCheckedChange = { checked[index] = it })
                            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Añadir artista") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addNew() }),
                    trailingIcon = {
                        IconButton(onClick = ::addNew, enabled = newName.isNotBlank()) {
                            Icon(Icons.Rounded.Add, contentDescription = "Añadir")
                        }
                    },
                )
                Spacer(Modifier.padding(4.dp))
                Text(
                    if (kept.isEmpty()) "Quedará sin artista" else "Quedará: ${kept.joinToString(", ")}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        // Lo escrito en el campo cuenta aunque no se haya pulsado «+».
        confirmButton = {
            TextButton(onClick = {
                addNew()
                onConfirm(names.filterIndexed { index, _ -> checked[index] })
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
