package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    // Con la app en español: títulos en español e inglés, y palabras clave de los dos idiomas.
    private val entries = listOf(
        SearchEntry("battery", listOf("Batería", "Battery"), listOf("energía ahorro sin límites", "power saving unrestricted")),
        SearchEntry("wifi", listOf("Red", "Network"), listOf("wifi datos móviles", "wi-fi mobile data")),
        SearchEntry("palette", listOf("Apariencia", "Appearance"), listOf("tema colores oscuro", "theme colors dark")),
        SearchEntry("language", listOf("Idioma", "Language"), listOf("español inglés", "spanish english")),
        SearchEntry("notifications", listOf("Notificaciones", "Notifications"), listOf("avisos", "alerts")),
    )

    private fun ids(q: String) = SettingsSearch.search(q, entries).map { it.id }

    @Test
    fun ignoresCaseAndAccents() {
        assertEquals(listOf("battery"), ids("BATERIA"))
        assertEquals(listOf("battery"), ids("bateria"))
        assertEquals(listOf("language"), ids("INGLES"))
        assertEquals(listOf("wifi"), ids("móviles"))
    }

    @Test
    fun findsEnglishWordsWhileTheAppIsInSpanish() {
        assertEquals(listOf("battery"), ids("battery"))
        assertEquals(listOf("palette"), ids("theme"))
        assertEquals(listOf("notifications"), ids("alerts"))
    }

    @Test
    fun findsSpanishSynonymsWhileTheAppIsInEnglish() {
        val english = listOf(SearchEntry("battery", listOf("Battery", "Batería"), listOf("power saving", "energía ahorro")))
        assertEquals(listOf("battery"), SettingsSearch.search("ahorro", english).map { it.id })
        assertEquals(listOf("battery"), SettingsSearch.search("energia", english).map { it.id })
    }

    @Test
    fun allWordsMustMatch() {
        assertEquals(listOf("wifi"), ids("datos wifi"))
        assertTrue(ids("datos bateria").isEmpty())
    }

    @Test
    fun noResultsAndEmptyQuery() {
        assertTrue(ids("zzz").isEmpty())
        assertTrue(ids("").isEmpty())
        assertTrue(ids("   ").isEmpty())
    }

    @Test
    fun titleMatchesComeFirst() {
        val list = listOf(
            SearchEntry("a", listOf("Other"), listOf("battery tips")),
            SearchEntry("b", listOf("Battery"), listOf("")),
        )
        assertEquals(listOf("b", "a"), SettingsSearch.search("battery", list).map { it.id })
    }

    @Test
    fun settingsFromSeveralTabsCanMatchTogether() {
        val list = listOf(
            SearchEntry("queue-wifi", listOf("Network"), listOf("wi-fi")),
            SearchEntry("follow-wifi", listOf("Followed pages"), listOf("wi-fi")),
        )
        assertEquals(listOf("queue-wifi", "follow-wifi"), SettingsSearch.search("wi-fi", list).map { it.id })
    }
}
