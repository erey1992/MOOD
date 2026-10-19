package com.mood.player.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Respuesta HTTP no satisfactoria. [code] 429 = el servidor limita las peticiones ("demasiadas peticiones"). */
class HttpException(val code: Int) : IOException("HTTP $code")

object Http {
    private const val MAX_ATTEMPTS = 4

    /**
     * Descarga [url]. Si el servidor responde 429 (límite de peticiones) o 503 (saturado), espera lo que
     * indica su cabecera Retry-After (o 1 s, 2 s, 4 s…) y lo vuelve a intentar. Llamar fuera del hilo principal.
     */
    fun get(url: String): ByteArray {
        var attempt = 1
        while (true) {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "MOOD/1.0 (Android)")
            try {
                val code = connection.responseCode
                if (code in 200..299) return connection.inputStream.use { it.readBytes() }
                if ((code == 429 || code == 503) && attempt < MAX_ATTEMPTS) {
                    val waitMs = connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.times(1_000)
                        ?: (1_000L shl (attempt - 1))
                    Log.d("MOOD", "HTTP $code, reintento $attempt en $waitMs ms: ${URL(url).host}")
                    // Un pequeño margen sobre lo pedido evita volver a llegar justo antes de que se libere el límite.
                    Thread.sleep(waitMs.coerceIn(500, 8_000) + 250)
                    attempt++
                    continue
                }
                throw HttpException(code)
            } finally {
                connection.disconnect()
            }
        }
    }
}

/** Caché pequeña de miniaturas descargadas para la lista de resultados. Llamar fuera del hilo principal. */
object RemoteImages {
    private val cache = LruCache<String, Bitmap>(40)

    fun load(url: String): Bitmap? =
        cache.get(url) ?: runCatching {
            val bytes = Http.get(url)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()?.also { cache.put(url, it) }
}
