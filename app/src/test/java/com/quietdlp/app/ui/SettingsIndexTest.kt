package com.quietdlp.app.ui

import com.quietdlp.app.R
import com.quietdlp.app.data.SettingsSearch
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsIndexTest {
    @Test
    fun fileNamesSearchOpensTheTabContainingTheControl() {
        assertEquals(SettingsPage.DOWNLOADS, SettingId.FILE_NAMES.page)
    }

    @Test
    fun profileSearchUsesTheTitleShownByTheProfileCard() {
        assertEquals(R.string.profile_title, SettingId.BACKUP.title)
    }

    private fun realText(): (Language, Int) -> String {
        val names = R.string::class.java.fields.associate { it.getInt(null) to it.name }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val resources = Language.entries.associateWith { language ->
            val folder = if (language == Language.ENGLISH) "values" else "values-es"
            val path = "src/main/res/$folder/strings.xml"
            val file = listOf(File(path), File("app/$path")).first { it.exists() }
            val elements = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until elements.length).associate {
                val node = elements.item(it)
                node.attributes.getNamedItem("name").nodeValue to node.textContent
            }
        }
        return { language, id -> resources.getValue(language).getValue(names.getValue(id)) }
    }

    @Test
    fun realRegistryFindsEnglishAndSpanishWithoutDependingOnTheShownLanguage() {
        val index = buildSettingsIndex(advanced = false, text = realText())
        listOf("BATERIA", "battery").forEach { query ->
            assertTrue(query, SettingsSearch.search(query, index).any { it.id == SettingId.BATTERY.name })
        }
        listOf("perfil", "profile").forEach { query ->
            assertTrue(query, SettingsSearch.search(query, index).any { it.id == SettingId.BACKUP.name })
        }
        assertTrue(SettingsSearch.search("unfindable-synthetic-query", index).isEmpty())
    }

    @Test
    fun fileNamesAreIndexedOnlyWhenTheirAdvancedControlIsVisible() {
        val text = realText()
        assertTrue(buildSettingsIndex(false, text).none { it.id == SettingId.FILE_NAMES.name })
        assertTrue(buildSettingsIndex(true, text).any { it.id == SettingId.FILE_NAMES.name })
    }
}
