package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorLogTextTest {

    @Test
    fun sanitizeDropsSensitiveLines() {
        val raw = "ERROR: boom\nrunning yt-dlp --cookies /data/x/cookies.txt\nCookie: a=b\nAuthorization: Bearer x\nlast"
        val out = ErrorLogText.sanitize(raw)
        assertEquals("ERROR: boom\nlast", out)
    }

    @Test
    fun sanitizeStripsQueryAndFragmentFromUrls() {
        val raw = "ERROR: Unsupported URL: https://cdn.example.com/v/a.mp4?sig=abc&key9=x#t=5 and http://b.org/p?q=1\nok https://c.net/path"
        val out = ErrorLogText.sanitize(raw)
        assertEquals(
            "ERROR: Unsupported URL: https://cdn.example.com/v/a.mp4 and http://b.org/p\nok https://c.net/path",
            out,
        )
    }

    @Test
    fun entryHasHeaderAndFallback() {
        val e = ErrorLogText.entry("2026-10-26 03:00", "example.com", "Audio M4A", "ERROR: nope")
        assertEquals("2026-10-26 03:00 · example.com · Audio M4A\nERROR: nope", e)
        assertTrue(ErrorLogText.entry("t", null, "k", "  ").endsWith("(no details)"))
        assertTrue(ErrorLogText.entry("t", null, "k", "x").startsWith("t · ? · k"))
    }

    @Test
    fun appendKeepsOnlyLastEntries() {
        var log = ""
        for (i in 1..ErrorLogText.MAX_ENTRIES + 5) log = ErrorLogText.append(log, "e$i")
        assertEquals(ErrorLogText.MAX_ENTRIES, ErrorLogText.count(log))
        assertTrue(log.endsWith("e${ErrorLogText.MAX_ENTRIES + 5}"))
        assertFalse(log.contains("e1\n"))
    }

    @Test
    fun appendRespectsByteCap() {
        val big = "x".repeat(1900)
        var log = ""
        repeat(19) { log = ErrorLogText.append(log, big + it) }
        assertTrue(log.toByteArray().size <= ErrorLogText.MAX_BYTES)
        assertTrue(log.endsWith(big + 18))
    }

    @Test
    fun countOfEmpty() {
        assertEquals(0, ErrorLogText.count(""))
        assertEquals(2, ErrorLogText.count("a${ErrorLogText.SEPARATOR}b"))
    }
}
