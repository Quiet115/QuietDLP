package com.quietdlp.app.data

/**
 * Lo que se descarga por defecto (con "Compartir" y con el enlace copiado).
 * [ASK] = preguntar cada vez: abre la app para elegir ese enlace.
 */
enum class DownloadMode(val key: String, val label: String, val quality: Quality?) {
    BEST_VIDEO("best_video", "Video · best", Quality(Kind.VIDEO)),
    VIDEO_1080("video_1080", "Video · 1080p", Quality(Kind.VIDEO, maxHeight = 1080)),
    VIDEO_720("video_720", "Video · 720p", Quality(Kind.VIDEO, maxHeight = 720)),
    MUSIC_BEST("music_best", "Audio · best", Quality(Kind.AUDIO, audioFormat = "m4a")),
    MUSIC_MP3("music_mp3", "Audio · MP3 128k", Quality(Kind.AUDIO, audioFormat = "mp3", audioBitrate = 128)),
    ASK("ask", "Ask each time", null);

    companion object {
        fun fromKey(key: String?): DownloadMode? = entries.firstOrNull { it.key == key }

        /**
         * Versiones anteriores guardaban un interruptor ("descarga directa") y la calidad por separado.
         * Se convierten una sola vez al nuevo modo.
         */
        fun fromLegacy(direct: Boolean, kind: Kind, maxHeight: Int, audioFormat: String): DownloadMode = when {
            !direct -> ASK
            kind == Kind.AUDIO -> if (audioFormat == "mp3") MUSIC_MP3 else MUSIC_BEST
            maxHeight in 1..720 -> VIDEO_720
            maxHeight in 721..1080 -> VIDEO_1080
            else -> BEST_VIDEO
        }
    }
}
