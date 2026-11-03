package com.quietdlp.app.data

import org.json.JSONObject

/** Lo que se muestra de un enlace antes de descargarlo, leído del JSON de `yt-dlp --dump-json`. */
data class MediaProbe(
    val title: String?,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Int,
    val heights: List<Int>,
    val subtitles: List<SubtitleTrack>,
    /** Imágenes en miniatura de todo el vídeo, si el sitio las ofrece. */
    val storyboard: Storyboard? = null,
    /** Cuántos capítulos trae el vídeo. */
    val chapterCount: Int = 0,
) {
    companion object {
        /** null si la salida no trae un JSON válido. Si hay varias líneas (varios vídeos) se lee la primera. */
        fun parse(output: String): MediaProbe? {
            val root = runCatching { JSONObject(output.trim()) }.getOrNull()
                ?: output.lineSequence().map { it.trim() }.filter { it.startsWith("{") }
                    .firstNotNullOfOrNull { line -> runCatching { JSONObject(line) }.getOrNull() }
                ?: return null
            return MediaProbe(
                title = root.text("title") ?: root.text("fulltitle"),
                thumbnail = root.text("thumbnail"),
                uploader = root.text("uploader"),
                durationSec = root.optDouble("duration", 0.0).let { if (it.isNaN()) 0 else it.toInt() },
                heights = heightsOf(root),
                subtitles = SubtitleProbe.parse(root),
                storyboard = Storyboard.parse(root),
                chapterCount = root.optJSONArray("chapters")?.length() ?: 0,
            )
        }

        /** Calidad = lado corto (1080×1920 vertical es 1080p), como se acostumbra y como ordena yt-dlp. */
        private fun heightsOf(root: JSONObject): List<Int> {
            val formats = root.optJSONArray("formats") ?: return emptyList()
            val out = ArrayList<Int>()
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                if ((f.text("vcodec") ?: "none") == "none") continue
                val h = f.optInt("height", 0)
                if (h <= 0) continue
                val w = f.optInt("width", 0)
                out += if (w > 0) minOf(w, h) else h
            }
            return out.filter { it >= 144 }.distinct().sortedDescending()
        }

        private fun JSONObject.text(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
    }
}
