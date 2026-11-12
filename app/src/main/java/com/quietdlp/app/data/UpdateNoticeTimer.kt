package com.quietdlp.app.data

import kotlin.math.ceil

/**
 * El tiempo del aviso de actualización: 20 segundos desde que aparece. Se cuenta con un instante de inicio guardado
 * fuera de la pantalla, así rotar o cambiar el tema no lo reinicia. Lógica pura para poder probarla.
 */
object UpdateNoticeTimer {
    const val DURATION_MS = 20_000L

    /** Pasos del anillo cuando se pidió reducir las animaciones. */
    const val REDUCED_STEPS = 20

    fun remainingMs(startedAt: Long, now: Long, durationMs: Long = DURATION_MS): Long =
        (durationMs - (now - startedAt)).coerceIn(0L, durationMs)

    /** 1 = anillo lleno, 0 = vacío. */
    fun fraction(startedAt: Long, now: Long, durationMs: Long = DURATION_MS): Float =
        remainingMs(startedAt, now, durationMs).toFloat() / durationMs

    /** Lo mismo, pero a saltos: el anillo baja de [steps] en [steps] en vez de vaciarse de forma continua. */
    fun steppedFraction(fraction: Float, steps: Int = REDUCED_STEPS): Float =
        if (fraction <= 0f) 0f else ceil(fraction * steps) / steps

    fun visible(startedAt: Long?, closed: Boolean, now: Long, durationMs: Long = DURATION_MS): Boolean =
        startedAt != null && !closed && remainingMs(startedAt, now, durationMs) > 0L

    /** Segundos que faltan, redondeados hacia arriba, para la descripción del anillo en lectores de pantalla. */
    fun secondsLeft(startedAt: Long, now: Long, durationMs: Long = DURATION_MS): Int =
        ceil(remainingMs(startedAt, now, durationMs) / 1000.0).toInt()
}
