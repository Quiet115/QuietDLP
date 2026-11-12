package com.quietdlp.app.data

object HistoryFilter {
    fun filter(items: List<DownloadItem>, query: String): List<DownloadItem> {
        val words = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return items
        return items.filter { i ->
            val haystack = (i.title + " " + i.groupTitle.orEmpty() + " " + SiteRules.normalizeHost(i.url).orEmpty() + " " + i.quality.label()).lowercase()
            words.all { it in haystack }
        }
    }
}
