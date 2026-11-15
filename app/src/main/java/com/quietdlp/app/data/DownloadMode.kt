package com.quietdlp.app.data

// Este modo se aplica al compartir y a los enlaces copiados.
enum class DownloadMode(val key: String, val label: String, val quality: Quality?) {
    BEST_VIDEO("best_video", "Video · best", Quality(Kind.VIDEO)),
    VIDEO_1080("video_1080", "Video · 1080p", Quality(Kind.VIDEO, maxHeight = 1080)),
    VIDEO_720("video_720", "Video · 720p", Quality(Kind.VIDEO, maxHeight = 720)),
        // Se conservan las claves antiguas music_best y music_mp3 para leer preferencias existentes.
    AUDIO_ORIGINAL("audio_original", "Audio · Original", Quality(Kind.AUDIO, audioFormat = "best")),
    AUDIO_FLAC("audio_flac", "Audio · FLAC", Quality(Kind.AUDIO, audioFormat = "flac")),
    AUDIO_ALAC("audio_alac", "Audio · ALAC", Quality(Kind.AUDIO, audioFormat = "alac")),
    AUDIO_WAV("audio_wav", "Audio · WAV", Quality(Kind.AUDIO, audioFormat = "wav")),
    MUSIC_BEST("music_best", "Audio · M4A", Quality(Kind.AUDIO, audioFormat = "m4a")),
    AUDIO_MP3_320("audio_mp3_320", "Audio · MP3 320k", Quality(Kind.AUDIO, audioFormat = "mp3", audioBitrate = 320)),
    AUDIO_OPUS_192("audio_opus_192", "Audio · Opus 192k", Quality(Kind.AUDIO, audioFormat = "opus", audioBitrate = 192)),
    AUDIO_MP3_192("audio_mp3_192", "Audio · MP3 192k", Quality(Kind.AUDIO, audioFormat = "mp3", audioBitrate = 192)),
    AUDIO_OPUS_128("audio_opus_128", "Audio · Opus 128k", Quality(Kind.AUDIO, audioFormat = "opus", audioBitrate = 128)),
    MUSIC_MP3("music_mp3", "Audio · MP3 128k", Quality(Kind.AUDIO, audioFormat = "mp3", audioBitrate = 128)),
    AUDIO_OPUS_64("audio_opus_64", "Audio · Opus 64k", Quality(Kind.AUDIO, audioFormat = "opus", audioBitrate = 64)),
    ASK("ask", "Ask each time", null);

    val audio: AudioOption?
        get() = quality?.takeIf { it.kind == Kind.AUDIO }?.let { AudioOptions.find(it.audioFormat, it.audioBitrate) }

    companion object {
        fun fromKey(key: String?): DownloadMode? = entries.firstOrNull { it.key == key }

    // Las preferencias antiguas separaban la descarga directa de la calidad.
        fun fromLegacy(direct: Boolean, kind: Kind, maxHeight: Int, audioFormat: String): DownloadMode = when {
            !direct -> ASK
            kind == Kind.AUDIO -> if (audioFormat == "mp3") MUSIC_MP3 else MUSIC_BEST
            maxHeight in 1..720 -> VIDEO_720
            maxHeight in 721..1080 -> VIDEO_1080
            else -> BEST_VIDEO
        }
    }
}
