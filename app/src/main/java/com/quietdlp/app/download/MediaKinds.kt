package com.quietdlp.app.download

import android.media.MediaMetadataRetriever
import java.io.File

/** Distingue lo que gallery-dl deja en una carpeta: fotos, la canción de la publicación u otra cosa. */
object MediaKinds {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "heic", "heif", "bmp")
    private val audioExt = setOf("m4a", "mp3", "opus", "aac", "ogg", "wav", "flac", "weba", "mka")

    fun isImage(f: File) = f.extension.lowercase() in imageExt

    /** true si el archivo solo tiene sonido, aunque su extensión diga otra cosa (Instagram usa ".mp4"). */
    fun isAudioOnly(f: File): Boolean {
        if (isImage(f)) return false
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes" &&
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != "yes"
        } catch (_: Exception) {
            f.extension.lowercase() in audioExt
        } finally {
            runCatching { r.release() }
        }
    }

    /** Un audio con extensión de vídeo (o sin extensión) pasa a ".m4a", para que se guarde como música. */
    fun asAudioFile(f: File): File {
        if (f.extension.lowercase() in audioExt) return f
        val target = File(f.parentFile, f.nameWithoutExtension.ifBlank { "audio" } + ".m4a")
        return if (f.renameTo(target)) target else f
    }

    /** Duración en milisegundos, o 0 si no se sabe. */
    fun durationMs(f: File): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            runCatching { r.release() }
        }
    }
}
