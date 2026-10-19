package com.mood.player.ui

import android.content.Context
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mood.player.data.ArtworkLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Collections

/** Miniaturas de carátulas para las listas, cacheadas en memoria para que el scroll sea fluido. */
object ThumbnailCache {
    private val cache = LruCache<Uri, ImageBitmap>(150)
    private val missing = Collections.synchronizedSet(HashSet<Uri>())

    private val _version = MutableStateFlow(0)

    /** Cambia cada vez que se invalida una carátula, para que las listas la vuelvan a pedir. */
    val version: StateFlow<Int> = _version.asStateFlow()

    fun get(uri: Uri?): ImageBitmap? = uri?.let { cache.get(it) }

    fun isMissing(uri: Uri) = uri in missing

    fun load(context: Context, uri: Uri): ImageBitmap? {
        val bitmap = ArtworkLoader.load(context, uri, maxSize = 160)?.asImageBitmap()
        if (bitmap != null) cache.put(uri, bitmap) else missing += uri
        return bitmap
    }

    /** Olvida la carátula de un fichero (p. ej. tras guardar una nueva desde el editor de datos). */
    fun invalidate(uri: Uri) {
        cache.remove(uri)
        missing -= uri
        _version.value++
    }
}
