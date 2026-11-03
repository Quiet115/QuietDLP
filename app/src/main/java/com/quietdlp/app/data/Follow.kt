package com.quietdlp.app.data

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** Qué programa lista la página: yt-dlp (vídeo y audio) o gallery-dl (fotos y galerías). */
enum class FollowEngine { YTDLP, GALLERY }

/**
 * Una página que se sigue. Lo ya visto no se guarda aquí (puede ser muchísimo): vive en un archivo aparte por página.
 * [pending] son las novedades que aún no se han bajado (todas si no se descarga solo, o las que Android no dejó bajar en segundo plano).
 */
data class FollowedPage(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val engine: FollowEngine = FollowEngine.YTDLP,
    val quality: Quality = Quality(Kind.VIDEO),
    /** true = lo nuevo se baja solo; false = solo avisa. */
    val auto: Boolean = true,
    /** Cada cuántos días se revisa (0.25 = cada 6 horas). */
    val days: Double = FollowInterval.DEFAULT_DAYS,
    val lastCheck: Long = 0L,
    /** Por qué falló la última revisión (null = fue bien). */
    val lastError: String? = null,
    /** Para numerar los archivos del grupo de esta página (los nuevos van arriba). */
    val counter: Int = 0,
    val pending: List<PlaylistEntry> = emptyList(),
) {
    val groupId: String get() = "follow-$id"
}

/** El tiempo entre revisiones (funciones puras, para poder probarlas). */
object FollowInterval {
    const val DEFAULT_DAYS = 1.0

    /** Android no deja tareas periódicas de menos de 15 minutos. */
    const val MIN_DAYS = 0.02

    private const val DAY_MS = 24 * 60 * 60 * 1000L

    /**
     * De 0 a 1 día se permiten decimales (0.25 = 6 horas); de 1 en adelante, días enteros, sin tope.
     * null si el texto no es un número.
     */
    fun parse(text: String): Double? {
        val v = text.trim().replace(',', '.').toDoubleOrNull() ?: return null
        if (v.isNaN() || v.isInfinite()) return null
        return clamp(v)
    }

    fun clamp(v: Double): Double = when {
        v < MIN_DAYS -> MIN_DAYS
        v < 1.0 -> (v * 100).roundToInt() / 100.0
        else -> v.roundToInt().coerceAtMost(3650).toDouble()
    }

    fun toMs(days: Double): Long = (clamp(days) * DAY_MS).toLong()

    /** "1 day", "3 days", "6 h", "30 min". */
    fun label(days: Double): String {
        val d = clamp(days)
        if (d >= 1.0) {
            val n = d.roundToInt()
            return if (n == 1) "1 day" else "$n days"
        }
        val minutes = (d * 24 * 60).roundToInt()
        return if (minutes >= 60) "${(minutes / 60.0).roundToInt()} h" else "$minutes min"
    }

    /** El texto para el cuadro de edición: "1", "0.25", "7". */
    fun text(days: Double): String {
        val d = clamp(days)
        return if (d >= 1.0) d.roundToInt().toString() else d.toString().trimEnd('0').trimEnd('.')
    }

    fun isDue(page: FollowedPage, now: Long): Boolean = now - page.lastCheck >= toMs(page.days)

    /** Cada cuánto despierta la tarea de Android: la revisión más frecuente, entre 15 minutos y 1 día. */
    fun jobPeriodMs(pages: List<FollowedPage>): Long {
        val shortest = pages.minOfOrNull { toMs(it.days) } ?: DAY_MS
        return shortest.coerceIn(15 * 60 * 1000L, DAY_MS)
    }
}

/** Ajusta el enlace de lo que se sigue (funciones puras, para poder probarlas). */
object FollowUrl {
    private val CHANNEL_ROOT = Regex("""^/(@[^/]+|channel/[^/]+|c/[^/]+|user/[^/]+)/?$""")

    /**
     * Un canal de YouTube sin pestaña ("youtube.com/@canal") da la lista de pestañas (Videos, Shorts, En vivo) y no la de
     * vídeos: se sigue la pestaña de vídeos. Cualquier otro enlace se deja como está.
     */
    fun normalize(url: String): String {
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return url
        val host = u.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return url
        if (host != "youtube.com") return url
        val path = u.rawPath ?: return url
        if (!CHANNEL_ROOT.matches(path)) return url
        return "https://www.youtube.com" + path.trimEnd('/') + "/videos"
    }

