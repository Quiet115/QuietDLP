package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateOperationStateTest {
    @Test
    fun downloadProgressRemainsVisibleAfterTheNoticeExpires() {
        assertFalse(UpdateNoticeTimer.visible(1000L, false, 22000L))
        val operation = UpdateOperationState.from(42, null)
        assertTrue(operation.visible)
        assertEquals(42, operation.progress)
    }

    @Test
    fun permissionAndDownloadFailuresRemainVisibleWithoutActiveProgress() {
        val operation = UpdateOperationState.from(-1, "Synthetic failure")
        assertTrue(operation.visible)
        assertEquals("Synthetic failure", operation.message)
    }

    @Test
    fun rebuildingFromRetainedValuesPreservesThePresentation() {
        val before = UpdateOperationState.from(64, null)
        assertEquals(before, UpdateOperationState.from(64, null))
        val failed = UpdateOperationState.from(-1, "Synthetic failure")
        assertEquals(failed, UpdateOperationState.from(-1, failed.message))
    }

    @Test
    fun clearingTheMessageAfterAnOperationHidesTheStatus() {
        assertFalse(UpdateOperationState.from(-1, null).visible)
        assertFalse(UpdateOperationState.from(-1, " ").visible)
        assertEquals(100, UpdateOperationState.from(110, null).progress)
    }
}
