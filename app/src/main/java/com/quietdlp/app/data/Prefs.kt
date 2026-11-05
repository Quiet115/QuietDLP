package com.quietdlp.app.data

import android.content.Context
import com.quietdlp.app.ui.Palettes

/** Ajustes sencillos guardados en SharedPreferences. */
class Prefs(context: Context) {
    private val sp = context.applicationContext
        .getSharedPreferences("quietdlp", Context.MODE_PRIVATE)

    /** true = al compartir desde otra app se descarga directo, sin abrir QuietDLP. */
    var quickShareDirect: Boolean
        get() = sp.getBoolean("quick_direct", true)
        set(v) = sp.edit().putBoolean("quick_direct", v).apply()

    var quickKind: Kind
        get() = runCatching { Kind.valueOf(sp.getString("quick_kind", Kind.VIDEO.name)!!) }
            .getOrDefault(Kind.VIDEO)
        set(v) = sp.edit().putString("quick_kind", v.name).apply()

    /** 0 = mejor calidad. */
    var quickMaxHeight: Int
        get() = sp.getInt("quick_height", 0)
        set(v) = sp.edit().putInt("quick_height", v).apply()

    var audioFormat: String
        get() = sp.getString("audio_format", "m4a") ?: "m4a"
        set(v) = sp.edit().putString("audio_format", v).apply()

    /** Hacer solo un vídeo con las fotos de una publicación y su canción. Desactivado por defecto. */
    var autoPhotoVideo: Boolean
        get() = sp.getBoolean("auto_photo_video", false)
        set(v) = sp.edit().putBoolean("auto_photo_video", v).apply()

    /** "Make video" guarda la duración que se elige (si no, vale solo esa vez). Desactivado por defecto. */
    var rememberSong: Boolean
        get() = sp.getBoolean("remember_song", false)
        set(v) = sp.edit().putBoolean("remember_song", v).apply()

    /** Cuánto suena la canción en el vídeo de fotos con música, en segundos. 0 = entera (por defecto). */
    var songSeconds: Int
        get() = sp.getInt("song_seconds", 0).coerceAtLeast(0)
        set(v) = sp.edit().putInt("song_seconds", v).apply()

    var audioBitrate: Int
        get() = sp.getInt("audio_bitrate", 0)
        set(v) = sp.edit().putInt("audio_bitrate", v).apply()

    /** Avisar de versiones nuevas de la propia app. Activado por defecto. */
    var appUpdateCheck: Boolean
        get() = sp.getBoolean("app_update_check", true)
        set(v) = sp.edit().putBoolean("app_update_check", v).apply()

    var lastAppCheckMs: Long
        get() = sp.getLong("last_app_check_ms", 0L)
        set(v) = sp.edit().putLong("last_app_check_ms", v).apply()

    /** Última versión que el usuario dijo "más tarde": no se vuelve a ofrecer sola. */
    var dismissedAppVersion: String
        get() = sp.getString("dismissed_app_version", "") ?: ""
        set(v) = sp.edit().putString("dismissed_app_version", v).apply()

    /** Si el enlace no tiene vídeo, intentar descargar sus fotos (gallery-dl). Activado por defecto. */
    var photosEnabled: Boolean
        get() = sp.getBoolean("photos_enabled", true)
        set(v) = sp.edit().putBoolean("photos_enabled", v).apply()

    /** Actualización automática de yt-dlp. Activada por defecto: sin ella las descargas acaban fallando. */
    var autoUpdate: Boolean
        get() = sp.getBoolean("auto_update", true)
        set(v) = sp.edit().putBoolean("auto_update", v).apply()

    /** Si ya se mostró el aviso inicial sobre la actualización automática. */
    var updateNoticeSeen: Boolean
        get() = sp.getBoolean("update_notice_seen", false)
        set(v) = sp.edit().putBoolean("update_notice_seen", v).apply()

    var lastUpdateMs: Long
        get() = sp.getLong("last_update_ms", 0L)
        set(v) = sp.edit().putLong("last_update_ms", v).apply()

    /**
     * Qué se descarga por defecto. Si el usuario nunca lo eligió, se deduce de los ajustes antiguos
     * (interruptor de descarga directa + calidad); en un móvil sin ajustes antiguos sale la mejor calidad.
     */
    var mode: DownloadMode
        get() = DownloadMode.fromKey(sp.getString("mode", null))
            ?: DownloadMode.fromLegacy(quickShareDirect, quickKind, quickMaxHeight, audioFormat)
        set(v) = sp.edit().putString("mode", v.key).apply()

    /** Si es true, la calidad que se eligió en la última descarga manual queda preseleccionada. Desactivado por defecto. */
    var rememberLast: Boolean
        get() = sp.getBoolean("remember_last", false)
        set(v) = sp.edit().putBoolean("remember_last", v).apply()

