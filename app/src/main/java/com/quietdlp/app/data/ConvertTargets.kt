package com.quietdlp.app.data

/**
 * Un formato al que se puede convertir un archivo ya descargado. [args] son los argumentos de ffmpeg
 * que van entre la entrada y la salida. [quality] es lo que se anota en el historial.
 */
data class ConvertTarget(
    val id: String,
    val label: String,
    val hint: String,
    val ext: String,
    val quality: Quality,
    /** Recodifica el vídeo: tarda mucho y gasta batería. */
    val slow: Boolean,
    /** El resultado es sin pérdida (FLAC, ALAC, WAV). */
    val lossless: Boolean,
    private val args: List<String>,
) {
    fun command(input: String, output: String): List<String> =
        listOf("-hide_banner", "-nostdin", "-y", "-i", input) + args + listOf("-progress", "pipe:1", "-nostats", output)
}

/** Opciones de conversión que se ofrecen para un archivo, agrupadas con su título. */
data class ConvertSection(val title: String, val targets: List<ConvertTarget>)

object ConvertTargets {
    private val LOSSLESS_MIMES = setOf("audio/flac", "audio/x-flac", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave")

    fun isAudio(mime: String?) = mime?.startsWith("audio/") == true
    fun isVideo(mime: String?) = mime?.startsWith("video/") == true

    /** ¿Se puede convertir este archivo? Solo audio y vídeo. */
    fun canConvert(mime: String?) = isAudio(mime) || isVideo(mime)

    fun isLosslessSource(mime: String?) = mime?.lowercase() in LOSSLESS_MIMES

    private val firstStreams = listOf("-map", "0:v:0", "-map", "0:a:0?")

    private fun h264(crf: Int, filter: String) = firstStreams + listOf(
        "-vf", filter, "-c:v", "libx264", "-preset", "veryfast", "-crf", "$crf", "-pix_fmt", "yuv420p",
        "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart",
    )

    // libx264 necesita ancho y alto pares.
    private const val EVEN = "scale=trunc(iw/2)*2:trunc(ih/2)*2"

    private fun video(
        id: String, label: String, hint: String, ext: String, q: Quality, slow: Boolean, args: List<String>,
    ) = ConvertTarget(id, label, hint, ext, q, slow = slow, lossless = false, args = args)

    val keepQuality: List<ConvertTarget> = listOf(
        video("v-mkv", "MKV", "No re-encode. Fast.", "mkv", Quality(Kind.VIDEO), false, firstStreams + listOf("-c", "copy")),
        video(
            "v-mp4-copy", "MP4", "No re-encode. Fast.", "mp4", Quality(Kind.VIDEO), false,
            firstStreams + listOf("-c", "copy", "-movflags", "+faststart"),
        ),
    )

    val reEncode: List<ConvertTarget> = listOf(
        video("v-mp4", "MP4 H.264", "Plays everywhere.", "mp4", Quality(Kind.VIDEO), true, h264(23, EVEN)),
        video(
            "v-webm", "WebM VP9", "Open format. Very slow.", "webm", Quality(Kind.VIDEO), true,
            firstStreams + listOf(
                "-c:v", "libvpx-vp9", "-b:v", "0", "-crf", "33", "-row-mt", "1", "-cpu-used", "4",
                "-c:a", "libopus", "-b:a", "128k",
            ),
        ),
    )

    val smaller: List<ConvertTarget> = listOf(
        video("v-720", "720p", "Smaller file.", "mp4", Quality(Kind.VIDEO, maxHeight = 720), true, h264(26, "scale=-2:min(720\\,ih),$EVEN")),
        video("v-480", "480p", "Much smaller file.", "mp4", Quality(Kind.VIDEO, maxHeight = 480), true, h264(28, "scale=-2:min(480\\,ih),$EVEN")),
    )

    val gif: List<ConvertTarget> = listOf(
        video(
            "v-gif", "GIF", "First 15 seconds, no sound.", "gif", Quality(Kind.PHOTO), true,
            listOf(
                "-map", "0:v:0", "-t", "15", "-an", "-loop", "0",
                "-vf", "fps=12,scale=min(480\\,iw):-1:flags=lanczos,split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
            ),
        ),
    )

    private fun audioTarget(o: AudioOption): ConvertTarget {
        val codec = when (o.format) {
            "flac" -> listOf("-c:a", "flac")
            "alac" -> listOf("-c:a", "alac")
            "wav" -> listOf("-c:a", "pcm_s16le")
            "m4a" -> listOf("-c:a", "aac", "-b:a", "256k")
            "mp3" -> listOf("-c:a", "libmp3lame", "-b:a", "${o.bitrate}k")
            "opus" -> listOf("-c:a", "libopus", "-b:a", "${o.bitrate}k")
            else -> error("No conversion for ${o.format}")
        }
        val ext = when (o.format) {
            "alac" -> "m4a"
            else -> o.format
        }
        val id = "a-" + o.format + if (o.bitrate > 0) "-${o.bitrate}" else ""
        return ConvertTarget(
            id = id, label = o.label, hint = o.hint, ext = ext,
            quality = Quality(Kind.AUDIO, audioFormat = o.format, audioBitrate = o.bitrate),
            slow = false, lossless = o.tier == AudioTier.LOSSLESS,
            args = listOf("-vn", "-map", "0:a:0", "-map_metadata", "0") + codec,
        )
    }

    /** Todos los destinos de audio, de mejor a peor calidad. "Original" no es una conversión. */
    val audio: List<ConvertTarget> = AudioOptions.all.filter { it.format != "best" }.map(::audioTarget)

    private val everything: List<ConvertTarget> by lazy { keepQuality + reEncode + smaller + gif + audio }

    fun byId(id: String?): ConvertTarget? = everything.firstOrNull { it.id == id }

    private fun tierOf(t: ConvertTarget): AudioTier = AudioOptions.find(t.quality.audioFormat, t.quality.audioBitrate).tier

    private fun audioSections(prefix: String): List<ConvertSection> =
        AudioTier.entries.mapNotNull { tier ->
            audio.filter { tierOf(it) == tier }.takeIf { it.isNotEmpty() }
                ?.let { ConvertSection(prefix + tier.label, it) }
        }

    /** Lo que se ofrece para un archivo según su tipo. Vacío si no se puede convertir. */
    fun sectionsFor(mime: String?): List<ConvertSection> = when {
        isVideo(mime) -> listOf(
            ConvertSection("Keep quality (fast)", keepQuality),
            ConvertSection("Re-encode (slow)", reEncode),
            ConvertSection("Smaller files (slow)", smaller),
            ConvertSection("Animated (slow)", gif),
        ) + audioSections("Audio only: ")
        isAudio(mime) -> audioSections("")
        else -> emptyList()
    }

    /** Aviso corto antes de convertir, o null si no hace falta. */
    fun warning(target: ConvertTarget, sourceMime: String?): String? {
        if (target.slow) return "Re-encoding is slow and uses battery. Keep the phone charging."
        if (target.quality.kind != Kind.AUDIO) return null
        val lossless = isLosslessSource(sourceMime)
        return when {
            target.lossless && !lossless -> "The file gets bigger. Quality doesn't improve."
            !target.lossless && !lossless -> "Converting from a lossy file loses a bit more quality."
            else -> null
        }
    }

    /** Nombre del archivo nuevo: "Título (FLAC).flac", sin caracteres que el sistema de archivos rechace. */
    fun outputName(title: String, target: ConvertTarget): String {
        val clean = title.replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), " ").replace(Regex("""\s+"""), " ").trim()
            .removeSuffix(".").trim().ifBlank { "Converted" }.take(100).trim()
        return "$clean (${target.label}).${target.ext}"
    }
}
