package com.quietdlp.app.data

// Las fotos y galerías usan gallery-dl; el vídeo y el audio usan yt-dlp.
enum class Kind { VIDEO, AUDIO, PHOTO }

enum class State { QUEUED, PREPARING, DOWNLOADING, PROCESSING, DONE, FAILED, CANCELED }

// maxHeight 0 pide la mejor calidad disponible. audioFormat solo se usa para audio.
data class Quality(
    val kind: Kind,
    val maxHeight: Int = 0,
    val audioFormat: String = "m4a",
    // Bitrate en kbps para audio con pérdida; 0 usa el valor del codificador.
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

    // La etiqueta usa el lado corto, también en vídeos verticales.
fun resolutionName(p: Int): String = when (p) {
    4320 -> "4320p · 8K"
    2160 -> "2160p · 4K"
    1440 -> "1440p · 2K"
    1080 -> "1080p · Full HD"
    720 -> "720p · HD"
    else -> "${p}p"
}

const val DEFAULT_PHOTO_LIMIT = 100

// Las conversiones usan este prefijo en lugar de un enlace de descarga.
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
    // Una publicación puede guardar varios archivos.
    val savedUris: List<String> = emptyList(),
    val mime: String? = null,
    // Dominio donde se requiere iniciar sesión tras un fallo de autenticación.
    val loginHost: String? = null,
    // Las fotos con canción permiten crear un vídeo.
    val photoVideo: Boolean = false,
    val groupId: String? = null,
    // El índice empieza en 0 y determina el orden de guardado.
    val groupIndex: Int = 0,
    val groupTitle: String? = null,
    // Wi-Fi se interpreta como una red sin tarifa.
    val wifiOnly: Boolean = false,
    val subs: SubtitleChoice? = null,
    // null descarga el archivo entero.
    val trim: TrimRange? = null,
    val cover: Boolean = false,
    val sponsor: SponsorChoice? = null,
    val chapters: ChapterMode = ChapterMode.OFF,
    // null indica que el enlace lo añadió el usuario.
    val followId: String? = null,
    // Novedad de una página seguida que el usuario aún no ha visto.
    val fresh: Boolean = false,
    // 0 descarga todas las fotos.
    val photoLimit: Int = DEFAULT_PHOTO_LIMIT,
) {
    val savedUri: String? get() = savedUris.firstOrNull()

    val isConversion: Boolean get() = url.startsWith(CONVERT_URL_PREFIX)

    val isActive: Boolean
        get() = state == State.QUEUED || state == State.PREPARING ||
            state == State.DOWNLOADING || state == State.PROCESSING
}
