package com.quietdlp.app.data

/*
 * Notas de versión legibles. GitHub las entrega en Markdown; aquí se interpreta un subconjunto seguro y el resto se
 * muestra como texto limpio, para que nunca queden asteriscos, almohadillas ni acentos graves sueltos en pantalla.
 * Sin dependencias de Android: se prueba en JVM. La pantalla solo dibuja estos bloques.
 */

enum class NoteStyle { PLAIN, BOLD, ITALIC, BOLD_ITALIC, CODE }

/** Un tramo de texto con un solo formato. [url] solo lo lleva un enlace http o https. */
data class NoteSpan(val text: String, val style: NoteStyle = NoteStyle.PLAIN, val url: String? = null)

sealed interface NoteBlock {
    /** [level] va de 1 a 3: los títulos más profundos se muestran como el 3. */
    data class Heading(val level: Int, val spans: List<NoteSpan>) : NoteBlock
    data class Paragraph(val spans: List<NoteSpan>) : NoteBlock

    /** Un elemento de lista; [marker] es «•» o el número («1.»), y [depth] empieza en 0. */
    data class Item(val marker: String, val depth: Int, val spans: List<NoteSpan>) : NoteBlock
    data class Code(val text: String) : NoteBlock
    data object Rule : NoteBlock
}

object ReleaseNotes {
    fun parse(markdown: String): List<NoteBlock> {
        val blocks = mutableListOf<NoteBlock>()
        val paragraph = StringBuilder()
        val code = StringBuilder()
        var fence: String? = null
        val indents = ArrayDeque<Int>()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks += NoteBlock.Paragraph(inline(paragraph.toString().trim()))
            paragraph.clear()
        }

