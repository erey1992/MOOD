package com.mood.player.data

import android.content.Context
import android.net.Uri
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File

/**
 * Copia en disco de la última biblioteca escaneada. Al abrir la app se enseña al instante y el escaneo
 * real se hace en segundo plano; si algo no cuadra (versión, fichero dañado) simplemente no se usa.
 */
class LibrarySnapshot(context: Context) {

    private val file = File(context.filesDir, "library_snapshot.bin")

    fun read(): List<MusicCollection> = runCatching {
        if (!file.exists()) return emptyList()
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != VERSION) return emptyList()
            List(input.readInt()) {
                MusicCollection(
                    path = input.readUTF(),
                    name = input.readUTF(),
                    subtitle = input.readNullable(),
                    tracks = List(input.readInt()) {
                        Track(
                            id = input.readLong(),
                            uri = Uri.parse(input.readUTF()),
                            title = input.readUTF(),
                            artist = input.readNullable(),
                            album = input.readNullable(),
                            durationMs = input.readLong(),
                            fileName = input.readNullable(),
                            path = input.readNullable(),
                            genre = input.readNullable(),
                            albumArtist = input.readNullable(),
                            trackNumber = input.readInt().takeIf { it >= 0 },
                            year = input.readInt().takeIf { it >= 0 },
                        )
                    },
                )
            }
        }
    }.getOrDefault(emptyList())

    fun write(collections: List<MusicCollection>) {
        runCatching {
            val tmp = File(file.path + ".tmp")
            DataOutputStream(tmp.outputStream().buffered()).use { out ->
                out.writeInt(VERSION)
                out.writeInt(collections.size)
                collections.forEach { collection ->
                    out.writeUTF(collection.path)
                    out.writeUTF(collection.name)
                    out.writeNullable(collection.subtitle)
                    out.writeInt(collection.tracks.size)
                    collection.tracks.forEach { track ->
                        out.writeLong(track.id)
                        out.writeUTF(track.uri.toString())
                        out.writeUTF(track.title)
                        out.writeNullable(track.artist)
                        out.writeNullable(track.album)
                        out.writeLong(track.durationMs)
                        out.writeNullable(track.fileName)
                        out.writeNullable(track.path)
                        out.writeNullable(track.genre)
                        out.writeNullable(track.albumArtist)
                        out.writeInt(track.trackNumber ?: -1)
                        out.writeInt(track.year ?: -1)
                    }
                }
            }
            tmp.renameTo(file)
        }
    }

    private fun DataInputStream.readNullable(): String? = if (readBoolean()) readUTF() else null

    private fun DataOutputStream.writeNullable(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private companion object {
        const val VERSION = 1
    }
}
