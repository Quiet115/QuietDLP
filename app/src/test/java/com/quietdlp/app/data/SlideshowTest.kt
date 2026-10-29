package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowTest {
    @Test
    fun secondsPerImageSplitsTheSongButStaysReasonable() {
        assertEquals(10.0 / 2, Slideshow.secondsPerImage(10_000, 2), 0.001)
        assertEquals(3.0, Slideshow.secondsPerImage(10_000, 10), 0.001)
        assertEquals(8.0, Slideshow.secondsPerImage(60_000, 2), 0.001)
        assertEquals(4.0, Slideshow.secondsPerImage(0, 3), 0.001)
        assertEquals(4.0, Slideshow.secondsPerImage(30_000, 0), 0.001)
        assertEquals(30_000L, Slideshow.totalMs(5, 6.0))
    }

    @Test
    fun canvasIsEvenAndNeverUpscales() {
        assertEquals(1080 to 1350, Slideshow.canvas(1080, 1350))
        assertEquals(1440 to 1080, Slideshow.canvas(4000, 3000))
        assertEquals(1080 to 1920, Slideshow.canvas(1080, 1920))
        assertEquals(320 to 240, Slideshow.canvas(321, 241))
        assertEquals(1080 to 1080, Slideshow.canvas(0, 0))
        // Una foto muy alargada se limita por el lado largo.
        val (w, h) = Slideshow.canvas(1000, 6000)
        assertTrue(h <= 1920 && w % 2 == 0 && h % 2 == 0)
    }

    @Test
    fun commandHasOneInputPerImagePlusTheAudio() {
        val c = Slideshow.command(listOf("a.jpg", "b.png", "c.webp"), "song.m4a", "out.mp4", 1080, 1350, 4.5)
        assertEquals(3, c.count { it == "-loop" })
        assertEquals(4, c.count { it == "-i" })
        assertEquals("song.m4a", c[c.lastIndexOf("-i") + 1])
        assertEquals("3:a:0", c[c.indexOf("[v]") + 2])
        assertTrue("concat=n=3" in c[c.indexOf("-filter_complex") + 1])
        assertTrue("4.500" in c)
        assertEquals("out.mp4", c.last())
        assertTrue("-shortest" in c && "-progress" in c)
    }

    @Test
    fun namesDropTheMoreSuffix() {
        assertEquals("Drawing (video).mp4", Slideshow.outputName("Drawing (+4 more)"))
        assertEquals("Drawing (video).mp4", Slideshow.outputName("Drawing"))
        assertEquals("a b (video).mp4", Slideshow.outputName("a/b"))
    }
}
