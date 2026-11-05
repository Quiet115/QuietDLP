package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedbackTest {
    private val info = DiagnosticInfo("1.3.3", "Android 17 (API 37)", "samsung SM-S731B", "Spanish")

    @Test
    fun `a bug report opens a new issue with the bug label and only the diagnostic data`() {
        val url = Feedback.url(FeedbackKind.BUG, info)

        assertTrue(url.startsWith("https://github.com/Quiet115/QuietDLP/issues/new?title=Bug%3A%20&labels=bug&body="))
        assertTrue(url.contains("App%20version%3A%201.3.3"))
        assertTrue(url.contains("Device%3A%20samsung%20SM-S731B"))
        assertTrue(url.contains("Language%3A%20Spanish"))
    }

    @Test
    fun `a suggestion uses the enhancement label`() {
        assertTrue(Feedback.url(FeedbackKind.SUGGESTION, info).contains("labels=enhancement"))
    }

    @Test
    fun `characters that would break the link are encoded`() {
        val tricky = info.copy(device = "A&B #1 100%")
        val query = Feedback.url(FeedbackKind.BUG, tricky).substringAfter("body=")

        assertTrue(query.contains("A%26B%20%231%20100%25"))
        assertFalse(query.contains("&"))
        assertFalse(query.contains(" "))
    }

    @Test
    fun `a body that does not fit is cut instead of failing`() {
        val huge = info.copy(device = "x".repeat(5000))
        val url = Feedback.url(FeedbackKind.BUG, huge)

        assertTrue(url.length <= Feedback.MAX_URL_LENGTH)
        assertTrue(url.startsWith("https://github.com/Quiet115/QuietDLP/issues/new?title="))
    }

    @Test
    fun `when something is cut the template goes before the diagnostic data`() {
        val url = Feedback.url(FeedbackKind.BUG, info, maxLength = 230)

        assertTrue(url.contains("App%20version"))
        assertFalse(url.contains("Steps"))
    }

    @Test
    fun `the diagnostic text lists exactly four facts`() {
        assertEquals(
            "App version: 1.3.3\nAndroid: Android 17 (API 37)\nDevice: samsung SM-S731B\nLanguage: Spanish",
            info.text(),
        )
    }
}
