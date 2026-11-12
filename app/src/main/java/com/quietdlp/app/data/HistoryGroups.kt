package com.quietdlp.app.data

sealed interface HistoryRow {
    val key: String

    data class Single(val item: DownloadItem) : HistoryRow {
        override val key: String get() = item.id
    }

    data class Group(val id: String, val title: String, val items: List<DownloadItem>) : HistoryRow {
        override val key: String get() = "g:$id"
        val total: Int get() = items.size
        val done: Int get() = items.count { it.state == State.DONE }
        val failed: Int get() = items.count { it.state == State.FAILED }
        val active: Int get() = items.count { it.isActive }

        val cover: DownloadItem get() = items.firstOrNull { it.savedUris.isNotEmpty() } ?: items.first()
    }
}

object HistoryGroups {
    fun rows(items: List<DownloadItem>): List<HistoryRow> {
        val byGroup = items.filter { it.groupId != null }.groupBy { it.groupId!! }
        val seen = HashSet<String>()
        val out = ArrayList<HistoryRow>(items.size)
        for (item in items) {
            val gid = item.groupId
            if (gid == null) {
                out += HistoryRow.Single(item)
                continue
            }
            if (!seen.add(gid)) continue
            val members = byGroup.getValue(gid).sortedBy { it.groupIndex }
            out += if (members.size == 1) HistoryRow.Single(members.first())
            else HistoryRow.Group(gid, members.firstNotNullOfOrNull { it.groupTitle } ?: "${members.size} files", members)
        }
        return out
    }

    // Solo frenan los anteriores ya iniciados: un reintento en cola no debe bloquearlos.
    fun mustWait(all: List<DownloadItem>, item: DownloadItem, started: (String) -> Boolean = { true }): Boolean {
        val gid = item.groupId ?: return false
        return all.any { it.groupId == gid && it.groupIndex < item.groupIndex && it.isActive && started(it.id) }
    }
}
