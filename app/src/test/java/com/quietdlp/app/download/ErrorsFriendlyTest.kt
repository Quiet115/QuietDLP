package com.quietdlp.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorsFriendlyTest {
    private fun err(msg: String) = RuntimeException(msg)

    @Test
    fun aBlockedDownloadSaysWhatToDoAndOffersTheEngineUpdate() {
        val m = Errors.friendly(err("ERROR: unable to download video data: HTTP Error 403: Forbidden"))
        assertTrue(m, m.startsWith("The site blocked the download."))
        assertTrue(Errors.needsEngineUpdate(m))
    }

    @Test
    fun anExtractorFailureOffersTheEngineUpdateToo() {
        val m = Errors.friendly(err("ERROR: [site] id: Unable to extract video data; please report this issue"))
        assertTrue(Errors.needsEngineUpdate(m))
    }

    @Test
    fun plainProblemsGetPlainMessages() {
        assertEquals("The page wasn't found.", Errors.friendly(err("ERROR: HTTP Error 404: Not Found")))
        assertEquals("Not enough space on the phone.", Errors.friendly(err("ERROR: [Errno 28] No space left on device")))
        assertFalse(Errors.needsEngineUpdate("The page wasn't found."))
        assertFalse(Errors.needsEngineUpdate(null))
    }

    @Test
    fun signInStillWinsOverTheBlockedMessage() {
        // "Sign in to confirm you're not a bot" no es un yt-dlp viejo: se pide iniciar sesión.
        val m = Errors.friendly(err("ERROR: [youtube] abc: Sign in to confirm you're not a bot"))
        assertEquals("This content requires signing in.", m)
    }
}
