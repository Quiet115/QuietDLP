package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorBlockTest {

    @Test
    fun theDefaultCategoriesAreTheUselessOnes() {
        assertEquals(listOf("sponsor", "selfpromo", "interaction"), SponsorCategories.DEFAULT)
        assertTrue(SponsorCategories.DEFAULT.all { c -> SponsorCategories.ALL.any { it.first == c } })
    }

    @Test
    fun cleanKeepsOnlyKnownCodesInListOrder() {
        assertEquals(listOf("sponsor", "outro"), SponsorCategories.clean(setOf("outro", "nonsense", "sponsor")))
        assertTrue(SponsorCategories.clean(emptySet()).isEmpty())
    }

    @Test
    fun theChoiceSurvivesBeingSavedAsText() {
        val c = SponsorChoice(SponsorMode.MARK, listOf("sponsor", "intro"))
        assertEquals(c, SponsorChoice.decode(c.encode()))
        assertNull(SponsorChoice.decode(null))
        assertNull(SponsorChoice.decode(""))
        assertNull(SponsorChoice.decode("remove|"))
        assertNull(SponsorChoice.decode("explode|sponsor"))
        assertNull(SponsorChoice.decode("remove|nonsense"))
        assertNull(SponsorChoice.decode("remove"))
    }

    @Test
    fun onlyYoutubeHasSponsorBlockData() {
        assertTrue(SponsorChoice.supports("https://www.youtube.com/watch?v=abc"))
        assertTrue(SponsorChoice.supports("https://youtu.be/abc"))
        assertTrue(SponsorChoice.supports("https://music.youtube.com/watch?v=abc"))
        assertTrue(SponsorChoice.supports("https://m.youtube.com/shorts/abc"))
        assertFalse(SponsorChoice.supports("https://vimeo.com/123"))
        assertFalse(SponsorChoice.supports("https://notyoutube.com/watch?v=abc"))
        assertFalse(SponsorChoice.supports("https://youtube.com.evil.example/watch"))
    }

    @Test
    fun removingPartsAsksYtDlpToCutThem() {
        val a = ChapterOptions.arguments(SponsorChoice(SponsorMode.REMOVE, listOf("sponsor", "selfpromo")), ChapterMode.OFF, "/d")
        assertEquals(listOf("--sponsorblock-remove", "sponsor,selfpromo"), a)
    }

    @Test
    fun markingPartsAlsoPutsTheChaptersInTheFile() {
        val a = ChapterOptions.arguments(SponsorChoice(SponsorMode.MARK, listOf("sponsor")), ChapterMode.OFF, "/d")
        assertEquals(listOf("--sponsorblock-mark", "sponsor", "--embed-chapters"), a)
    }

    @Test
    fun keepingChaptersEmbedsThemOnce() {
        assertEquals(listOf("--embed-chapters"), ChapterOptions.arguments(null, ChapterMode.EMBED, "/d"))
        val both = ChapterOptions.arguments(SponsorChoice(SponsorMode.MARK, listOf("sponsor")), ChapterMode.EMBED, "/d")
        assertEquals(1, both.count { it == "--embed-chapters" })
    }

    @Test
    fun splittingGivesAFileTemplateForTheChapters() {
        val a = ChapterOptions.arguments(null, ChapterMode.SPLIT, "/data/dl/1")
        assertEquals(listOf("--split-chapters", "-o", ChapterOptions.chapterTemplate("/data/dl/1")), a)
        // Con la carpeta: si no, yt-dlp los deja en su carpeta de trabajo.
        assertTrue(a.last().startsWith("chapter:/data/dl/1/"))
        assertTrue(a.none { it == "--embed-chapters" })
    }

    @Test
    fun nothingRequestedMeansNoArguments() {
        assertTrue(ChapterOptions.arguments(null, ChapterMode.OFF, "/d").isEmpty())
        assertTrue(ChapterOptions.arguments(SponsorChoice(SponsorMode.REMOVE, emptyList()), ChapterMode.OFF, "/d").isEmpty())
    }

    @Test
    fun chapterFilesAreToldApartFromTheWholeVideo() {
        assertTrue(ChapterOptions.isChapterFile("My video - 03 The middle part.mp4"))
        assertTrue(ChapterOptions.isChapterFile("My video - 12 Outro.m4a"))
        assertFalse(ChapterOptions.isChapterFile("My video.mp4"))
        assertFalse(ChapterOptions.isChapterFile("Artist - Title.m4a"))
    }

    @Test
    fun chapterModesRoundTripAndUnknownMeansOff() {
        ChapterMode.entries.forEach { assertEquals(it, ChapterMode.fromId(it.id)) }
        assertEquals(ChapterMode.OFF, ChapterMode.fromId(null))
        assertEquals(ChapterMode.OFF, ChapterMode.fromId("what"))
    }
}
