package com.quietdlp.app.data

import java.util.Locale

data class TrimRange(val startSec: Int, val endSec: Int) {
    val length: Int get() = endSec - startSec

    // Formato de intervalo que espera el motor.
    fun section(): String = "*$startSec-$endSec"

    // Evita pisar la descarga completa y los dos puntos inválidos en nombres de archivo.
    fun fileLabel(): String = " (${part(startSec)}-${part(endSec)})"

    fun encode(): String = "$startSec-$endSec"

    companion object {
        fun decode(text: String?): TrimRange? {
            val p = text?.split("-") ?: return null
            if (p.size != 2) return null
            val a = p[0].toIntOrNull() ?: return null
            val b = p[1].toIntOrNull() ?: return null
            return if (a >= 0 && b > a) TrimRange(a, b) else null
        }

        private fun part(sec: Int): String {
            val h = sec / 3600
            val m = sec % 3600 / 60
            val s = sec % 60
            return if (h > 0) String.format(Locale.ROOT, "%dh%02dm%02ds", h, m, s) else String.format(Locale.ROOT, "%dm%02ds", m, s)
        }

        fun formatTime(sec: Int): String {
            val s = sec.coerceAtLeast(0)
            val h = s / 3600
            val m = s % 3600 / 60
            val r = s % 60
            return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, r) else String.format(Locale.ROOT, "%d:%02d", m, r)
        }

        fun parseTime(text: String): Int? {
            val parts = text.trim().split(":")
            if (parts.size !in 1..3 || parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 6 }) return null
            val n = parts.map { it.toInt() }
            // Los minutos y los segundos que van detrás de otro número no pasan de 59.
            if (n.drop(1).any { it > 59 }) return null
            return n.fold(0) { acc, v -> acc * 60 + v }
        }

        fun choose(start: Int, end: Int, duration: Int): TrimRange? {
            if (duration <= 0 || start < 0 || end > duration || end <= start) return null
            if (start == 0 && end == duration) return null
            return TrimRange(start, end)
        }
    }
}
