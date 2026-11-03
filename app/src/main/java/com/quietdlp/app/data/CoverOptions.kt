package com.quietdlp.app.data

/** Portada: la miniatura del vídeo metida dentro del archivo (la que ven los reproductores de música). */
object CoverOptions {

    /** Una portada de música es cuadrada: se toma el centro de la miniatura, que suele ser 16:9. */
    private const val SQUARE = "ThumbnailsConvertor:-vf \"crop='min(iw,ih)':'min(iw,ih)'\""

    /** WAV no puede llevar imagen. Los demás formatos de audio sí (con mutagen, que ya va dentro de la app). */
    fun audioSupported(format: String): Boolean = format != "wav"

    /** Si se puede ofrecer la portada para este tipo de descarga. */
    fun available(kind: Kind, audioFormat: String): Boolean = when (kind) {
        Kind.VIDEO -> true
        Kind.AUDIO -> audioSupported(audioFormat)
        Kind.PHOTO -> false
    }

    /** Las opciones de yt-dlp. Vacío si no hace falta o no se puede. */
    fun ytDlpOptions(kind: Kind, audioFormat: String): List<Pair<String, String?>> {
        if (!available(kind, audioFormat)) return emptyList()
        val out = ArrayList<Pair<String, String?>>()
        out += "--embed-thumbnail" to null
        // Muchos sitios dan la miniatura en WebP, que casi ningún reproductor entiende dentro de un archivo.
        out += "--convert-thumbnails" to "jpg"
        if (kind == Kind.AUDIO) out += "--ppa" to SQUARE
        return out
    }
}
