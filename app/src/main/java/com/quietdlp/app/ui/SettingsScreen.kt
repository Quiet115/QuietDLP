package com.quietdlp.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.quietdlp.app.Links
import com.quietdlp.app.LoginActivity
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.R
import com.quietdlp.app.data.SiteKeys
import com.quietdlp.app.data.AppOrientation
import com.quietdlp.app.data.AudioTier
import com.quietdlp.app.data.DownloadMode
import com.quietdlp.app.data.Layouts
import com.quietdlp.app.data.ErrorLogText
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.Prefs
import com.quietdlp.app.data.Quality
import com.quietdlp.app.data.SiteRule
import com.quietdlp.app.data.SiteRules
import com.quietdlp.app.data.FileNameTemplate
import com.quietdlp.app.data.Slideshow
import com.quietdlp.app.download.CookieStore
import com.quietdlp.app.download.ErrorLog
import com.quietdlp.app.update.AppUpdater
import com.quietdlp.app.update.AppIdentity
import com.quietdlp.app.Strings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.chooseFolder(uri)
    }
    val importCookies = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCookies(uri)
    }

    // Ajustes en pestañas (estándar común de las apps Quiet): primero las propias, luego las comunes.
    val pages = SettingsPage.entries.filter { it != SettingsPage.ADVANCED || vm.advanced }
    var pageName by rememberSaveable { mutableStateOf(SettingsPage.DOWNLOADS.name) }
    val current = pages.firstOrNull { it.name == pageName } ?: SettingsPage.DOWNLOADS

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Strings.get(R.string.settings_screen_settings), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.get(R.string.settings_screen_back))
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
        SettingsChips(
            labels = pages.map { it.label },
            selected = pages.indexOf(current),
            onSelect = { pageName = pages[it].name },
        )
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Layouts.MAX_SINGLE_COLUMN_DP.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(key(current) { rememberScrollState() })
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ───── Descargas ─────
            if (current == SettingsPage.DOWNLOADS) {
            SectionCard(Strings.get(R.string.settings_screen_download)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Strings.get(R.string.settings_screen_default_download), style = MaterialTheme.typography.titleSmall)
                    Hint(Strings.get(R.string.settings_screen_for_share_and_copied_links))
                    ModePicker(vm.mode, includeAsk = true) { vm.changeMode(it) }
                }
                SwitchRow(
                    Strings.get(R.string.settings_screen_remember_last_choice),
                    if (vm.rememberLast) Strings.get(R.string.settings_screen_links_opened_in_the_app) else Strings.get(R.string.settings_screen_off_starts_from_the_default),
                    vm.rememberLast, vm::changeRememberLast,
                )
                SwitchRow(
                    Strings.get(R.string.settings_screen_share_option_with_settings),
                    if (vm.shareOptions) Strings.get(R.string.settings_screen_share_shows_quietdlp_options_too) else Strings.get(R.string.settings_screen_off),
                    vm.shareOptions, vm::changeShareOptions,
                )
                SwitchRow(Strings.get(R.string.settings_screen_offer_copied_links), Strings.get(R.string.settings_screen_when_you_open_the_app), vm.clipboardOffer, vm::changeClipboardOffer)
                SwitchRow(
                    Strings.get(R.string.settings_screen_photos_and_galleries),
                    if (vm.photosEnabled) Strings.get(R.string.settings_screen_if_there_s_no_video) else Strings.get(R.string.settings_screen_off),
                    vm.photosEnabled, vm::changePhotosEnabled,
                )
                SwitchRow(
                    Strings.get(R.string.settings_screen_name_music_artist_title),
                    if (vm.musicNames) Strings.get(R.string.settings_screen_also_fills_the_artist_title) else Strings.get(R.string.settings_screen_off_files_keep_the_video),
                    vm.musicNames, vm::changeMusicNames,
                )
                SwitchRow(
                    Strings.get(R.string.settings_screen_cover_image_in_music),
                    if (vm.coverAudio) Strings.get(R.string.settings_screen_the_thumbnail_goes_inside_the) else Strings.get(R.string.settings_screen_off),
                    vm.coverAudio, vm::changeCoverAudio,
                )
                SwitchRow(
                    Strings.get(R.string.settings_screen_cover_image_in_videos),
                    if (vm.coverVideo) Strings.get(R.string.settings_screen_the_thumbnail_goes_inside_the_2) else Strings.get(R.string.settings_screen_off),
                    vm.coverVideo, vm::changeCoverVideo,
                )
                SwitchRow(
                    Strings.get(R.string.settings_screen_video_from_photos_and_music),
                    if (vm.autoPhotoVideo) Strings.get(R.string.settings_screen_makes_the_video_by_itself) else Strings.get(R.string.settings_screen_off_use_make_video_on),
                    vm.autoPhotoVideo, vm::changeAutoPhotoVideo,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Strings.get(R.string.settings_screen_song_length_in_videos), style = MaterialTheme.typography.titleSmall)
                    Hint(Strings.get(R.string.settings_screen_how_long_the_song_plays))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slideshow.SONG_LENGTHS.forEach { (seconds, label) ->
                            QuietChip(vm.songSeconds == seconds, { vm.changeSongSeconds(seconds) }, Strings.tr(label))
                        }
                    }
                }
                SwitchRow(
                    Strings.get(R.string.settings_screen_remember_my_choice),
                    if (vm.rememberSong) Strings.get(R.string.settings_screen_make_video_saves_the_length) else Strings.get(R.string.settings_screen_off_make_video_starts_from),
                    vm.rememberSong, vm::changeRememberSong,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(Strings.get(R.string.settings_screen_save_to), style = MaterialTheme.typography.titleSmall)
                    Hint(
                        if (vm.folderUri.isEmpty()) Strings.get(R.string.settings_screen_gallery_album_music_in_music, vm.folderName, vm.folderName)
                        else "${folderLabel(vm.folderUri)} › ${vm.folderName}",
                    )
                    OutlinedTextField(
                        value = vm.folderName,
                        onValueChange = vm::changeFolderName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(Strings.get(R.string.settings_screen_folder_name)) },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickFolder.launch(null) }) { Text(Strings.get(R.string.settings_screen_choose_location)) }
                        if (vm.folderUri.isNotEmpty()) TextButton(onClick = vm::resetFolder) { Text(Strings.get(R.string.settings_screen_use_default)) }
                    }
                }
            }

            // ───── Nombre de los archivos (solo Avanzado) ─────
            if (vm.advanced) NameSection(vm)
            }

            // ───── Cola ─────
            if (current == SettingsPage.QUEUE) QueueSection(vm)

            // ───── Copia de seguridad ─────
            if (current == SettingsPage.BACKUP) BackupSection(vm)

            // ───── Reglas por sitio ─────
            if (current == SettingsPage.SITES) SitesSection(vm)

            // ───── Aspecto ─────
            if (current == SettingsPage.APPEARANCE) AppearanceSection(vm)

            // ───── Actualizaciones ─────
            if (current == SettingsPage.UPDATES) {
            SectionCard(Strings.get(R.string.settings_screen_updates)) {
                Text(Strings.get(R.string.settings_screen_quietdlp, AppUpdater.currentVersion(ctx)), style = MaterialTheme.typography.titleSmall)
                if (AppUpdater.isConfigured(ctx)) {
                    SwitchRow(Strings.get(R.string.settings_screen_notify_me_of_new_versions), if (vm.appUpdateEnabled) Strings.get(R.string.settings_screen_on) else Strings.get(R.string.settings_screen_off), vm.appUpdateEnabled, vm::changeAppUpdateEnabled)
                    OutlinedButton(onClick = { vm.checkAppUpdate(manual = true) }, enabled = !vm.appUpdateBusy) {
                        Text(if (vm.appUpdateBusy) Strings.get(R.string.settings_screen_checking) else Strings.get(R.string.settings_screen_check_now))
                    }
                } else {
                    Hint(Strings.get(R.string.settings_screen_available_once_there_s_a))
                }
                vm.appUpdateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                SwitchRow(Strings.get(R.string.settings_screen_update_the_download_engine_automatically), if (vm.autoUpdate) Strings.get(R.string.settings_screen_on_at_most_once_a) else Strings.get(R.string.settings_screen_off), vm.autoUpdate, vm::changeAutoUpdate)
                if (vm.advanced) {
                    Hint(Strings.get(R.string.engine_version, vm.ytdlpVersion ?: Strings.get(R.string.unknown)))
                    OutlinedButton(onClick = vm::updateEngine, enabled = !vm.updating && vm.engineReady) {
                        Text(if (vm.updating) Strings.get(R.string.settings_screen_updating) else Strings.get(R.string.settings_screen_update_engine_now))
                    }
                    vm.updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            }

            // ───── Solo en modo avanzado ─────
            if (current == SettingsPage.ADVANCED) {
                var siteAddress by remember { mutableStateOf("") }
                SectionCard(Strings.get(R.string.settings_screen_sessions)) {
                    Hint(Strings.get(R.string.settings_screen_for_content_that_requires_signing))
                    OutlinedTextField(
                        value = siteAddress,
                        onValueChange = { siteAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(Strings.get(R.string.settings_screen_website_address)) },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = {
                            val host = CookieStore.normalizeHost(siteAddress)
                            if (host == null) {
                                Toast.makeText(ctx, Strings.get(R.string.settings_screen_enter_a_valid_address_like), Toast.LENGTH_SHORT).show()
                            } else {
                                ctx.startActivity(Intent(ctx, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_HOST, host))
                            }
                        },
                        enabled = siteAddress.isNotBlank(),
                    ) { Text(Strings.get(R.string.settings_screen_sign_in)) }
                    vm.sessions.forEach { host ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(host, style = MaterialTheme.typography.bodyMedium)
                                Hint(Strings.get(R.string.settings_screen_session_saved))
                            }
                            TextButton(onClick = { vm.logout(host) }) { Text(Strings.get(R.string.settings_screen_sign_out)) }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { importCookies.launch(arrayOf("*/*")) }) {
                            Text(if (vm.hasImportedCookies) Strings.get(R.string.settings_screen_replace_cookies_txt) else Strings.get(R.string.settings_screen_import_cookies_txt))
                        }
                        if (vm.hasImportedCookies) TextButton(onClick = vm::removeImportedCookies) { Text(Strings.get(R.string.settings_screen_remove)) }
                    }
                    Hint(Strings.get(R.string.settings_screen_cookies_txt_for_sites_that))
                    vm.accountMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }

                var errorLog by remember { mutableStateOf(ErrorLog.read(ctx)) }
                SectionCard(Strings.get(R.string.settings_screen_error_log)) {
                    val n = ErrorLogText.count(errorLog)
                    Hint(if (n == 0) Strings.get(R.string.settings_screen_no_errors) else if (n == 1) Strings.get(R.string.errors_recent_one) else Strings.get(R.string.errors_recent_many, n))
                    if (n > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("QuietDLP log", errorLog))
                                Toast.makeText(ctx, Strings.get(R.string.settings_screen_copied), Toast.LENGTH_SHORT).show()
                            }) { Text(Strings.get(R.string.settings_screen_copy)) }
                            TextButton(onClick = { ErrorLog.clear(ctx); errorLog = "" }) { Text(Strings.get(R.string.settings_screen_clear)) }
                        }
                    }
                }
            }

            if (current == SettingsPage.LANGUAGE) {
                LanguageTab(remember { LanguageStore(ctx).load() }) { chosen ->
                    LanguageStore(ctx).save(chosen)
                    // Se recrea la pantalla para que todos los textos salgan en el idioma nuevo.
                    var c = ctx
                    while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
                    (c as? android.app.Activity)?.recreate()
                }
            }
            if (current == SettingsPage.FEATURES) FeaturesTab()
            if (current == SettingsPage.FEEDBACK) FeedbackTab(remember { LanguageStore(ctx).load() })

            // ───── Apoyo y licencia ─────
            if (current == SettingsPage.ABOUT) {
            SectionCard(Strings.get(R.string.settings_screen_about)) {
                OfficialSource()
                if (Links.DONATE_URL.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(Links.DONATE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(Strings.get(R.string.settings_screen_buy_me_a_kebab_5)) }
                    SwitchRow(Strings.get(R.string.settings_screen_show_the_support_card), Strings.get(R.string.settings_screen_on_the_main_screen_below), vm.supportCard, vm::changeSupportCard)
                }
                Hint(Strings.get(R.string.settings_screen_free_software_gpl_3_0))
            }
            }
            Box(Modifier.height(24.dp))
        }
        }
        }
    }
}