    /** "Canal - Videos" (el título de la pestaña de YouTube) → "Canal". */
    fun cleanTitle(title: String): String {
        val t = title.trim()
        for (tab in listOf(" - Videos", " - Shorts", " - Live", " - Streams")) if (t.endsWith(tab) && t.length > tab.length) return t.removeSuffix(tab)
        return t
    }

    /** Un nombre para una galería a partir del enlace: "sitio - etiqueta o última parte de la ruta". */
    fun galleryTitle(url: String): String {
        val u = runCatching { java.net.URI(url.trim()) }.getOrNull() ?: return url.take(40)
        val host = u.host?.removePrefix("www.") ?: return url.take(40)
        val tags = u.rawQuery?.split('&')?.firstOrNull { it.startsWith("tags=") }?.removePrefix("tags=")
        // Un número suelto al final es la página del listado, no el nombre.
        val last = u.rawPath?.split('/')?.lastOrNull { it.isNotBlank() && !it.all(Char::isDigit) && !it.endsWith(".php") }
        val part = (tags ?: last)?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            ?.replace('_', ' ')?.replace('+', ' ')?.trim()?.take(50)
        return if (part.isNullOrBlank()) host else "$host - $part"
    }
}

/** Qué hay de nuevo en una lista (funciones puras, para poder probarlas). */
object FollowDiff {
    /** Las entradas de [listed] cuyo enlace no está en [seen], en el mismo orden y sin repetir. */
    fun fresh(listed: List<PlaylistEntry>, seen: Set<String>): List<PlaylistEntry> {
        val out = LinkedHashMap<String, PlaylistEntry>()
        for (e in listed) if (e.url !in seen && e.url !in out) out[e.url] = e
        return out.values.toList()
    }
}

/** Las páginas seguidas, guardadas como texto. */
object FollowJson {
    fun encode(pages: List<FollowedPage>): String {
        val arr = JSONArray()
        pages.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id).put("url", p.url).put("title", p.title).put("thumb", p.thumbnail)
                    .put("engine", p.engine.name)
                    .put("kind", p.quality.kind.name).put("height", p.quality.maxHeight)
                    .put("audioFmt", p.quality.audioFormat).put("audioBr", p.quality.audioBitrate)
                    .put("auto", p.auto).put("days", p.days)
                    .put("last", p.lastCheck).put("error", p.lastError).put("counter", p.counter)
                    .put(
                        "pending",
                        JSONArray().also { a ->
                            p.pending.forEach { e ->
                                a.put(JSONObject().put("u", e.url).put("t", e.title).put("i", e.thumbnail))
                            }
                        },
                    ),
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<FollowedPage> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { n ->
            runCatching {
                val o = arr.getJSONObject(n)
                val pend = o.optJSONArray("pending")
                FollowedPage(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    title = o.optString("title").ifBlank { o.getString("url") },
                    thumbnail = o.optString("thumb").takeIf { it.startsWith("http") },
                    engine = runCatching { FollowEngine.valueOf(o.optString("engine")) }.getOrDefault(FollowEngine.YTDLP),
                    quality = Quality(
                        kind = runCatching { Kind.valueOf(o.optString("kind")) }.getOrDefault(Kind.VIDEO),
                        maxHeight = o.optInt("height", 0),
                        audioFormat = o.optString("audioFmt", "m4a"),
                        audioBitrate = o.optInt("audioBr", 0),
                    ),
                    auto = o.optBoolean("auto", true),
                    days = FollowInterval.clamp(o.optDouble("days", FollowInterval.DEFAULT_DAYS)),
                    lastCheck = o.optLong("last", 0L),
                    lastError = o.optString("error").takeIf { it.isNotBlank() && it != "null" },
                    counter = o.optInt("counter", 0),
                    pending = (0 until (pend?.length() ?: 0)).mapNotNull { k ->
                        val e = pend!!.optJSONObject(k) ?: return@mapNotNull null
                        val u = e.optString("u").takeIf { it.startsWith("http") } ?: return@mapNotNull null
                        PlaylistEntry(u, e.optString("t").ifBlank { u }, e.optString("i").takeIf { it.startsWith("http") })
                    },
                )
            }.getOrNull()
        }
    }
}
