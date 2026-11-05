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

/** Fotos que se bajan de un enlace con varias si el usuario no elige otra cantidad. */
const val DEFAULT_PHOTO_LIMIT = 100

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
    /** Las descargas que salen del mismo enlace con varios archivos (lista, varios enlaces) comparten [groupId]. */
    val groupId: String? = null,
    /** Posición dentro del grupo (0 = el primero): los archivos se guardan en este orden. */
    val groupIndex: Int = 0,
    val groupTitle: String? = null,
    /** Esperar a tener Wi-Fi (una red sin tarifa) antes de empezar. */
    val wifiOnly: Boolean = false,
    /** Subtítulos pedidos para este vídeo (null = ninguno). */
    val subs: SubtitleChoice? = null,
    /** Solo se baja este trozo (null = el archivo entero). */
    val trim: TrimRange? = null,
    /** Con la miniatura metida dentro del archivo como portada. */
    val cover: Boolean = false,
    /** SponsorBlock: qué hacer con las partes marcadas por la gente (null = nada). */
    val sponsor: SponsorChoice? = null,
    /** Qué hacer con los capítulos del vídeo. */
    val chapters: ChapterMode = ChapterMode.OFF,
    /** La página seguida de la que salió (null = la pegó el usuario). */
    val followId: String? = null,
    /** Llegó sola de una página seguida y el usuario aún no la ha visto. */
    val fresh: Boolean = false,
    /** Cuántas fotos se bajan de un enlace con varias (0 = todas). */
    val photoLimit: Int = DEFAULT_PHOTO_LIMIT,
) {
    val savedUri: String? get() = savedUris.firstOrNull()

    /** true si es una conversión de un archivo ya guardado y no una descarga de un enlace. */
    val isConversion: Boolean get() = url.startsWith(CONVERT_URL_PREFIX)

    val isActive: Boolean
        get() = state == State.QUEUED || state == State.PREPARING ||
            state == State.DOWNLOADING || state == State.PROCESSING
}
