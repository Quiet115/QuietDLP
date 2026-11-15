package com.quietdlp.app.data

import org.json.JSONArray
import org.json.JSONObject

// Los permisos y las referencias a archivos no se transfieren a otro dispositivo.
object SettingsBackup {
    private const val APP = "QuietDLP"
    private const val FORMAT = 1

    const val MAX_BYTES = 8_000_000

    class TooLarge : IllegalArgumentException()
    class InvalidData : IllegalArgumentException()

    // Solo viajan ajustes portables. Los datos ligados al dispositivo o a la hora quedan fuera.
    val KEYS: Map<String, Char> = mapOf(
        "quick_direct" to 'b', "quick_kind" to 's', "quick_height" to 'i', "audio_format" to 's', "audio_bitrate" to 'i',
        "auto_photo_video" to 'b', "remember_song" to 'b', "song_seconds" to 'i', "app_update_check" to 'b',
        "photos_enabled" to 'b', "auto_update" to 'b', "mode" to 's', "remember_last" to 'b', "last_kind" to 's',
        "last_height" to 'i', "last_audio_format" to 's', "last_audio_bitrate" to 'i', "clipboard_offer" to 'b',
        "palette" to 's', "custom_palettes" to 's', "advanced" to 'b', "folder_name" to 's', "orientation" to 's',
        "support_card" to 'b', "max_parallel" to 'i', "wifi_only" to 'b', "ask_multi" to 'b', "battery_hint_off" to 'b',
        "subs_on" to 'b', "sub_langs" to 's', "sub_mode" to 's', "cover_audio" to 'b', "cover_video" to 'b', "music_names" to 'b', "name_template" to 's', "sponsor_mode" to 's', "sponsor_cats" to 's', "chapter_mode" to 's',
        "follow_system_mode" to 'b', "palette_dark" to 's', "palette_light" to 's', "language" to 's', "site_rules" to 's', "follow_wifi" to 'b', "share_options" to 'b',
    )

    // La vista previa agrupa los cambios por pestaña.
    enum class Group { DOWNLOADS, QUEUE, SITES, ADVANCED, APPEARANCE, LANGUAGE, UPDATES, ABOUT }

    fun groupOf(key: String): Group = when (key) {
        "max_parallel", "wifi_only", "ask_multi", "battery_hint_off", "follow_wifi" -> Group.QUEUE
        "site_rules" -> Group.SITES
        "advanced", "name_template", "sponsor_mode", "sponsor_cats", "chapter_mode" -> Group.ADVANCED
        "palette", "custom_palettes", "orientation", "follow_system_mode", "palette_dark", "palette_light" -> Group.APPEARANCE
        "language" -> Group.LANGUAGE
        "app_update_check", "auto_update" -> Group.UPDATES
        "support_card" -> Group.ABOUT
        else -> Group.DOWNLOADS
    }

    sealed interface Check {
        data object NotAProfile : Check

        data object FromNewerVersion : Check

        data class Ready(val values: Map<String, Any>, val history: Int, val follows: Int, val omittedHistory: Long = 0L) : Check
    }

    fun check(json: String): Check {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return Check.NotAProfile
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return Check.NotAProfile
        if (root.optString("app") != APP) return Check.NotAProfile
        val format = root.opt("format") as? Int ?: return Check.NotAProfile
        if (format > FORMAT) return Check.FromNewerVersion
        if (format < 1) return Check.NotAProfile
        val values = decode(json) ?: return Check.NotAProfile
        val (history, omitted) = historyRows(root)
        val previouslyOmitted = (root.opt("historyOmitted") as? Int)?.coerceAtLeast(0) ?: 0
        return Check.Ready(values, history.size, decodeFollows(json).size, previouslyOmitted.toLong() + omitted)
    }

    fun changes(values: Map<String, Any>, current: Map<String, *>): Map<Group, Int> {
        val out = LinkedHashMap<Group, Int>()
        for (key in KEYS.keys) {
            val v = values[key] ?: if (key == "language") "en" else null
            val previous = current[key] ?: if (key == "language") "en" else null
            if (previous == v) continue
            val g = groupOf(key)
            out[g] = (out[g] ?: 0) + 1
        }
        return out
    }

    private fun matches(type: Char, v: Any?): Boolean = when (type) {
        'b' -> v is Boolean
        'i' -> v is Int
        's' -> v is String && v.length <= 200_000
        else -> false
    }

