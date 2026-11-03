package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryJsonTest {

    private fun item(id: String, state: State, loginHost: String? = null) = DownloadItem(
        id = id,
        url = "https://example.com/$id",
        title = "Title $id",
        thumbnail = null,
        quality = Quality(Kind.AUDIO, maxHeight = 720, audioFormat = "mp3"),
        state = state,
        message = if (state == State.FAILED) "Boom" else null,
        savedUris = if (state == State.DONE) listOf("content://media/1", "content://media/2") else emptyList(),
        mime = if (state == State.DONE) "audio/mpeg" else null,
        loginHost = loginHost,
    )

    @Test
    fun photoVideoFlagSurvivesAndOldHistoryReadsAsFalse() {
        val withMusic = item("m", State.DONE).copy(photoVideo = true)
        val back = HistoryJson.decode(HistoryJson.encode(listOf(withMusic, item("n", State.DONE))))
        assertTrue(back[0].photoVideo)
        assertTrue(!back[1].photoVideo)
        val old = """[{"id":"x","url":"https://example.com/a","title":"T","kind":"PHOTO","height":0,"audioFmt":"m4a",""" +
            """"audioBr":0,"state":"DONE","savedUris":["content://media/1"]}]"""
        assertTrue(!HistoryJson.decode(old)[0].photoVideo)
    }

    @Test
    fun groupFieldsSurviveAndOldHistoryHasNoGroup() {
        val grouped = item("g1", State.DONE).copy(groupId = "G", groupIndex = 7, groupTitle = "My list", wifiOnly = true)
        val back = HistoryJson.decode(HistoryJson.encode(listOf(grouped, item("n", State.DONE))))
        assertEquals("G", back[0].groupId)
        assertEquals(7, back[0].groupIndex)
        assertEquals("My list", back[0].groupTitle)
        assertTrue(back[0].wifiOnly)
        assertNull(back[1].groupId)
        assertEquals(0, back[1].groupIndex)
        assertTrue(!back[1].wifiOnly)
    }

    @Test
    fun subtitleChoiceSurvivesAndOldHistoryHasNone() {
        val withSubs = item("s", State.DONE).copy(subs = SubtitleChoice(listOf("en", "es"), SubtitleMode.SEPARATE, true))
        val back = HistoryJson.decode(HistoryJson.encode(listOf(withSubs, item("n", State.DONE))))
        assertEquals(SubtitleChoice(listOf("en", "es"), SubtitleMode.SEPARATE, true), back[0].subs)
        assertNull(back[1].subs)
    }

    @Test
    fun trimSurvivesAndOldHistoryHasNone() {
        val trimmed = item("t", State.DONE).copy(trim = TrimRange(30, 40))
        val back = HistoryJson.decode(HistoryJson.encode(listOf(trimmed, item("n", State.DONE))))
        assertEquals(TrimRange(30, 40), back[0].trim)
        assertNull(back[1].trim)
    }

    @Test
    fun aLongPlaylistIsNotCutAt200() {
        val many = (1..1000).map { item("p$it", State.DONE).copy(groupId = "G", groupIndex = it) }
        assertEquals(1000, HistoryJson.decode(HistoryJson.encode(many)).size)
    }

    @Test
    fun historyFromTheClipExperimentStillLoads() {
        // La 0.3.3 y la 0.3.4 guardaban estas dos claves; ya no se usan, pero no deben romper nada.
        val json = """[{"id":"x","url":"https://example.com/a","title":"T","kind":"PHOTO","height":0,"audioFmt":"m4a",""" +
            """"audioBr":0,"state":"DONE","savedUris":["content://media/1"],"photoVideo":true,"clipStart":30000,"clipLength":88000}]"""
        val item = HistoryJson.decode(json).single()
        assertTrue(item.photoVideo)
        assertEquals("x", item.id)
    }

    @Test
    fun conversionsAreRecognizedByTheirUrl() {
        assertTrue(item("a", State.DONE).copy(url = CONVERT_URL_PREFIX + "content://x").isConversion)
        assertTrue(!item("a", State.DONE).isConversion)
    }

    @Test
    fun roundTripKeepsEverything() {
        val list = listOf(
            item("a", State.DONE),
            item("b", State.FAILED, loginHost = "example.com"),
            item("c", State.CANCELED),
        )
        val back = HistoryJson.decode(HistoryJson.encode(list))
        assertEquals(3, back.size)
        assertEquals("a", back[0].id)
        assertEquals(Quality(Kind.AUDIO, 720, "mp3"), back[0].quality)
        assertEquals(100f, back[0].progress)
        assertEquals(listOf("content://media/1", "content://media/2"), back[0].savedUris)
        assertEquals("audio/mpeg", back[0].mime)
        assertEquals("example.com", back[1].loginHost)
        assertEquals("Boom", back[1].message)
        assertNull(back[0].thumbnail)
        assertEquals(State.CANCELED, back[2].state)
    }

    @Test
    fun audioBitrateRoundTrips() {
        val i = item("m", State.DONE).copy(quality = Quality(Kind.AUDIO, audioFormat = "mp3", audioBitrate = 128))
        assertEquals(128, HistoryJson.decode(HistoryJson.encode(listOf(i))).single().quality.audioBitrate)
    }

    @Test
    fun readsEntriesSavedByVersion024() {
        val old = """[{"id":"x","url":"https://example.com/a","title":"T","kind":"VIDEO","height":0,"audioFmt":"m4a",""" +
            """"state":"DONE","savedUri":"content://media/9","mime":"video/mp4"}]"""
        val back = HistoryJson.decode(old).single()
        assertEquals(listOf("content://media/9"), back.savedUris)
        assertEquals("content://media/9", back.savedUri)
        assertEquals(0, back.quality.audioBitrate)
    }

    @Test
    fun activeItemsLoadAsInterrupted() {
        val back = HistoryJson.decode(HistoryJson.encode(listOf(item("x", State.DOWNLOADING))))
        assertEquals(State.FAILED, back.single().state)
        assertEquals("Interrupted", back.single().message)
        assertTrue(!back.single().isActive)
    }

    @Test
    fun listIsCapped() {
        val many = (1..HistoryJson.MAX_ITEMS + 50).map { item("i$it", State.DONE) }
        assertEquals(HistoryJson.MAX_ITEMS, HistoryJson.decode(HistoryJson.encode(many)).size)
    }

    @Test
    fun garbageGivesEmptyList() {
        assertTrue(HistoryJson.decode("not json").isEmpty())
        assertTrue(HistoryJson.decode("").isEmpty())
    }

    @Test
    fun badEntryIsSkipped() {
        val good = HistoryJson.encode(listOf(item("ok", State.DONE)))
        val mixed = good.dropLast(1) + ",{\"id\":\"bad\"}]"
        assertEquals(listOf("ok"), HistoryJson.decode(mixed).map { it.id })
    }
}
