package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaProbeTest {

    private val json = """{"title":"My video","fulltitle":"My video (full)","thumbnail":"https://x/t.jpg","uploader":"Me","duration":123.4,
        "formats":[
          {"vcodec":"none","acodec":"mp4a","height":null,"width":null},
          {"vcodec":"avc1","width":1920,"height":1080},
          {"vcodec":"avc1","width":1080,"height":1920},
          {"vcodec":"vp9","width":1280,"height":720},
          {"vcodec":"vp9","width":160,"height":90},
          {"vcodec":"avc1","height":480}
        ],
        "subtitles":{"en":[{"ext":"vtt","name":"English"}]},
        "automatic_captions":{"en-orig":[{"ext":"vtt"}]}}"""

    @Test
    fun readsTheBasicsAndTheQualities() {
        val p = MediaProbe.parse(json)!!
        assertEquals("My video", p.title)
        assertEquals("https://x/t.jpg", p.thumbnail)
        assertEquals("Me", p.uploader)
        assertEquals(123, p.durationSec)
        // 1080 (los dos, horizontal y vertical, son 1080p), 720 y 480; el de 90p queda fuera por pequeño.
        assertEquals(listOf(1080, 720, 480), p.heights)
    }

    @Test
    fun readsTheSubtitleTracks() {
        val p = MediaProbe.parse(json)!!
        assertEquals(listOf("en", "en-orig"), p.subtitles.map { it.code })
    }

    @Test
    fun aMissingOrNullFieldIsNullNotTheWordNull() {
        val p = MediaProbe.parse("""{"title":null,"fulltitle":"Full","thumbnail":null,"duration":null}""")!!
        assertEquals("Full", p.title)
        assertNull(p.thumbnail)
        assertNull(p.uploader)
        assertEquals(0, p.durationSec)
        assertTrue(p.heights.isEmpty())
        assertTrue(p.subtitles.isEmpty())
    }

    @Test
    fun onlyAudioMeansNoQualities() {
        val p = MediaProbe.parse("""{"title":"Song","formats":[{"vcodec":"none","acodec":"opus"}]}""")!!
        assertTrue(p.heights.isEmpty())
    }

    @Test
    fun takesTheFirstJsonLineAndIgnoresNoise() {
        val out = "WARNING: something\n{\"title\":\"First\"}\n{\"title\":\"Second\"}\n"
        assertEquals("First", MediaProbe.parse(out)!!.title)
    }

    @Test
    fun garbageGivesNull() {
        assertNull(MediaProbe.parse(""))
        assertNull(MediaProbe.parse("not json"))
        assertNull(MediaProbe.parse("{broken"))
    }
}
