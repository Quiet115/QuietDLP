package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConvertTargetsTest {
    private fun all() = (ConvertTargets.keepQuality + ConvertTargets.reEncode + ConvertTargets.smaller +
        ConvertTargets.gif + ConvertTargets.audio)

    @Test
    fun idsAndNamesAreUnique() {
        assertEquals(all().size, all().map { it.id }.toSet().size)
        assertEquals(all().size, all().map { it.label to it.ext }.toSet().size)
    }

    @Test
    fun everyAudioOptionExceptOriginalCanBeConvertedTo() {
        val expected = AudioOptions.all.filter { it.format != "best" }
        assertEquals(expected.size, ConvertTargets.audio.size)
        assertTrue(ConvertTargets.audio.none { it.slow })
        assertEquals(expected.map { it.label }, ConvertTargets.audio.map { it.label })
    }

    @Test
    fun commandsReadTheInputAndWriteTheOutput() {
        all().forEach { t ->
            val c = t.command("in", "out.${t.ext}")
            assertEquals("-i", c[c.indexOf("in") - 1])
            assertEquals("out.${t.ext}", c.last())
            assertTrue("${t.id} reports progress", "-progress" in c)
            assertTrue("${t.id} never asks questions", "-nostdin" in c && "-y" in c)
        }
    }

    @Test
    fun audioTargetsDropTheVideoAndKeepTags() {
        ConvertTargets.audio.forEach {
            val c = it.command("in", "out")
            assertTrue("${it.id} -vn", "-vn" in c)
            assertTrue("${it.id} tags", "-map_metadata" in c)
        }
        val mp3 = ConvertTargets.audio.first { it.id == "a-mp3-320" }.command("in", "out")
        assertEquals("320k", mp3[mp3.indexOf("-b:a") + 1])
        assertEquals("m4a", ConvertTargets.audio.first { it.id == "a-alac" }.ext)
    }

    @Test
    fun onlyReEncodingTargetsAreSlow() {
        assertTrue(ConvertTargets.keepQuality.none { it.slow })
        assertTrue((ConvertTargets.reEncode + ConvertTargets.smaller + ConvertTargets.gif).all { it.slow })
        ConvertTargets.keepQuality.forEach { assertTrue("-c" in it.command("a", "b") && "copy" in it.command("a", "b")) }
    }

    @Test
    fun menuDependsOnTheFileType() {
        assertTrue(ConvertTargets.sectionsFor("image/jpeg").isEmpty())
        assertTrue(ConvertTargets.sectionsFor(null).isEmpty())
        assertFalse(ConvertTargets.canConvert("image/png"))
        val audio = ConvertTargets.sectionsFor("audio/mp4")
        assertEquals(listOf("Lossless", "High", "Standard", "Small"), audio.map { it.title })
        val video = ConvertTargets.sectionsFor("video/mp4")
        assertEquals("Keep quality (fast)", video.first().title)
        assertTrue(video.any { it.title == "Audio only: Lossless" })
        // Lo mejor primero también dentro de cada grupo de audio.
        assertEquals(listOf("FLAC", "ALAC", "WAV"), audio.first().targets.map { it.label })
    }

    @Test
    fun warningsMatchWhatReallyHappens() {
        val flac = ConvertTargets.audio.first { it.id == "a-flac" }
        val mp3 = ConvertTargets.audio.first { it.id == "a-mp3-320" }
        assertEquals("The file gets bigger. Quality doesn't improve.", ConvertTargets.warning(flac, "audio/mp4"))
        assertNull(ConvertTargets.warning(flac, "audio/flac"))
        assertNotNull(ConvertTargets.warning(mp3, "audio/mpeg"))
        assertNull(ConvertTargets.warning(mp3, "audio/wav"))
        assertTrue(ConvertTargets.warning(ConvertTargets.reEncode.first(), "video/mp4")!!.contains("slow"))
        assertNull(ConvertTargets.warning(ConvertTargets.keepQuality.first(), "video/mp4"))
    }

    @Test
    fun outputNamesAreSafe() {
        val flac = ConvertTargets.audio.first { it.id == "a-flac" }
        assertEquals("Song (FLAC).flac", ConvertTargets.outputName("Song", flac))
        assertEquals("a b c d (FLAC).flac", ConvertTargets.outputName("a/b:c*d", flac))
        assertEquals("Converted (FLAC).flac", ConvertTargets.outputName("  ?? ", flac))
        assertTrue(ConvertTargets.outputName("x".repeat(500), flac).length < 120)
    }

    private fun assertFalse(b: Boolean) = org.junit.Assert.assertFalse(b)
}
