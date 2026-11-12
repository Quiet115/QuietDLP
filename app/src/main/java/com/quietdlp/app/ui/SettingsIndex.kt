package com.quietdlp.app.ui

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.quietdlp.app.R
import kotlinx.coroutines.delay

/**
 * El registro único de ajustes que se pueden buscar: cada uno se declara una sola vez (en qué pestaña está, su título y
 * sus palabras clave) y de aquí salen el índice del buscador y los puntos a los que salta la pantalla
 * ([settingAnchor]). Los textos están en recursos, en los dos idiomas.
 */
internal enum class SettingId(val page: SettingsPage, @StringRes val title: Int, @StringRes val keywords: Int) {
    DEFAULT_DOWNLOAD(SettingsPage.DOWNLOADS, R.string.settings_screen_default_download, R.string.kw_default_download),
    REMEMBER_LAST(SettingsPage.DOWNLOADS, R.string.settings_screen_remember_last_choice, R.string.kw_remember_last),
    SHARE_OPTIONS(SettingsPage.DOWNLOADS, R.string.settings_screen_share_option_with_settings, R.string.kw_share_options),
    CLIPBOARD(SettingsPage.DOWNLOADS, R.string.settings_screen_offer_copied_links, R.string.kw_clipboard),
    PHOTOS(SettingsPage.DOWNLOADS, R.string.settings_screen_photos_and_galleries, R.string.kw_photos),
    MUSIC_NAMES(SettingsPage.DOWNLOADS, R.string.settings_screen_name_music_artist_title, R.string.kw_music_names),
    COVER_AUDIO(SettingsPage.DOWNLOADS, R.string.settings_screen_cover_image_in_music, R.string.kw_cover_audio),
    COVER_VIDEO(SettingsPage.DOWNLOADS, R.string.settings_screen_cover_image_in_videos, R.string.kw_cover_video),
    VIDEO_FROM_PHOTOS(SettingsPage.DOWNLOADS, R.string.settings_screen_video_from_photos_and_music, R.string.kw_video_from_photos),
    SONG_LENGTH(SettingsPage.DOWNLOADS, R.string.settings_screen_song_length_in_videos, R.string.kw_song_length),
    REMEMBER_SONG(SettingsPage.DOWNLOADS, R.string.settings_screen_remember_my_choice, R.string.kw_remember_song),
    SAVE_TO(SettingsPage.DOWNLOADS, R.string.settings_screen_save_to, R.string.kw_save_to),
    PARALLEL(SettingsPage.QUEUE, R.string.settings_screen_downloads_at_once, R.string.kw_parallel),
    NETWORK(SettingsPage.QUEUE, R.string.settings_screen_network, R.string.kw_network),
    FOLLOW(SettingsPage.QUEUE, R.string.settings_screen_followed_pages, R.string.kw_follow),
    MULTI(SettingsPage.QUEUE, R.string.settings_screen_links_with_several_files, R.string.kw_multi),
    SITE_KEYS(SettingsPage.QUEUE, R.string.settings_screen_site_keys, R.string.kw_site_keys),
    BATTERY(SettingsPage.QUEUE, R.string.settings_screen_battery, R.string.kw_battery),
    SITES(SettingsPage.SITES, R.string.settings_screen_sites, R.string.kw_sites),
    BACKUP(SettingsPage.BACKUP, R.string.settings_screen_backup, R.string.kw_backup),
    FILE_NAMES(SettingsPage.ADVANCED, R.string.settings_screen_file_names, R.string.kw_file_names),
    SESSIONS(SettingsPage.ADVANCED, R.string.settings_screen_sessions, R.string.kw_sessions),
    ERROR_LOG(SettingsPage.ADVANCED, R.string.settings_screen_error_log, R.string.kw_error_log),
    PERMISSIONS(SettingsPage.PERMISSIONS, R.string.settings_permissions, R.string.kw_permissions),
    APPEARANCE(SettingsPage.APPEARANCE, R.string.settings_screen_appearance, R.string.kw_appearance),
    FOLLOW_SYSTEM(SettingsPage.APPEARANCE, R.string.follow_system_mode, R.string.kw_follow_system),
    LANGUAGE(SettingsPage.LANGUAGE, R.string.settings_language, R.string.kw_language),
    UPDATE_NOTIFY(SettingsPage.UPDATES, R.string.settings_screen_notify_me_of_new_versions, R.string.kw_update_notify),
    UPDATE_ENGINE(SettingsPage.UPDATES, R.string.settings_screen_update_the_download_engine_automatically, R.string.kw_update_engine),
    FEATURES(SettingsPage.FEATURES, R.string.settings_features, R.string.kw_features),
    FEEDBACK(SettingsPage.FEEDBACK, R.string.settings_feedback, R.string.kw_feedback),
    OFFICIAL_SOURCE(SettingsPage.ABOUT, R.string.settings_screen_official_source, R.string.kw_official_source),
    SUPPORT_CARD(SettingsPage.ABOUT, R.string.settings_screen_show_the_support_card, R.string.kw_support_card),
}

/** A qué ajuste hay que bajar y resaltar ahora mismo (lo pone el buscador al tocar un resultado). */
internal class SettingsFocus {
    var target by mutableStateOf<SettingId?>(null)
}

internal val LocalSettingsFocus = compositionLocalOf { SettingsFocus() }

@OptIn(ExperimentalFoundationApi::class)
/** Marca un ajuste como destino del buscador: baja hasta él y lo resalta un momento. */
@Composable
internal fun Modifier.settingAnchor(id: SettingId): Modifier {
    val focus = LocalSettingsFocus.current
    val requester = remember { BringIntoViewRequester() }
    val active = focus.target == id
    LaunchedEffect(active) {
        if (active) {
            delay(150)
            requester.bringIntoView()
            delay(1600)
            if (focus.target == id) focus.target = null
        }
    }
    val tint by animateColorAsState(
        if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
        label = "settingHighlight",
    )
    return this.bringIntoViewRequester(requester).drawWithContent {
        drawContent()
        if (tint.alpha > 0f) drawRoundRect(tint, cornerRadius = CornerRadius(10.dp.toPx()))
    }
}