    fun encode(values: Map<String, *>, history: List<DownloadItem> = emptyList(), follows: List<FollowedPage> = emptyList()): String {
        val settings = JSONObject()
        KEYS.forEach { (key, type) ->
            val v = values[key]
            if (v is String && v.length > 200_000) throw TooLarge()
            if (v != null && !matches(type, v)) throw InvalidData()
            if (matches(type, v)) settings.put(key, v)
        }
        val root = JSONObject().put("app", APP).put("format", FORMAT).put("settings", settings)
        val finished = history.filterNot { it.isActive }
        val done = finished.filter { isHistoryLink(it.url) }.distinctBy { it.id }
        if (done.size > HistoryJson.MAX_ITEMS) throw TooLarge()
        if (finished.size > done.size) root.put("historyOmitted", finished.size - done.size)
        if (done.isNotEmpty()) root.put("history", JSONArray(HistoryJson.encode(done.map(::portableHistoryItem))))
        // Al restaurar una página, lo que ya existe se da por visto.
        if (follows.isNotEmpty()) root.put("follows", JSONArray(FollowJson.encode(follows.map {
            it.copy(thumbnail = it.thumbnail?.takeIf(::isWebLink), pending = emptyList(), lastError = null, counter = 0)
        })))
        return root.toString(2).also {
            if (it.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw TooLarge()
            if (parse(it) == null) throw InvalidData()
        }
    }

    private fun parse(json: String): JSONObject? {
        if (json.toByteArray(Charsets.UTF_8).size > MAX_BYTES) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val format = root.opt("format") as? Int ?: return null
        if (root.optString("app") != APP || format !in 1..FORMAT || root.optJSONObject("settings") == null) return null
        if (root.has("history")) {
            val rows = root.optJSONArray("history") ?: return null
            if (historyRows(root).first.size > HistoryJson.MAX_ITEMS) return null
        }
        if (root.has("follows")) {
            val rows = root.optJSONArray("follows") ?: return null
            val follows = FollowJson.decode(rows.toString())
            if (follows.size != rows.length() || follows.any { !isWebLink(it.url) }) return null
        }
        return root
    }

    fun decodeHistory(json: String): List<DownloadItem> {
        val root = parse(json) ?: return emptyList()
        // Incluso las copias antiguas son datos externos, no autorización para actuar sobre archivos.
        return historyRows(root).first.map(::portableHistoryItem)
    }

    private fun historyRows(root: JSONObject): Pair<List<DownloadItem>, Int> {
        val rows = root.optJSONArray("history") ?: return emptyList<DownloadItem>() to 0
        val kept = HistoryJson.decode(rows.toString()).filter { isHistoryLink(it.url) }.distinctBy { it.id }
        return kept to rows.length() - kept.size
    }

    private fun isHistoryLink(url: String): Boolean = isWebLink(url) || url.startsWith(CONVERT_URL_PREFIX)

    private fun portableHistoryItem(item: DownloadItem): DownloadItem = item.copy(
        savedUris = emptyList(),
        url = if (item.url.startsWith(CONVERT_URL_PREFIX)) CONVERT_URL_PREFIX else item.url,
        thumbnail = item.thumbnail?.takeIf(::isWebLink),
    )

    private fun isWebLink(value: String): Boolean = runCatching {
        val uri = java.net.URI(value)
        uri.scheme?.lowercase() in listOf("http", "https") && !uri.rawAuthority.isNullOrBlank()
    }.getOrDefault(false)

    fun decodeFollows(json: String): List<FollowedPage> {
        val rows = parse(json)?.optJSONArray("follows") ?: return emptyList()
        return FollowJson.decode(rows.toString())
    }

    fun decode(json: String): Map<String, Any>? {
        val root = parse(json) ?: return null
        val settings = root.optJSONObject("settings") ?: return null
        val out = LinkedHashMap<String, Any>()
        KEYS.forEach { (key, type) ->
            if (!settings.has(key)) return@forEach
            val v = settings.opt(key)
            if (matches(type, v) && (key != "language" || v in listOf("en", "es"))) out[key] = v!!
        }
        return out
    }

    // null restaura el valor predeterminado; nunca se tocan claves ajenas al perfil.
    fun restore(json: String, write: (String, Any?) -> Unit): Int {
        val values = decode(json) ?: return -1
        KEYS.keys.forEach { key -> write(key, values[key] ?: if (key == "language") "en" else null) }
        return values.size
    }
}
