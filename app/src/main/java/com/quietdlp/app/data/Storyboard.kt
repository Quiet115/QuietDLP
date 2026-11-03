package com.quietdlp.app.data

import org.json.JSONObject
import kotlin.math.ceil
import kotlin.math.floor

/** Una imagen que trae varios fotogramas en cuadrícula (en total, [Storyboard.columns] × [Storyboard.rows]). */
data class StoryboardFragment(val url: String, val durationSec: Double)

/** Dónde está un fotograma: en qué imagen ([fragment]) y en qué casilla de su cuadrícula. */
data class FrameRef(val fragment: Int, val col: Int, val row: Int)

/**
 * Las imágenes en miniatura de todo el vídeo que ofrecen algunos sitios (YouTube las llama "storyboards"): uno cada
 * pocos segundos, sin bajar el vídeo. Sirven para ver qué parte se está recortando.
 */
data class Storyboard(
    val width: Int,
    val height: Int,
    val columns: Int,
    val rows: Int,
    val fragments: List<StoryboardFragment>,
) {
    private val perFragment: Int get() = columns * rows

    /** Segundos entre un fotograma y el siguiente (se saca de la primera imagen, que siempre está llena). */
    val interval: Double get() = fragments.firstOrNull()?.let { it.durationSec / perFragment } ?: 0.0

    /** El fotograma del segundo [sec] de un vídeo que dura [videoDurationSec]. null si los datos no sirven. */
    fun locate(sec: Double, videoDurationSec: Int): FrameRef? {
        val step = interval
        if (fragments.isEmpty() || perFragment <= 0 || step <= 0.0 || videoDurationSec <= 0) return null
        // La última imagen suele traer casillas vacías: no se pasa del final del vídeo.
        val total = minOf(fragments.size * perFragment, ceil(videoDurationSec / step).toInt()).coerceAtLeast(1)
        val index = floor(sec / step).toInt().coerceIn(0, total - 1)
        val inFragment = index % perFragment
        return FrameRef(index / perFragment, inFragment % columns, inFragment / columns)
    }

    companion object {
        /** La mejor (la más grande) de las que trae el JSON de yt-dlp, o null si el sitio no ofrece ninguna. */
        fun parse(root: JSONObject): Storyboard? {
            val formats = root.optJSONArray("formats") ?: return null
            var best: Storyboard? = null
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                if (f.optString("format_id").startsWith("sb").not()) continue
                val list = f.optJSONArray("fragments") ?: continue
                val fragments = (0 until list.length()).mapNotNull { n ->
                    val o = list.optJSONObject(n) ?: return@mapNotNull null
                    val url = o.optString("url")
                    // Solo imágenes por https: no se sigue ninguna otra cosa que venga en el JSON.
                    if (!url.startsWith("https://")) null else StoryboardFragment(url, o.optDouble("duration", 0.0))
                }
                val sb = Storyboard(
                    width = f.optInt("width", 0),
                    height = f.optInt("height", 0),
                    columns = f.optInt("columns", 0),
                    rows = f.optInt("rows", 0),
                    fragments = fragments,
                )
                if (sb.width <= 0 || sb.height <= 0 || sb.columns <= 0 || sb.rows <= 0 || sb.fragments.isEmpty()) continue
                if (sb.interval <= 0.0) continue
                if (best == null || sb.width > best.width) best = sb
            }
            return best
        }
    }
}
