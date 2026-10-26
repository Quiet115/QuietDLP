package com.quietdlp.app.data

/** Comprobación de espacio libre antes de descargar (funciones puras, para poder probarlas). */
object FreeSpace {
    /** El tamaño no se conoce antes de descargar; los vídeos largos pesan cientos de MB y se copian dos veces. */
    const val MIN_FREE_BYTES = 300L * 1024 * 1024

    fun isLow(freeBytes: Long, minBytes: Long = MIN_FREE_BYTES): Boolean = freeBytes < minBytes

    fun message(freeBytes: Long): String =
        "Not enough free space (${format(freeBytes.coerceAtLeast(0))} left). Free some up and retry."

    fun format(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(java.util.Locale.US, bytes / (1L shl 30).toDouble())
        else -> "${bytes / (1L shl 20)} MB"
    }
}
