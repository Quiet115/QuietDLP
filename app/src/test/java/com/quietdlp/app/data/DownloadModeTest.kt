package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadModeTest {

    @Test
    fun freshPhoneGetsBestVideo() {
        // Valores por defecto de los ajustes antiguos: descarga directa, vídeo, sin tope.
        assertEquals(DownloadMode.BEST_VIDEO, DownloadMode.fromLegacy(true, Kind.VIDEO, 0, "m4a"))
    }

    @Test
    fun directOffMeansAsk() {
        assertEquals(DownloadMode.ASK, DownloadMode.fromLegacy(false, Kind.VIDEO, 0, "m4a"))
        assertNull(DownloadMode.ASK.quality)
    }

    @Test
    fun legacyHeightsAndAudioMapToClosestMode() {
        assertEquals(DownloadMode.VIDEO_1080, DownloadMode.fromLegacy(true, Kind.VIDEO, 1080, "m4a"))
        assertEquals(DownloadMode.VIDEO_720, DownloadMode.fromLegacy(true, Kind.VIDEO, 720, "m4a"))
        assertEquals(DownloadMode.VIDEO_720, DownloadMode.fromLegacy(true, Kind.VIDEO, 480, "m4a"))
        assertEquals(DownloadMode.MUSIC_BEST, DownloadMode.fromLegacy(true, Kind.AUDIO, 0, "m4a"))
        assertEquals(DownloadMode.MUSIC_MP3, DownloadMode.fromLegacy(true, Kind.AUDIO, 0, "mp3"))
    }

    @Test
    fun keysAreUniqueAndRoundTrip() {
        assertEquals(DownloadMode.entries.size, DownloadMode.entries.map { it.key }.toSet().size)
        DownloadMode.entries.forEach { assertEquals(it, DownloadMode.fromKey(it.key)) }
        assertNull(DownloadMode.fromKey("nope"))
        assertNull(DownloadMode.fromKey(null))
    }

    @Test
    fun everyAudioOptionHasExactlyOneModeInTheSameOrder() {
        val fromModes = DownloadMode.entries.mapNotNull { it.audio }
        assertEquals(AudioOptions.all, fromModes)
        DownloadMode.entries.filter { it.audio != null }.forEach {
            assertEquals(it.audio!!.format, it.quality!!.audioFormat)
            assertEquals(it.audio!!.bitrate, it.quality!!.audioBitrate)
        }
    }

    @Test
    fun oldSavedKeysStillWork() {
        assertEquals("m4a", DownloadMode.fromKey("music_best")!!.quality!!.audioFormat)
        assertEquals(128, DownloadMode.fromKey("music_mp3")!!.quality!!.audioBitrate)
        assertNull(DownloadMode.BEST_VIDEO.audio)
        assertNull(DownloadMode.ASK.audio)
    }

    @Test
    fun qualitiesAreWhatTheLabelsSay() {
        assertEquals(Quality(Kind.VIDEO, 0), DownloadMode.BEST_VIDEO.quality)
        assertEquals(1080, DownloadMode.VIDEO_1080.quality!!.maxHeight)
        assertEquals("m4a", DownloadMode.MUSIC_BEST.quality!!.audioFormat)
        assertEquals(128, DownloadMode.MUSIC_MP3.quality!!.audioBitrate)
        assertEquals("Audio MP3 128k", DownloadMode.MUSIC_MP3.quality!!.label())
        assertEquals("Audio M4A", DownloadMode.MUSIC_BEST.quality!!.label())
    }
}
