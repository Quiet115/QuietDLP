package com.quietdlp.app.data

/** PHOTO = fotos y galerías (las descarga gallery-dl; yt-dlp solo hace vídeo y audio). */
enum class Kind { VIDEO, AUDIO, PHOTO }

enum class State { QUEUED, PREPARING, DOWNLOADING, PROCESSING, DONE, FAILED, CANCELED }

/**
 * Calidad pedida. [maxHeight] = 0 significa "la mejor disponible".
 * [audioFormat] solo se usa cuando [kind] es AUDIO (ver [AudioOptions]).
 */
data class Quality(
    val kind: Kind,
    val maxHeight: Int = 0,
    val audioFormat: String = "m4a",
    /** Bitrate en kbps para audio recodificado con pérdida (MP3, Opus). 0 = lo que dé el codificador por defecto. */
    val audioBitrate: Int = 0,
) {
    fun label(): String = when (kind) {
        Kind.AUDIO ->
            if (audioFormat == "best") "Audio (original)"
            else "Audio ${audioFormat.uppercase()}" + if (audioBitrate > 0) " ${audioBitrate}k" else ""
        Kind.PHOTO -> "Photos"
        Kind.VIDEO -> if (maxHeight > 0) "Video ${maxHeight}p" else "Video (best quality)"
    }
}

/**
 * Nombre de calidad como lo entiende la gente (el del lado corto del vídeo):
 * un vídeo vertical de 1080×1920 es "1080p", no "1920p".
 */
fun resolutionName(p: Int): String = when (p) {
    4320 -> "4320p · 8K"
    2160 -> "2160p · 4K"
    1440 -> "1440p · 2K"
    1080 -> "1080p · Full HD"
    720 -> "720p · HD"
    else -> "${p}p"
}

/** Las conversiones de archivos ya descargados se anotan en el historial con una "url" que empieza así. */
const val CONVERT_URL_PREFIX = "quietdlp-convert:"

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val quality: Quality,
    val state: State = State.QUEUED,
    val progress: Float = 0f,
    val etaSeconds: Long = 0,
    val message: String? = null,
    /** Todos los archivos guardados (una publicación puede traer varios). */
    val savedUris: List<String> = emptyList(),
    val mime: String? = null,
    /** Dominio donde hay que iniciar sesión, si la descarga falló por eso (p. ej. "ejemplo.com"). */
    val loginHost: String? = null,
    /** Una publicación de fotos que trajo su canción: se puede hacer un vídeo con las dos cosas. */
    val photoVideo: Boolean = false,
    /** El trozo de la canción que eligió el autor (dónde empieza y cuánto dura); 0 si no se pudo leer. */
    val clipStartMs: Long = 0L,
    val clipLengthMs: Long = 0L,
) {
    val savedUri: String? get() = savedUris.firstOrNull()

    /** true si es una conversión de un archivo ya guardado y no una descarga de un enlace. */
    val isConversion: Boolean get() = url.startsWith(CONVERT_URL_PREFIX)

    val isActive: Boolean
        get() = state == State.QUEUED || state == State.PREPARING ||
            state == State.DOWNLOADING || state == State.PROCESSING
}
