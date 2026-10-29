package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlideshowTest {
    @Test
    fun secondsPerImageSplitsTheWholeSong() {
        assertEquals(10.0 / 2, Slideshow.secondsPerImage(10_000, 2), 0.001)
        assertEquals(3.0, Slideshow.secondsPerImage(10_000, 10), 0.001)
        // Una sola foto dura lo que la canción, no un máximo fijo.
        assertEquals(28.0, Slideshow.secondsPerImage(28_000, 1), 0.001)
        assertEquals(30.0, Slideshow.secondsPerImage(60_000, 2), 0.001)
        // Pero nunca más de 90 segundos en total.
        assertEquals(90.0, Slideshow.secondsPerImage(300_000, 1), 0.001)
        assertEquals(45.0, Slideshow.secondsPerImage(300_000, 2), 0.001)
        assertEquals(4.0, Slideshow.secondsPerImage(0, 3), 0.001)
        assertEquals(4.0, Slideshow.secondsPerImage(30_000, 0), 0.001)
        assertEquals(30_000L, Slideshow.totalMs(5, 6.0))
    }

    @Test
    fun theUserCanLimitHowLongTheSongPlays() {
        // Canción de 88,9 s con una foto: entera, o cortada a lo que se pida.
        assertEquals(88.9, Slideshow.secondsPerImage(88_900, 1, 0), 0.001)
        assertEquals(45.0, Slideshow.secondsPerImage(88_900, 1, 45), 0.001)
        assertEquals(15.0, Slideshow.secondsPerImage(88_900, 1, 15), 0.001)
        // Pedir más de lo que dura no la alarga.
        assertEquals(10.0, Slideshow.secondsPerImage(10_000, 1, 45), 0.001)
        // Con muchas fotos manda el mínimo de 3 s por foto.
        assertEquals(3.0, Slideshow.secondsPerImage(88_900, 10, 15), 0.001)
    }

    @Test
    fun aCutSongFadesOutAndAShortOneDoesNot() {
        assertTrue(Slideshow.cutsSong(88_900, 45_000))
        assertTrue(!Slideshow.cutsSong(88_900, 88_900))
        assertTrue(!Slideshow.cutsSong(2_000, 6_000))
        val cut = Slideshow.command(listOf("a.jpg"), "s.m4a", "o.mp4", 640, 800, 45.0, fadeOut = true)
        assertEquals("afade=t=out:st=43.000:d=2", cut[cut.indexOf("-af") + 1])
        assertTrue("-af" !in Slideshow.command(listOf("a.jpg"), "s.m4a", "o.mp4", 640, 800, 45.0))
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
        // La canción se repite si las fotos duran más que ella.
        assertEquals("-stream_loop", c[c.lastIndexOf("-i") - 2])
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
