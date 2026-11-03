package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicNamingTest {
    private val noise = Regex(MusicNaming.TITLE_NOISE)
    private val artistNoise = Regex(MusicNaming.ARTIST_NOISE)

    private fun title(s: String) = noise.replace(s, "").trimEnd()
    private fun artist(s: String) = artistNoise.replace(s, "")

    @Test
    fun theUsualAdornmentsAreRemovedFromTheTitle() {
        assertEquals("Never Gonna Give You Up", title("Never Gonna Give You Up (Official Video) (4K Remaster)"))
        assertEquals("Bohemian Rhapsody", title("Bohemian Rhapsody (Official Video Remastered)"))
        assertEquals("Song", title("Song [Official Audio]"))
        assertEquals("Song", title("Song (Lyrics)"))
        assertEquals("Song", title("Song (lyric video)"))
        assertEquals("Song", title("Song (Visualizer)"))
        assertEquals("Song", title("Song (HD)"))
    }

    @Test
    fun whatIdentifiesTheVersionStays() {
        assertEquals("Song (feat. Someone)", title("Song (feat. Someone)"))
        assertEquals("Song (Live)", title("Song (Live)"))
        assertEquals("Song (2022 Remaster)", title("Song (2022 Remaster)"))
        assertEquals("Song (Acoustic Version)", title("Song (Acoustic Version)"))
        assertEquals("Official Secrets", title("Official Secrets"))
    }

    @Test
    fun channelSuffixesAreRemovedFromTheArtist() {
        assertEquals("Queen", artist("Queen Official"))
        assertEquals("Rick Astley", artist("Rick Astley - Topic"))
        assertEquals("LuisFonsi", artist("LuisFonsiVEVO"))
        assertEquals("Queen", artist("Queen"))
        assertEquals("Official Charts Company", artist("Official Charts Company"))
    }

    @Test
    fun theArgumentsAreAWellFormedYtDlpCommandInOrder() {
        val a = MusicNaming.arguments()
        // Cada orden lleva sus argumentos: parse-metadata 1, replace-in-metadata 3.
        var i = 0
        var parse = 0
        var replace = 0
        while (i < a.size) {
            when (a[i]) {
                "--parse-metadata" -> { parse++; i += 2 }
                "--replace-in-metadata" -> { replace++; i += 4 }
                else -> error("unexpected ${a[i]} at $i")
            }
        }
        assertEquals(3, parse)
        assertEquals(3, replace)
        // Se parte el título antes de componer artista y canción, y se limpia después.
        val split = a.indexOfFirst { it.startsWith("title:") }
        assertTrue(split < a.indexOf("%(artist,nameA,uploader)s:%(meta_artist)s"))
        assertTrue(a.indexOf("%(track,nameT,title)s:%(meta_title)s") < a.indexOf("--replace-in-metadata"))
    }

    @Test
    fun theDashesAreWrittenAsCodeNotAsCharacters() {
        // Así el argumento es ASCII puro y no depende de cómo se pase al proceso.
        assertTrue(MusicNaming.arguments().all { arg -> arg.all { it.code < 128 } })
    }

    @Test
    fun theFileTemplateUsesTheCleanedFieldsAndKeepsTheTrim() {
        assertEquals("%(meta_artist).60B - %(meta_title).100B%(playlist_index& - {}|)s.%(ext)s", MusicNaming.fileTemplate())
        assertTrue(MusicNaming.fileTemplate(" (0m30s-0m40s)").contains("%(meta_title).100B (0m30s-0m40s)%(playlist_index"))
    }
}
