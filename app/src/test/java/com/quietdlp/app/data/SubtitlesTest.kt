package com.quietdlp.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitlesTest {

    private fun entry(name: String) = """[{"ext":"vtt","url":"https://x/a.vtt","name":"$name"}]"""

    private val json = JSONObject(
        """{"subtitles":{"es":${entry("Spanish")},"en":${entry("English")},"live_chat":${entry("Live chat")},"fr":[]},
            "automatic_captions":{"en-orig":${entry("English (Original)")},"en":${entry("English")},
              "de":${entry("German")},"fr":${entry("French")}}}""",
    )

    @Test
    fun listsRealTracksFirstThenOnlyTheOriginalAutomaticOne() {
        val tracks = SubtitleProbe.parse(json)
        assertEquals(listOf("en", "es", "en-orig"), tracks.map { it.code })
        assertEquals(listOf(false, false, true), tracks.map { it.auto })
        assertEquals("English (auto)", tracks.last().label)
    }

    @Test
    fun theLiveChatAndEmptyTracksAreNotSubtitles() {
        val codes = SubtitleProbe.parse(json).map { it.code }
        assertTrue("live_chat" !in codes)
        assertTrue("fr" !in codes)
    }

    @Test
    fun withoutAnOriginalMarkerTheAutomaticOnesAreKept() {
        val j = JSONObject("""{"automatic_captions":{"es":${entry("Spanish")},"pt":${entry("Portuguese")}}}""")
        assertEquals(listOf("pt", "es"), SubtitleProbe.parse(j).map { it.code })
    }

    @Test
    fun noSubtitlesGivesAnEmptyList() {
        assertTrue(SubtitleProbe.parse(JSONObject("{}")).isEmpty())
        assertTrue(SubtitleProbe.parse(JSONObject("""{"subtitles":{},"automatic_captions":{}}""")).isEmpty())
    }

    @Test
    fun regionalCodesGetReadableNames() {
        val j = JSONObject("""{"subtitles":{"es-419":${entry("x")},"zh-Hans":${entry("y")}}}""")
        val names = SubtitleProbe.parse(j).associate { it.code to it.name }
        assertTrue(names.getValue("es-419").startsWith("Spanish"))
        assertTrue(names.getValue("zh-Hans").startsWith("Chinese"))
    }

    @Test
    fun anUnknownCodeFallsBackToTheSiteNameOrTheCode() {
        val j = JSONObject("""{"subtitles":{"xx-weird":${entry("Klingon CC")}}}""")
        val t = SubtitleProbe.parse(j).single()
        assertTrue(t.name.isNotBlank())
    }

    @Test
    fun preselectKeepsWhatWasChosenBeforeIfTheVideoHasIt() {
        val tracks = SubtitleProbe.parse(json)
        assertEquals(listOf("es"), SubtitleProbe.preselect(tracks, listOf("es", "ja"), "en"))
    }

    @Test
    fun preselectUsesThePhoneLanguageThenEnglishThenNothing() {
        val tracks = SubtitleProbe.parse(json)
        assertEquals(listOf("es"), SubtitleProbe.preselect(tracks, emptyList(), "es"))
        assertEquals(listOf("en"), SubtitleProbe.preselect(tracks, emptyList(), "ja"))
        assertTrue(SubtitleProbe.preselect(emptyList(), emptyList(), "es").isEmpty())
        val onlyGerman = listOf(SubtitleTrack("de", "German", false))
        assertTrue(SubtitleProbe.preselect(onlyGerman, emptyList(), "es").isEmpty())
    }

    @Test
    fun preselectPrefersARealTrackOverAnAutomaticOne() {
        val tracks = listOf(SubtitleTrack("en-orig", "English", true), SubtitleTrack("en", "English", false))
        assertEquals(listOf("en"), SubtitleProbe.preselect(tracks, emptyList(), "en"))
    }

    @Test
    fun regionalVariantsCountAsTheSamePhoneLanguage() {
        val tracks = listOf(SubtitleTrack("es-419", "Spanish (Latin America)", false))
        assertEquals(listOf("es-419"), SubtitleProbe.preselect(tracks, emptyList(), "es"))
        assertEquals("es", SubtitleProbe.languageOf("es-419"))
        assertEquals("en", SubtitleProbe.languageOf("en-orig"))
    }

    @Test
    fun choiceSurvivesBeingSavedAsText() {
        val c = SubtitleChoice(listOf("en", "es-419"), SubtitleMode.MKV, includeAuto = true)
        assertEquals(c, SubtitleChoice.decode(c.encode()))
        assertNull(SubtitleChoice.decode(null))
        assertNull(SubtitleChoice.decode(""))
        assertNull(SubtitleChoice.decode("inside||0"))
        assertNull(SubtitleChoice.decode("garbage"))
    }

    @Test
    fun insideTheVideoEmbedsAndKeepsMp4() {
        val c = SubtitleChoice(listOf("en", "es"), SubtitleMode.INSIDE)
        val o = SubtitleOptions.ytDlpOptions(c).toMap()
        assertEquals("en,es", o["--sub-langs"])
        assertEquals("srt", o["--convert-subs"])
        assertTrue("--embed-subs" in o)
        // Con --write-subs yt-dlp dejaría además el .srt suelto en la carpeta.
        assertTrue("--write-subs" !in o)
        assertTrue("--write-auto-subs" !in o)
        assertEquals("mp4", SubtitleOptions.container(c))
    }

    @Test
    fun mkvEmbedsAndChangesTheContainer() {
        val c = SubtitleChoice(listOf("en"), SubtitleMode.MKV, includeAuto = true)
        val o = SubtitleOptions.ytDlpOptions(c).toMap()
        assertTrue("--embed-subs" in o)
        assertTrue("--write-auto-subs" in o)
        assertEquals("mkv", SubtitleOptions.container(c))
    }

    @Test
    fun aSeparateFileIsNotEmbeddedAndKeepsMp4() {
        val c = SubtitleChoice(listOf("en"), SubtitleMode.SEPARATE)
        val o = SubtitleOptions.ytDlpOptions(c).toMap()
        assertTrue("--embed-subs" !in o)
        assertTrue("--write-subs" in o)
        assertEquals("mp4", SubtitleOptions.container(c))
    }

    @Test
    fun noChoiceMeansNoOptionsAndMp4() {
        assertTrue(SubtitleOptions.ytDlpOptions(null).isEmpty())
        assertTrue(SubtitleOptions.ytDlpOptions(SubtitleChoice(emptyList())).isEmpty())
        assertEquals("mp4", SubtitleOptions.container(null))
    }
}
