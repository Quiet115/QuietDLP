package com.quietdlp.app.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ConcurrencyGateTest {
    @Test
    fun letsInOnlyAsManyAsTheLimit() = runBlocking {
        val gate = ConcurrencyGate(2)
        gate.acquire()
        gate.acquire()
        assertEquals(2, gate.inUse)
        // Un tercero tiene que esperar.
        assertNull(withTimeoutOrNull(500) { gate.acquire() })
        gate.release()
        assertNotNull(withTimeoutOrNull(1_000) { gate.acquire() })
    }

    @Test
    fun limitCanDropWhileRunning() = runBlocking {
        val gate = ConcurrencyGate(5)
        repeat(3) { gate.acquire() }
        gate.limit = 1
        // Los 3 que ya iban siguen; nadie más entra hasta que quede menos de 1.
        assertNull(withTimeoutOrNull(500) { gate.acquire() })
        gate.release()
        gate.release()
        assertNull(withTimeoutOrNull(500) { gate.acquire() })
        gate.release()
        assertNotNull(withTimeoutOrNull(1_000) { gate.acquire() })
    }

    @Test
    fun limitNeverGoesBelowOne() {
        assertEquals(1, ConcurrencyGate(0).limit)
        val gate = ConcurrencyGate(3)
        gate.limit = -4
        assertEquals(1, gate.limit)
    }

    @Test
    fun releasingTooMuchDoesNotGoNegative() {
        val gate = ConcurrencyGate(2)
        gate.release()
        assertEquals(0, gate.inUse)
    }
}
