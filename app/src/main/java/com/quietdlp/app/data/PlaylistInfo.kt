package com.quietdlp.app.data

import org.json.JSONObject

/** Un vídeo de una lista de reproducción (o un enlace de varios pegados a la vez). */
data class PlaylistEntry(val url: String, val title: String, val thumbnail: String?)

/** Lo que contiene un enlace con varios archivos. */
data class PlaylistInfo(val title: String, val entries: List<PlaylistEntry>)

/** Lee la respuesta de `yt-dlp --flat-playlist -J` (función pura, para poder probarla). */
object PlaylistProbe {
    /** Tope de archivos que se leen de un enlace: un canal entero podría tener miles. */
    const val MAX_ENTRIES = 1000

    /**
     * La lista que hay en [json], o null si el enlace no es una lista de varios archivos distintos.
     * Una publicación con varias fotos o vídeos (carrusel) también sale como lista en yt-dlp, pero todos sus
     * elementos apuntan a la misma página: eso no es una lista de reproducción y se descarga como una sola cosa.
     */
    fun parse(json: String, sourceUrl: String): PlaylistInfo? {
        val start = json.indexOf('{')
        if (start < 0) return null
        val root = runCatching { JSONObject(json.substring(start)) }.getOrNull() ?: return null
        if (root.optString("_type") != "playlist") return null
        val arr = root.optJSONArray("entries") ?: return null

        val seen = LinkedHashSet<String>()
        val entries = ArrayList<PlaylistEntry>()
        for (n in 0 until arr.length()) {
            val o = arr.optJSONObject(n) ?: continue
            val url = listOf("url", "webpage_url").map { o.optString(it) }.firstOrNull { it.startsWith("http") } ?: continue
            if (!seen.add(url)) continue
            val title = o.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: url
            entries += PlaylistEntry(url, title, thumbnailOf(o))
        }
        if (entries.size < 2) return null
        val source = pageOf(sourceUrl)
        if (entries.all { pageOf(it.url) == source }) return null

        val title = root.optString("title").takeIf { it.isNotBlank() && it != "null" } ?: "Playlist"
        return PlaylistInfo(title, entries)
    }

    private fun thumbnailOf(o: JSONObject): String? {
        o.optString("thumbnail").takeIf { it.startsWith("http") }?.let { return it }
        val list = o.optJSONArray("thumbnails") ?: return null
        for (i in list.length() - 1 downTo 0) {
            list.optJSONObject(i)?.optString("url")?.takeIf { it.startsWith("http") }?.let { return it }
        }
        return null
    }

    /** La página sin parámetros ni barra final, para saber si dos enlaces son la misma publicación. */
    private fun pageOf(url: String): String =
        url.substringBefore('#').substringBefore('?').trimEnd('/').lowercase()
}
