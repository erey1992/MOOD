package com.mood.player.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mood.player.data.AlbumInfo
import com.mood.player.data.AlbumInfoSource
import com.mood.player.data.MusicCollection
import com.mood.player.data.RemoteImages
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface AlbumInfoState {
    data object Loading : AlbumInfoState
    data class Loaded(val info: AlbumInfo) : AlbumInfoState
    data object NotFound : AlbumInfoState
    data object Error : AlbumInfoState
}

/**
 * Ficha del álbum: nombre, artista, año y la lista oficial de canciones (iTunes), marcando cuáles
 * tienes en este álbum y cuáles faltan. Las tuyas que no están en la lista oficial aparecen aparte.
 */
@Composable
fun AlbumInfoDialog(album: MusicCollection, artist: String?, onDismiss: () -> Unit) {
    val colors = LocalMoodColors.current
    var attempt by remember { mutableIntStateOf(0) }
    val state by produceState<AlbumInfoState>(AlbumInfoState.Loading, album.name, artist, attempt) {
        value = AlbumInfoState.Loading
        value = try {
            AlbumInfoSource.find(album.name, artist)?.let { AlbumInfoState.Loaded(it) } ?: AlbumInfoState.NotFound
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AlbumInfoState.Error
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Información del álbum") },
        text = {
            when (val s = state) {
                AlbumInfoState.Loading -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = colors.accent) }
                AlbumInfoState.NotFound -> Text(
                    "No se encontró «${album.name}»${artist?.let { " de $it" }.orEmpty()} en el catálogo. " +
                        "Si el nombre del álbum o el artista no son correctos, corrígelos con «Buscar datos en internet».",
                )
                AlbumInfoState.Error -> Text("No se pudo consultar la información. Revisa tu conexión a internet.")
                is AlbumInfoState.Loaded -> AlbumInfoContent(s.info, album)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        dismissButton = if (state == AlbumInfoState.Error) {
            { TextButton(onClick = { attempt++ }) { Text("Reintentar") } }
        } else null,
    )
}

@Composable
private fun AlbumInfoContent(info: AlbumInfo, album: MusicCollection) {
    val colors = LocalMoodColors.current
    val matches = remember(info, album) {
        AlbumInfoSource.matchTitles(info.tracks.map { it.title }, album.tracks.map { it.title })
    }
    val haveCount = matches.count { it != null }
    val extras = remember(matches, album) {
        album.tracks.filterIndexed { index, _ -> index !in matches }
    }
    val multiDisc = info.tracks.map { it.disc }.distinct().size > 1

    LazyColumn(Modifier.heightIn(max = 460.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RemoteArtwork(info.artworkUrl)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(info.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(info.artist, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                    info.year?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary) }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (haveCount == info.tracks.size) "Tienes el álbum completo: ${info.tracks.size} canciones."
                else "Tienes $haveCount de ${info.tracks.size} canciones. Faltan ${info.tracks.size - haveCount}.",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (haveCount == info.tracks.size) Color(0xFF10B981) else colors.textPrimary,
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
        }
        items(info.tracks.indices.toList()) { i ->
            val track = info.tracks[i]
            val have = matches[i] != null
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (multiDisc) "${track.disc}-${track.number}" else "${track.number}",
                    Modifier.width(34.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
                Text(
                    track.title,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (have) colors.textPrimary else colors.textSecondary,
                    fontWeight = if (have) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                if (have) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "La tienes", tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                } else {
                    Text("Falta", style = MaterialTheme.typography.labelMedium, color = colors.iconMuted)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Rounded.RadioButtonUnchecked, contentDescription = null, tint = colors.iconMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
        if (extras.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(10.dp))
                Text(
                    "En tu biblioteca, pero no en la lista oficial",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textSecondary,
                )
            }
            items(extras) { track ->
                Text(
                    track.title,
                    Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RemoteArtwork(url: String?) {
    val colors = LocalMoodColors.current
    val image by produceState<ImageBitmap?>(null, url) {
        value = url?.let { withContext(Dispatchers.IO) { RemoteImages.load(it)?.asImageBitmap() } }
    }
    val modifier = Modifier
        .size(72.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(colors.track)
    val current = image
    if (current != null) {
        Image(current, contentDescription = "Carátula del álbum", contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = colors.iconMuted)
        }
    }
}
