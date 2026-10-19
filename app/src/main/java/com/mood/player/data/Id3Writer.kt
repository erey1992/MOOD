package com.mood.player.data

import java.io.ByteArrayOutputStream

/**
 * Editor mínimo de etiquetas ID3v2 para MP3.
 *
 * Lee la etiqueta existente (v2.2, v2.3 o v2.4), sustituye solo los frames de los campos indicados
 * y escribe el resultado como ID3v2.3 (la versión más compatible), conservando el resto de frames
 * y los datos de audio sin tocar.
 */
object Id3Writer {

    private class Frame(val id: String, val data: ByteArray)

    private class ParsedTag(val frames: List<Frame>, val audioStart: Int)

    /** Espacio libre al final de la etiqueta para que futuras ediciones no tengan que mover el audio. */
    private const val PADDING = 1024

    private val V22_TO_V23 = mapOf(
        "TT2" to "TIT2", "TP1" to "TPE1", "TP2" to "TPE2", "TAL" to "TALB",
        "TYE" to "TYER", "TCO" to "TCON", "TRK" to "TRCK", "TPA" to "TPOS", "TCM" to "TCOM",
    )

    fun rewrite(file: ByteArray, text: Map<TagField, String>, artwork: ByteArray?): ByteArray {
        val existing = parse(file)
        val replaced = buildSet {
            text.keys.forEach { addAll(it.frameIds) }
            if (artwork != null) addAll(TagField.ARTWORK.frameIds)
        }

        val frames = existing.frames.filterNot { it.id in replaced }.toMutableList()
        text.forEach { (field, value) -> frames += Frame(field.frameIds.first(), textFrame(value)) }
        artwork?.let { frames += Frame("APIC", pictureFrame(it)) }

        val body = ByteArrayOutputStream()
        frames.forEach { frame ->
            body.write(frame.id.toByteArray(Charsets.ISO_8859_1))
            body.write(int32(frame.data.size))
            body.write(byteArrayOf(0, 0))
            body.write(frame.data)
        }

        val tagSize = body.size() + PADDING
        val audioSize = file.size - existing.audioStart
        val out = ByteArrayOutputStream(10 + tagSize + audioSize)
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        out.write(synchsafe(tagSize))
        body.writeTo(out)
        out.write(ByteArray(PADDING))
        out.write(file, existing.audioStart, audioSize)
        return out.toByteArray()
    }

    private fun parse(file: ByteArray): ParsedTag {
        if (file.size < 10 || file[0] != 'I'.code.toByte() || file[1] != 'D'.code.toByte() || file[2] != '3'.code.toByte()) {
            return ParsedTag(emptyList(), 0)
        }
        val major = file[3].toInt()
        val flags = file[5].toInt() and 0xFF
        val tagEnd = (10 + readSynchsafe(file, 6)).coerceAtMost(file.size)
        val hasFooter = major == 4 && flags and 0x10 != 0
        val audioStart = (tagEnd + if (hasFooter) 10 else 0).coerceAtMost(file.size)

        // Con desincronización a nivel de etiqueta no podemos conservar los frames de forma fiable.
        if (major !in 2..4 || flags and 0x80 != 0) return ParsedTag(emptyList(), audioStart)

        var pos = 10
        if (major >= 3 && flags and 0x40 != 0) {
            pos += if (major == 3) 4 + readInt32(file, 10) else readSynchsafe(file, 10)
        }

        val frames = mutableListOf<Frame>()
        val headerSize = if (major == 2) 6 else 10
        val idLength = if (major == 2) 3 else 4
        while (pos + headerSize <= tagEnd) {
            if (file[pos].toInt() == 0) break // comienzo del padding
            val id = String(file, pos, idLength, Charsets.ISO_8859_1)
            if (!id.all { it in 'A'..'Z' || it in '0'..'9' }) break

            val size = when (major) {
                2 -> readInt24(file, pos + 3)
                3 -> readInt32(file, pos + 4)
                else -> readSynchsafe(file, pos + 4)
            }
            val dataStart = pos + headerSize
            if (size < 0 || dataStart + size > tagEnd) break
            val data = file.copyOfRange(dataStart, dataStart + size)
            pos = dataStart + size

            when (major) {
                2 -> V22_TO_V23[id]?.let { frames += Frame(it, data) }
                3 -> {
                    val formatFlags = file[dataStart - 1].toInt()
                    if (formatFlags and 0xE0 == 0) frames += Frame(id, data)
                }
                else -> {
                    val formatFlags = file[dataStart - 1].toInt()
                    if (formatFlags and 0x4F == 0) frames += fromV24(Frame(id, data))
                }
            }
        }
        return ParsedTag(frames, audioStart)
    }

    /** ID3v2.3 no admite UTF-8: los textos v2.4 en UTF-8 se recodifican. */
    private fun fromV24(frame: Frame): Frame {
        if (!frame.id.startsWith("T") || frame.data.isEmpty() || frame.data[0].toInt() != 3) return frame
        val text = String(frame.data, 1, frame.data.size - 1, Charsets.UTF_8).trimEnd('\u0000')
        return Frame(frame.id, textFrame(text))
    }

    private fun textFrame(value: String): ByteArray =
        if (value.all { it.code <= 0xFF }) {
            byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)
        } else {
            byteArrayOf(1) + value.toByteArray(Charsets.UTF_16) // incluye BOM
        }

    private fun pictureFrame(image: ByteArray): ByteArray {
        val isPng = image.size > 4 && image[0] == 0x89.toByte() && image[1] == 'P'.code.toByte()
        val mime = if (isPng) "image/png" else "image/jpeg"
        val out = ByteArrayOutputStream(image.size + 32)
        out.write(0) // codificación ISO-8859-1
        out.write(mime.toByteArray(Charsets.ISO_8859_1))
        out.write(0)
        out.write(3) // tipo de imagen: portada
        out.write(0) // descripción vacía
        out.write(image)
        return out.toByteArray()
    }

    private fun readSynchsafe(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0x7F shl 21) or (b[at + 1].toInt() and 0x7F shl 14) or
            (b[at + 2].toInt() and 0x7F shl 7) or (b[at + 3].toInt() and 0x7F)

    private fun readInt32(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF shl 24) or (b[at + 1].toInt() and 0xFF shl 16) or
            (b[at + 2].toInt() and 0xFF shl 8) or (b[at + 3].toInt() and 0xFF)

    private fun readInt24(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 0xFF shl 16) or (b[at + 1].toInt() and 0xFF shl 8) or (b[at + 2].toInt() and 0xFF)

    private fun synchsafe(value: Int): ByteArray = byteArrayOf(
        (value shr 21 and 0x7F).toByte(),
        (value shr 14 and 0x7F).toByte(),
        (value shr 7 and 0x7F).toByte(),
        (value and 0x7F).toByte(),
    )

    private fun int32(value: Int): ByteArray = byteArrayOf(
        (value shr 24).toByte(),
        (value shr 16).toByte(),
        (value shr 8).toByte(),
        value.toByte(),
    )
}
