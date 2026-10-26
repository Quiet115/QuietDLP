package com.quietdlp.app.download

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/** Comprueba y borra los archivos que la app guardó en la galería. */
object MediaFiles {
    fun exists(ctx: Context, uri: String): Boolean = runCatching {
        val u = Uri.parse(uri)
        if (u.scheme == "file") File(u.path ?: return false).exists()
        else ctx.contentResolver.openFileDescriptor(u, "r")?.use { true } ?: false
    }.getOrDefault(false)

    /** true si el archivo ya no existe al terminar (se borró o ya no estaba). */
    fun delete(ctx: Context, uri: String): Boolean {
        runCatching {
            val u = Uri.parse(uri)
            when {
                u.scheme == "file" -> File(u.path ?: return@runCatching).delete()
                DocumentsContract.isDocumentUri(ctx, u) -> DocumentsContract.deleteDocument(ctx.contentResolver, u)
                else -> ctx.contentResolver.delete(u, null, null)
            }
        }
        return !exists(ctx, uri)
    }

    /**
     * ¿Hay en el álbum de la app (galería o música) un archivo cuyo nombre empieza igual que este título?
     * Es una consulta rápida y solo sirve como aviso: si el nombre cambió al guardarse, simplemente no encuentra nada.
     */
    fun existsByName(ctx: Context, title: String): Boolean = runCatching {
        val folder = com.quietdlp.app.data.Prefs(ctx).folderName
        val prefix = title.replace(Regex("""[<>:"/\\|?*\p{Cntrl}]"""), "").trim().take(40)
        if (prefix.length < 6) return@runCatching false
        val esc = prefix.replace("!", "!!").replace("%", "!%").replace("_", "!_")
        ctx.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '!' AND " +
                "(${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? OR ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?)",
            arrayOf("$esc%", "DCIM/$folder/%", "Music/$folder/%"), null,
        )?.use { it.moveToFirst() } ?: false
    }.getOrDefault(false)
}
