package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrimRangeTest {

    @Test
    fun theSectionIsWhatYtDlpExpects() {
        assertEquals("*30-40", TrimRange(30, 40).section())
        assertEquals(10, TrimRange(30, 40).length)
    }

    @Test
    fun theFileLabelHasNoColonsAndKeepsTheOrder() {
        assertEquals(" (0m30s-0m40s)", TrimRange(30, 40).fileLabel())
        assertEquals(" (1m05s-12m00s)", TrimRange(65, 720).fileLabel())
        assertEquals(" (0m00s-1h02m05s)", TrimRange(0, 3725).fileLabel())
    }

    @Test
    fun timesAreShownAsMinutesAndSeconds() {
        assertEquals("0:00", TrimRange.formatTime(0))
        assertEquals("0:09", TrimRange.formatTime(9))
        assertEquals("3:34", TrimRange.formatTime(214))
        assertEquals("1:02:05", TrimRange.formatTime(3725))
        assertEquals("0:00", TrimRange.formatTime(-5))
    }

    @Test
    fun typedTimesAreUnderstood() {
        assertEquals(90, TrimRange.parseTime("90"))
        assertEquals(90, TrimRange.parseTime("1:30"))
        assertEquals(90, TrimRange.parseTime(" 01:30 "))
        assertEquals(3723, TrimRange.parseTime("1:02:03"))
        assertEquals(0, TrimRange.parseTime("0"))
        assertEquals(125, TrimRange.parseTime("125")) // segundos sueltos pueden pasar de 59
        assertEquals(185, TrimRange.parseTime("3:05"))
    }

    @Test
    fun nonsenseTimesAreRejected() {
        listOf("", " ", "abc", "1:60", "1:2:3:4", "1::3", "-5", "1.5", "1:30s", ":30", "99999999").forEach {
            assertNull(it, TrimRange.parseTime(it))
        }
    }

    @Test
    fun chooseKeepsOnlyValidPartialRanges() {
        assertEquals(TrimRange(30, 40), TrimRange.choose(30, 40, 200))
        assertEquals(TrimRange(0, 40), TrimRange.choose(0, 40, 200))
        assertEquals(TrimRange(30, 200), TrimRange.choose(30, 200, 200))
        // Todo el vídeo = no hay nada que recortar.
        assertNull(TrimRange.choose(0, 200, 200))
        assertNull(TrimRange.choose(40, 40, 200))
        assertNull(TrimRange.choose(50, 40, 200))
        assertNull(TrimRange.choose(-1, 40, 200))
        assertNull(TrimRange.choose(10, 300, 200))
        assertNull(TrimRange.choose(10, 20, 0))
    }

    @Test
    fun theRangeSurvivesBeingSavedAsText() {
        assertEquals(TrimRange(30, 40), TrimRange.decode(TrimRange(30, 40).encode()))
        assertNull(TrimRange.decode(null))
        assertNull(TrimRange.decode(""))
        assertNull(TrimRange.decode("40-30"))
        assertNull(TrimRange.decode("a-b"))
        assertNull(TrimRange.decode("1-2-3"))
    }
}
