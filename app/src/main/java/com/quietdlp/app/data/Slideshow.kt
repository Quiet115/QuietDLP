package com.quietdlp.app.data

import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Presentación de fotos con música: varias imágenes seguidas, cada una unos segundos, con el audio por debajo. */
object Slideshow {
    /** Más fotos que estas no entran en el vídeo (un perfil entero daría un vídeo interminable). */
    const val MAX_IMAGES = 30

    /** Valor de "hasta cuándo suena la canción" que significa "el trozo que eligió el autor en la publicación". */
    const val AS_POSTED = -1

    /** Hasta cuándo puede sonar la canción: segundos (0 = entera, [AS_POSTED] = como se publicó) y su nombre. */
    val SONG_LENGTHS: List<Pair<Int, String>> =
        listOf(AS_POSTED to "As posted", 0 to "Whole song", 45 to "45 s", 30 to "30 s", 15 to "15 s")

    /** Qué parte del archivo de audio suena: desde [startMs], con [availableMs] por delante y como mucho [maxSeconds]. */
    class AudioPlan(val startMs: Long, val availableMs: Long, val maxSeconds: Int)

    /**
     * Resuelve la elección del usuario. "Como se publicó" usa el trozo del autor si se pudo leer; si no, la canción
     * entera. Si el trozo empieza más allá del final del archivo, se ignora el inicio.
     */
    fun audioPlan(audioMs: Long, choice: Int, clipStartMs: Long, clipLengthMs: Long): AudioPlan {
        if (choice == AS_POSTED && clipLengthMs > 0 && audioMs > 1000) {
            val start = clipStartMs.coerceIn(0L, audioMs - 1000)
            val length = min(clipLengthMs, audioMs - start)
            return AudioPlan(start, audioMs - start, Math.ceil(length / 1000.0).toInt())
        }
        return AudioPlan(0L, audioMs, if (choice > 0) choice else 0)
    }

    private const val MIN_SECONDS = 3.0
    private const val MAX_TOTAL_SECONDS = 90.0
    private const val DEFAULT_SECONDS = 4.0
    private const val SHORT_SIDE = 1080
    private const val LONG_SIDE = 1920

    /**
     * Segundos por foto: la canción repartida entre las fotos, con un mínimo de 3. Así el vídeo dura lo que la
     * canción (hasta 90 segundos); si las fotos piden más tiempo que la canción, esta se repite.
     */
    fun secondsPerImage(audioMs: Long, count: Int, maxSeconds: Int = 0): Double {
        if (audioMs <= 0 || count <= 0) return DEFAULT_SECONDS
        var total = min(audioMs / 1000.0, MAX_TOTAL_SECONDS)
        // El usuario puede pedir menos (0 = la canción entera).
        if (maxSeconds > 0) total = min(total, maxSeconds.toDouble())
        return maxOf(MIN_SECONDS, total / count)
    }

    /** ¿Termina el vídeo antes que la canción? Entonces el final se apaga poco a poco. */
    fun cutsSong(audioMs: Long, videoMs: Long): Boolean = audioMs > videoMs + 500

    private const val FADE_SECONDS = 2.0

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

    /** Comando de ffmpeg: las fotos en orden (cada una [secondsPerImage] segundos) y el audio, que se repite si hace falta. */
    fun command(
        images: List<String>, audio: String, output: String, width: Int, height: Int, secondsPerImage: Double,
        fadeOut: Boolean = false, audioStartSec: Double = 0.0,
    ): List<String> {
        require(images.isNotEmpty()) { "No images" }
        val sec = String.format(Locale.US, "%.3f", secondsPerImage)
        val fadeStart = String.format(Locale.US, "%.3f", maxOf(0.0, images.size * secondsPerImage - FADE_SECONDS))
        val audioStart = if (audioStartSec > 0) listOf("-ss", String.format(Locale.US, "%.3f", audioStartSec)) else emptyList()
        val fade = if (fadeOut) listOf("-af", "afade=t=out:st=$fadeStart:d=${FADE_SECONDS.toInt()}") else emptyList()
        val fit = "scale=$width:$height:force_original_aspect_ratio=decrease," +
            "pad=$width:$height:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,format=yuv420p"
        val filter = images.indices.joinToString(";") { "[$it:v]$fit[v$it]" } + ";" +
            images.indices.joinToString("") { "[v$it]" } + "concat=n=${images.size}:v=1:a=0[v]"
        return listOf("-hide_banner", "-nostdin", "-y") +
            images.flatMap { listOf("-loop", "1", "-framerate", "25", "-t", sec, "-i", it) } +
            listOf("-stream_loop", "-1") + audioStart + listOf("-i", audio, "-filter_complex", filter, "-map", "[v]", "-map", "${images.size}:a:0") + fade +
            listOf(
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "192k", "-shortest", "-movflags", "+faststart",
                "-progress", "pipe:1", "-nostats", output,
            )
    }
}
