package com.quietdlp.app.data

import java.text.Normalizer

// El índice conserva los dos idiomas sin cambiar el que muestra la pantalla.
data class SearchEntry(
    val id: String,
    val titles: List<String>,
    val keywords: List<String>,
)

object SettingsSearch {

    // Quitar acentos permite buscar sin ellos.
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun search(query: String, entries: List<SearchEntry>): List<SearchEntry> {
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val scored = entries.mapNotNull { e ->
            val title = normalize(e.titles.joinToString(" "))
            val all = title + " " + normalize(e.keywords.joinToString(" "))
            if (words.all { it in all }) (if (words.all { it in title }) 0 else 1) to e else null
        }
        return scored.sortedBy { it.first }.map { it.second }
    }
}
