package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongClipTest {
    @Test
    fun readsStartAndLength() {
        val json = """{"num":1,"audio_clip_start_ms":30000,"audio_clip_length_ms":45000,"audio_title":"x"}"""
        assertEquals(SongClip(30_000, 45_000), SongClip.parse(json))
    }

    @Test
    fun acceptsDecimalsAndText() {
        assertEquals(SongClip(1_500, 45_000), SongClip.parse("""{"audio_clip_start_ms":1500.0,"audio_clip_length_ms":"45000"}"""))
    }

    @Test
    fun aMissingStartMeansTheBeginning() {
        assertEquals(SongClip(0, 20_000), SongClip.parse("""{"audio_clip_length_ms":20000}"""))
        assertEquals(SongClip(0, 20_000), SongClip.parse("""{"audio_clip_start_ms":null,"audio_clip_length_ms":20000}"""))
        assertEquals(SongClip(0, 20_000), SongClip.parse("""{"audio_clip_start_ms":-5,"audio_clip_length_ms":20000}"""))
    }

    @Test
    fun noLengthMeansNoClip() {
        assertNull(SongClip.parse("""{"audio_clip_start_ms":30000}"""))
        assertNull(SongClip.parse("""{"audio_clip_start_ms":30000,"audio_clip_length_ms":null}"""))
        assertNull(SongClip.parse("""{"audio_clip_length_ms":0}"""))
        assertNull(SongClip.parse("""{"audio_clip_length_ms":"soon"}"""))
        assertNull(SongClip.parse("not json"))
        assertNull(SongClip.parse("{}"))
    }

    @Test
    fun rawDataIsKeptForTheErrorLog() {
        val json = """{"audio_clip_length_ms":null,"audio_clip_raw":{"should_mute_audio":false,"x":1}}"""
        assertEquals(true, SongClip.rawOf(json)!!.contains("should_mute_audio"))
        assertNull(SongClip.rawOf("""{"audio_clip_raw":null}"""))
        assertNull(SongClip.rawOf("{}"))
        assertEquals(600, SongClip.rawOf("""{"audio_clip_raw":{"k":"${"x".repeat(2000)}"}}""")!!.length)
    }
}
