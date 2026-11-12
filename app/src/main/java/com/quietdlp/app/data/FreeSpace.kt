package com.quietdlp.app.data

object FreeSpace {
    // El tamaño se desconoce al empezar y el archivo se copia dos veces.
    const val MIN_FREE_BYTES = 300L * 1024 * 1024

    fun isLow(freeBytes: Long, minBytes: Long = MIN_FREE_BYTES): Boolean = freeBytes < minBytes

    fun message(freeBytes: Long): String =
        "Not enough free space (${format(freeBytes.coerceAtLeast(0))} left). Free some up and retry."

    fun format(bytes: Long): String = when {
        bytes >= 1L shl 30 -> "%.1f GB".format(java.util.Locale.US, bytes / (1L shl 30).toDouble())
        else -> "${bytes / (1L shl 20)} MB"
    }
}
