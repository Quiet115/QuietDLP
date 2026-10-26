package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreeSpaceTest {
    private val mb = 1024L * 1024

    @Test
    fun lowBelowThresholdOnly() {
        assertTrue(FreeSpace.isLow(299 * mb))
        assertFalse(FreeSpace.isLow(300 * mb))
        assertFalse(FreeSpace.isLow(5 * 1024 * mb))
    }

    @Test
    fun formatsSizes() {
        assertEquals("120 MB", FreeSpace.format(120 * mb))
        assertEquals("1.5 GB", FreeSpace.format(1536 * mb))
        assertEquals("0 MB", FreeSpace.format(0))
    }

    @Test
    fun messageIsShortAndNotALoginError() {
        val m = FreeSpace.message(50 * mb)
        assertEquals("Not enough free space (50 MB left). Free some up and retry.", m)
        assertEquals("Not enough free space (0 MB left). Free some up and retry.", FreeSpace.message(-5))
    }
}
