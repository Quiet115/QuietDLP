package com.quietdlp.app.data

import kotlin.math.ceil

// El inicio se guarda fuera de la pantalla para que la rotación no reinicie el aviso.
object UpdateNoticeTimer {
    const val DURATION_MS = 20_000L

    const val REDUCED_STEPS = 20

    fun remainingMs(startedAt: Long, now: Long, durationMs: Long = DURATION_MS): Long =
        (durationMs - (now - startedAt)).coerceIn(0L, durationMs)

    fun fraction(startedAt: Long, now: Long, durationMs: Long = DURATION_MS): Float =
        remainingMs(startedAt, now, durationMs).toFloat() / durationMs

    fun steppedFraction(fraction: Float, steps: Int = REDUCED_STEPS): Float =
        if (fraction <= 0f) 0f else ceil(fraction * steps) / steps

    fun visible(startedAt: Long?, closed: Boolean, now: Long, durationMs: Long = DURATION_MS): Boolean =
        startedAt != null && !closed && remainingMs(startedAt, now, durationMs) > 0L

    // Redondear hacia arriba para los lectores de pantalla.
    fun secondsLeft(startedAt: Long, now: Long, durationMs: Long = DURATION_MS): Int =
        ceil(remainingMs(startedAt, now, durationMs) / 1000.0).toInt()
}
