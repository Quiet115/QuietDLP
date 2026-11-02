package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistProbeTest {
    private val source = "https://www.youtube.com/playlist?list=PL1"

    @Test
    fun readsAPlaylist() {
        val json = """
            {"_type":"playlist","title":"Road trip","entries":[
              {"url":"https://www.youtube.com/watch?v=a","title":"One","thumbnails":[{"url":"https://i/1s.jpg"},{"url":"https://i/1.jpg"}]},
              {"url":"https://www.youtube.com/watch?v=b","title":"Two","thumbnail":"https://i/2.jpg"},
              {"url":"https://www.youtube.com/watch?v=c","title":null}
            ]}
        """.trimIndent()
        val p = PlaylistProbe.parse(json, source)!!
        assertEquals("Road trip", p.title)
        assertEquals(3, p.entries.size)
        assertEquals("One", p.entries[0].title)
        assertEquals("https://i/1.jpg", p.entries[0].thumbnail)
        assertEquals("https://i/2.jpg", p.entries[1].thumbnail)
        // Sin título se usa el enlace; el orden es el de la lista.
        assertEquals("https://www.youtube.com/watch?v=c", p.entries[2].title)
    }

    @Test
    fun aSingleVideoIsNotAPlaylist() {
        assertNull(PlaylistProbe.parse("""{"id":"a","title":"Just one","formats":[]}""", "https://youtu.be/a"))
    }

    @Test
    fun aCarouselOfTheSamePostIsNotAPlaylist() {
        val json = """
            {"_type":"playlist","title":"Post","entries":[
              {"url":"https://www.instagram.com/p/XYZ/?img_index=1","title":"a"},
              {"url":"https://www.instagram.com/p/XYZ/?img_index=2","title":"b"}
            ]}
        """.trimIndent()
        assertNull(PlaylistProbe.parse(json, "https://www.instagram.com/p/XYZ/"))
    }

    @Test
    fun aListNeedsAtLeastTwoDifferentFiles() {
        val one = """{"_type":"playlist","entries":[{"url":"https://x.com/v/1"},{"url":"https://x.com/v/1"}]}"""
        assertNull(PlaylistProbe.parse(one, "https://x.com/list"))
        val two = """{"_type":"playlist","entries":[{"url":"https://x.com/v/1"},{"url":"https://x.com/v/2"}]}"""
        assertNotNull(PlaylistProbe.parse(two, "https://x.com/list"))
        assertEquals("Playlist", PlaylistProbe.parse(two, "https://x.com/list")!!.title)
    }

    @Test
    fun entriesWithoutALinkAreSkippedAndGarbageGivesNull() {
        val json = """{"_type":"playlist","entries":[{"id":"a"},{"url":"https://x.com/v/1"},{"url":"https://x.com/v/2"}]}"""
        assertEquals(2, PlaylistProbe.parse(json, "https://x.com/list")!!.entries.size)
        assertNull(PlaylistProbe.parse("not json", source))
        assertNull(PlaylistProbe.parse("", source))
    }

    @Test
    fun ignoresWarningLinesBeforeTheJson() {
        val json = "WARNING: something\n" + """{"_type":"playlist","entries":[{"url":"https://x.com/v/1"},{"url":"https://x.com/v/2"}]}"""
        assertNotNull(PlaylistProbe.parse(json, "https://x.com/list"))
    }
}
