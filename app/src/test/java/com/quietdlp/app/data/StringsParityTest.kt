package com.quietdlp.app.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada texto en inglés tiene su traducción al español, con los mismos marcadores (%1$s), y no hay nombres repetidos. */
class StringsParityTest {
    private fun load(path: String): Map<String, String> {
        val file = listOf("src/main/res/$path", "app/src/main/res/$path").map(::File).first { it.exists() }
        val xml = file.readText()
        val names = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL).findAll(xml).toList()
        val map = LinkedHashMap<String, String>()
        for (m in names) {
            assertTrue("duplicate string name ${m.groupValues[1]} in $path", map.put(m.groupValues[1], m.groupValues[2]) == null)
        }
        return map
    }

    private fun placeholders(text: String) = Regex("%[0-9]" + "\\$" + "s").findAll(text).map { it.value }.sorted().toList()

    @Test
    fun everyEnglishTextHasASpanishOneWithTheSamePlaceholders() {
        val en = load("values/strings.xml")
        val es = load("values-es/strings.xml")
        val missing = en.keys - es.keys - setOf("app_name", "share_label")
        assertTrue("missing in Spanish: $missing", missing.isEmpty())
        val extra = es.keys - en.keys
        assertTrue("only in Spanish: $extra", extra.isEmpty())
        for ((name, text) in es) {
            assertEquals("placeholders of $name", placeholders(en.getValue(name)), placeholders(text))
        }
    }
}
