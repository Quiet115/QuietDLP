package com.quietdlp.app.data

object CoverOptions {

    // La portada de música usa el centro cuadrado de la miniatura.
    private const val SQUARE = "ThumbnailsConvertor:-vf \"crop='min(iw,ih)':'min(iw,ih)'\""

    // WAV queda fuera porque no admite portada.
    fun audioSupported(format: String): Boolean = format != "wav"

    fun available(kind: Kind, audioFormat: String): Boolean = when (kind) {
        Kind.VIDEO -> true
        Kind.AUDIO -> audioSupported(audioFormat)
        Kind.PHOTO -> false
    }

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
