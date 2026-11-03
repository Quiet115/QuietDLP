package com.quietdlp.app.download

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Baja una de las imágenes con fotogramas de un vídeo (ver [com.quietdlp.app.data.Storyboard]). */
object FrameFetch {
    /** Una imagen de estas pesa unos cientos de KB; si algo pesa mucho más, no es lo que se espera. */
    private const val MAX_BYTES = 4_000_000

    /** null si no se pudo bajar, no es una imagen o pesa demasiado. Solo https. */
    fun bitmap(url: String): Bitmap? {
        if (!url.startsWith("https://")) return null
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("User-Agent", "Mozilla/5.0")
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) return null
            if (c.contentLengthLong > MAX_BYTES) return null
            val bytes = c.inputStream.use { readLimited(it) } ?: return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } finally {
            c.disconnect()
        }
    }

    private fun readLimited(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
        return out.toByteArray()
    }
}
