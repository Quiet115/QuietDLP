package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    private fun item(id: String, title: String, url: String, kind: Kind = Kind.VIDEO) = DownloadItem(
        id = id, url = url, title = title, quality = Quality(kind), state = State.DONE,
    )

    private val items = listOf(
        item("1", "Me at the zoo", "https://www.youtube.com/watch?v=aaa"),
        item("2", "Lofi beats to study", "https://soundcloud.com/x/lofi", Kind.AUDIO),
        item("3", "Video by someone", "https://www.instagram.com/reel/abc/"),
    )

    @Test
    fun emptyQueryKeepsEverything() {
        assertEquals(items, HistoryFilter.filter(items, ""))
        assertEquals(items, HistoryFilter.filter(items, "   "))
    }

    @Test
    fun matchesTitleCaseInsensitive() {
        assertEquals(listOf("1"), HistoryFilter.filter(items, "ZOO").map { it.id })
    }

    @Test
    fun matchesSiteAndType() {
        assertEquals(listOf("2"), HistoryFilter.filter(items, "soundcloud").map { it.id })
        assertEquals(listOf("2"), HistoryFilter.filter(items, "audio").map { it.id })
        assertEquals(listOf("3"), HistoryFilter.filter(items, "instagram").map { it.id })
    }

    @Test
    fun allWordsMustMatch() {
        assertEquals(listOf("2"), HistoryFilter.filter(items, "lofi study").map { it.id })
        assertEquals(emptyList<String>(), HistoryFilter.filter(items, "lofi zoo").map { it.id })
    }
}
