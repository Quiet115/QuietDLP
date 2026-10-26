package com.quietdlp.app.data

/** PHOTO = fotos y galerías (las descarga gallery-dl; yt-dlp solo hace vídeo y audio). */
enum class Kind { VIDEO, AUDIO, PHOTO }

enum class State { QUEUED, PREPARING, DOWNLOADING, PROCESSING, DONE, FAILED, CANCELED }

/**
 * Calidad pedida. [maxHeight] = 0 significa "la mejor disponible".
 * [audioFormat] solo se usa cuando [kind] es AUDIO ("m4a" o "mp3").
 */
data class Quality(
    val kind: Kind,
    val maxHeight: Int = 0,
    val audioFormat: String = "m4a",
    /** Tope de bitrate en kbps para audio recodificado (MP3). 0 = la mejor calidad. */
    val audioBitrate: Int = 0,
) {
    fun label(): String = when (kind) {
        Kind.AUDIO -> "Audio ${audioFormat.uppercase()}" + if (audioBitrate > 0) " ${audioBitrate}k" else ""
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
) {
    val savedUri: String? get() = savedUris.firstOrNull()

    val isActive: Boolean
        get() = state == State.QUEUED || state == State.PREPARING ||
            state == State.DOWNLOADING || state == State.PROCESSING
}
