package com.mood.player.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size

object ArtworkLoader {

    /** Devuelve la carátula incrustada en el MP3 o, si no tiene, la miniatura del sistema. */
    fun load(context: Context, uri: Uri, maxSize: Int = 800): Bitmap? =
        loadEmbedded(context, uri, maxSize) ?: loadThumbnail(context, uri, maxSize)

    private fun loadEmbedded(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { decodeSampled(it, maxSize) }
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun loadThumbnail(context: Context, uri: Uri, maxSize: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            context.contentResolver.loadThumbnail(uri, Size(maxSize, maxSize), null)
        }.getOrNull()
    }

    fun decodeSampled(bytes: ByteArray, maxSize: Int = 800): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSize && bounds.outHeight / (sample * 2) >= maxSize) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
