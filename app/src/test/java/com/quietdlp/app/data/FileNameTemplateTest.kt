package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileNameTemplateTest {

    @Test
    fun theDefaultKeepsTheTitleAsAlways() {
        assertEquals("%(title).100B", FileNameTemplate.toYtDlp(FileNameTemplate.DEFAULT))
        assertEquals("%(title).100B", FileNameTemplate.toYtDlp(""))
        assertEquals("%(title).100B", FileNameTemplate.toYtDlp("   "))
    }

    @Test
    fun everyPresetBecomesAYtDlpTemplateWithoutLeftoverBraces() {
        FileNameTemplate.PRESETS.forEach { (label, t) ->
            val y = FileNameTemplate.toYtDlp(t)
            assertFalse(label, "{" in y || "}" in y)
            assertTrue(label, "%(title)" in y)
        }
    }

    @Test
    fun tokensAreTranslated() {
        assertEquals("%(uploader).60B - %(title).100B", FileNameTemplate.toYtDlp("{channel} - {title}"))
        assertEquals("%(upload_date>%Y-%m-%d)s - %(title).100B", FileNameTemplate.toYtDlp("{date} - {title}"))
        assertEquals("%(title).100B [%(id)s]", FileNameTemplate.toYtDlp("{title} [{id}]"))
        assertEquals("%(title).100B %(height)sp", FileNameTemplate.toYtDlp("{title} {resolution}"))
    }

    @Test
    fun unknownMarksAreDroppedAndThePathIsClean() {
        assertEquals("%(title).100B", FileNameTemplate.toYtDlp("{title}{nothing}"))
        // Sin / ni \ (no se pueden crear carpetas) y sin % (rompería la plantilla de yt-dlp).
        assertEquals("a-b%(title).100B", FileNameTemplate.toYtDlp("a-b{title}"))
        val evil = FileNameTemplate.toYtDlp("../x\\y%(id)s{title}")
        assertFalse("/" in evil || "\\" in evil)
        assertFalse(evil.contains("%(id)s{") )
        assertEquals("..xy(id)s%(title).100B", evil)
    }

    @Test
    fun aNameWithoutTitleOrIdGetsTheIdSoVideosDoNotOverwriteEachOther() {
        assertEquals("%(uploader).60B [%(id)s]", FileNameTemplate.toYtDlp("{channel}"))
        assertEquals("fixed [%(id)s]", FileNameTemplate.toYtDlp("fixed"))
        assertEquals("%(id)s", FileNameTemplate.toYtDlp("{id}"))
    }

    @Test
    fun theMarkPatternEscapesBothBracesBecauseAndroidRejectsABareOne() {
        assertEquals("""\{([a-z]+)\}""", FileNameTemplate.TOKEN_PATTERN)
    }

    @Test
    fun theLengthIsLimited() {
        assertEquals(80, FileNameTemplate.sanitize("x".repeat(500)).length)
    }

    @Test
    fun thePreviewUsesAnExampleVideo() {
        assertEquals("My video", FileNameTemplate.preview("{title}"))
        assertEquals("My channel - My video", FileNameTemplate.preview("{channel} - {title}"))
        assertEquals("2026-11-03 - My video", FileNameTemplate.preview("{date} - {title}"))
        assertEquals("My video [aBc123xYz]", FileNameTemplate.preview("{title} [{id}]"))
        assertEquals("My channel [aBc123xYz]", FileNameTemplate.preview("{channel}"))
    }
}
