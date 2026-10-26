package com.quietdlp.app.data

/** Detecta enlaces ya descargados (funciones puras, para poder probarlas). */
object Duplicates {
    /** Parámetros que solo sirven para rastrear el origen del enlace: no cambian el contenido. */
    private val tracking = setOf(
        "si", "igsh", "igshid", "is_from_webapp", "sender_device", "feature", "fbclid", "gclid",
        "ref", "ref_src", "ref_url", "share_id", "share_app_id", "share_item_id", "source", "t",
    )

    /** Webs donde el contenido se identifica por la ruta: cualquier parámetro (?exln=, ?igsh=...) es de rastreo. */
    private val pathOnlyHosts = setOf(
        "instagram.com", "tiktok.com", "x.com", "twitter.com", "reddit.com",
        "vimeo.com", "soundcloud.com", "twitch.tv", "pinterest.com",
    )

    /** Clave para comparar dos enlaces: sin parámetros de rastreo, sin #fragmento y sin "/" final. */
    fun key(url: String): String {
        val noFragment = url.trim().substringBefore('#')
        val base = noFragment.substringBefore('?')
        val query = noFragment.substringAfter('?', "")
            .split('&')
            .filter { it.isNotBlank() }
            .filterNot { p ->
                val name = p.substringBefore('=').lowercase()
                name in tracking || name.startsWith("utm_")
            }
            .joinToString("&")
        val scheme = base.substringBefore("://", "https").lowercase()
        val rest = base.substringAfter("://", base)
        val host = rest.substringBefore('/').lowercase().removePrefix("www.").removePrefix("m.")
        val path = rest.substringAfter('/', "").trimEnd('/')
        val keptQuery = if (pathOnlyHosts.any { host == it || host.endsWith(".$it") }) "" else query
        return buildString {
            append(scheme.removeSuffix("s")).append("://").append(host)
            if (path.isNotEmpty()) append('/').append(path)
            if (keptQuery.isNotEmpty()) append('?').append(keptQuery)
        }
    }

    /** Clave corta para el índice de lo descargado: mismo enlace y mismo tipo (vídeo, audio, fotos). */
    fun indexKey(url: String, kind: Kind): String = keyHash(url).take(24) + kind.name.first()

    /** Huella del enlace para recordarlo sin guardarlo: los enlaces copiados pueden llevar claves. */
    fun keyHash(url: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(key(url).toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Busca una descarga del mismo enlace y del mismo tipo (vídeo, audio o fotos) que ya esté hecha
     * (y cuyo archivo siga existiendo) o que esté en curso.
     */
    fun find(
        items: List<DownloadItem>,
        url: String,
        kind: Kind,
        fileExists: (String) -> Boolean,
    ): DownloadItem? {
        val k = key(url)
        return items.firstOrNull { i ->
            key(i.url) == k && i.quality.kind == kind && when {
                i.isActive -> true
                i.state == State.DONE -> i.savedUris.isNotEmpty() && i.savedUris.all(fileExists)
                else -> false
            }
        }
    }
}