        fun addBlock(block: NoteBlock) {
            flushParagraph()
            if (block !is NoteBlock.Item) indents.clear()
            blocks += block
        }

        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').replace(COMMENT, "").split('\n')
        for (raw in lines) {
            val open = fence
            if (open != null) {
                if (raw.trim().startsWith(open)) {
                    addBlock(NoteBlock.Code(code.toString().trimEnd('\n')))
                    code.clear()
                    fence = null
                } else {
                    code.append(raw).append('\n')
                }
                continue
            }

            val line = stripHtml(raw).trimEnd()
            var text = line.trim()
            while (text.startsWith(">")) text = text.removePrefix(">").trimStart()
            val item = ITEM.matchEntire(line)

            when {
                text.startsWith("```") || text.startsWith("~~~") -> {
                    flushParagraph()
                    fence = text.take(3)
                }
                text.isEmpty() -> flushParagraph()
                RULE.matches(text) -> addBlock(NoteBlock.Rule)
                HEADING.matchEntire(text) != null -> {
                    val match = HEADING.matchEntire(text)!!
                    addBlock(NoteBlock.Heading(match.groupValues[1].length.coerceAtMost(3), inline(match.groupValues[2])))
                }
                text.startsWith("|") -> {
                    val cells = text.trim('|').split('|').map { it.trim() }
                    if (cells.all { TABLE_RULE.matches(it) }) {
                        flushParagraph()
                    } else {
                        addBlock(NoteBlock.Paragraph(inline(cells.filter { it.isNotEmpty() }.joinToString(" · "))))
                    }
                }
                item != null && line.trim() == text -> {
                    val width = item.groupValues[1].replace("\t", "    ").length
                    while (indents.isNotEmpty() && indents.last() > width) indents.removeLast()
                    if (indents.isEmpty() || indents.last() < width) indents.addLast(width)
                    val marker = item.groupValues[2]
                    var body = item.groupValues[3]
                    var prefix = ""
                    TASK.matchEntire(body)?.let {
                        prefix = if (it.groupValues[1].isBlank()) "☐ " else "☑ "
                        body = it.groupValues[2]
                    }
                    flushParagraph()
                    blocks += NoteBlock.Item(
                        marker = if (marker[0].isDigit()) marker.dropLast(1) + "." else "•",
                        depth = indents.size - 1,
                        spans = merge((if (prefix.isEmpty()) emptyList() else listOf(NoteSpan(prefix))) + inline(body)),
                    )
                }
                // Una línea sangrada tras un elemento de lista sigue siendo parte de ese elemento.
                line.startsWith("  ") && paragraph.isEmpty() && blocks.lastOrNull() is NoteBlock.Item -> {
                    val last = blocks.removeAt(blocks.lastIndex) as NoteBlock.Item
                    blocks += last.copy(spans = merge(last.spans + NoteSpan(" ") + inline(text)))
                }
                // Almohadillas sin título válido («##» solo, o siete o más seguidas) no se muestran.
                text.all { it == '#' } -> flushParagraph()
                else -> paragraph.append(text.replace(LONG_HASHES, "")).append(' ')
            }
        }
        flushParagraph()
        if (fence != null) blocks += NoteBlock.Code(code.toString().trimEnd('\n'))
        return blocks
    }

    // --- Texto en línea ---------------------------------------------------------------

    private val COMMENT = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val RULE = Regex("""([-*_])(\s*\1){2,}""")
    private val HEADING = Regex("""(#{1,6})\s+(.*?)\s*#*""")
    private val ITEM = Regex("""(\s*)([-*+]|\d{1,9}[.)])\s+(.*)""")
    private val LONG_HASHES = Regex("""^#{7,}\s*""")
    private val TASK = Regex("""\[([ xX])]\s+(.*)""")
    private val TABLE_RULE = Regex(""":?-+:?""")
    private val LINK = Regex("""\[([^\]]*)]\(\s*((?:[^()\s]|\([^()\s]*\))*)(?:\s+"[^"]*")?\s*\)""")
    private val IMAGE = Regex("""!\[([^\]]*)]\([^)]*\)""")
    private val AUTOLINK = Regex("""<(https?://[^>\s]+)>""")
    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>&"

    private fun stripHtml(line: String): String =
        line.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<(?!https?://)/?[a-zA-Z!][^>]*>"), "")
            .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&amp;", "&")

    private fun style(bold: Boolean, italic: Boolean) = when {
        bold && italic -> NoteStyle.BOLD_ITALIC
        bold -> NoteStyle.BOLD
        italic -> NoteStyle.ITALIC
        else -> NoteStyle.PLAIN
    }

    internal fun inline(text: String): List<NoteSpan> {
        val out = mutableListOf<NoteSpan>()
        spans(text, bold = false, italic = false, url = null, out = out)
        return merge(out)
    }

    private fun merge(spans: List<NoteSpan>): List<NoteSpan> {
        val merged = mutableListOf<NoteSpan>()
        for (span in spans) {
            if (span.text.isEmpty()) continue
            val last = merged.lastOrNull()
            if (last != null && last.style == span.style && last.url == span.url) merged[merged.lastIndex] = last.copy(text = last.text + span.text)
            else merged += span
        }
        return merged
    }

    private fun spans(t: String, bold: Boolean, italic: Boolean, url: String?, out: MutableList<NoteSpan>) {
        val buffer = StringBuilder()
        fun flush() {
            if (buffer.isNotEmpty()) out += NoteSpan(buffer.toString(), style(bold, italic), url)
            buffer.clear()
        }

        var i = 0
        while (i < t.length) {
            val c = t[i]
            when {
                c == '\\' && i + 1 < t.length && t[i + 1] in ESCAPABLE -> {
                    buffer.append(t[i + 1])
                    i += 2
                }
                c == '`' -> {
                    var run = 0
                    while (i + run < t.length && t[i + run] == '`') run++
                    val marker = "`".repeat(run)
                    val end = t.indexOf(marker, i + run)
                    if (end >= 0) {
                        flush()
                        out += NoteSpan(t.substring(i + run, end).trim(), NoteStyle.CODE, url)
                        i = end + run
                    } else {
                        i += run // Sin cierre: se descartan los acentos graves.
                    }
                }
                c == '!' && IMAGE.matchAt(t, i) != null -> {
                    val image = IMAGE.matchAt(t, i)!!
                    buffer.append(image.groupValues[1])
                    i += image.value.length
                }
                c == '[' && LINK.matchAt(t, i) != null -> {
                    val link = LINK.matchAt(t, i)!!
                    val target = link.groupValues[2].takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    flush()
                    val label = link.groupValues[1].ifBlank { link.groupValues[2] }
                    spans(label, bold, italic, target ?: url, out)
                    i += link.value.length
                }
                c == '<' && AUTOLINK.matchAt(t, i) != null -> {
                    val auto = AUTOLINK.matchAt(t, i)!!
                    flush()
                    out += NoteSpan(auto.groupValues[1], style(bold, italic), auto.groupValues[1])
                    i += auto.value.length
                }
                c == '*' || c == '_' -> {
                    var run = 0
                    while (i + run < t.length && t[i + run] == c) run++
                    val size = run.coerceAtMost(3)
                    val marker = c.toString().repeat(size)
                    val after = i + size
                    val opens = after < t.length && !t[after].isWhitespace() && (c == '*' || i == 0 || !t[i - 1].isLetterOrDigit())
                    val close = if (opens) findClose(t, marker, after, c) else -1
                    if (close > after) {
                        flush()
                        spans(t.substring(after, close), bold || size >= 2, italic || size != 2, url, out)
                        i = close + size
                    } else {
                        // Sin pareja: los asteriscos se descartan; un guion bajo suelto es parte de una palabra y se queda.
                        if (c == '_') buffer.append(marker)
                        i += run
                    }
                }
                c == '~' && t.startsWith("~~", i) -> {
                    val close = t.indexOf("~~", i + 2)
                    if (close > i + 2) {
                        flush()
                        spans(t.substring(i + 2, close), bold, italic, url, out)
                        i = close + 2
                    } else {
                        i += 2
                    }
                }
                else -> {
                    buffer.append(c)
                    i++
                }
            }
        }
        flush()
    }

    /** El cierre de un énfasis: la misma marca, no precedida de espacio, y sin ser parte de otra más larga. */
    private fun findClose(t: String, marker: String, from: Int, c: Char): Int {
        var j = t.indexOf(marker, from)
        while (j >= 0) {
            val before = t[j - 1]
            val next = j + marker.length
            val longer = next < t.length && t[next] == c
            val wordEnd = c == '*' || next >= t.length || !t[next].isLetterOrDigit()
            if (!before.isWhitespace() && before != c && !longer && wordEnd) return j
            j = t.indexOf(marker, j + 1)
        }
        return -1
    }
}
