package com.quietdlp.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Qué descargar siempre de una web: por ejemplo, solo audio en las webs de música. */
data class SiteRule(val host: String, val quality: Quality)

/** Reglas por sitio (funciones puras, para poder probarlas). */
object SiteRules {
    const val MAX = 50

    /** "https://www.Ejemplo.com/ruta?x=1" o "ejemplo.com" → "ejemplo.com". null si no parece una dirección. */
    fun normalizeHost(input: String): String? {
        var t = input.trim().lowercase()
        if (t.isEmpty()) return null
        t = t.substringAfter("://", t).substringBefore('/').substringBefore('?').substringBefore('#')
            .substringBefore(':').removePrefix("www.").removePrefix("m.")
        val ok = t.contains('.') && !t.startsWith('.') && !t.endsWith('.') &&
            t.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return t.takeIf { ok }
    }

    /** La regla que corresponde a este enlace: el sitio exacto o uno de sus subdominios; gana la más específica. */
    fun match(rules: List<SiteRule>, url: String): SiteRule? {
        val host = normalizeHost(url) ?: return null
        return rules
            .filter { host == it.host || host.endsWith("." + it.host) }
            .maxByOrNull { it.host.length }
    }

    fun upsert(rules: List<SiteRule>, rule: SiteRule): List<SiteRule> =
        (rules.filterNot { it.host == rule.host } + rule).takeLast(MAX)

    fun remove(rules: List<SiteRule>, host: String): List<SiteRule> = rules.filterNot { it.host == host }

    fun encode(rules: List<SiteRule>): String {
        val arr = JSONArray()
        rules.take(MAX).forEach {
            arr.put(
                JSONObject().put("host", it.host).put("kind", it.quality.kind.name)
                    .put("height", it.quality.maxHeight).put("fmt", it.quality.audioFormat)
                    .put("br", it.quality.audioBitrate),
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<SiteRule> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                val host = normalizeHost(o.getString("host")) ?: return@runCatching null
                SiteRule(
                    host,
                    Quality(Kind.valueOf(o.getString("kind")), o.optInt("height", 0), o.optString("fmt", "m4a"), o.optInt("br", 0)),
                )
            }.getOrNull()
        }
    }
}
