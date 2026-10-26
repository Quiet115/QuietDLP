package com.quietdlp.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.quietdlp.app.Links
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Actualizaciones de la propia app. Mira la última versión publicada en GitHub (Releases), descarga el APK,
 * comprueba que es de verdad de QuietDLP (misma firma, versión mayor, SHA-256 si GitHub lo da) y lo abre en el
 * instalador de Android. El usuario siempre confirma la instalación: la app nunca instala nada sola.
 *
 * Solo contacta con api.github.com y con las descargas de ese mismo repositorio; no envía datos personales.
 */
object AppUpdater {

    data class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val sha256: String?,
        val sizeBytes: Long,
    )

    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    /**
     * Origen local SOLO para builds de depuración: el PC sirve la versión nueva y `adb reverse` la acerca al móvil
     * por el cable USB (tools/dev_update.py). Las versiones de publicación nunca lo usan.
     */
    private const val DEV_BASE = "http://127.0.0.1:8765"

    fun isDebuggable(ctx: Context): Boolean =
        ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun repoConfigured(): Boolean =
        Links.GITHUB_REPO.matches(Regex("""[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"""))

    /** true si hay algún sitio donde buscar versiones nuevas (el repositorio de GitHub o, en depuración, el origen local). */
    fun isConfigured(ctx: Context): Boolean = isDebuggable(ctx) || repoConfigured()

    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "0"

    /** true si [candidate] es más nueva que [current]. Compara número a número: 0.10.0 es mayor que 0.9.2. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-').substringBefore('+')
            .split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Convierte la respuesta de GitHub (releases/latest) en una [Release]. Devuelve null si no hay un APK
     * descargable o si su dirección no pertenece al repositorio esperado.
     */
    fun parseRelease(json: String, repo: String, extraPrefixes: List<String> = emptyList()): Release? {
        val o = JSONObject(json)
        if (o.optBoolean("draft") || o.optBoolean("prerelease")) return null
        val version = o.optString("tag_name").removePrefix("v").removePrefix("V").ifBlank { return null }
        val assets = o.optJSONArray("assets") ?: return null
        val prefixes = listOf("https://github.com/$repo/releases/download/") + extraPrefixes

        var best: JSONObject? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            val url = a.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true) || prefixes.none { url.startsWith(it) }) continue
            // Se prefiere el APK "de versión" sobre uno de depuración si hubiera varios.
            if (best == null || (best.optString("name").contains("debug", true) && !name.contains("debug", true))) best = a
        }
        val apk = best ?: return null
        val digest = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
        return Release(
            version = version,
            notes = o.optString("body").trim(),
            apkUrl = apk.getString("browser_download_url"),
            sha256 = digest,
            sizeBytes = apk.optLong("size"),
        )
    }

    private fun open(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "QuietDLP")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        return c
    }

    /** Bloqueante: llamar desde un hilo de fondo. null = no hay versión publicada o no se pudo leer. */
    fun fetchLatest(ctx: Context): Release? {
        // En depuración se mira primero el origen local; si no está encendido, se sigue con GitHub (si hay repositorio).
        if (isDebuggable(ctx)) {
            try {
                val c = open("$DEV_BASE/release.json")
                try {
                    if (c.responseCode == 200) {
                        val body = c.inputStream.bufferedReader().use { it.readText() }
                        return parseRelease(body, Links.GITHUB_REPO, listOf("$DEV_BASE/"))
                    }
                } finally {
                    c.disconnect()
                }
            } catch (_: java.io.IOException) {
                // servidor local apagado
            }
        }
        if (!repoConfigured()) return null
        val c = open("https://api.github.com/repos/${Links.GITHUB_REPO}/releases/latest")
        try {
            if (c.responseCode == 404) return null // aún no hay ninguna versión publicada
            require(c.responseCode == 200) { "GitHub responded ${c.responseCode}" }
            val body = c.inputStream.bufferedReader().use { it.readText() }
            return parseRelease(body, Links.GITHUB_REPO)
        } finally {
            c.disconnect()
        }
    }

    /** Descarga el APK a la caché con progreso (0..100). Verifica el SHA-256 si GitHub lo indicó. */
    fun download(ctx: Context, release: Release, onProgress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "QuietDLP-${release.version}.apk")
        val c = open(release.apkUrl)
        val sha = MessageDigest.getInstance("SHA-256")
        try {
            require(c.responseCode == 200) { "The download responded ${c.responseCode}" }
            val total = c.contentLengthLong.takeIf { it > 0 } ?: release.sizeBytes
            require(total <= MAX_APK_BYTES) { "The file is too large" }
            var done = 0L
            var lastPct = -1
            c.inputStream.use { input ->
                out.outputStream().use { os ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        os.write(buf, 0, n)
                        sha.update(buf, 0, n)
                        done += n
                        require(done <= MAX_APK_BYTES) { "The file is too large" }
                        if (total > 0) {
                            val pct = (done * 100 / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) { lastPct = pct; onProgress(pct) }
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        val actual = sha.digest().joinToString("") { "%02x".format(it) }
        if (release.sha256 != null) {
            require(actual.equals(release.sha256, ignoreCase = true)) {
                out.delete()
                "File check failed (SHA-256 mismatch)"
            }
        }
        return out
    }

    /**
     * Comprueba que el APK descargado es una versión más nueva de ESTA misma app y está firmado por la misma
     * clave. Devuelve un mensaje de error, o null si todo está bien. (Android lo vuelve a comprobar al instalar;
     * esto da un mensaje claro antes.)
     */
    fun checkApk(ctx: Context, file: File): String? {
        val pm = ctx.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else 0
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags)
            ?: return "The downloaded file isn't a valid APK."
        if (archive.packageName != ctx.packageName) return "The downloaded file isn't QuietDLP."
        val installed = pm.getPackageInfo(ctx.packageName, 0)
        val newCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else @Suppress("DEPRECATION") archive.versionCode.toLong()
        val oldCode = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else @Suppress("DEPRECATION") installed.versionCode.toLong()
        if (newCode <= oldCode) return "You already have this version or a newer one."

        if (Build.VERSION.SDK_INT >= 28) {
            val mine = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
            val theirs = archive.signingInfo?.apkContentsSigners?.map { sha256(it.toByteArray()) }?.toSet().orEmpty()
            if (mine.isEmpty() || theirs.isEmpty() || mine.intersect(theirs).isEmpty()) {
                return "The signature doesn't match the installed one. Uninstall QuietDLP and install the new one " +
                    "(settings and sessions are lost)."
            }
        }
        return null
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** true si Android ya permite a QuietDLP lanzar el instalador. */
    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    /** Pantalla de Android donde el usuario concede "Instalar apps de esta fuente" a QuietDLP. */
    fun permissionIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(ctx: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
