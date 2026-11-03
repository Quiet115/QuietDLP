package com.quietdlp.app.data

import org.json.JSONObject
import java.util.Locale

/** Una pista de subtítulos que ofrece el sitio. [code] es el que entiende yt-dlp ("en", "es-419", "en-orig"). */
data class SubtitleTrack(val code: String, val name: String, val auto: Boolean) {
    val label: String get() = if (auto) "$name (auto)" else name
}

/** Dónde van los subtítulos. Pegarlos a la imagen se hace aparte, desde Convert (hay que recodificar el vídeo). */
enum class SubtitleMode(val id: String, val label: String) {
    INSIDE("inside", "Inside the video"),
    MKV("mkv", "Inside the video (MKV)"),
    SEPARATE("file", "Separate .srt file");

    companion object {
        fun fromId(id: String?): SubtitleMode = entries.firstOrNull { it.id == id } ?: INSIDE
    }
}

/** Lo que se pidió: qué idiomas ([codes], tal como los da el sitio), si valen los automáticos y dónde van. */
data class SubtitleChoice(
    val codes: List<String>,
    val mode: SubtitleMode = SubtitleMode.INSIDE,
    val includeAuto: Boolean = false,
) {
    /** Texto corto para guardarlo en el historial: "modo|en,es|1". */
    fun encode(): String = "${mode.id}|${codes.joinToString(",")}|${if (includeAuto) 1 else 0}"

    companion object {
        fun decode(text: String?): SubtitleChoice? {
            if (text.isNullOrBlank()) return null
            val p = text.split("|")
            if (p.size < 3) return null
            val codes = p[1].split(",").filter { it.isNotBlank() }
            if (codes.isEmpty()) return null
            return SubtitleChoice(codes, SubtitleMode.fromId(p[0]), p[2] == "1")
        }
    }
}

object SubtitleProbe {

    /**
     * Las pistas que lee del JSON de yt-dlp ("subtitles" y "automatic_captions"). Primero las de verdad, por nombre,
     * y después las automáticas. YouTube ofrece los automáticos traducidos a más de cien idiomas: de esos solo se queda
     * el idioma original (los que acaban en "-orig").
     */
    fun parse(root: JSONObject): List<SubtitleTrack> {
        val manual = tracksOf(root.optJSONObject("subtitles"), auto = false)
        val autoAll = tracksOf(root.optJSONObject("automatic_captions"), auto = true)
        val originals = autoAll.filter { it.code.endsWith("-orig") }
        val auto = if (originals.isNotEmpty()) originals else autoAll
        return manual.sortedBy { it.name } + auto.sortedBy { it.name }
    }

    private fun tracksOf(o: JSONObject?, auto: Boolean): List<SubtitleTrack> {
        if (o == null) return emptyList()
        val out = ArrayList<SubtitleTrack>()
        val keys = o.keys()
        while (keys.hasNext()) {
            val code = keys.next()
            // El chat de un directo viene como "subtítulo", pero no lo es.
            if (code == "live_chat" || code.isBlank()) continue
            val entries = o.optJSONArray(code)
            if (entries == null || entries.length() == 0) continue
            out += SubtitleTrack(code, displayName(code, entries.optJSONObject(0)?.optString("name")), auto)
        }
        return out
    }

    private fun displayName(code: String, siteName: String?): String {
        val tag = code.removeSuffix("-orig")
        val fromLocale = Locale.forLanguageTag(tag).getDisplayName(Locale.ENGLISH)
        if (fromLocale.isNotBlank() && !fromLocale.equals(tag, ignoreCase = true) && !fromLocale.equals("und", true)) {
            return fromLocale
        }
        return siteName?.takeIf { it.isNotBlank() } ?: tag
    }

    /**
     * Qué idiomas dejar marcados al abrir un vídeo: los que el usuario eligió la última vez (si este vídeo los tiene);
     * si no, el idioma del móvil; si no, inglés. Con cada idioma prefiere la pista de verdad a la automática.
     */
    fun preselect(tracks: List<SubtitleTrack>, remembered: List<String>, phoneLanguage: String): List<String> {
        val same = remembered.filter { code -> tracks.any { it.code == code } }
        if (same.isNotEmpty()) return same
        for (lang in listOf(phoneLanguage, "en")) {
            val match = tracks.firstOrNull { !it.auto && languageOf(it.code) == lang }
                ?: tracks.firstOrNull { languageOf(it.code) == lang }
            if (match != null) return listOf(match.code)
        }
        return emptyList()
    }

    /** "es-419" y "es" son el mismo idioma: "es". */
    fun languageOf(code: String): String = code.removeSuffix("-orig").substringBefore('-').lowercase()
}

/** Las opciones de yt-dlp para una elección de subtítulos. Vacío si no se pidió nada. */
object SubtitleOptions {

    /** Extensiones de los archivos de subtítulos que yt-dlp deja suelto cuando no se incrustan. */
    val FILE_EXTENSIONS = setOf("srt", "vtt", "ass", "ttml", "lrc")

    fun ytDlpOptions(choice: SubtitleChoice?): List<Pair<String, String?>> {
        if (choice == null || choice.codes.isEmpty()) return emptyList()
        val out = ArrayList<Pair<String, String?>>()
        // --embed-subs ya baja los subtítulos y borra los archivos sueltos al terminar; con --write-subs además
        // los dejaría en la carpeta. Por eso --write-subs solo se pide cuando van aparte.
        if (choice.mode == SubtitleMode.SEPARATE) out += "--write-subs" to null else out += "--embed-subs" to null
        if (choice.includeAuto) out += "--write-auto-subs" to null
        out += "--sub-langs" to choice.codes.joinToString(",")
        out += "--convert-subs" to "srt"
        return out
    }

    /** El contenedor del vídeo final. */
    fun container(choice: SubtitleChoice?): String =
        if (choice != null && choice.codes.isNotEmpty() && choice.mode == SubtitleMode.MKV) "mkv" else "mp4"
}
