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
    fun songLengthChoicesStartWithAsPostedThenTheWholeSong() {
        val seconds = Slideshow.SONG_LENGTHS.map { it.first }
        assertEquals(Slideshow.AS_POSTED, seconds[0])
        assertEquals(0, seconds[1])
        assertEquals(seconds.size, seconds.toSet().size)
        // Después, de más larga a más corta.
        assertEquals(seconds.drop(2).sortedDescending(), seconds.drop(2))
        assertTrue(Slideshow.SONG_LENGTHS.all { it.second.isNotBlank() })
    }

    @Test
    fun asPostedUsesTheAuthorsClipWhenKnown() {
        // Archivo de 88,9 s; el autor eligió 45 s desde el segundo 30.
        val p = Slideshow.audioPlan(88_900, Slideshow.AS_POSTED, 30_000, 45_000)
        assertEquals(30_000L, p.startMs)
        assertEquals(58_900L, p.availableMs)
        assertEquals(45, p.maxSeconds)
        // El trozo no cabe entero: se recorta a lo que queda del archivo.
        val tail = Slideshow.audioPlan(88_900, Slideshow.AS_POSTED, 80_000, 45_000)
        assertEquals(80_000L, tail.startMs)
        assertEquals(9, tail.maxSeconds)
        // Un inicio más allá del final no rompe nada.
        assertEquals(87_900L, Slideshow.audioPlan(88_900, Slideshow.AS_POSTED, 500_000, 45_000).startMs)
    }

    @Test
    fun withoutAClipOrWithAnotherChoiceTheSongStartsAtTheBeginning() {
        val none = Slideshow.audioPlan(88_900, Slideshow.AS_POSTED, 0, 0)
        assertEquals(0L, none.startMs)
        assertEquals(0, none.maxSeconds)
        assertEquals(88_900L, none.availableMs)
        val whole = Slideshow.audioPlan(88_900, 0, 30_000, 45_000)
        assertEquals(0L, whole.startMs)
        assertEquals(0, whole.maxSeconds)
        val cut = Slideshow.audioPlan(88_900, 30, 30_000, 45_000)
        assertEquals(0L, cut.startMs)
        assertEquals(30, cut.maxSeconds)
    }

    @Test
    fun theCommandSeeksIntoTheSongOnlyWhenAsked() {
        val c = Slideshow.command(listOf("a.jpg"), "s.m4a", "o.mp4", 640, 800, 45.0, audioStartSec = 30.0)
        assertEquals("30.000", c[c.indexOf("-ss") + 1])
        assertEquals("-ss", c[c.lastIndexOf("-i") - 2])
        assertTrue("-ss" !in Slideshow.command(listOf("a.jpg"), "s.m4a", "o.mp4", 640, 800, 45.0))
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
