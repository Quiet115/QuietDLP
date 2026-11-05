package com.quietdlp.app.data

import java.net.URI

/** Un perfil de una red social y los enlaces de su foto y su banner (los que entiende gallery-dl). */
data class ProfileRef(
    val network: String,
    val user: String,
    val profileUrl: String,
    val avatarUrl: String,
    /** null si esa red no tiene banner que se pueda bajar. */
    val bannerUrl: String?,
)

/** Reconoce enlaces de perfil (funciones puras, para poder probarlas). */
object ProfilePhoto {
    private val USER = Regex("""^[A-Za-z0-9._-]{1,40}$""")

    private val INSTAGRAM_RESERVED = setOf(
        "p", "reel", "reels", "tv", "stories", "explore", "accounts", "direct", "about", "legal", "directory", "web", "developer",
        "privacy", "terms", "challenge", "emails", "session", "static", "api", "oauth", "download",
    )
    private val TWITTER_RESERVED = setOf(
        "home", "explore", "i", "search", "settings", "messages", "notifications", "intent", "share", "compose", "login", "signup",
        "hashtag", "tos", "privacy", "about", "jobs", "download",
    )
    private val DEVIANTART_RESERVED = setOf(
        "tag", "search", "popular", "watch", "shop", "core-membership", "join", "users", "journal", "topic", "about", "forum",
        "groups", "notifications", "messages", "settings", "developers", "checkout", "today", "browse", "daily-deviations",
    )
    private val VSCO_RESERVED = setOf("discover", "feed", "search", "explore", "about", "login", "join", "i", "grid")
    private val FACEBOOK_RESERVED = setOf(
        "watch", "groups", "pages", "marketplace", "events", "reel", "reels", "share", "photo", "photos", "story.php", "profile.php",
        "login", "gaming", "stories", "help", "policies", "privacy", "public", "permalink.php", "sharer", "dialog", "hashtag", "people",
    )

    /** El perfil al que apunta el enlace, o null si no es una página de perfil de una red conocida (por ejemplo, una publicación). */
    fun parse(url: String): ProfileRef? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.removePrefix("mobile.") ?: return null
        val seg = uri.rawPath.orEmpty().split('/').filter { it.isNotBlank() }
        return when {
            host == "instagram.com" -> single(seg, INSTAGRAM_RESERVED)?.let {
                ref("Instagram", it, "https://www.instagram.com/$it/", "https://www.instagram.com/$it/avatar/", null)
            }
            host == "tiktok.com" -> seg.singleOrNull()?.takeIf { it.startsWith("@") && USER.matches(it.drop(1)) }?.let {
                ref("TikTok", it.drop(1), "https://www.tiktok.com/$it", "https://www.tiktok.com/$it/avatar", null)
            }
            host == "bsky.app" -> if (seg.size == 2 && seg[0] == "profile" && USER.matches(seg[1])) {
                val h = seg[1]
                ref("Bluesky", h, "https://bsky.app/profile/$h", "https://bsky.app/profile/$h/avatar", "https://bsky.app/profile/$h/banner")
            } else null
            host == "deviantart.com" -> single(seg, DEVIANTART_RESERVED)?.let {
                ref("DeviantArt", it, "https://www.deviantart.com/$it", "https://www.deviantart.com/$it/avatar/", "https://www.deviantart.com/$it/banner/")
            }
            host.endsWith(".deviantart.com") && seg.isEmpty() -> host.removeSuffix(".deviantart.com").takeIf { USER.matches(it) }?.let {
                ref("DeviantArt", it, "https://www.deviantart.com/$it", "https://www.deviantart.com/$it/avatar/", "https://www.deviantart.com/$it/banner/")
            }
            host == "vsco.co" -> single(seg, VSCO_RESERVED)?.let {
                ref("VSCO", it, "https://vsco.co/$it", "https://vsco.co/$it/avatar", null)
            }
            host == "twitter.com" || host == "x.com" -> single(seg, TWITTER_RESERVED)?.let {
                ref("X", it, "https://x.com/$it", "https://x.com/$it/photo", "https://x.com/$it/header_photo")
            }
            host == "facebook.com" || host == "fb.com" -> single(seg, FACEBOOK_RESERVED)?.let {
                ref("Facebook", it, "https://www.facebook.com/$it", "https://www.facebook.com/$it/avatar", null)
            }
            host == "snapchat.com" -> seg.singleOrNull()?.takeIf { it.startsWith("@") && USER.matches(it.drop(1)) }?.let {
                ref("Snapchat", it.drop(1), "https://www.snapchat.com/$it", "https://www.snapchat.com/$it/avatar", null)
            }
            else -> null
        }
    }

    /** Los títulos que la app da a la foto y al banner de un perfil: el archivo se guarda con ese nombre. */
    fun isProfileTitle(title: String): Boolean = title.endsWith(" (profile)") || title.endsWith(" (banner)")

    /** El único trozo de la ruta, si es un nombre de usuario y no una página reservada de la red. */
    private fun single(seg: List<String>, reserved: Set<String>): String? =
        seg.singleOrNull()?.takeIf { USER.matches(it) && it.lowercase() !in reserved }

    private fun ref(network: String, user: String, profile: String, avatar: String, banner: String?) =
        ProfileRef(network, user, profile, avatar, banner)
}
