package com.quietdlp.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.quietdlp.app.R
import com.quietdlp.app.Strings

/**
 * «Enviar a QuietRingtones»: abre esa app (aparte, por intent) para recortar un audio descargado y ponerlo como tono,
 * notificación o alarma. El archivo va como URI de contenido con permiso de lectura temporal, nunca como ruta.
 */
object QuietRingtones {
    /** La app firmada de GitHub. La de depuración (`com.quietringtones.debug`) no es un destino. */
    const val PACKAGE = "com.quietringtones"
    const val RELEASES_URL = "https://github.com/Quiet115/QuietRingtones/releases/latest"

    fun isAudio(mime: String?): Boolean = mime?.startsWith("audio/") == true

    fun isInstalled(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getPackageInfo(PACKAGE, 0)
    }.isSuccess

    /** El intent del contrato: ACTION_SEND con el MIME real, el audio en EXTRA_STREAM y lectura temporal. */
    fun sendIntent(uri: Uri, mime: String, title: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            setPackage(PACKAGE)
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!title.isNullOrBlank()) putExtra(Intent.EXTRA_TITLE, title)
            clipData = ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    /** true si se abrió QuietRingtones; false si no está instalada. */
    fun send(ctx: Context, uri: Uri, mime: String, title: String?): Boolean {
        if (!isInstalled(ctx)) return false
        return try {
            ctx.startActivity(sendIntent(uri, mime, title))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    fun openReleases(ctx: Context) {
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL))) }
    }
}

/** Botón de un audio descargado. Si QuietRingtones no está instalada, ofrece abrir su página de descarga. */
@Composable
fun SendToRingtonesButton(uri: String, mime: String?, title: String?, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    var missing by remember { mutableStateOf(false) }
    TextButton(
        modifier = modifier,
        onClick = { if (!QuietRingtones.send(ctx, Uri.parse(uri), mime ?: "audio/*", title)) missing = true },
    ) { Text(Strings.get(R.string.ringtones_send)) }
    if (missing) {
        AlertDialog(
            onDismissRequest = { missing = false },
            title = { Text(Strings.get(R.string.ringtones_missing_title)) },
            text = { Text(Strings.get(R.string.ringtones_missing_body)) },
            confirmButton = {
                TextButton(onClick = { missing = false; QuietRingtones.openReleases(ctx) }) { Text(Strings.get(R.string.ringtones_open_page)) }
            },
            dismissButton = { TextButton(onClick = { missing = false }) { Text(Strings.get(R.string.settings_screen_cancel)) } },
        )
    }
}
