package com.quietdlp.app.data

/** Formato del registro de errores (funciones puras, para poder probarlas). */
object ErrorLogText {
    const val MAX_ENTRIES = 20
    const val MAX_BYTES = 32 * 1024
    private const val MAX_ENTRY_CHARS = 2000
    const val SEPARATOR = "\n---\n"

    private val sensitive = listOf("--cookies", "cookie:", "authorization:", "set-cookie", "password", "token=")

    private val queryOrFragment = Regex("""(https?://[^\s?#]+)[?#]\S*""")

    /** Quita líneas que podrían llevar cookies o claves, la parte de las URL que va tras ? o # (puede llevar claves) y acota el tamaño. */
    fun sanitize(raw: String): String =
        raw.lines()
            .filterNot { line -> sensitive.any { line.contains(it, ignoreCase = true) } }
            .joinToString("\n")
            .replace(queryOrFragment, "$1")
            .trim()
            .take(MAX_ENTRY_CHARS)

    fun entry(stamp: String, host: String?, kind: String, raw: String): String =
        "$stamp · ${host ?: "?"} · $kind\n${sanitize(raw).ifBlank { "(no details)" }}"

    /** Añade la entrada al final y descarta las más antiguas si se pasa de número o de tamaño. */
    fun append(existing: String, entry: String): String {
        val all = (if (existing.isBlank()) emptyList() else existing.split(SEPARATOR)) + entry
        var kept = all.takeLast(MAX_ENTRIES)
        while (kept.size > 1 && kept.joinToString(SEPARATOR).toByteArray().size > MAX_BYTES) {
            kept = kept.drop(1)
        }
        return kept.joinToString(SEPARATOR).take(MAX_BYTES)
    }

    fun count(text: String): Int = if (text.isBlank()) 0 else text.split(SEPARATOR).size
}
