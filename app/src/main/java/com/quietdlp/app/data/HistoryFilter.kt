package com.quietdlp.app.data

/** Búsqueda en el historial (función pura, para poder probarla). */
object HistoryFilter {
    /** Deja las descargas cuyo título, web o tipo de calidad contengan todas las palabras escritas. */
    fun filter(items: List<DownloadItem>, query: String): List<DownloadItem> {
        val words = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return items
        return items.filter { i ->
            val haystack = (i.title + " " + SiteRules.normalizeHost(i.url).orEmpty() + " " + i.quality.label()).lowercase()
            words.all { it in haystack }
        }
    }
}
