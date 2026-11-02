package com.quietdlp.app.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorsRateLimitTest {
    @Test
    fun recognizesASiteRefusingForGoingTooFast() {
        listOf(
            "ERROR: unable to download video data: HTTP Error 429: Too Many Requests",
            "ERROR: [youtube] abc: The current session has been rate-limited by YouTube for up to an hour.",
            "ERROR: Unable to download JSON metadata: HTTP Error 429",
            "[instagram] Too many requests, please wait",
            "ERROR: 429 Client Error: Too Many Requests for url",
            "ERROR: rate limit reached",
        ).forEach { assertTrue(it, Errors.isRateLimitedText(it)) }
    }

    @Test
    fun otherErrorsAreNotRateLimits() {
        listOf(
            "ERROR: [youtube] abc429xyz: Video unavailable",
            "ERROR: Unsupported URL: https://example.com/429",
            "ERROR: Sign in to confirm your age",
            "ERROR: Unable to resolve host",
        ).forEach { assertFalse(it, Errors.isRateLimitedText(it)) }
    }

    @Test
    fun findsEveryLinkInOrderWithoutRepeats() {
        val text = "Mira: https://a.com/1, y https://b.com/2) https://a.com/1\nhttps://c.com/3."
        assertEquals(listOf("https://a.com/1", "https://b.com/2", "https://c.com/3"), UrlUtils.extractAll(text))
        assertTrue(UrlUtils.extractAll(null).isEmpty())
        assertTrue(UrlUtils.extractAll("sin enlaces").isEmpty())
    }
}
