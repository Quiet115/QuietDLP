package com.quietdlp.app.data

enum class QualityLevel(val label: String, val videoHeight: Int, val audioFormat: String, val audioBitrate: Int) {
    HIGH("High", 0, "m4a", 0),
    MEDIUM("Medium", 720, "mp3", 192),
    LOW("Low", 480, "mp3", 128);

    fun quality(kind: Kind): Quality =
        if (kind == Kind.AUDIO) Quality(Kind.AUDIO, 0, audioFormat, audioBitrate) else Quality(Kind.VIDEO, videoHeight)

    companion object {
    // Aproxima una elección detallada a uno de los niveles del modo simple.
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
