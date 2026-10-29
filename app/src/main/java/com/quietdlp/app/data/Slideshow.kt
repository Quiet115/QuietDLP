package com.quietdlp.app.data

import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Presentación de fotos con música: varias imágenes seguidas, cada una unos segundos, con el audio por debajo. */
object Slideshow {
    /** Más fotos que estas no entran en el vídeo (un perfil entero daría un vídeo interminable). */
    const val MAX_IMAGES = 30

    private const val MIN_SECONDS = 3.0
    private const val MAX_SECONDS = 8.0
    private const val DEFAULT_SECONDS = 4.0
    private const val SHORT_SIDE = 1080
    private const val LONG_SIDE = 1920

    /** Segundos por foto: lo que reparta la canción entre las fotos, entre 3 y 8. */
    fun secondsPerImage(audioMs: Long, count: Int): Double =
        if (audioMs <= 0 || count <= 0) DEFAULT_SECONDS else (audioMs / 1000.0 / count).coerceIn(MIN_SECONDS, MAX_SECONDS)

    fun totalMs(count: Int, secondsPerImage: Double): Long = (count * secondsPerImage * 1000).toLong()

    /** Tamaño del vídeo según la primera foto: sin ampliar, lado corto hasta 1080 y largo hasta 1920, siempre par. */
    fun canvas(width: Int, height: Int): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1080 to 1080
        val shortSide = min(width, height)
        val longSide = maxOf(width, height)
        val scale = minOf(1.0, SHORT_SIDE.toDouble() / shortSide, LONG_SIDE.toDouble() / longSide)
        fun even(v: Double) = maxOf(2, (v.roundToInt() / 2) * 2)
        return even(width * scale) to even(height * scale)
    }

    /** Nombre del vídeo: "Título (video).mp4". Quita el " (+3 more)" que se añade a las descargas con varios archivos. */
    fun outputName(title: String): String {
        val base = title.replace(Regex("""\s*\(\+\d+ more\)\s*$"""), "")
        return ConvertTargets.outputName(base, VIDEO_NAME_TARGET)
    }

    private val VIDEO_NAME_TARGET = ConvertTarget(
        id = "slideshow", label = "video", hint = "", ext = "mp4", quality = Quality(Kind.VIDEO),
        slow = true, lossless = false, args = emptyList(),
    )

    /** Comando de ffmpeg: las fotos en orden (cada una [secondsPerImage] segundos) y el audio, hasta que acabe lo más corto. */
    fun command(images: List<String>, audio: String, output: String, width: Int, height: Int, secondsPerImage: Double): List<String> {
        require(images.isNotEmpty()) { "No images" }
        val sec = String.format(Locale.US, "%.3f", secondsPerImage)
        val fit = "scale=$width:$height:force_original_aspect_ratio=decrease," +
            "pad=$width:$height:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,format=yuv420p"
        val filter = images.indices.joinToString(";") { "[$it:v]$fit[v$it]" } + ";" +
            images.indices.joinToString("") { "[v$it]" } + "concat=n=${images.size}:v=1:a=0[v]"
        return listOf("-hide_banner", "-nostdin", "-y") +
            images.flatMap { listOf("-loop", "1", "-framerate", "25", "-t", sec, "-i", it) } +
            listOf("-i", audio, "-filter_complex", filter, "-map", "[v]", "-map", "${images.size}:a:0") +
            listOf(
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart",
                "-progress", "pipe:1", "-nostats", output,
            )
    }
}
