package com.quietdlp.app.data

/**
 * Las tres calidades del modo simple, iguales para vídeo y para audio.
 * Vídeo: alta = la mejor que haya (0 = sin tope), media = 720p, baja = 480p.
 * Audio: los tres formatos más compatibles: alta = M4A (AAC, se copia tal cual si el origen ya lo es),
 * media = MP3 192k, baja = MP3 128k.
 */
enum class QualityLevel(val label: String, val videoHeight: Int, val audioFormat: String, val audioBitrate: Int) {
    HIGH("High", 0, "m4a", 0),
    MEDIUM("Medium", 720, "mp3", 192),
    LOW("Low", 480, "mp3", 128);

    companion object {
        /** La calidad que más se parece a una elección detallada (la del modo avanzado o la de un modo por defecto). */
        fun fromQuality(q: Quality): QualityLevel = when (q.kind) {
            Kind.AUDIO -> when (AudioOptions.find(q.audioFormat, q.audioBitrate).tier) {
                AudioTier.STANDARD -> MEDIUM
                AudioTier.SMALL -> LOW
                else -> HIGH
            }
            Kind.VIDEO -> when (q.maxHeight) {
                in 1..480 -> LOW
                in 481..720 -> MEDIUM
                else -> HIGH
            }
            Kind.PHOTO -> HIGH
        }
    }
}
