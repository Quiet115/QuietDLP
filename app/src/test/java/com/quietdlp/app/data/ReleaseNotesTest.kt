package com.quietdlp.app.data

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

class ReleaseNotesTest {
    private fun spans(vararg parts: Pair<String, NoteStyle>) = parts.map { NoteSpan(it.first, it.second) }
    private fun plain(text: String) = listOf(NoteSpan(text))

    @Test
    fun `bold feedback has no asterisks left`() {
        val blocks = ReleaseNotes.parse("**Feedback**")
        assertEquals(listOf<NoteBlock>(NoteBlock.Paragraph(listOf(NoteSpan("Feedback", NoteStyle.BOLD)))), blocks)
    }

    @Test
    fun `headings become highlighted text and deeper ones are shown as level three`() {
        val blocks = ReleaseNotes.parse("# Uno\n## Novedades\n### Tres\n#### Cuatro ##")
        assertEquals(
            listOf(
                NoteBlock.Heading(1, plain("Uno")),
                NoteBlock.Heading(2, plain("Novedades")),
                NoteBlock.Heading(3, plain("Tres")),
                NoteBlock.Heading(3, plain("Cuatro")),
            ),
            blocks,
        )
    }

    @Test
    fun `an issue reference is not a heading and keeps its hash`() {
        val blocks = ReleaseNotes.parse("Fixes #12 and #34")
        assertEquals(listOf<NoteBlock>(NoteBlock.Paragraph(plain("Fixes #12 and #34"))), blocks)
    }

    @Test
    fun `bullets and numbers become items`() {
        val blocks = ReleaseNotes.parse("- one\n* two\n+ three\n1. first\n2) second")
        assertEquals(
            listOf(
                NoteBlock.Item("•", 0, plain("one")),
                NoteBlock.Item("•", 0, plain("two")),
                NoteBlock.Item("•", 0, plain("three")),
                NoteBlock.Item("1.", 0, plain("first")),
                NoteBlock.Item("2.", 0, plain("second")),
            ),
            blocks,
        )
    }

    @Test
    fun `nested lists get a depth whether they use two or four spaces`() {
        val two = ReleaseNotes.parse("- a\n  - b\n    - c\n- d").filterIsInstance<NoteBlock.Item>().map { it.depth }
        val four = ReleaseNotes.parse("- a\n    - b\n        - c\n- d").filterIsInstance<NoteBlock.Item>().map { it.depth }
        assertEquals(listOf(0, 1, 2, 0), two)
        assertEquals(listOf(0, 1, 2, 0), four)
    }

    @Test
    fun `a wrapped line stays with its list item`() {
        val blocks = ReleaseNotes.parse("- first line\n  and the rest\n- next")
        assertEquals(NoteBlock.Item("•", 0, plain("first line and the rest")), blocks[0])
        assertEquals(2, blocks.size)
    }

    @Test
    fun `task list items get a box`() {
        val blocks = ReleaseNotes.parse("- [x] done\n- [ ] todo")
        assertEquals(NoteBlock.Item("•", 0, plain("☑ done")), blocks[0])
        assertEquals(NoteBlock.Item("•", 0, plain("☐ todo")), blocks[1])
    }

    @Test
    fun `italic bold and both`() {
        val blocks = ReleaseNotes.parse("*a* and _b_ and **c** and ***d***")
        assertEquals(
            listOf<NoteBlock>(
                NoteBlock.Paragraph(
                    spans(
                        "a" to NoteStyle.ITALIC, " and " to NoteStyle.PLAIN, "b" to NoteStyle.ITALIC, " and " to NoteStyle.PLAIN,
                        "c" to NoteStyle.BOLD, " and " to NoteStyle.PLAIN, "d" to NoteStyle.BOLD_ITALIC,
                    ),
                ),
            ),
            blocks,
        )
    }

    @Test
    fun `underscores inside words are kept`() {
        val blocks = ReleaseNotes.parse("uses snake_case_names and a_b")
        assertEquals(listOf<NoteBlock>(NoteBlock.Paragraph(plain("uses snake_case_names and a_b"))), blocks)
    }

    @Test
    fun `links show their text and are clickable only for http and https`() {
        val web = ReleaseNotes.parse("See [the page](https://example.com/x) now").single() as NoteBlock.Paragraph
        assertEquals(listOf(NoteSpan("See "), NoteSpan("the page", NoteStyle.PLAIN, "https://example.com/x"), NoteSpan(" now")), web.spans)

        val unsafe = ReleaseNotes.parse("[click](javascript:alert(1))").single() as NoteBlock.Paragraph
        assertEquals(listOf(NoteSpan("click")), unsafe.spans)
        assertNull(unsafe.spans.single().url)

        val auto = ReleaseNotes.parse("<https://example.com>").single() as NoteBlock.Paragraph
        assertEquals(listOf(NoteSpan("https://example.com", NoteStyle.PLAIN, "https://example.com")), auto.spans)
    }

