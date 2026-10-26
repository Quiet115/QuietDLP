package com.quietdlp.app.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import com.quietdlp.app.data.Prefs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File

/**
 * Publica el archivo terminado para que aparezca donde el usuario lo espera:
 *  - Vídeos y fotos → carpeta DCIM/QuietDLP: la Galería la muestra como un álbum llamado "QuietDLP"
 *    (como el álbum de cualquier otra app), con vídeos y fotos juntos.
 *  - Audios → Música/QuietDLP: los reproductores de música lo encuentran ahí.
 *
 * Android no permite a una app crear carpetas sueltas en la raíz del almacenamiento sin un permiso
 * de "acceso a todos los archivos", y MediaStore solo admite DCIM/Pictures/Movies para imagen y vídeo
 * y Music/Podcasts/etc. para audio. DCIM/QuietDLP es la opción que da un álbum único con un solo nombre.
 */
object MediaSaver {

    data class Saved(val uri: Uri, val mime: String, val displayName: String)

    private fun mimeOf(file: File): String {
        val ext = file.extension.lowercase()
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)?.let { return it }
        return when (ext) {
            "m4a" -> "audio/mp4"
            "opus" -> "audio/ogg"
            "mkv" -> "video/x-matroska"
            "webp" -> "image/webp"
            else -> "video/mp4"
        }
    }

    fun publish(ctx: Context, file: File): Saved {
        val mime = mimeOf(file)
        val folder = Prefs(ctx).folderName
        // Carpeta elegida por el usuario; si ya no se puede escribir en ella, se usa el álbum de la app.
        val tree = Prefs(ctx).folderUri
        if (tree.isNotEmpty()) {
            runCatching { return publishToTree(ctx, file, mime, Uri.parse(tree), folder) }
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            publishScoped(ctx, file, mime, folder)
        } else {
            publishLegacy(ctx, file, mime, folder)
        }
    }

    /** Dentro de la carpeta elegida se usa (o se crea) una subcarpeta con el nombre configurado. */
    private fun childFolder(ctx: Context, tree: Uri, parent: Uri, name: String): Uri {
        val resolver = ctx.contentResolver
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getDocumentId(parent))
        resolver.query(
            children,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getString(1) == name && c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    return DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0))
                }
            }
        }
        return DocumentsContract.createDocument(resolver, parent, DocumentsContract.Document.MIME_TYPE_DIR, name)
            ?: error("Couldn't create the folder")
    }

    private fun publishToTree(ctx: Context, file: File, mime: String, tree: Uri, folder: String): Saved {
        val resolver = ctx.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val parent = childFolder(ctx, tree, root, folder)
        val doc = DocumentsContract.createDocument(resolver, parent, mime, file.name)
            ?: error("Couldn't create the file in the chosen folder")
        try {
            resolver.openOutputStream(doc)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: error("Couldn't write the file")
        } catch (t: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, doc) }
            throw t
        }
        return Saved(doc, mime, file.name)
    }

    private fun relativePath(mime: String, folder: String) =
        if (mime.startsWith("audio")) "${Environment.DIRECTORY_MUSIC}/$folder"
        else "${Environment.DIRECTORY_DCIM}/$folder"

    private fun publishScoped(ctx: Context, file: File, mime: String, folder: String): Saved {
        val resolver = ctx.contentResolver
        val volume = MediaStore.VOLUME_EXTERNAL_PRIMARY
        val collection = when {
            mime.startsWith("audio") -> MediaStore.Audio.Media.getContentUri(volume)
            mime.startsWith("image") -> MediaStore.Images.Media.getContentUri(volume)
            else -> MediaStore.Video.Media.getContentUri(volume)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(mime, folder))
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("Couldn't create the file in storage")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { it.copyTo(out) }
            } ?: error("Couldn't write the file")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        return Saved(uri, mime, file.name)
    }

    @Suppress("DEPRECATION")
    private fun publishLegacy(ctx: Context, file: File, mime: String, folder: String): Saved {
        val top = if (mime.startsWith("audio")) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_DCIM
        val dir = File(Environment.getExternalStoragePublicDirectory(top), folder).apply { mkdirs() }
        var dest = File(dir, file.name)
        var n = 1
        while (dest.exists()) {
            dest = File(dir, "${file.nameWithoutExtension} ($n).${file.extension}")
            n++
        }
        file.copyTo(dest)
        MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime), null)
        return Saved(Uri.fromFile(dest), mime, dest.name)
    }
}
