package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityLevelTest {
    @Test
    fun everyLevelMapsBackToItselfForVideoAndAudio() {
        QualityLevel.entries.forEach {
            assertEquals(it, QualityLevel.fromQuality(Quality(Kind.VIDEO, it.videoHeight)))
            assertEquals(it, QualityLevel.fromQuality(Quality(Kind.AUDIO, 0, it.audioFormat, it.audioBitrate)))
        }
    }

    @Test
    fun videoLevelsGoFromTheBestToTheSmallest() {
        // 0 = sin tope (la mejor que haya); después 720p y 480p.
        assertEquals(listOf(0, 720, 480), QualityLevel.entries.map { it.videoHeight })
        assertEquals(listOf("High", "Medium", "Low"), QualityLevel.entries.map { it.label })
    }

    @Test
    fun audioLevelsUseTheMostCompatibleFormatsAndExistInTheList() {
        assertTrue(QualityLevel.entries.all { it.audioFormat in setOf("m4a", "mp3") })
        QualityLevel.entries.forEach {
            val o = AudioOptions.find(it.audioFormat, it.audioBitrate)
            assertEquals(it.audioFormat, o.format)
            assertEquals(it.audioBitrate, o.bitrate)
        }
        // Mejor calidad primero: alta no pierde frente a media ni baja.
        val tiers = QualityLevel.entries.map { AudioOptions.find(it.audioFormat, it.audioBitrate).tier.ordinal }
        assertEquals(tiers.sorted(), tiers)
    }

    @Test
    fun detailedChoicesLandOnTheClosestLevel() {
        assertEquals(QualityLevel.HIGH, QualityLevel.fromQuality(Quality(Kind.VIDEO, 1080)))
        assertEquals(QualityLevel.HIGH, QualityLevel.fromQuality(Quality(Kind.VIDEO, 0)))
        assertEquals(QualityLevel.MEDIUM, QualityLevel.fromQuality(Quality(Kind.VIDEO, 720)))
        assertEquals(QualityLevel.MEDIUM, QualityLevel.fromQuality(Quality(Kind.VIDEO, 576)))
        assertEquals(QualityLevel.LOW, QualityLevel.fromQuality(Quality(Kind.VIDEO, 480)))
        assertEquals(QualityLevel.LOW, QualityLevel.fromQuality(Quality(Kind.VIDEO, 360)))
        assertEquals(QualityLevel.HIGH, QualityLevel.fromQuality(Quality(Kind.AUDIO, audioFormat = "flac")))
        assertEquals(QualityLevel.HIGH, QualityLevel.fromQuality(Quality(Kind.AUDIO, audioFormat = "mp3", audioBitrate = 320)))
        assertEquals(QualityLevel.MEDIUM, QualityLevel.fromQuality(Quality(Kind.AUDIO, audioFormat = "opus", audioBitrate = 128)))
        assertEquals(QualityLevel.LOW, QualityLevel.fromQuality(Quality(Kind.AUDIO, audioFormat = "opus", audioBitrate = 64)))
    }
}
