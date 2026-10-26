package com.quietdlp.app.download

import android.content.Context
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.Duplicates
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.State
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Memoria de lo ya descargado que NO se borra con el historial: una huella por enlace y los archivos guardados.
 * Así, al volver a bajar algo de hace meses, la app avisa sin tener que revisar nada lento: es una búsqueda
 * en memoria y, solo si hay coincidencia, se comprueba que el archivo siga en el móvil.
 */
object DownloadIndex {
    enum class Dup { NONE, ACTIVE, DONE }

    private const val MAX_ENTRIES = 20_000
    private val map = LinkedHashMap<String, List<String>>()
    private var loaded = false

    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "downloaded.idx")

    @Synchronized
    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        val f = file(ctx)
        if (!f.exists()) return
        runCatching {
            f.forEachLine { line ->
                val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEachLine
                val arr = o.optJSONArray("u") ?: return@forEachLine
                map[o.getString("h")] = (0 until arr.length()).map { arr.getString(it) }
            }
        }
    }

    /** Primera vez (o tras actualizar desde una versión sin índice): se llena con lo que ya hay en el historial. */
    @Synchronized
    fun seed(ctx: Context, items: List<DownloadItem>) {
        if (file(ctx).exists()) return
        ensureLoaded(ctx)
        val done = items.filter { it.state == State.DONE && it.savedUris.isNotEmpty() }.reversed()
        done.forEach { map[Duplicates.indexKey(it.url, it.quality.kind)] = it.savedUris }
        rewrite(ctx)
    }

    @Synchronized
    fun record(ctx: Context, url: String, kind: Kind, uris: List<String>) {
        ensureLoaded(ctx)
        val h = Duplicates.indexKey(url, kind)
        map.remove(h)
        map[h] = uris
        if (map.size > MAX_ENTRIES) {
            map.keys.take(map.size - MAX_ENTRIES).forEach { map.remove(it) }
            rewrite(ctx)
        } else {
            runCatching { file(ctx).appendText(line(h, uris) + "\n") }
        }
    }

    @Synchronized
    fun lookup(ctx: Context, url: String, kind: Kind): List<String>? {
        ensureLoaded(ctx)
        return map[Duplicates.indexKey(url, kind)]
    }

    /** ¿Este enlace ya está en curso o descargado (y su archivo sigue en el móvil)? */
    fun check(ctx: Context, url: String, kind: Kind, title: String? = null): Dup {
        Duplicates.find(DownloadRepository.items.value, url, kind) { MediaFiles.exists(ctx, it) }
            ?.let { return if (it.isActive) Dup.ACTIVE else Dup.DONE }
        val uris = lookup(ctx, url, kind)
        if (uris != null && uris.isNotEmpty() && uris.all { MediaFiles.exists(ctx, it) }) return Dup.DONE
        // Mismo enlace desde otra dirección: se compara el nombre del archivo (solo en el álbum de la app).
        if (!title.isNullOrBlank() && MediaFiles.existsByName(ctx, title)) return Dup.DONE
        return Dup.NONE
    }

    private fun line(h: String, uris: List<String>) = JSONObject().put("h", h).put("u", JSONArray(uris)).toString()

    private fun rewrite(ctx: Context) {
        runCatching { file(ctx).writeText(map.entries.joinToString("") { line(it.key, it.value) + "\n" }) }
    }
}
