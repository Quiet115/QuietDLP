package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowTest {

    @Test
    fun decimalsAreAllowedBelowOneDayAndWholeDaysAbove() {
        assertEquals(0.25, FollowInterval.parse("0.25")!!, 0.0)
        assertEquals(0.5, FollowInterval.parse("0,5")!!, 0.0)
        assertEquals(1.0, FollowInterval.parse("1")!!, 0.0)
        assertEquals(7.0, FollowInterval.parse("7")!!, 0.0)
        assertEquals(3.0, FollowInterval.parse("2.6")!!, 0.0)
        assertEquals(90.0, FollowInterval.parse("90")!!, 0.0)
    }

    @Test
    fun theIntervalIsNeverShorterThanAndroidAllowsAndBadTextIsRejected() {
        assertEquals(FollowInterval.MIN_DAYS, FollowInterval.parse("0")!!, 0.0)
        assertEquals(FollowInterval.MIN_DAYS, FollowInterval.parse("-3")!!, 0.0)
        assertNull(FollowInterval.parse(""))
        assertNull(FollowInterval.parse("abc"))
        assertNull(FollowInterval.parse("NaN"))
        assertTrue(FollowInterval.toMs(FollowInterval.MIN_DAYS) >= 15 * 60 * 1000L)
    }

    @Test
    fun labelsAreShortAndPlain() {
        assertEquals("1 day", FollowInterval.label(1.0))
        assertEquals("3 days", FollowInterval.label(3.0))
        assertEquals("6 h", FollowInterval.label(0.25))
        assertEquals("12 h", FollowInterval.label(0.5))
        assertEquals("29 min", FollowInterval.label(0.02))
        assertEquals("1", FollowInterval.text(1.0))
        assertEquals("0.25", FollowInterval.text(0.25))
        assertEquals("0.5", FollowInterval.text(0.5))
    }

    @Test
    fun aPageIsDueWhenItsTimeHasPassed() {
        val page = FollowedPage("a", "https://x.test/c", "C", days = 1.0, lastCheck = 1_000_000L)
        assertFalse(FollowInterval.isDue(page, 1_000_000L + 23 * 3_600_000L))
        assertTrue(FollowInterval.isDue(page, 1_000_000L + 24 * 3_600_000L))
        assertTrue(FollowInterval.isDue(page.copy(lastCheck = 0), 90_000_000L))
    }

    @Test
    fun theJobWakesAtTheShortestIntervalButNeverMoreThanADay() {
        val day = 24 * 3_600_000L
        assertEquals(day, FollowInterval.jobPeriodMs(emptyList()))
        val pages = listOf(
            FollowedPage("a", "https://x.test/a", "A", days = 7.0),
            FollowedPage("b", "https://x.test/b", "B", days = 0.25),
        )
        assertEquals(6 * 3_600_000L, FollowInterval.jobPeriodMs(pages))
        assertEquals(day, FollowInterval.jobPeriodMs(pages.take(1)))
        assertEquals(1_728_000L, FollowInterval.jobPeriodMs(listOf(FollowedPage("c", "https://x.test/c", "C", days = 0.02))))
    }

    private fun e(n: Int) = PlaylistEntry("https://x.test/v/$n", "Video $n", null)

    @Test
    fun onlyWhatWasNotSeenIsNewAndTheOrderIsKept() {
        val listed = listOf(e(5), e(4), e(3), e(2), e(1))
        val seen = setOf("https://x.test/v/3", "https://x.test/v/2", "https://x.test/v/1")
        assertEquals(listOf(e(5), e(4)), FollowDiff.fresh(listed, seen))
        assertTrue(FollowDiff.fresh(listed, listed.map { it.url }.toSet()).isEmpty())
        assertEquals(listOf(e(1)), FollowDiff.fresh(listOf(e(1), e(1)), emptySet()))
    }

    @Test
    fun followedPagesSurviveBeingSavedAsText() {
        val page = FollowedPage(
            id = "ab12", url = "https://www.youtube.com/@x", title = "X", thumbnail = "https://i.test/t.jpg",
            engine = FollowEngine.GALLERY, quality = Quality(Kind.AUDIO, 0, "mp3", 192), auto = false, days = 0.25,
            lastCheck = 123L, lastError = "boom", counter = 4,
            pending = listOf(PlaylistEntry("https://x.test/v/9", "Nine", "https://i.test/9.jpg")),
        )
        val back = FollowJson.decode(FollowJson.encode(listOf(page, page.copy(id = "cd34", lastError = null, thumbnail = null))))
        assertEquals(page, back[0])
        assertNull(back[1].lastError)
        assertNull(back[1].thumbnail)
        assertEquals("follow-ab12", back[0].groupId)
    }

    @Test
    fun brokenOrOldDataBecomesDefaultsNotACrash() {
        assertTrue(FollowJson.decode("not json").isEmpty())
        val one = FollowJson.decode("""[{"id":"z","url":"https://x.test/c"},{"nonsense":1}]""")
        assertEquals(1, one.size)
        assertEquals(FollowEngine.YTDLP, one[0].engine)
        assertTrue(one[0].auto)
        assertEquals(FollowInterval.DEFAULT_DAYS, one[0].days, 0.0)
        assertEquals("https://x.test/c", one[0].title)
    }

    @Test
    fun aChannelWithOneVideoCanBeFollowedEvenThoughItIsNotAPlaylistToPickFrom() {
        val json = """{"_type":"playlist","title":"My channel","entries":[{"url":"https://www.youtube.com/watch?v=abc","title":"Only one"}]}"""
        assertNull(PlaylistProbe.parse(json, "https://www.youtube.com/@my/videos"))
        val info = PlaylistProbe.parseForFollow(json)!!
        assertEquals("My channel", info.title)
        assertEquals(1, info.entries.size)
        assertNull(PlaylistProbe.parseForFollow("""{"_type":"video","title":"x"}"""))
        assertNull(PlaylistProbe.parseForFollow("junk"))
    }

    @Test
    fun theFollowOriginAndNewMarkSurviveInTheHistory() {
        val item = DownloadItem(id = "1", url = "https://x.test/v/1", title = "T", quality = Quality(Kind.VIDEO), followId = "ab12", fresh = true)
        val back = HistoryJson.decode(HistoryJson.encode(listOf(item.copy(state = State.DONE)))).single()
        assertEquals("ab12", back.followId)
        assertTrue(back.fresh)
        val plain = HistoryJson.decode(HistoryJson.encode(listOf(item.copy(state = State.DONE, followId = null, fresh = false)))).single()
        assertNull(plain.followId)
        assertFalse(plain.fresh)
    }

    @Test
    fun aYoutubeChannelWithoutTabFollowsTheVideosTab() {
        assertEquals("https://www.youtube.com/@AlJazeeraEnglish/videos", FollowUrl.normalize("https://www.youtube.com/@AlJazeeraEnglish"))
        assertEquals("https://www.youtube.com/@x/videos", FollowUrl.normalize("https://m.youtube.com/@x/"))
        assertEquals("https://www.youtube.com/channel/UC123/videos", FollowUrl.normalize("https://youtube.com/channel/UC123"))
        // Ya tienen pestaña o son otra cosa: se dejan igual.
        assertEquals("https://www.youtube.com/@x/shorts", FollowUrl.normalize("https://www.youtube.com/@x/shorts"))
        assertEquals("https://www.youtube.com/playlist?list=PL1", FollowUrl.normalize("https://www.youtube.com/playlist?list=PL1"))
        assertEquals("https://www.youtube.com/watch?v=abc", FollowUrl.normalize("https://www.youtube.com/watch?v=abc"))
        assertEquals("https://vimeo.com/user123", FollowUrl.normalize("https://vimeo.com/user123"))
        assertEquals("https://notyoutube.com/@x", FollowUrl.normalize("https://notyoutube.com/@x"))
    }

    @Test
    fun namesForFollowedPagesAreShort() {
        assertEquals("Al Jazeera English", FollowUrl.cleanTitle("Al Jazeera English - Videos"))
        assertEquals("Videos", FollowUrl.cleanTitle("Videos"))
        assertEquals("My - Videos list", FollowUrl.cleanTitle("My - Videos list"))
        assertEquals(
            "commons.wikimedia.org - Category:Featured pictures on Wikimedia Commons",
            FollowUrl.galleryTitle("https://commons.wikimedia.org/wiki/Category:Featured_pictures_on_Wikimedia_Commons"),
        )
        assertEquals("booru.example - cat ears", FollowUrl.galleryTitle("https://booru.example/index.php?page=post&s=list&tags=cat_ears"))
        assertEquals("tumblr.com - someblog", FollowUrl.galleryTitle("https://www.tumblr.com/someblog"))
        assertEquals("example.com", FollowUrl.galleryTitle("https://example.com/"))
        assertEquals("gallery.example - ai generated", FollowUrl.galleryTitle("https://gallery.example/post/list/ai_generated/1"))
    }

    @Test
    fun followedPagesTravelInTheBackupWithoutWhatIsPending() {
        val page = FollowedPage("ab12", "https://x.test/c", "C", pending = listOf(PlaylistEntry("https://x.test/v/1", "One", null)), counter = 3, lastError = "boom")
        val json = SettingsBackup.encode(emptyMap<String, Any>(), emptyList(), listOf(page))
        val back = SettingsBackup.decodeFollows(json).single()
        assertEquals("https://x.test/c", back.url)
        assertTrue(back.pending.isEmpty())
        assertEquals(0, back.counter)
        assertNull(back.lastError)
        assertTrue(SettingsBackup.decodeFollows(SettingsBackup.encode(emptyMap<String, Any>())).isEmpty())
    }

    @Test
    fun theFollowWifiSettingTravelsInTheBackup() {
        assertEquals('b', SettingsBackup.KEYS["follow_wifi"])
    }
}
