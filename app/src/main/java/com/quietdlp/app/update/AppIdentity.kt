package com.quietdlp.app.update

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** La huella de la firma con la que está firmada esta copia de QuietDLP: la misma en todas las versiones oficiales. */
object AppIdentity {

    /** SHA-256 de la firma instalada, en hexadecimal minúscula. null si Android no la da. */
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

    /** Para leerla: grupos de 8 caracteres, "9a9bd88d 2c2e581f ...". */
    fun grouped(hex: String): String = hex.chunked(8).joinToString(" ")
}
