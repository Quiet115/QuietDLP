package com.quietdlp.app.download

import android.content.Context
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.ErrorLogText
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Registro de las últimas descargas fallidas, para copiarlo y enviarlo al pedir ayuda. Se guarda solo en el móvil. */
object ErrorLog {
    private fun file(ctx: Context) = File(ctx.applicationContext.filesDir, "errors.log")

    @Synchronized
    fun add(ctx: Context, item: DownloadItem, t: Throwable) {
        runCatching {
            val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val host = UrlUtils.hostOf(item.url)
            val e = ErrorLogText.entry(stamp, host, item.quality.label(), t.message ?: t.toString())
            val f = file(ctx)
            f.writeText(ErrorLogText.append(if (f.exists()) f.readText() else "", e))
        }
    }

    @Synchronized
    fun read(ctx: Context): String = runCatching { file(ctx).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    @Synchronized
    fun clear(ctx: Context) {
        runCatching { file(ctx).delete() }
    }
}
