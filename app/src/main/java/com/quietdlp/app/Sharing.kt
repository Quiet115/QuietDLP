package com.quietdlp.app

import android.content.ClipData
import android.content.Intent
import android.net.Uri

object Sharing {
    /** Menú "Compartir" de Android con los archivos descargados (uno o varios). */
    fun chooser(uris: List<Uri>, mime: String?): Intent {
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE)
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        send.type = if (uris.size == 1) mime ?: "*/*" else "*/*"
        send.clipData = ClipData.newRawUri("", uris[0]).also { c -> uris.drop(1).forEach { c.addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
