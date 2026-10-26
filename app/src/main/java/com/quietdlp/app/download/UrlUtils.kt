package com.quietdlp.app.download

import java.io.File

object UrlUtils {
    private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

    /**
     * Saca el primer enlace de un texto. Las apps de compartir suelen añadir texto
     * alrededor del enlace ("Mira este vídeo: https://..."), por eso no basta con usar el texto entero.
     */
    fun extract(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val raw = urlRegex.find(text)?.value ?: return null
        return raw.trimEnd('.', ',', ')', ']', '!', ';')
    }

    fun hostOf(url: String): String? =
        runCatching { android.net.Uri.parse(url).host }.getOrNull()?.lowercase()

    /**
     * Dominio "de verdad" de un nombre de host: "vt.ejemplo.com" → "ejemplo.com". Las cookies de sesión
     * se guardan para el dominio completo, así que un enlace corto o un subdominio sirven igual.
     * (Heurística simple: tiene en cuenta terminaciones como .co.uk, no es una lista completa.)
     */
    fun registrableDomain(host: String): String {
        val labels = host.removePrefix("www.").split('.')
        if (labels.size <= 2) return labels.joinToString(".")
        val commonSecond = setOf("co", "com", "org", "net", "gov", "edu", "ac")
        val take = if (labels.last().length == 2 && labels[labels.size - 2] in commonSecond) 3 else 2
        return labels.takeLast(take).joinToString(".")
    }

    fun shorten(url: String, max: Int = 48): String {
        val clean = url.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        return if (clean.length <= max) clean else clean.take(max - 1) + "…"
    }
}

object Errors {
    private fun errorLineOf(t: Throwable): String {
        val raw = t.message ?: t.toString()
        return raw.lines().lastOrNull { it.contains("ERROR", ignoreCase = true) } ?: raw
    }

    /** true si el fallo se debe a que la web exige una sesión iniciada (o la guardada caducó). */
    fun isLoginRequired(t: Throwable): Boolean {
        val l = errorLineOf(t).lowercase()
        return "no longer valid" in l || "expired" in l && "cookie" in l ||
            "available to everyone" in l || "certain audiences" in l || "sign in" in l ||
            "log in" in l || "login" in l || "private" in l || "cookies" in l ||
            "empty media response" in l || "age" in l && "restrict" in l
    }

    /**
     * true si yt-dlp no encontró vídeo en el enlace: publicaciones de solo fotos, galerías o webs que
     * yt-dlp no conoce. En esos casos se prueba con gallery-dl.
     */
    fun isNoVideo(t: Throwable): Boolean {
        val l = errorLineOf(t).lowercase()
        return "no video" in l || "unsupported url" in l || "no video formats" in l
    }

    /** true si el fallo parece una caída de internet (se espera a que vuelva y se reintenta). */
    fun isNetwork(t: Throwable): Boolean = isNetworkText(errorLineOf(t))

    fun isNetworkText(line: String): Boolean {
        val l = line.lowercase()
        return "unable to resolve host" in l || "network is unreachable" in l ||
            "temporary failure in name resolution" in l || "name or service not known" in l ||
            "failed to resolve" in l || "failed to establish a new connection" in l ||
            "timed out" in l || "connection reset" in l || "connection aborted" in l ||
            "connection refused" in l || "remote end closed connection" in l ||
            "incompleteread" in l || "unexpected end of stream" in l || "eof occurred in violation" in l
    }

    /** Traduce los errores técnicos de yt-dlp a un mensaje que entienda el usuario. */
    fun friendly(t: Throwable): String {
        val errorLine = errorLineOf(t)
        val l = errorLine.lowercase()
        return when {
            "unsupported url" in l -> "This link isn't supported."
            isLoginRequired(t) -> "This content requires signing in."
            "unable to resolve host" in l || "network is unreachable" in l ||
                "timed out" in l || "connection" in l && "refused" in l ->
                "No internet connection."
            "video unavailable" in l || "not available" in l ->
                "The video isn't available."
            // Quitar el prefijo técnico "[nombre-del-sitio] id:" que añade yt-dlp.
            else -> errorLine.removePrefix("ERROR:").trim()
                .replace(Regex("""^\[[^\]]+]\s*\S+:\s*"""), "")
                .take(220)
        }
    }

    fun cleanTitle(file: File): String = file.nameWithoutExtension.ifBlank { "Download" }
}