    var lastKind: Kind
        get() = runCatching { Kind.valueOf(sp.getString("last_kind", Kind.VIDEO.name)!!) }
            .getOrDefault(Kind.VIDEO)
        set(v) = sp.edit().putString("last_kind", v.name).apply()

    var lastHeight: Int
        get() = sp.getInt("last_height", 0)
        set(v) = sp.edit().putInt("last_height", v).apply()

    var lastAudioFormat: String
        get() = sp.getString("last_audio_format", "m4a") ?: "m4a"
        set(v) = sp.edit().putString("last_audio_format", v).apply()

    var lastAudioBitrate: Int
        get() = sp.getInt("last_audio_bitrate", 0)
        set(v) = sp.edit().putInt("last_audio_bitrate", v).apply()

    /** Ofrecer descargar el enlace que el usuario tiene copiado al abrir la app. Activado por defecto. */
    var clipboardOffer: Boolean
        get() = sp.getBoolean("clipboard_offer", true)
        set(v) = sp.edit().putBoolean("clipboard_offer", v).apply()

    /** Huella (SHA-256) del último enlace copiado que ya se ofreció o se descartó: no se vuelve a ofrecer. */
    var lastClipKey: String
        get() = sp.getString("last_clip_key", "") ?: ""
        set(v) = sp.edit().putString("last_clip_key", v).apply()

    /** Marca de tiempo del último contenido del portapapeles que ya se leyó. */
    var lastClipMs: Long
        get() = sp.getLong("last_clip_ms", 0L)
        set(v) = sp.edit().putLong("last_clip_ms", v).apply()

    /** Paleta de colores elegida ("obsidian" por defecto; "wallpaper" = colores del sistema, antes "system"). */
    var paletteId: String
        get() = Palettes.normalizeId(sp.getString("palette", "obsidian"))
        set(v) = sp.edit().putString("palette", v).apply()

    /** Paletas creadas por el usuario, en JSON (ver Palettes.encodeCustom). */
    var customPalettes: String
        get() = sp.getString("custom_palettes", "[]") ?: "[]"
        set(v) = sp.edit().putString("custom_palettes", v).apply()

    /** Modo avanzado: muestra calidades, formatos, sesiones y detalles del motor. Desactivado por defecto. */
    var advanced: Boolean
        get() = sp.getBoolean("advanced", false)
        set(v) = sp.edit().putBoolean("advanced", v).apply()

    /** Nombre de la carpeta de descargas ("QuietDLP" por defecto). Se crea dentro del álbum o de la carpeta elegida. */
    var folderName: String
        get() = FolderName.clean(sp.getString("folder_name", FolderName.DEFAULT) ?: FolderName.DEFAULT)
        set(v) = sp.edit().putString("folder_name", v).apply()

    /** Carpeta elegida por el usuario (permiso persistente de Android). Vacío = álbum QuietDLP en la galería. */
    var folderUri: String
        get() = sp.getString("folder_uri", "") ?: ""
        set(v) = sp.edit().putString("folder_uri", v).apply()

    /** Orientación de la pantalla: "auto" (sigue al teléfono), "portrait" o "landscape". */
    var orientation: AppOrientation
        get() = AppOrientation.fromKey(sp.getString("orientation", null))
        set(v) = sp.edit().putString("orientation", v.key).apply()

    /** Mostrar la tarjeta de apoyo en la pantalla principal. Activada por defecto. */
    var supportCard: Boolean
        get() = sp.getBoolean("support_card", true)
        set(v) = sp.edit().putBoolean("support_card", v).apply()

    /** Cuántas descargas van a la vez (1 a 5). */
    var maxParallel: Int
        get() = sp.getInt("max_parallel", MAX_PARALLEL).coerceIn(1, MAX_PARALLEL)
        set(v) = sp.edit().putInt("max_parallel", v.coerceIn(1, MAX_PARALLEL)).apply()

    /** Descargar solo con Wi-Fi (una red sin tarifa). Si no, con datos se avisa antes de una descarga grande. */
    var wifiOnly: Boolean
        get() = sp.getBoolean("wifi_only", false)
        set(v) = sp.edit().putBoolean("wifi_only", v).apply()

    /** Usuario y clave de API de una web de fotos (de la cuenta del propio usuario). No entran en la copia de seguridad. */
    var siteAddress: String
        get() = sp.getString("site_address", "") ?: ""
        set(v) = sp.edit().putString("site_address", v).apply()

    var siteUser: String
        get() = sp.getString("site_user", "") ?: ""
        set(v) = sp.edit().putString("site_user", v).apply()

    var siteKey: String
        get() = sp.getString("site_key", "") ?: ""
        set(v) = sp.edit().putString("site_key", v).apply()

    /** Revisar las páginas seguidas (y bajar lo nuevo) solo con Wi-Fi. */
    var followWifiOnly: Boolean
        get() = sp.getBoolean("follow_wifi", true)
        set(v) = sp.edit().putBoolean("follow_wifi", v).apply()

