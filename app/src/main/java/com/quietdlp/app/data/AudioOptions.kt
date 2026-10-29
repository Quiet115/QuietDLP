package com.quietdlp.app.data

/** Grupos de calidad, de mejor a peor. El orden del enum es el orden en pantalla. */
enum class AudioTier(val label: String) {
    ORIGINAL("Original"),
    LOSSLESS("Lossless"),
    HIGH("High"),
    STANDARD("Standard"),
    SMALL("Small"),
}

/**
 * Una forma de guardar el audio. [format] es el que entiende yt-dlp (`--audio-format`); "best" deja el audio como viene.
 * [bitrate] en kbps solo cuenta en formatos con pérdida; 0 = lo que dé el codificador por defecto.
 */
data class AudioOption(
    val format: String,
    val bitrate: Int,
    val tier: AudioTier,
    val label: String,
    val hint: String,
)

object AudioOptions {
    /** Todas las opciones, de la mejor calidad a la más pequeña. */
    val all: List<AudioOption> = listOf(
        AudioOption("best", 0, AudioTier.ORIGINAL, "Original", "Best stream, no conversion"),
        AudioOption("flac", 0, AudioTier.LOSSLESS, "FLAC", "Lossless, large. Can't improve a lossy source."),
        AudioOption("alac", 0, AudioTier.LOSSLESS, "ALAC", "Lossless in M4A, large. Can't improve a lossy source."),
        AudioOption("wav", 0, AudioTier.LOSSLESS, "WAV", "Uncompressed, very large."),
        AudioOption("m4a", 0, AudioTier.HIGH, "M4A", "AAC. Plays everywhere."),
        AudioOption("mp3", 320, AudioTier.HIGH, "MP3 320k", "Most compatible."),
        AudioOption("opus", 192, AudioTier.HIGH, "Opus 192k", "Great quality, small files."),
        AudioOption("mp3", 192, AudioTier.STANDARD, "MP3 192k", "Good for most music."),
        AudioOption("opus", 128, AudioTier.STANDARD, "Opus 128k", "Good quality, small files."),
        AudioOption("mp3", 128, AudioTier.SMALL, "MP3 128k", "Smallest MP3 worth keeping."),
        AudioOption("opus", 64, AudioTier.SMALL, "Opus 64k", "Voice and podcasts."),
    )

    val default: AudioOption = all.first { it.format == "m4a" }

    /** Opciones agrupadas por calidad, en orden, sin grupos vacíos. */
    fun byTier(): List<Pair<AudioTier, List<AudioOption>>> =
        AudioTier.entries.map { t -> t to all.filter { it.tier == t } }.filter { it.second.isNotEmpty() }

    /**
     * La opción que corresponde a un formato y bitrate guardados. Si no existe tal cual (por ejemplo "mp3" sin bitrate,
     * de versiones anteriores) se usa la primera del mismo formato, o la de por defecto.
     */
    fun find(format: String?, bitrate: Int): AudioOption =
        all.firstOrNull { it.format == format && it.bitrate == bitrate }
            ?: all.firstOrNull { it.format == format }
            ?: default

    /** Opciones de yt-dlp para sacar el audio. Un valor `null` es una opción sin argumento. */
    fun ytDlpOptions(format: String, bitrate: Int): List<Pair<String, String?>> = listOf(
        // Con M4A conviene un audio que ya sea m4a, para no recodificar y perder calidad.
        "-f" to if (format == "m4a") "ba[ext=m4a]/ba/b" else "ba/b",
        "-x" to null,
        "--audio-format" to format,
        "--audio-quality" to if (bitrate > 0) "${bitrate}K" else "0",
        "--embed-metadata" to null,
    )
}
