package com.mood.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mood.player.data.Track
import com.mood.player.data.isManageable
import java.io.File

/**
 * Posibles canciones repetidas, agrupadas por canción. Muestra de cada copia su carpeta, fichero y duración
 * para decidir cuál borrar. Tocar una copia la reproduce.
 */
@Composable
fun DuplicatesList(
    groups: List<List<Track>>,
    currentMediaId: String?,
    onPlay: (group: List<Track>, index: Int) -> Unit,
    onDelete: (Track) -> Unit,
) {
    val colors = LocalMoodColors.current
    if (groups.isEmpty()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            OrbIcon(OrbPalette[6], Icons.Rounded.CheckCircle, 80.dp)
            Spacer(Modifier.size(20.dp))
            Text(
                "No hay canciones repetidas",
                color = colors.textPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "${groups.size} ${if (groups.size == 1) "canción aparece" else "canciones aparecen"} más de una vez " +
                    "(mismo título y artista). Compara carpeta, fichero y duración antes de borrar.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        groups.forEachIndexed { groupIndex, group ->
            item(key = "head-$groupIndex-${group.first().uri}") {
                val first = group.first()
                Column(Modifier.padding(top = 10.dp)) {
                    Text(
                        first.title,
                        color = colors.textPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(group.firstNotNullOfOrNull { it.artist }, "${group.size} copias").joinToString(" · "),
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(group, key = { "copy-$groupIndex-${it.uri}" }) { track ->
                DuplicateRow(
                    track = track,
                    colorIndex = groupIndex,
                    isCurrent = track.uri.toString() == currentMediaId,
                    onPlay = { onPlay(group, group.indexOf(track)) },
                    onDelete = { onDelete(track) },
                )
            }
        }
    }
}

@Composable
private fun DuplicateRow(track: Track, colorIndex: Int, isCurrent: Boolean, onPlay: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalMoodColors.current
    val folder = track.path?.let { File(it).parentFile?.name }
    GlassCard(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tint = if (isCurrent) colors.accent else null,
        onClick = onPlay,
    ) {
        Row(Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            TrackArt(track.uri, orbColor(colorIndex), 44.dp, corner = 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    folder?.let { "Carpeta: $it" } ?: "Fuera de la biblioteca de Android",
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(track.fileName, formatDuration(track.durationMs), track.album).joinToString(" · "),
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isCurrent) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Sonando", tint = colors.accent, modifier = Modifier.size(20.dp))
            }
            if (track.isManageable) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.Delete, contentDescription = "Borrar esta copia", tint = MaterialTheme.colorScheme.error)
                }
            } else {
                Box(Modifier.size(12.dp))
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
