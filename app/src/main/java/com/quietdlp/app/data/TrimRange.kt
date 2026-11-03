package com.quietdlp.app.data

import java.util.Locale

/** Un trozo de un vídeo o audio, en segundos: se baja solo desde [startSec] hasta [endSec]. */
data class TrimRange(val startSec: Int, val endSec: Int) {
    val length: Int get() = endSec - startSec

    /** Lo que se le pasa a `--download-sections` de yt-dlp. */
    fun section(): String = "*$startSec-$endSec"

    /** Se añade al nombre del archivo para que no pise la descarga completa. Sin ":" (no valen en un nombre de archivo). */
    fun fileLabel(): String = " (${part(startSec)}-${part(endSec)})"

    /** Para guardarlo en el historial: "30-40". */
    fun encode(): String = "$startSec-$endSec"

    companion object {
        fun decode(text: String?): TrimRange? {
            val p = text?.split("-") ?: return null
            if (p.size != 2) return null
            val a = p[0].toIntOrNull() ?: return null
            val b = p[1].toIntOrNull() ?: return null
            return if (a >= 0 && b > a) TrimRange(a, b) else null
        }

        /** "0m30s", "12m05s", "1h02m05s". */
        private fun part(sec: Int): String {
            val h = sec / 3600
            val m = sec % 3600 / 60
            val s = sec % 60
            return if (h > 0) String.format(Locale.ROOT, "%dh%02dm%02ds", h, m, s) else String.format(Locale.ROOT, "%dm%02ds", m, s)
        }

        /** "1:05", "12:05", "1:02:05". */
        fun formatTime(sec: Int): String {
            val s = sec.coerceAtLeast(0)
            val h = s / 3600
            val m = s % 3600 / 60
            val r = s % 60
            return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, r) else String.format(Locale.ROOT, "%d:%02d", m, r)
        }

        /** Lo que escribe el usuario: "90" (segundos), "1:30" o "1:02:03". null si no se entiende. */
        fun parseTime(text: String): Int? {
            val parts = text.trim().split(":")
            if (parts.size !in 1..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 6 }) return null
            val n = parts.map { it.toInt() }
            // Los minutos y los segundos que van detrás de otro número no pasan de 59.
            if (n.drop(1).any { it > 59 }) return null
            return n.fold(0) { acc, v -> acc * 60 + v }
        }

        /** El trozo elegido, o null si no hay que recortar (todo el vídeo) o no es válido. */
        fun choose(start: Int, end: Int, duration: Int): TrimRange? {
            if (duration <= 0 || start < 0 || end > duration || end <= start) return null
            if (start == 0 && end == duration) return null
            return TrimRange(start, end)
        }
    }
}
