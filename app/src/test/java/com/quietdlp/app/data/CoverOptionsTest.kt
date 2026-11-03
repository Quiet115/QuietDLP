package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverOptionsTest {

    @Test
    fun everyAudioFormatTakesACoverExceptWav() {
        AudioOptions.all.map { it.format }.distinct().forEach {
            assertEquals(it, it != "wav", CoverOptions.audioSupported(it))
        }
    }

    @Test
    fun photosNeverGetACover() {
        assertFalse(CoverOptions.available(Kind.PHOTO, "m4a"))
        assertTrue(CoverOptions.ytDlpOptions(Kind.PHOTO, "m4a").isEmpty())
    }

    @Test
    fun wavAudioGetsNoOptionsInsteadOfAFailedDownload() {
        assertFalse(CoverOptions.available(Kind.AUDIO, "wav"))
        assertTrue(CoverOptions.ytDlpOptions(Kind.AUDIO, "wav").isEmpty())
    }

    @Test
    fun aVideoCoverEmbedsAJpgAndKeepsTheFramingOfTheThumbnail() {
        val o = CoverOptions.ytDlpOptions(Kind.VIDEO, "m4a").toMap()
        assertTrue("--embed-thumbnail" in o)
        assertEquals("jpg", o["--convert-thumbnails"])
        assertFalse("--ppa" in o)
    }

    @Test
    fun anAudioCoverIsCroppedToASquare() {
        val o = CoverOptions.ytDlpOptions(Kind.AUDIO, "mp3").toMap()
        assertTrue("--embed-thumbnail" in o)
        assertTrue(o.getValue("--ppa")!!.contains("min(iw,ih)"))
    }

    @Test
    fun everyOptionWithAValueHasOne() {
        // Una opción con valor null se manda sin argumento: aquí solo --embed-thumbnail lo es.
        val nulls = CoverOptions.ytDlpOptions(Kind.AUDIO, "mp3").filter { it.second == null }.map { it.first }
        assertEquals(listOf("--embed-thumbnail"), nulls)
    }
}
