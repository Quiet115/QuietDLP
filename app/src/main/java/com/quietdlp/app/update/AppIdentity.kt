package com.quietdlp.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object AppIdentity {

    // null si Android no entrega la firma instalada.
    fun signatureSha256(ctx: Context): String? = runCatching {
        val pm = ctx.packageManager
        val bytes: ByteArray? = if (Build.VERSION.SDK_INT >= 28) {
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES).signatures?.firstOrNull()?.toByteArray()
        }
        bytes?.let { hex(MessageDigest.getInstance("SHA-256").digest(it)) }
    }.getOrNull()

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    fun grouped(hex: String): String = hex.chunked(8).joinToString(" ")
}
