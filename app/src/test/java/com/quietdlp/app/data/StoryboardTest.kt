package com.quietdlp.app.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryboardTest {

    // Como el de un vídeo de 213 s: 3×3 fotogramas por imagen, 12 imágenes de 17,75 s (un fotograma cada ~1,97 s).
    private val big = Storyboard(
        320, 180, 3, 3,
        (0 until 12).map { StoryboardFragment("https://i.example/M$it.jpg", 17.75) },
    )

    @Test
    fun theIntervalComesFromTheFirstFullImage() {
        assertEquals(17.75 / 9, big.interval, 1e-9)
    }

    @Test
    fun theFirstFrameIsTheStartOfTheVideo() {
        assertEquals(FrameRef(0, 0, 0), big.locate(0.0, 213))
        assertEquals(FrameRef(0, 0, 0), big.locate(1.5, 213))
    }

    @Test
    fun framesFillEachGridLeftToRightThenTopToBottom() {
        // 1,97 s por fotograma: el segundo 2 es el fotograma 1, el 4 el 2, el 6 el 3 (segunda fila).
        assertEquals(FrameRef(0, 1, 0), big.locate(2.0, 213))
        assertEquals(FrameRef(0, 2, 0), big.locate(4.0, 213))
        assertEquals(FrameRef(0, 0, 1), big.locate(6.0, 213))
        assertEquals(FrameRef(0, 2, 2), big.locate(16.0, 213))
    }

    @Test
    fun afterTheLastCellOfAnImageItMovesToTheNextImage() {
        assertEquals(FrameRef(1, 0, 0), big.locate(17.75, 213))
        assertEquals(FrameRef(2, 0, 0), big.locate(35.5, 213))
    }

    @Test
    fun neverPastTheEndOfTheVideoOrBeforeTheStart() {
        val last = big.locate(213.0, 213)!!
        assertEquals(11, last.fragment)
        assertEquals(big.locate(999.0, 213), last)
        assertEquals(FrameRef(0, 0, 0), big.locate(-5.0, 213))
    }

    @Test
    fun emptyCellsAtTheEndOfTheLastImageAreNotUsed() {
        // 40 s de vídeo con 9 fotogramas por imagen y 1,97 s cada uno: caben 21 fotogramas (3 imágenes, la última con 3).
        val sb = Storyboard(160, 90, 3, 3, (0 until 5).map { StoryboardFragment("https://i.example/$it.jpg", 17.75) })
        val last = sb.locate(40.0, 40)!!
        assertEquals(2, last.fragment) // 40 / 1,97 = 20,3 → 21 fotogramas → índice 20 → imagen 2
    }

    @Test
    fun badDataGivesNoFrame() {
        assertNull(big.locate(5.0, 0))
        assertNull(Storyboard(10, 10, 0, 0, emptyList()).locate(1.0, 10))
        assertNull(Storyboard(10, 10, 3, 3, listOf(StoryboardFragment("https://x/a.jpg", 0.0))).locate(1.0, 10))
    }

    private fun format(id: String, w: Int, cols: Int, vararg urls: String, duration: Double = 10.0) =
        """{"format_id":"$id","ext":"mhtml","width":$w,"height":${w * 9 / 16},"columns":$cols,"rows":$cols,
            "fragments":[${urls.joinToString(",") { """{"url":"$it","duration":$duration}""" }}]}"""

    @Test
    fun parsePicksTheBiggestStoryboard() {
        val json = JSONObject(
            """{"formats":[
                {"format_id":"18","vcodec":"avc1"},
                ${format("sb3", 48, 10, "https://a/default.jpg", duration = 213.0)},
                ${format("sb1", 160, 5, "https://a/b0.jpg", "https://a/b1.jpg", duration = 49.0)},
                ${format("sb0", 320, 3, "https://a/c0.jpg", "https://a/c1.jpg", "https://a/c2.jpg", duration = 17.75)}
            ]}""",
        )
        val sb = Storyboard.parse(json)!!
        assertEquals(320, sb.width)
        assertEquals(3, sb.columns)
        assertEquals(3, sb.fragments.size)
    }

    @Test
    fun parseIgnoresNonHttpsAndBrokenOnes() {
        val json = JSONObject(
            """{"formats":[
                ${format("sb0", 320, 3, "http://insecure/a.jpg", "file:///etc/passwd")},
                {"format_id":"sb1","fragments":[]},
                {"format_id":"sb2","width":0,"height":0,"columns":3,"rows":3,"fragments":[{"url":"https://a/x.jpg","duration":9}]}
            ]}""",
        )
        assertNull(Storyboard.parse(json))
        assertNull(Storyboard.parse(JSONObject("{}")))
    }

    @Test
    fun theMediaProbeCarriesTheStoryboardWhenThereIsOne() {
        val withSb = """{"title":"T","formats":[${format("sb0", 320, 3, "https://a/c0.jpg", duration = 17.75)}]}"""
        assertTrue(MediaProbe.parse(withSb)!!.storyboard != null)
        assertNull(MediaProbe.parse("""{"title":"T"}""")!!.storyboard)
    }
}
