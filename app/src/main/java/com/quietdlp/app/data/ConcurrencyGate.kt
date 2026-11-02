package com.quietdlp.app.data

import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * Cuántas descargas pueden ir a la vez. A diferencia de un semáforo, el tope se puede cambiar mientras
 * funciona (p. ej. bajarlo a 1 si una web empieza a rechazar por ir muy rápido).
 */
class ConcurrencyGate(limit: Int) {
    @Volatile
    var limit: Int = limit.coerceAtLeast(1)
        set(v) {
            field = v.coerceAtLeast(1)
        }

    private val used = AtomicInteger(0)

    val inUse: Int get() = used.get()

    /** Espera a que haya un hueco y lo ocupa. */
    suspend fun acquire() {
        while (true) {
            val u = used.get()
            if (u < limit && used.compareAndSet(u, u + 1)) return
            delay(POLL_MS)
        }
    }

    fun release() {
        used.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    private companion object {
        const val POLL_MS = 200L
    }
}