    @Test
    fun `bold inside a link keeps both`() {
        val paragraph = ReleaseNotes.parse("[**big**](https://example.com)").single() as NoteBlock.Paragraph
        assertEquals(listOf(NoteSpan("big", NoteStyle.BOLD, "https://example.com")), paragraph.spans)
    }

    @Test
    fun `images keep only their text and html is dropped`() {
        val paragraph = ReleaseNotes.parse("Look ![a cat](https://x/y.png) <b>here</b><br>and<img src=\"z\"> there").single() as NoteBlock.Paragraph
        assertEquals(listOf(NoteSpan("Look a cat here and there")), paragraph.spans)
    }

    @Test
    fun `html comments are removed even over several lines`() {
        val blocks = ReleaseNotes.parse("before\n<!-- hidden\nmore -->\nafter")
        // Un comentario en su propia línea corta el párrafo, como hace Markdown.
        assertEquals(listOf<NoteBlock>(NoteBlock.Paragraph(plain("before")), NoteBlock.Paragraph(plain("after"))), blocks)
    }

    @Test
    fun `inline code and fenced code blocks keep their characters`() {
        val inline = ReleaseNotes.parse("Run `a * b` now").single() as NoteBlock.Paragraph
        assertEquals(listOf(NoteSpan("Run "), NoteSpan("a * b", NoteStyle.CODE), NoteSpan(" now")), inline.spans)

        val fenced = ReleaseNotes.parse("Before\n```kotlin\nval x = **y** <tag>\n  indented\n```\nAfter")
        assertEquals(
            listOf(
                NoteBlock.Paragraph(plain("Before")),
                NoteBlock.Code("val x = **y** <tag>\n  indented"),
                NoteBlock.Paragraph(plain("After")),
            ),
            fenced,
        )
    }

    @Test
    fun `an unclosed code fence still shows its text`() {
        assertEquals(listOf<NoteBlock>(NoteBlock.Code("text")), ReleaseNotes.parse("```\ntext"))
    }

    @Test
    fun `rules and tables`() {
        assertEquals(listOf<NoteBlock>(NoteBlock.Rule, NoteBlock.Rule, NoteBlock.Rule), ReleaseNotes.parse("---\n***\n* * *"))
        val table = ReleaseNotes.parse("| Name | Size |\n| --- | :-: |\n| app | 2 MB |")
        assertEquals(
            listOf<NoteBlock>(NoteBlock.Paragraph(plain("Name · Size")), NoteBlock.Paragraph(plain("app · 2 MB"))),
            table,
        )
    }

    @Test
    fun `quotes lose the marker and paragraphs join their lines`() {
        val blocks = ReleaseNotes.parse("> quoted\n> text\n\nsecond\nline")
        assertEquals(listOf<NoteBlock>(NoteBlock.Paragraph(plain("quoted text")), NoteBlock.Paragraph(plain("second line"))), blocks)
    }

    @Test
    fun `no raw markdown marks are left in anything that is shown as text`() {
        val samples = listOf(
            "**Feedback** tab\n\n## What's new in 1.4.1\n\n- **About tab**: the last tab\n- *Version* in `Updates`\n  - nested **deep**",
            "5 * 3 = 15 and **unclosed bold",
            "an `unclosed tick and ***triple",
            "####### seven hashes\n##\n#",
            "~~struck~~ and ~~half",
            "* * \n- \n**\n__\n``",
            "Full Changelog: https://github.com/Quiet115/QuietRingtones/compare/v1.4.0...v1.4.1",
        )
        for (sample in samples) {
            for (block in ReleaseNotes.parse(sample)) {
                val shown = when (block) {
                    is NoteBlock.Heading -> block.spans
                    is NoteBlock.Paragraph -> block.spans
                    is NoteBlock.Item -> block.spans
                    else -> emptyList()
                }.filter { it.style != NoteStyle.CODE }.joinToString("") { it.text }
                assertFalse("asterisk left in «$shown» from «$sample»", '*' in shown)
                assertFalse("backtick left in «$shown» from «$sample»", '`' in shown)
                assertFalse("hash left in «$shown» from «$sample»", shown.startsWith("#"))
                assertFalse("tilde left in «$shown» from «$sample»", "~~" in shown)
            }
        }
    }

    @Test
    fun `a real release note comes out clean`() {
        val notes = "## What's new in 1.4.1\n\n- **About tab**: the last tab in Settings shows the app version.\n- **Version in Updates**: the tab now ends with the installed version.\n"
        val blocks = ReleaseNotes.parse(notes)
        assertEquals(3, blocks.size)
        assertEquals(NoteBlock.Heading(2, plain("What's new in 1.4.1")), blocks[0])
        val first = blocks[1] as NoteBlock.Item
        assertEquals(NoteSpan("About tab", NoteStyle.BOLD), first.spans[0])
        assertTrue(first.spans[1].text.startsWith(": the last tab"))
    }

    @Test
    fun `empty or blank notes give no blocks`() {
        assertEquals(emptyList<NoteBlock>(), ReleaseNotes.parse(""))
        assertEquals(emptyList<NoteBlock>(), ReleaseNotes.parse("  \n\n \r\n"))
    }
}
