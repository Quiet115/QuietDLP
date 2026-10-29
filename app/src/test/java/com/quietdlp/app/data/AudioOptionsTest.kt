package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioOptionsTest {
    private val ytDlpFormats = setOf("best", "aac", "alac", "flac", "m4a", "mp3", "opus", "vorbis", "wav")
    private val lossless = setOf("best", "flac", "alac", "wav")

    @Test
    fun betterQualityComesFirst() {
        val tiers = AudioOptions.all.map { it.tier.ordinal }
        assertEquals(tiers.sorted(), tiers)
        assertEquals(AudioTier.ORIGINAL, AudioOptions.all.first().tier)
        assertEquals(AudioTier.SMALL, AudioOptions.all.last().tier)
    }

    @Test
    fun withinAFormatHigherBitrateComesFirst() {
        AudioOptions.all.groupBy { it.format }.values.forEach { same ->
            val rates = same.map { it.bitrate }
            assertEquals(rates.sortedDescending(), rates)
        }
    }

    @Test
    fun everyOptionIsValidAndUnique() {
        assertEquals(AudioOptions.all.size, AudioOptions.all.map { it.format to it.bitrate }.toSet().size)
        assertEquals(AudioOptions.all.size, AudioOptions.all.map { it.label }.toSet().size)
        AudioOptions.all.forEach {
            assertTrue("${it.label} format", it.format in ytDlpFormats)
            assertTrue("${it.label} text", it.label.isNotBlank() && it.hint.isNotBlank())
            if (it.format in lossless) assertEquals("${it.label} has no bitrate", 0, it.bitrate)
        }
        AudioOptions.all.filter { it.tier == AudioTier.LOSSLESS }.forEach { assertTrue(it.format in lossless) }
    }

    @Test
    fun groupsFollowTheTierOrderAndHaveNoGaps() {
        val groups = AudioOptions.byTier()
        assertEquals(AudioTier.entries, groups.map { it.first })
        assertEquals(AudioOptions.all, groups.flatMap { it.second })
    }

    @Test
    fun savedChoicesMapToAnOption() {
        assertEquals(AudioOptions.default, AudioOptions.find("m4a", 0))
        assertEquals("MP3 128k", AudioOptions.find("mp3", 128).label)
        // "mp3" sin bitrate, como lo guardaban versiones anteriores: pasa a la mejor MP3.
        assertEquals("MP3 320k", AudioOptions.find("mp3", 0).label)
        assertEquals(AudioOptions.default, AudioOptions.find("nope", 5))
        assertEquals(AudioOptions.default, AudioOptions.find(null, 0))
    }

    @Test
    fun builtInModesStillExist() {
        DownloadMode.entries.mapNotNull { it.quality }.filter { it.kind == Kind.AUDIO }.forEach {
            val o = AudioOptions.find(it.audioFormat, it.audioBitrate)
            assertEquals(it.audioFormat, o.format)
            assertEquals(it.audioBitrate, o.bitrate)
        }
    }

    @Test
    fun ytDlpOptionsKeepTheSourceWhenPossible() {
        val m4a = AudioOptions.ytDlpOptions("m4a", 0).toMap()
        assertEquals("ba[ext=m4a]/ba/b", m4a["-f"])
        assertEquals("0", m4a["--audio-quality"])
        val mp3 = AudioOptions.ytDlpOptions("mp3", 192).toMap()
        assertEquals("ba/b", mp3["-f"])
        assertEquals("mp3", mp3["--audio-format"])
        assertEquals("192K", mp3["--audio-quality"])
        val original = AudioOptions.ytDlpOptions("best", 0).toMap()
        assertEquals("best", original["--audio-format"])
        assertTrue(original.containsKey("-x"))
    }

    @Test
    fun qualityLabelsReadWell() {
        assertEquals("Audio (original)", Quality(Kind.AUDIO, audioFormat = "best").label())
        assertEquals("Audio FLAC", Quality(Kind.AUDIO, audioFormat = "flac").label())
        assertEquals("Audio OPUS 192k", Quality(Kind.AUDIO, audioFormat = "opus", audioBitrate = 192).label())
    }
}
