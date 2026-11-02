package com.quietdlp.app.data

/** Una fila de la lista de descargas: un archivo suelto o un grupo (lista de reproducción, varios enlaces). */
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

        /** El archivo que da la miniatura del grupo: el primero que ya tiene imagen. */
        val cover: DownloadItem get() = items.firstOrNull { it.savedUris.isNotEmpty() } ?: items.first()
    }
}

/** Agrupa y ordena las descargas (funciones puras, para poder probarlas). */
object HistoryGroups {
    /**
     * Junta las descargas con el mismo grupo en una sola fila, que queda donde estaba la primera de ellas.
     * Dentro del grupo van en el orden original del enlace. Un grupo al que solo le queda un archivo se ve como uno suelto.
     */
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

    /**
     * true si [item] tiene que esperar antes de guardar su archivo: queda alguno anterior del mismo grupo sin terminar.
     * Así los archivos se guardan siempre en el orden del enlace, aunque se descarguen a la vez y pesen distinto.
     * [started] dice si un archivo ya salió de la cola: uno que sigue esperando su turno no frena a los demás
     * (si no, un reintento al final de la cola bloquearía a los que ya van por delante).
     */
    fun mustWait(all: List<DownloadItem>, item: DownloadItem, started: (String) -> Boolean = { true }): Boolean {
        val gid = item.groupId ?: return false
        return all.any { it.groupId == gid && it.groupIndex < item.groupIndex && it.isActive && started(it.id) }
    }
}
