package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNoticeTimerTest {

    private val start = 5_000L

    @Test
    fun lastsTwentySecondsAndThenDisappears() {
        assertTrue(UpdateNoticeTimer.visible(start, closed = false, now = start))
        assertTrue(UpdateNoticeTimer.visible(start, closed = false, now = start + 19_999))
        assertFalse(UpdateNoticeTimer.visible(start, closed = false, now = start + 20_000))
        assertFalse(UpdateNoticeTimer.visible(start, closed = false, now = start + 90_000))
    }

    @Test
    fun ringEmptiesContinuouslyFromFullToZero() {
        assertEquals(1f, UpdateNoticeTimer.fraction(start, start), 0.0001f)
        assertEquals(0.5f, UpdateNoticeTimer.fraction(start, start + 10_000), 0.0001f)
        assertEquals(0f, UpdateNoticeTimer.fraction(start, start + 25_000), 0.0001f)
    }

    @Test
    fun closedOrNeverStartedIsNeverVisible() {
        assertFalse(UpdateNoticeTimer.visible(start, closed = true, now = start + 1_000))
        assertFalse(UpdateNoticeTimer.visible(null, closed = false, now = start))
    }

    @Test
    fun remainingTimeDependsOnlyOnTheStartInstantSoRotatingDoesNotRestartIt() {
        // La pantalla se recrea a los 7 s: con el mismo inicio, quedan 13 s y no 20.
        assertEquals(13_000L, UpdateNoticeTimer.remainingMs(start, start + 7_000))
        assertEquals(13, UpdateNoticeTimer.secondsLeft(start, start + 7_000))
    }

    @Test
    fun clockGoingBackwardsNeverExceedsTheFullDuration() {
        assertEquals(UpdateNoticeTimer.DURATION_MS, UpdateNoticeTimer.remainingMs(start, start - 3_000))
    }

    @Test
    fun reducedMotionRingMovesInStepsAndStillReachesZero() {
        val steps = (0..20_000 step 100).map { UpdateNoticeTimer.steppedFraction(UpdateNoticeTimer.fraction(0, it.toLong())) }.toSet()
        assertEquals(UpdateNoticeTimer.REDUCED_STEPS + 1, steps.size)
        assertEquals(0f, UpdateNoticeTimer.steppedFraction(0f), 0f)
        assertEquals(1f, UpdateNoticeTimer.steppedFraction(1f), 0f)
    }
}
