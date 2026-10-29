package com.quietdlp.app.data

import org.json.JSONObject

/**
 * La parte de la canción que el autor eligió en Instagram: desde dónde empieza y cuánto dura.
 * Se lee del JSON de metadatos que escribe gallery-dl (ver [GalleryDl]), donde un pequeño envoltorio añade
 * `audio_clip_start_ms` y `audio_clip_length_ms`.
 */
data class SongClip(val startMs: Long, val lengthMs: Long) {
    companion object {
        /** El trozo, o null si el JSON no lo trae (o la duración es 0). */
        fun parse(json: String): SongClip? {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            val length = o.number("audio_clip_length_ms") ?: return null
            if (length <= 0) return null
            return SongClip(startMs = maxOf(0L, o.number("audio_clip_start_ms") ?: 0L), lengthMs = length)
        }

        /** Los datos crudos del consumo de la canción, para el registro de errores cuando no se pudo leer el trozo. */
        fun rawOf(json: String): String? {
            val o = runCatching { JSONObject(json) }.getOrNull() ?: return null
            if (!o.has("audio_clip_raw") || o.isNull("audio_clip_raw")) return null
            return o.get("audio_clip_raw").toString().take(600)
        }

        // Instagram da estos valores como números, pero por si acaso también se aceptan como texto.
        private fun JSONObject.number(key: String): Long? {
            if (!has(key) || isNull(key)) return null
            return when (val v = get(key)) {
                is Number -> v.toLong()
                is String -> v.trim().toDoubleOrNull()?.toLong()
                else -> null
            }
        }
    }
}
