package com.quietdlp.app.download

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import java.io.File

/**
 * Guarda las cookies de sesión (para contenido que pide iniciar sesión) en formato Netscape,
 * que es el que entiende yt-dlp con `--cookies`.
 *
 * No conoce ninguna web en concreto: el usuario escribe la dirección que necesite.
 *
 * Las cookies son credenciales: se guardan solo en la carpeta privada de la app (sin copia de seguridad),
 * nunca se escriben en el registro y yt-dlp solo las envía al sitio al que pertenecen.
 */
object CookieStore {

    const val IMPORTED_ID = "imported"

    private const val HEADER = "# Netscape HTTP Cookie File"
    private const val SITE_PREFIX = "site-"

    /**
     * Convierte lo que escribe el usuario ("ejemplo.com", "www.ejemplo.com/entrar", "https://ejemplo.com")
     * en el nombre de dominio ("ejemplo.com"). Devuelve null si no parece una dirección válida.
     */
    fun normalizeHost(input: String): String? {
        val t = input.trim().lowercase()
        if (t.isEmpty()) return null
        val withScheme = if ("://" in t) t else "https://$t"
        val host = runCatching { Uri.parse(withScheme).host }.getOrNull() ?: return null
        val clean = host.removePrefix("www.")
        val ok = clean.contains('.') && clean.all { it.isLetterOrDigit() || it == '.' || it == '-' }
        return clean.takeIf { ok }
    }

    private fun dir(ctx: Context) = File(ctx.filesDir, "cookies").apply { mkdirs() }
    private fun siteFile(ctx: Context, host: String) = File(dir(ctx), "$SITE_PREFIX$host.txt")
    private fun importedFile(ctx: Context) = File(dir(ctx), "$IMPORTED_ID.txt")

    fun savedHosts(ctx: Context): List<String> = dir(ctx).listFiles().orEmpty()
        .map { it.name }
        .filter { it.startsWith(SITE_PREFIX) && it.endsWith(".txt") }
        .map { it.removePrefix(SITE_PREFIX).removeSuffix(".txt") }
        .sorted()

    fun hasImported(ctx: Context): Boolean = importedFile(ctx).exists()

    fun removeSite(ctx: Context, host: String) {
        siteFile(ctx, host).delete()
    }

    fun removeImported(ctx: Context) {
        importedFile(ctx).delete()
    }

    /**
     * Lee las cookies que el WebView guardó tras el inicio de sesión y las convierte a formato Netscape.
     * Devuelve false si el WebView no tiene ninguna cookie de ese sitio.
     */
    fun saveFromWebView(ctx: Context, host: String, currentUrl: String?): Boolean {
        val cm = CookieManager.getInstance()
        val urls = listOfNotNull(
            "https://$host",
            "https://www.$host",
            currentUrl?.takeIf { it.startsWith("https://") },
        )
        val pairs = LinkedHashMap<String, String>()
        for (u in urls) {
            cm.getCookie(u)?.split(";")?.forEach { part ->
                val kv = part.trim()
                val i = kv.indexOf('=')
                if (i > 0) pairs[kv.substring(0, i)] = kv.substring(i + 1)
            }
        }
        if (pairs.isEmpty()) return false

        val domain = ".$host"
        // El WebView no da la caducidad real: se pone a un año (las sesiones duran ese orden).
        val expiry = System.currentTimeMillis() / 1000 + 365L * 24 * 3600
        val lines = pairs.map { (k, v) -> "$domain\tTRUE\t/\tTRUE\t$expiry\t$k\t$v" }
        siteFile(ctx, host).writeText((listOf(HEADER) + lines).joinToString("\n") + "\n")
        return true
    }

    /** Importa un cookies.txt exportado desde un navegador de escritorio (para webs que no dejan iniciar sesión en un WebView). */
    fun importFile(ctx: Context, uri: Uri): Boolean {
        val lines = runCatching {
            ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines().take(20_000) }
        }.getOrNull() ?: return false
        val valid = lines.any { it.isNotBlank() && it.count { c -> c == '\t' } >= 6 }
        if (!valid) return false
        val body = lines.filterNot { it.startsWith("# Netscape") || it.startsWith("# HTTP Cookie") }
        importedFile(ctx).writeText((listOf(HEADER) + body).joinToString("\n") + "\n")
        return true
    }

    /** Junta todas las cookies guardadas en un solo archivo para pasárselo a yt-dlp. null = no hay ninguna. */
    @Synchronized
    fun mergedPath(ctx: Context): String? {
        val files = dir(ctx).listFiles { f -> f.extension == "txt" }.orEmpty()
        if (files.isEmpty()) return null
        val body = files.flatMap { it.readLines() }
            .filter { it.isNotBlank() && !it.startsWith("# Netscape") && !it.startsWith("# HTTP Cookie") }
        val out = File(ctx.filesDir, "cookies_all.txt")
        out.writeText((listOf(HEADER) + body).joinToString("\n") + "\n")
        return out.absolutePath
    }
}
