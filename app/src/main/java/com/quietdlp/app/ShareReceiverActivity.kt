package com.quietdlp.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.quietdlp.app.data.Prefs
import com.quietdlp.app.data.Quality
import com.quietdlp.app.download.DownloadRepository
import com.quietdlp.app.download.DownloadService
import com.quietdlp.app.download.DownloadIndex
import com.quietdlp.app.download.UrlUtils

/**
 * Destino del menú "Compartir" de otras apps.
 * Casi nunca tiene pantalla: recoge el enlace, lo manda al servicio de descarga y se cierra al instante,
 * así el usuario sigue en la app desde la que compartió. Solo muestra un aviso si ese enlace ya estaba descargado.
 */
class ShareReceiverActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Algunas apps mandan el texto como Spanned (CharSequence), no como String.
        val shared = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString() ?: intent?.dataString
        val url = UrlUtils.extract(shared)
        val quality = url?.let { Prefs(this).qualityFor(it) }

        when {
            url == null -> {
                toast("No link found in what you shared")
                close()
            }

            // "Preguntar cada vez": se abre la app para elegir.
            quality == null -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_URL, url)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                )
                close()
            }

            else -> {
                DownloadRepository.init(this)
                if (DownloadIndex.check(this, url, quality.kind) == DownloadIndex.Dup.NONE) start(url, quality)
                else askAgain(url, quality)
            }
        }
    }

    private fun start(url: String, quality: Quality) {
        DownloadService.enqueue(this, url, quality)
        toast("QuietDLP: downloading in the background…")
        close()
    }

    private fun askAgain(url: String, quality: Quality) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Already downloaded")
            .setMessage("Download it again?")
            .setPositiveButton("Download") { _, _ -> start(url, quality) }
            .setNegativeButton("Cancel") { _, _ -> close() }
            .setOnCancelListener { close() }
            .show()
    }

    private fun close() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