    /** Preguntar antes de descargar un enlace con varios archivos. Si es false, se descarga todo sin preguntar. */
    var askMulti: Boolean
        get() = sp.getBoolean("ask_multi", true)
        set(v) = sp.edit().putBoolean("ask_multi", v).apply()

    /** No volver a sugerir quitar la restricción de batería cuando hay una cola larga. */
    var batteryHintOff: Boolean
        get() = sp.getBoolean("battery_hint_off", false)
        set(v) = sp.edit().putBoolean("battery_hint_off", v).apply()

    /** Lo último que se eligió para los subtítulos: encendidos, idiomas (separados por coma) y dónde se guardan. */
    var subsOn: Boolean
        get() = sp.getBoolean("subs_on", false)
        set(v) = sp.edit().putBoolean("subs_on", v).apply()

    var subLangs: List<String>
        get() = (sp.getString("sub_langs", "") ?: "").split(",").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("sub_langs", v.joinToString(",")).apply()

    var subMode: SubtitleMode
        get() = SubtitleMode.fromId(sp.getString("sub_mode", null))
        set(v) = sp.edit().putString("sub_mode", v.id).apply()

    /** Portada (la miniatura dentro del archivo): por defecto sí en audio y no en vídeo. */
    var coverAudio: Boolean
        get() = sp.getBoolean("cover_audio", true)
        set(v) = sp.edit().putBoolean("cover_audio", v).apply()

    var coverVideo: Boolean
        get() = sp.getBoolean("cover_video", false)
        set(v) = sp.edit().putBoolean("cover_video", v).apply()

    fun coverFor(kind: Kind): Boolean = when (kind) {
        Kind.AUDIO -> coverAudio
        Kind.VIDEO -> coverVideo
        Kind.PHOTO -> false
    }

    /** Los ajustes (y el historial, si se da) en un texto JSON, para guardarlos en un archivo (ver [SettingsBackup]). */
    fun exportSettings(history: List<DownloadItem> = emptyList(), follows: List<FollowedPage> = emptyList()): String =
        SettingsBackup.encode(sp.all, history, follows)

    /** Aplica los ajustes de una copia. Devuelve cuántos puso, o -1 si el texto no es una copia de QuietDLP. */
    fun importSettings(json: String): Int {
        val values = SettingsBackup.decode(json) ?: return -1
        val e = sp.edit()
        values.forEach { (key, v) ->
            when (v) {
                is Boolean -> e.putBoolean(key, v)
                is Int -> e.putInt(key, v)
                is String -> e.putString(key, v)
            }
        }
        e.apply()
        return values.size
    }

    /** Poner a la música el nombre "Artista - Título" y esos datos dentro del archivo. */
    var musicNames: Boolean
        get() = sp.getBoolean("music_names", true)
        set(v) = sp.edit().putBoolean("music_names", v).apply()

    /** El nombre de los archivos (modo Avanzado), con marcas como {title} (ver [FileNameTemplate]). */
    var nameTemplate: String
        get() = sp.getString("name_template", FileNameTemplate.DEFAULT) ?: FileNameTemplate.DEFAULT
        set(v) = sp.edit().putString("name_template", v.take(200)).apply()

    /** SponsorBlock: "off", "remove" o "mark", y las clases de partes (separadas por coma). */
    var sponsorMode: String
        get() = sp.getString("sponsor_mode", "off") ?: "off"
        set(v) = sp.edit().putString("sponsor_mode", v).apply()

    var sponsorCats: List<String>
        get() = SponsorCategories.clean(
            (sp.getString("sponsor_cats", null) ?: SponsorCategories.DEFAULT.joinToString(",")).split(",").toSet(),
        )
        set(v) = sp.edit().putString("sponsor_cats", v.joinToString(",")).apply()

    /** Capítulos: "off", "embed" o "split". Por defecto van dentro del archivo. */
    var chapterMode: String
        get() = sp.getString("chapter_mode", "embed") ?: "embed"
        set(v) = sp.edit().putString("chapter_mode", v).apply()

    /** Reglas por sitio, en JSON (ver SiteRules.encode). */
    var siteRules: String
        get() = sp.getString("site_rules", "[]") ?: "[]"
        set(v) = sp.edit().putString("site_rules", v).apply()

    /**
     * Qué descargar sin preguntar para este enlace: la regla de su sitio si la hay; si no, el modo por defecto.
     * null = preguntar (se abre la app para elegir).
     */
    fun qualityFor(url: String): Quality? =
        SiteRules.match(SiteRules.decode(siteRules), url)?.quality ?: modeQuality()

    /** Calidad de lo que se descarga sin preguntar. En modo "preguntar" no hay: se abre la app. */
    fun modeQuality(): Quality? = mode.quality

    companion object {
        const val MAX_PARALLEL = 5
    }
}
