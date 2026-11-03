package com.quietdlp.app.data

/**
 * El nombre de los archivos descargados (modo Avanzado). Se escribe con marcas entre llaves y se traduce a una plantilla
 * de yt-dlp: {title}, {channel}, {date}, {id} y {resolution}. La música sigue su propio nombre ([MusicNaming]).
 */
object FileNameTemplate {
    const val DEFAULT = "{title}"

    /** Los nombres hechos que se ofrecen: (texto que se ve, plantilla). */
    val PRESETS: List<Pair<String, String>> = listOf(
        "Title" to "{title}",
        "Channel - Title" to "{channel} - {title}",
        "Date - Title" to "{date} - {title}",
        "Title [id]" to "{title} [{id}]",
    )

    private val TOKENS = mapOf(
        "title" to "%(title).100B",
        "channel" to "%(uploader).60B",
        "date" to "%(upload_date>%Y-%m-%d)s",
        "id" to "%(id)s",
        "resolution" to "%(height)sp",
    )

    private val EXAMPLE = mapOf(
        "title" to "My video",
        "channel" to "My channel",
        "date" to "2026-11-03",
        "id" to "aBc123xYz",
        "resolution" to "1080p",
    )

    /** Las llaves van las dos escapadas: Android las rechaza sin escapar (la JVM del PC no), y eso cerraba la app. */
    const val TOKEN_PATTERN = """\{([a-z]+)\}"""

    private val TOKEN = Regex(TOKEN_PATTERN)

    /** Quita lo que no puede ir en un nombre de archivo ni en una plantilla de yt-dlp, y limita el largo. */
    fun sanitize(input: String): String =
        input.filterNot { it == '/' || it == '\\' || it == '%' || it.isISOControl() }.take(80)

    /** Las marcas que no existen se ignoran. */
    private fun cleaned(template: String): String =
        TOKEN.replace(sanitize(template)) { if (it.groupValues[1] in TOKENS) it.value else "" }.trim()

    /**
     * La plantilla de yt-dlp (sin extensión). Si el nombre no lleva ni {title} ni {id} dos vídeos distintos se llamarían
     * igual y uno pisaría al otro: se añade el id.
     */
    fun toYtDlp(template: String): String {
        var t = cleaned(template).ifBlank { DEFAULT }
        if ("{title}" !in t && "{id}" !in t) t = "$t [{id}]"
        return TOKEN.replace(t) { TOKENS.getValue(it.groupValues[1]) }
    }

    /** Cómo quedaría el nombre con un vídeo de ejemplo (para enseñarlo mientras se escribe). */
    fun preview(template: String): String {
        var t = cleaned(template).ifBlank { DEFAULT }
        if ("{title}" !in t && "{id}" !in t) t = "$t [{id}]"
        return TOKEN.replace(t) { EXAMPLE.getValue(it.groupValues[1]) }
    }
}