/**
 * Las pestañas de Ajustes, en el orden común de las apps Quiet: primero las propias (Descargas la primera) y luego
 * Apariencia, Actualizaciones y Acerca de (Idioma, Funciones y Comentarios se añaden en sus pasos).
 */
private enum class SettingsPage(val label: Int) {
    DOWNLOADS(R.string.settings_downloads),
    QUEUE(R.string.settings_queue),
    SITES(R.string.settings_sites),
    BACKUP(R.string.settings_backup),
    ADVANCED(R.string.settings_advanced),
    APPEARANCE(R.string.settings_appearance),
    LANGUAGE(R.string.settings_language),
    UPDATES(R.string.settings_updates),
    FEATURES(R.string.settings_features),
    FEEDBACK(R.string.settings_feedback),
    ABOUT(R.string.settings_about),
}

/** Cuántas descargas a la vez, Wi-Fi o datos, qué hacer con enlaces de varios archivos y la batería. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QueueSection(vm: MainViewModel) {
    val ctx = LocalContext.current
    var restricted by remember { mutableStateOf(vm.batteryRestricted()) }
    // Al volver del ajuste de Android, comprobar de nuevo cómo quedó.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { restricted = vm.batteryRestricted() }
    SectionCard(Strings.get(R.string.settings_screen_queue)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Strings.get(R.string.settings_screen_downloads_at_once), style = MaterialTheme.typography.titleSmall)
            Hint(Strings.get(R.string.settings_screen_if_a_site_starts_refusing))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..Prefs.MAX_PARALLEL).forEach { n -> QuietChip(vm.maxParallel == n, { vm.changeMaxParallel(n) }, "$n") }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Strings.get(R.string.settings_screen_network), style = MaterialTheme.typography.titleSmall)
            Hint(
                if (vm.wifiOnly) Strings.get(R.string.settings_screen_downloads_wait_until_you_have)
                else Strings.get(R.string.settings_screen_on_mobile_data_big_downloads),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietChip(vm.wifiOnly, { vm.changeWifiOnly(true) }, Strings.get(R.string.settings_screen_wi_fi_only))
                QuietChip(!vm.wifiOnly, { vm.changeWifiOnly(false) }, Strings.get(R.string.settings_screen_wi_fi_and_data))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Strings.get(R.string.settings_screen_followed_pages), style = MaterialTheme.typography.titleSmall)
            Hint(
                if (vm.followWifiOnly) Strings.get(R.string.settings_screen_checks_and_downloads_only_on)
                else Strings.get(R.string.settings_screen_also_checks_on_mobile_data),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietChip(vm.followWifiOnly, { vm.changeFollowWifiOnly(true) }, Strings.get(R.string.settings_screen_wi_fi_only))
                QuietChip(!vm.followWifiOnly, { vm.changeFollowWifiOnly(false) }, Strings.get(R.string.settings_screen_wi_fi_and_data))
            }
        }
        SiteKeySection(vm)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Strings.get(R.string.settings_screen_links_with_several_files), style = MaterialTheme.typography.titleSmall)
            Hint(if (vm.askMulti) Strings.get(R.string.settings_screen_asks_which_ones_to_download) else Strings.get(R.string.settings_screen_downloads_all_of_them_without))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuietChip(vm.askMulti, { vm.changeAskMulti(true) }, Strings.get(R.string.settings_screen_ask_me))
                QuietChip(!vm.askMulti, { vm.changeAskMulti(false) }, Strings.get(R.string.settings_screen_download_all))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(Strings.get(R.string.settings_screen_battery), style = MaterialTheme.typography.titleSmall)
            Hint(
                if (restricted) Strings.get(R.string.settings_screen_android_may_pause_downloads_when)
                else Strings.get(R.string.settings_screen_no_limits_downloads_keep_going),
            )
            OutlinedButton(onClick = { openBatterySetting(ctx, listOnly = !restricted) }) {
                Text(if (restricted) Strings.get(R.string.settings_screen_allow_without_limits) else Strings.get(R.string.settings_screen_battery_settings))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SitesSection(vm: MainViewModel) {
    var editing by remember { mutableStateOf<SiteRule?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    SectionCard(Strings.get(R.string.settings_screen_sites)) {
        Hint(Strings.get(R.string.settings_screen_pick_a_website_and_always))
        vm.siteRules.forEach { rule ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = rule }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rule.host, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                StatusPill(Strings.tr(rule.quality.label()), Tone.ACTIVE)
            }
        }
        OutlinedButton(onClick = { adding = true }) { Text(Strings.get(R.string.settings_screen_add_site)) }
    }
    if (adding) SiteRuleEditor(null, onDismiss = { adding = false }, onRemove = null) { host, q ->
        vm.saveSiteRule(host, q); adding = false
    }
    editing?.let { rule ->
        SiteRuleEditor(rule, onDismiss = { editing = null }, onRemove = { vm.removeSiteRule(rule.host); editing = null }) { host, q ->
            vm.saveSiteRule(host, q); editing = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiteRuleEditor(
    initial: SiteRule?,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)?,
    onSave: (String, Quality) -> Unit,
) {
    var address by remember { mutableStateOf(initial?.host ?: "") }
    val modes = DownloadMode.entries.filter { it.quality != null }
    var picked by remember {
        mutableStateOf(modes.firstOrNull { it.quality == initial?.quality } ?: DownloadMode.MUSIC_BEST)
    }
    val host = SiteRules.normalizeHost(address)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) Strings.get(R.string.settings_screen_add_site) else Strings.get(R.string.settings_screen_edit_site)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    enabled = initial == null,
                    label = { Text(Strings.get(R.string.settings_screen_website_address)) },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(Strings.get(R.string.settings_screen_always_download), style = MaterialTheme.typography.labelMedium)
                ModePicker(picked, includeAsk = false) { picked = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(host ?: return@TextButton, picked.quality!!) }, enabled = host != null) { Text(Strings.get(R.string.settings_screen_save)) }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text(Strings.get(R.string.settings_screen_remove)) }
                TextButton(onClick = onDismiss) { Text(Strings.get(R.string.settings_screen_cancel)) }
            }
        },
    )
}

/** Los modos de descarga en grupos: vídeo, luego el audio de mejor a menor calidad y, si se pide, "preguntar". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModePicker(selected: DownloadMode?, includeAsk: Boolean, onPick: (DownloadMode) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(Strings.get(R.string.settings_screen_video), style = MaterialTheme.typography.labelMedium, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadMode.entries.filter { it.quality?.kind == Kind.VIDEO }.forEach { m ->
                QuietChip(selected == m, { onPick(m) }, Strings.tr(m.label).substringAfter(" · ").replaceFirstChar { it.uppercase() })
            }
        }
        AudioTier.entries.forEach { tier ->
            val modes = DownloadMode.entries.filter { it.audio?.tier == tier }
            if (modes.isNotEmpty()) {
                Text(Strings.get(R.string.settings_screen_audio, Strings.tr(tier.label)), style = MaterialTheme.typography.labelMedium, color = muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { m -> QuietChip(selected == m, { onPick(m) }, Strings.tr(m.audio!!.label)) }
                }
            }
        }
        if (includeAsk) {
            FlowRow { QuietChip(selected == DownloadMode.ASK, { onPick(DownloadMode.ASK) }, Strings.tr(DownloadMode.ASK.label)) }
        }
    }
}

/** La clave de una web de fotos: usuario y clave de API de la cuenta del propio usuario. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SiteKeySection(vm: MainViewModel) {
    var dialog by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Strings.get(R.string.settings_screen_site_keys), style = MaterialTheme.typography.titleSmall)
        Hint(if (vm.keySet) Strings.get(R.string.settings_screen_key_saved) else Strings.get(R.string.settings_screen_no_key))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = true }) { Text(if (vm.keySet) Strings.get(R.string.settings_screen_change_key) else Strings.get(R.string.settings_screen_add_key)) }
            if (vm.keySet) TextButton(onClick = vm::clearSiteKey) { Text(Strings.get(R.string.settings_screen_remove)) }
        }
    }
    if (dialog) {
        var address by remember { mutableStateOf("") }
        var user by remember { mutableStateOf("") }
        var key by remember { mutableStateOf("") }
        val ok = SiteKeys.category(address) != null && SiteKeys.valid(SiteKeys.cleanUser(user), SiteKeys.cleanKey(key))
        AlertDialog(
            onDismissRequest = { dialog = false },
            title = { Text(Strings.get(R.string.settings_screen_site_key)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(address, { address = it }, label = { Text(Strings.get(R.string.settings_screen_site_address)) }, singleLine = true)
                    OutlinedTextField(user, { user = it }, label = { Text(Strings.get(R.string.settings_screen_user_id)) }, singleLine = true)
                    OutlinedTextField(key, { key = it }, label = { Text(Strings.get(R.string.settings_screen_api_key)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { if (vm.saveSiteKey(address, user, key)) dialog = false }, enabled = ok) { Text(Strings.get(R.string.settings_screen_save)) } },
            dismissButton = { TextButton(onClick = { dialog = false }) { Text(Strings.get(R.string.settings_screen_cancel)) } },
        )
    }
}

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/** De dónde viene la app de verdad y cómo comprobar que esta copia lo es. */
@Composable
private fun OfficialSource() {
    val ctx = LocalContext.current
    val sha = remember { AppIdentity.signatureSha256(ctx) }
    fun copy(label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(ctx, Strings.get(R.string.settings_screen_copied), Toast.LENGTH_SHORT).show()
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(Strings.get(R.string.settings_screen_official_source), style = MaterialTheme.typography.titleSmall)
        Hint(Strings.get(R.string.settings_screen_quietdlp_is_only_on_github))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                runCatching {
                    ctx.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(Links.SOURCE_URL + "/releases")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            }) { Text(Strings.get(R.string.settings_screen_open_page)) }
            TextButton(onClick = { copy(Strings.get(R.string.settings_screen_quietdlp_link), Links.SOURCE_URL) }) { Text(Strings.get(R.string.settings_screen_copy_link)) }
        }
        if (sha != null) {
            Text(Strings.get(R.string.settings_screen_signature_of_this_copy_sha), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(AppIdentity.grouped(sha), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Hint(Strings.get(R.string.settings_screen_it_s_the_same_in)) }
                TextButton(onClick = { copy(Strings.get(R.string.settings_screen_quietdlp_signature), sha) }) { Text(Strings.get(R.string.settings_screen_copy)) }
            }
        }
    }
}

