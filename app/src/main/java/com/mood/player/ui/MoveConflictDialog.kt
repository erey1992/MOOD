package com.mood.player.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mood.player.data.FileDetails
import java.io.File
import java.text.DateFormat
import java.util.Date

/**
 * Al mover, una canción ya existe en la carpeta de destino: muestra las dos versiones lado a lado para
 * decidir si reemplazar la que hay o no mover la nueva. Con varias, se puede aplicar la decisión a las demás.
 */
@Composable
fun MoveConflictDialog(session: ConflictSession, onResolve: (replace: Set<Uri>) -> Unit, onCancel: () -> Unit) {
    var index by remember(session) { mutableIntStateOf(0) }
    val decisions = remember(session) { mutableStateMapOf<Uri, Boolean>() }
    var applyToRest by remember(session) { mutableStateOf(false) }
    val conflict = session.conflicts[index]
    val remaining = session.conflicts.size - index - 1

    fun decide(replace: Boolean) {
        val affected = if (applyToRest) session.conflicts.drop(index) else listOf(conflict)
        affected.forEach { decisions[it.track.uri] = replace }
        if (applyToRest || remaining == 0) onResolve(decisions.filterValues { it }.keys) else index++
    }

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Column {
                Text("Ya existe en «${session.folderName}»")
                if (session.conflicts.size > 1) {
                    Text(
                        "${index + 1} de ${session.conflicts.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    File(conflict.incoming.path).name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FileVersion("Ya en la carpeta", conflict.existing, conflict.incoming, OrbPalette[1], Modifier.weight(1f))
                    FileVersion("La que mueves", conflict.incoming, conflict.existing, OrbPalette[3], Modifier.weight(1f))
                }
                if (remaining > 0) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { applyToRest = !applyToRest },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = applyToRest, onCheckedChange = { applyToRest = it })
                        Text(
                            if (remaining == 1) "Hacer lo mismo con la otra repetida"
                            else "Hacer lo mismo con las $remaining repetidas restantes",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onCancel) { Text("Cancelar") }
                TextButton(onClick = { decide(replace = false) }) { Text("Omitir") }
                TextButton(onClick = { decide(replace = true) }) {
                    Text("Reemplazar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    )
}

/** Una versión del fichero. Los datos que difieren de la otra versión van en negrita; la de mayor calidad lleva ▲. */
@Composable
private fun FileVersion(label: String, file: FileDetails, other: FileDetails, color: Color, modifier: Modifier = Modifier) {
    val colors = LocalMoodColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.3f), shape)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        TrackArt(file.uri, color, 64.dp)
        Spacer(Modifier.height(8.dp))
        val betterQuality = (file.bitrateKbps ?: 0) > (other.bitrateKbps ?: 0)
        val rows = listOf(
            Triple("Título", file.title ?: "—", file.title != other.title),
            Triple("Artista", file.artist ?: "—", file.artist != other.artist),
            Triple("Álbum", file.album ?: "—", file.album != other.album),
            Triple(
                "Duración",
                file.durationMs?.let(::formatDuration) ?: "—",
                file.durationMs?.div(1000) != other.durationMs?.div(1000),
            ),
            Triple("Tamaño", "%.1f MB".format(file.sizeBytes / 1_048_576.0), file.sizeBytes != other.sizeBytes),
            Triple(
                "Calidad",
                (file.bitrateKbps?.let { "$it kbps" } ?: "—") + if (betterQuality) " ▲" else "",
                file.bitrateKbps != other.bitrateKbps,
            ),
            Triple(
                "Modificado",
                DateFormat.getDateInstance(DateFormat.SHORT).format(Date(file.modifiedMs)),
                file.modifiedMs / 60_000 != other.modifiedMs / 60_000,
            ),
        )
        rows.forEach { (name, value, differs) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            ) {
                Text(name, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                Text(
                    value,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (differs) colors.textPrimary else colors.textSecondary,
                    fontWeight = if (differs) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
