package com.quietdlp.app.data

import org.json.JSONArray
import org.json.JSONObject

// El historial guarda referencias a archivos. Los permisos de carpeta solo valen en el dispositivo que los concedió.
object SettingsBackup {
    private const val APP = "QuietDLP"
    private const val FORMAT = 1

    const val MAX_BYTES = 8_000_000

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

        data class Ready(val values: Map<String, Any>, val history: Int, val follows: Int) : Check
    }

    fun check(json: String): Check {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return Check.NotAProfile
        if (root.optString("app") != APP) return Check.NotAProfile
        val format = root.optInt("format", 0)
        if (format > FORMAT) return Check.FromNewerVersion
        if (format < 1) return Check.NotAProfile
        val values = decode(json) ?: return Check.NotAProfile
        return Check.Ready(values, decodeHistory(json).size, decodeFollows(json).size)
    }

    fun changes(values: Map<String, Any>, current: Map<String, *>): Map<Group, Int> {
        val out = LinkedHashMap<Group, Int>()
        for ((key, v) in values) {
            if (current[key] == v) continue
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
            if (matches(type, v)) settings.put(key, v)
        }
        val root = JSONObject().put("app", APP).put("format", FORMAT).put("settings", settings)
        val done = history.filterNot { it.isActive }
        if (done.isNotEmpty()) root.put("history", JSONArray(HistoryJson.encode(done)))
        // Al restaurar una página, lo que ya existe se da por visto.
        if (follows.isNotEmpty()) root.put("follows", JSONArray(FollowJson.encode(follows.map { it.copy(pending = emptyList(), lastError = null, counter = 0) })))
        return root.toString(2)
    }

    private fun parse(json: String): JSONObject? {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return if (root.optString("app") == APP && root.optInt("format", 0) in 1..FORMAT) root else null
    }

    fun decodeHistory(json: String): List<DownloadItem> {
        val rows = parse(json)?.optJSONArray("history") ?: return emptyList()
        return HistoryJson.decode(rows.toString())
    }

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
            if (matches(type, v)) out[key] = v!!
        }
        return out
    }
}