/** Cómo se llaman los archivos descargados (modo Avanzado). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NameSection(vm: MainViewModel) {
    SectionCard(Strings.get(R.string.settings_screen_file_names)) {
        Hint(Strings.get(R.string.settings_screen_how_downloaded_files_are_named))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FileNameTemplate.PRESETS.forEach { (label, template) ->
                QuietChip(vm.nameTemplate == template, { vm.changeNameTemplate(template) }, Strings.tr(label))
            }
        }
        OutlinedTextField(
            value = vm.nameTemplate,
            onValueChange = vm::changeNameTemplate,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(Strings.get(R.string.settings_screen_name)) },
            singleLine = true,
        )
        Hint(Strings.get(R.string.settings_screen_marks_title_channel_date_id))
        Hint(Strings.get(R.string.settings_screen_looks_like, FileNameTemplate.preview(vm.nameTemplate)))
    }
}

/** Guardar los ajustes en un archivo y ponerlos de vuelta (por ejemplo en otro móvil). */
@Composable
private fun BackupSection(vm: MainViewModel) {
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) vm.saveSettingsTo(uri)
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restoreSettingsFrom(uri)
    }
    SectionCard(Strings.get(R.string.settings_screen_backup)) {
        Hint(Strings.get(R.string.settings_screen_your_history_stays_when_you))
        Hint(Strings.get(R.string.settings_screen_settings_and_history_sessions_the))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { save.launch("quietdlp-backup.json") }) { Text(Strings.get(R.string.settings_screen_save_backup)) }
            OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text(Strings.get(R.string.settings_screen_restore_backup)) }
        }
        vm.backupMessage?.let { Hint(it) }
    }
}

