package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppOrientationTest {
    @Test
    fun keysRoundTripAndUnknownMeansAuto() {
        AppOrientation.entries.forEach { assertEquals(it, AppOrientation.fromKey(it.key)) }
        assertEquals(AppOrientation.AUTO, AppOrientation.fromKey(null))
        assertEquals(AppOrientation.AUTO, AppOrientation.fromKey("sideways"))
    }

    @Test
    fun twoColumnsOnlyOnWideScreens() {
        assertFalse(Layouts.twoColumns(360))
        assertFalse(Layouts.twoColumns(411))
        assertFalse(Layouts.twoColumns(839))
        assertTrue(Layouts.twoColumns(840))
        assertTrue(Layouts.twoColumns(915))
    }
}
