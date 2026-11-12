package com.quietdlp.app.data

import java.text.Normalizer

/**
 * Un ajuste en el índice del buscador. [titles] y [keywords] llevan los textos en el idioma elegido y en inglés a la
 * vez, así que se encuentra igual escribiendo en cualquiera de los dos.
 */
data class SearchEntry(
    val id: String,
    val titles: List<String>,
    val keywords: List<String>,
)

object SettingsSearch {

    /** Minúsculas y sin acentos, para que "bateria" encuentre "Batería". */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    /**
     * Los ajustes que contienen todas las palabras escritas, en el orden del índice, con los que coinciden en el título
     * primero. Una consulta vacía no encuentra nada.
     */
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