/** "primary:Download/Videos" → "Download/Videos". */
private fun folderLabel(uri: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
    val path = id.substringAfter(':', "")
    if (path.isEmpty()) Strings.get(R.string.settings_screen_internal_storage) else path
}.getOrDefault(Strings.get(R.string.settings_screen_chosen_folder))

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(vm: MainViewModel) {
    var editing by remember { mutableStateOf<PaletteSpec?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    SectionCard(Strings.get(R.string.settings_screen_appearance)) {
        Text(Strings.get(R.string.settings_screen_screen_orientation), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOrientation.entries.forEach { o -> QuietChip(vm.orientation == o, { vm.changeOrientation(o) }, Strings.tr(o.label)) }
        }
        Hint(Strings.get(R.string.settings_screen_auto_follows_your_phone_s))
        val groups = listOf(PaletteGroup.DARK, PaletteGroup.COLORFUL, PaletteGroup.LIGHT)
        groups.forEach { g ->
            Text(Strings.tr(g.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Palettes.presets.filter { it.group == g }.forEach { p ->
                    PaletteSwatch(p, vm.paletteId == p.id) { vm.choosePalette(p.id) }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(Strings.get(R.string.settings_screen_system), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow {
                val sys = PaletteSpec(
                    Palettes.SYSTEM, Strings.get(R.string.settings_screen_wallpaper), PaletteGroup.DARK, true,
                    0xFF1B1B1F.toInt(), 0xFF2A2A30.toInt(), 0xFFA8C7FA.toInt(), 0xFFE8B4C8.toInt(),
                )
                PaletteSwatch(sys, vm.paletteId == Palettes.SYSTEM) { vm.choosePalette(Palettes.SYSTEM) }
            }
        }
        Text(Strings.tr(PaletteGroup.CUSTOM.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            vm.customPalettes.forEach { p ->
                PaletteSwatch(p, vm.paletteId == p.id) { vm.choosePalette(p.id) }
            }
            if (vm.customPalettes.size < Palettes.MAX_CUSTOM) {
                Box(
                    Modifier
                        .size(width = 86.dp, height = 68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { creating = true },
                    contentAlignment = Alignment.Center,
                ) { Text(Strings.get(R.string.settings_screen_new), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
        }
        vm.customPalettes.firstOrNull { it.id == vm.paletteId }?.let { sel ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = sel }) { Text(Strings.get(R.string.settings_screen_edit, Strings.tr(sel.name))) }
                TextButton(onClick = { vm.deleteCustomPalette(sel.id) }) { Text(Strings.get(R.string.settings_screen_delete)) }
            }
        }
    }
    if (creating) {
        PaletteEditor(null, onDismiss = { creating = false }) { vm.saveCustomPalette(it); creating = false }
    }
    editing?.let { sel ->
        PaletteEditor(sel, onDismiss = { editing = null }) { vm.saveCustomPalette(it); editing = null }
    }
}

/** Crea o edita una paleta propia: cuatro colores a elegir, con un punto de partida sugerido. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteEditor(initial: PaletteSpec?, onDismiss: () -> Unit, onSave: (PaletteSpec) -> Unit) {
    val start = initial ?: Palettes.presets.first()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var bg by remember { mutableIntStateOf(start.background) }
    var surface by remember { mutableIntStateOf(start.surface) }
    var accent by remember { mutableIntStateOf(start.accent) }
    var accent2 by remember { mutableIntStateOf(start.accent2) }
    var open by remember { mutableIntStateOf(-1) }
    var dark by remember { mutableStateOf(start.dark) }
    var hue by remember { mutableFloatStateOf(Palettes.toHsv(start.accent)[0]) }
    val id = remember { initial?.id ?: "c" + System.currentTimeMillis() }
    val preview = Palettes.fromColors(id, name, bg, surface, accent, accent2)
    val scheme = Palettes.scheme(preview)

    fun suggest() {
        val c = Palettes.suggest(dark, hue.toInt(), hue.toInt(), 40)
        bg = c[0]; surface = c[1]; accent = c[2]; accent2 = c[3]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) Strings.get(R.string.settings_screen_new_palette) else Strings.get(R.string.settings_screen_edit_palette)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) },
                    label = { Text(Strings.get(R.string.settings_screen_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                // Vista previa con los colores elegidos; el texto toma solo el color de mejor contraste.
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(preview.background)).padding(12.dp)) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(preview.surface)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(Strings.get(R.string.settings_screen_quietdlp_2), color = scheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text(Strings.get(R.string.settings_screen_save_a_video_song_or), color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(CircleShape).background(Color(preview.accent)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text(Strings.get(R.string.settings_screen_download), color = scheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                            Box(Modifier.clip(CircleShape).background(Color(preview.accent2)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text(Strings.get(R.string.settings_screen_done), color = scheme.onSecondary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Text(Strings.get(R.string.settings_screen_start_from), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietChip(dark, { dark = true; suggest() }, Strings.get(R.string.settings_screen_dark))
                    QuietChip(!dark, { dark = false; suggest() }, Strings.get(R.string.settings_screen_light))
                }
                Slider(value = hue, onValueChange = { hue = it; suggest() }, valueRange = 0f..359f)

                Text(Strings.get(R.string.settings_screen_colors), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorRow(Strings.get(R.string.settings_screen_background), bg, open == 0, { open = if (open == 0) -1 else 0 }) { bg = it }
                ColorRow(Strings.get(R.string.settings_screen_cards), surface, open == 1, { open = if (open == 1) -1 else 1 }) { surface = it }
                ColorRow(Strings.get(R.string.settings_screen_accent), accent, open == 2, { open = if (open == 2) -1 else 2 }) { accent = it }
                ColorRow(Strings.get(R.string.settings_screen_second_accent), accent2, open == 3, { open = if (open == 3) -1 else 3 }) { accent2 = it }
                Hint(Strings.get(R.string.settings_screen_text_color_is_chosen_automatically))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(preview) }) { Text(Strings.get(R.string.settings_screen_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.get(R.string.settings_screen_cancel)) } },
    )
}

/** Una fila de color: muestra el color y, al tocarla, deja ajustarlo con deslizadores o escribiendo su código. */
@Composable
private fun ColorRow(label: String, color: Int, expanded: Boolean, onToggle: () -> Unit, onChange: (Int) -> Unit) {
    val hsv = Palettes.toHsv(color)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(Palettes.toHex(color), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)) {
                Text(Strings.get(R.string.settings_screen_hue), style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[0], onValueChange = { onChange(Palettes.hsv(it, hsv[1], hsv[2])) }, valueRange = 0f..359f)
                Text(Strings.get(R.string.settings_screen_saturation), style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[1], onValueChange = { onChange(Palettes.hsv(hsv[0], it, hsv[2])) }, valueRange = 0f..1f)
                Text(Strings.get(R.string.settings_screen_brightness), style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[2], onValueChange = { onChange(Palettes.hsv(hsv[0], hsv[1], it)) }, valueRange = 0f..1f)
                var hex by remember(color) { mutableStateOf(Palettes.toHex(color)) }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v.take(7)
                        Palettes.parseHex(hex)?.let(onChange)
                    },
                    label = { Text(Strings.get(R.string.settings_screen_code)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
