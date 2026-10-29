package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FfmpegOutputTest {
    @Test
    fun readsTheDuration() {
        assertEquals(201_450L, FfmpegOutput.durationMs("  Duration: 00:03:21.45, start: 0.000000, bitrate: 128 kb/s"))
        assertEquals(3_723_000L, FfmpegOutput.durationMs("Duration: 01:02:03.00, start: 0"))
        assertNull(FfmpegOutput.durationMs("Duration: N/A, bitrate: N/A"))
        assertNull(FfmpegOutput.durationMs("frame=1"))
    }

    @Test
    fun readsTheProgress() {
        assertEquals(1_234L, FfmpegOutput.doneMs("out_time_us=1234567"))
        assertNull(FfmpegOutput.doneMs("out_time_us=N/A"))
        assertNull(FfmpegOutput.doneMs("out_time=00:00:01.23"))
        assertEquals(50, FfmpegOutput.percent(5_000, 10_000))
        assertEquals(99, FfmpegOutput.percent(10_000, 10_000))
        assertEquals(0, FfmpegOutput.percent(-5, 10_000))
        assertEquals(-1, FfmpegOutput.percent(5_000, 0))
    }

    @Test
    fun findsTheCauseOfAnError() {
        val lines = listOf(
            "Stream mapping:", "Unknown encoder 'libfoo'", "frame=0", "fps=0.00", "progress=end", "",
        )
        assertEquals("Unknown encoder 'libfoo'", FfmpegOutput.errorLine(lines))
        assertEquals("ffmpeg failed", FfmpegOutput.errorLine(listOf("frame=0", "progress=end")))
        // El mensaje del cargador no debe esconder la causa detrás de la ruta de la app.
        val linker = """CANNOT LINK EXECUTABLE "/data/app/~~x==/com.quietdlp.app-y==/lib/arm64/libffmpeg.so": """ +
            """library "libc++_shared.so" not found: needed by /a/librubberband.so"""
        assertEquals(
            """Can't start ffmpeg: library "libc++_shared.so" not found: needed by /a/librubberband.so""",
            FfmpegOutput.errorLine(listOf(linker, "")),
        )
        assertEquals(200, FfmpegOutput.errorLine(listOf("x".repeat(500))).length)
    }
}
