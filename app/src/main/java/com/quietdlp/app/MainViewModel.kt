package com.quietdlp.app

import android.app.Application
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.Intent
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import com.quietdlp.app.data.AppOrientation
import com.quietdlp.app.data.PlaylistEntry
import com.quietdlp.app.data.PlaylistInfo
import com.quietdlp.app.data.FollowEngine
import com.quietdlp.app.data.FollowInterval
import com.quietdlp.app.data.FollowUrl
import com.quietdlp.app.data.SiteKeys
import com.quietdlp.app.data.FollowedPage
import com.quietdlp.app.download.FollowChecker
import com.quietdlp.app.download.FollowProbe
import com.quietdlp.app.download.FollowScheduler
import com.quietdlp.app.download.FollowStore
import com.quietdlp.app.data.PlaylistProbe
import com.quietdlp.app.data.State
import com.quietdlp.app.download.Connectivity
import kotlinx.coroutines.async
import java.util.Locale
import java.util.UUID
import com.quietdlp.app.data.AudioOption
import com.quietdlp.app.data.AudioOptions
import com.quietdlp.app.data.ConvertTarget
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.FolderName
import com.quietdlp.app.ui.PaletteSpec
import com.quietdlp.app.ui.Palettes
import com.quietdlp.app.data.DownloadMode
import com.quietdlp.app.data.Duplicates
import com.quietdlp.app.data.SiteRule
import com.quietdlp.app.data.SiteRules
import com.quietdlp.app.download.DownloadIndex
import com.quietdlp.app.download.MediaFiles
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.ChapterMode
import com.quietdlp.app.data.CoverOptions
import com.quietdlp.app.data.MediaProbe
import com.quietdlp.app.data.QualityLevel
import com.quietdlp.app.data.SettingsBackup
import com.quietdlp.app.data.SponsorCategories
import com.quietdlp.app.data.SponsorChoice
import com.quietdlp.app.data.SponsorMode
import com.quietdlp.app.data.SubtitleChoice
import com.quietdlp.app.data.SubtitleMode
import com.quietdlp.app.data.SubtitleProbe
import com.quietdlp.app.data.Storyboard
import com.quietdlp.app.data.SubtitleTrack
import com.quietdlp.app.data.TrimRange
import com.quietdlp.app.data.Prefs
import com.quietdlp.app.data.Quality
import android.net.Uri
import com.quietdlp.app.download.CookieStore
import com.quietdlp.app.download.DownloadRepository
import com.quietdlp.app.download.DownloadService
import com.quietdlp.app.download.Engine
import com.quietdlp.app.download.GalleryDl
import com.quietdlp.app.update.AppUpdater
import com.quietdlp.app.download.Errors
import com.quietdlp.app.download.UrlUtils
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MediaInfo(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val uploader: String?,
    val durationSec: Int,
    val heights: List<Int>,
    /** > 0 → el enlace no tiene vídeo pero sí fotos (se descargan con gallery-dl). */
    val photoCount: Int = 0,
    /** > 0 → el enlace es una lista con tantos vídeos (se pueden bajar todos). */
    val playlistCount: Int = 0,
    /** Los subtítulos que ofrece el sitio (vacío si no hay o si el enlace es una lista). */
    val subtitles: List<SubtitleTrack> = emptyList(),
    /** Fotogramas del vídeo para ver qué parte se recorta (solo algunos sitios, como YouTube). */
    val storyboard: Storyboard? = null,
    /** Cuántos capítulos trae el vídeo. */
    val chapterCount: Int = 0,
)

/** Un enlace con varios archivos: se pregunta cuáles bajar antes de empezar (ver [MainViewModel.confirmSelection]). */
class SelectionPrompt(val title: String, val entries: List<PlaylistEntry>, val quality: Quality)

/** Hay una descarga grande y el móvil está con datos: bajar ya o esperar al Wi-Fi. [start] recibe "esperar al Wi-Fi". */
class DataPrompt(val start: (Boolean) -> Unit)

/**
 * Una web pidió iniciar sesión. [quality] != null → al guardar la sesión se reintenta la descarga;
 * null → se vuelve a analizar el enlace (flujo manual). [oldItemId] = descarga fallida a sustituir.
 */
data class LoginPrompt(
    val host: String,
    val url: String,
    val quality: Quality? = null,
    val title: String? = null,
    val thumbnail: String? = null,
    val oldItemId: String? = null,
)

/** Desde cuántos archivos se considera una cola larga (y se sugiere quitar la restricción de batería). */
private const val LONG_QUEUE = 5

class MainViewModel(private val app: Application) : AndroidViewModel(app) {

    val prefs = Prefs(app)

    // ── Pegar enlace y analizar ──
    var url by mutableStateOf("")
    var analyzing by mutableStateOf(false)
        private set
    var info by mutableStateOf<MediaInfo?>(null)
        private set
    var error by mutableStateOf<String?>(null)

    // ── Elección para la descarga manual ──
    var kind by mutableStateOf(Kind.VIDEO)
    var height by mutableIntStateOf(0)
    private val startAudio = AudioOptions.find(prefs.audioFormat, prefs.audioBitrate)
    var audioFormat by mutableStateOf(startAudio.format)
    var audioBitrate by mutableIntStateOf(startAudio.bitrate)

    // ── Portada: la miniatura dentro del archivo ──
    /** Lo que muestra el interruptor de la tarjeta; empieza con lo que diga Ajustes para este tipo de descarga. */
    var cover by mutableStateOf(false)
        private set

    fun changeCover(v: Boolean) {
        cover = v
    }

    // ── SponsorBlock, capítulos y nombre de los archivos (modo Avanzado) ──
    var sponsorMode by mutableStateOf(SponsorMode.fromId(prefs.sponsorMode))
        private set
    var sponsorCats by mutableStateOf(prefs.sponsorCats)
        private set
    var chapterMode by mutableStateOf(ChapterMode.fromId(prefs.chapterMode))
        private set
    var nameTemplate by mutableStateOf(prefs.nameTemplate)
        private set

    /** SponsorBlock solo tiene datos de YouTube, y no se mezcla con un trozo (sus tiempos son los del vídeo entero). */
    fun canSponsor(i: MediaInfo): Boolean =
        advanced && SponsorChoice.supports(i.url) && i.photoCount == 0 && i.playlistCount == 0 && !trimOn

    fun canChapters(i: MediaInfo): Boolean =
        advanced && i.chapterCount > 0 && i.photoCount == 0 && i.playlistCount == 0 && !trimOn

    fun chooseSponsorMode(m: SponsorMode?) {
        sponsorMode = m
        prefs.sponsorMode = m?.id ?: "off"
    }

    fun toggleSponsorCategory(code: String) {
        sponsorCats = SponsorCategories.clean(if (code in sponsorCats) sponsorCats - code else sponsorCats + code)
        prefs.sponsorCats = sponsorCats
    }

    fun chooseChapterMode(m: ChapterMode) {
        chapterMode = m
        prefs.chapterMode = m.id
    }

    fun changeNameTemplate(v: String) {
        nameTemplate = v.take(200)
        prefs.nameTemplate = nameTemplate
    }

    private fun sponsorChoice(i: MediaInfo): SponsorChoice? {
        val m = sponsorMode ?: return null
        return if (canSponsor(i) && sponsorCats.isNotEmpty()) SponsorChoice(m, sponsorCats) else null
    }

    /** Quitar partes con SponsorBlock cambia la duración: los subtítulos no se piden para no dejarlos desfasados. */
    fun removingParts(i: MediaInfo): Boolean = sponsorChoice(i)?.mode == SponsorMode.REMOVE

    private fun chapterChoice(i: MediaInfo): ChapterMode = if (canChapters(i)) chapterMode else ChapterMode.OFF

    /** Si se pide de verdad: encendida y posible para este tipo de descarga (WAV y fotos no pueden llevar imagen). */
    private fun coverWanted(q: Quality): Boolean = cover && CoverOptions.available(q.kind, q.audioFormat)

    // ── Recortar: bajar solo un trozo ──
    var trimOn by mutableStateOf(false)
        private set
    var trimStart by mutableIntStateOf(0)
        private set
    var trimEnd by mutableIntStateOf(0)
        private set

    /** Se puede recortar un vídeo o audio suelto cuya duración se conoce (no listas, fotos ni directos). */
    fun canTrim(i: MediaInfo): Boolean = i.photoCount == 0 && i.playlistCount == 0 && i.durationSec > 1

    fun changeTrimOn(v: Boolean) {
        trimOn = v
        if (v) info?.storyboard?.let(::loadFrames)
    }

    // Fotogramas: las imágenes con cuadrícula se bajan al encender el recorte y de cada una se recorta el fotograma pedido.
    private val grids = mutableStateMapOf<Int, android.graphics.Bitmap>()
    private val cropped = HashMap<Triple<Int, Int, Int>, androidx.compose.ui.graphics.ImageBitmap>()
    private var framesJob: kotlinx.coroutines.Job? = null

    private fun loadFrames(sb: Storyboard) {
        if (grids.isNotEmpty() || framesJob?.isActive == true) return
        framesJob = viewModelScope.launch(Dispatchers.IO) {
            sb.fragments.forEachIndexed { n, f ->
                val bmp = runCatching { com.quietdlp.app.download.FrameFetch.bitmap(f.url) }.getOrNull()
                if (bmp != null) withContext(Dispatchers.Main) { grids[n] = bmp }
            }
        }
    }

    private fun clearFrames() {
        framesJob?.cancel()
        framesJob = null
        grids.clear()
        cropped.clear()
    }

    /** El fotograma del segundo [sec]; null mientras no se ha bajado o si el sitio no los ofrece. */
    fun frameAt(sec: Int): androidx.compose.ui.graphics.ImageBitmap? {
        val i = info ?: return null
        val sb = i.storyboard ?: return null
        val ref = sb.locate(sec.toDouble(), i.durationSec) ?: return null
        val grid = grids[ref.fragment] ?: return null
        return cropped.getOrPut(Triple(ref.fragment, ref.col, ref.row)) {
            val x = ref.col * sb.width
            val y = ref.row * sb.height
            if (x + sb.width > grid.width || y + sb.height > grid.height) return null
            android.graphics.Bitmap.createBitmap(grid, x, y, sb.width, sb.height).asImageBitmap()
        }
    }

    /** Pone el trozo dentro de la duración y con al menos un segundo. */
    fun setTrim(start: Int, end: Int) {
        val d = info?.durationSec ?: return
        val s = start.coerceIn(0, d - 1)
        trimStart = s
        trimEnd = end.coerceIn(s + 1, d)
    }

    /** Al escribir el inicio o el fin a mano se devuelve lo que quedó, para mostrarlo ya corregido. */
    fun setTrimStart(sec: Int): Int {
        setTrim(sec.coerceAtMost(trimEnd - 1), trimEnd)
        return trimStart
    }

    fun setTrimEnd(sec: Int): Int {
        setTrim(trimStart, sec.coerceAtLeast(trimStart + 1))
        return trimEnd
    }

    /** El trozo pedido, o null si no hay que recortar (apagado, todo el archivo, o no se puede). */
    private fun trimChoice(i: MediaInfo): TrimRange? =
        if (!trimOn || !canTrim(i)) null else TrimRange.choose(trimStart, trimEnd, i.durationSec)

    // ── Subtítulos (solo vídeo) ──
    var subsOn by mutableStateOf(prefs.subsOn)
        private set
    var subCodes by mutableStateOf<List<String>>(emptyList())
        private set
    var subMode by mutableStateOf(prefs.subMode)
        private set

    /** Dónde van los subtítulos: el modo simple siempre los mete dentro del vídeo; el avanzado deja elegir. */
    val effectiveSubMode: SubtitleMode get() = if (advanced) subMode else SubtitleMode.INSIDE

    fun changeSubsOn(v: Boolean) {
        subsOn = v
        prefs.subsOn = v
    }

    /** Modo simple: un solo idioma. */
    fun chooseSubtitle(code: String) {
        subCodes = listOf(code)
        prefs.subLangs = subCodes
    }

    /** Modo avanzado: se suman o se quitan idiomas. */
    fun toggleSubtitle(code: String) {
        subCodes = if (code in subCodes) subCodes - code else subCodes + code
        prefs.subLangs = subCodes
    }

    fun chooseSubMode(m: SubtitleMode) {
        subMode = m
        prefs.subMode = m
    }

    /** Lo que se pide a yt-dlp, o null si no hay que bajar subtítulos (apagados, sin idioma, audio, fotos o lista). */
    private fun subtitleChoice(i: MediaInfo): SubtitleChoice? {
        if (kind != Kind.VIDEO || !subsOn || i.photoCount > 0 || i.playlistCount > 0 || removingParts(i)) return null
        val codes = subCodes.filter { c -> i.subtitles.any { it.code == c } }
        if (codes.isEmpty()) return null
        return SubtitleChoice(codes, effectiveSubMode, includeAuto = i.subtitles.any { it.auto && it.code in codes })
    }

    // ── Motor ──
    var engineReady by mutableStateOf(false)
        private set
    var engineError by mutableStateOf<String?>(null)
        private set
    var ytdlpVersion by mutableStateOf<String?>(null)
        private set
    var updating by mutableStateOf(false)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set

    // ── Aspecto ──
    var paletteId by mutableStateOf(prefs.paletteId)
        private set
    var customPalettes by mutableStateOf(Palettes.decodeCustom(prefs.customPalettes))
        private set

    fun choosePalette(id: String) {
        paletteId = id
        prefs.paletteId = id
    }

    /** Guarda una paleta propia (nueva o editada) y la deja elegida. */
    fun saveCustomPalette(spec: PaletteSpec) {
        val list = customPalettes.filterNot { it.id == spec.id } + spec
        customPalettes = list.takeLast(Palettes.MAX_CUSTOM)
        prefs.customPalettes = Palettes.encodeCustom(customPalettes)
        choosePalette(spec.id)
    }

    fun deleteCustomPalette(id: String) {
        customPalettes = customPalettes.filterNot { it.id == id }
        prefs.customPalettes = Palettes.encodeCustom(customPalettes)
        if (paletteId == id) choosePalette(Palettes.DEFAULT)
    }

    // ── Reglas por sitio ──
    var siteRules by mutableStateOf(SiteRules.decode(prefs.siteRules))
        private set

    fun saveSiteRule(host: String, quality: Quality) {
        siteRules = SiteRules.upsert(siteRules, SiteRule(host, quality))
        prefs.siteRules = SiteRules.encode(siteRules)
    }

    fun removeSiteRule(host: String) {
        siteRules = SiteRules.remove(siteRules, host)
        prefs.siteRules = SiteRules.encode(siteRules)
    }

    /** ¿Hay algo que descargar sin preguntar para este enlace (regla del sitio o modo por defecto)? */
    fun canQuickDownload(link: String): Boolean = prefs.qualityFor(link) != null

    /** Guarda la elección actual como regla del sitio del enlace que se está viendo. */
    fun rememberForSite(info: MediaInfo): String? {
        val host = SiteRules.normalizeHost(info.url) ?: return null
        if (kind == Kind.PHOTO) return null
        saveSiteRule(host, Quality(kind, if (kind == Kind.VIDEO) height else 0, audioFormat, audioBitrate))
        return host
    }

    // ── Modo simple / avanzado ──
    var advanced by mutableStateOf(prefs.advanced)
        private set

    fun changeAdvanced(v: Boolean) {
        advanced = v
        prefs.advanced = v
    }

    // ── Carpeta de descarga ──
    /** Vacío = álbum QuietDLP en la galería. */
    var folderUri by mutableStateOf(prefs.folderUri)
        private set

    fun chooseFolder(uri: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val ok = runCatching { app.contentResolver.takePersistableUriPermission(uri, flags) }.isSuccess
        if (!ok) {
            Toast.makeText(app, "Couldn't use that folder", Toast.LENGTH_SHORT).show()
            return
        }
        releaseFolder()
        folderUri = uri.toString()
        prefs.folderUri = folderUri
    }

    /** Nombre de la carpeta de descargas. Se guarda ya limpio, pero el campo deja escribir libremente. */
    var folderName by mutableStateOf(prefs.folderName)
        private set

    fun changeFolderName(v: String) {
        folderName = v.take(40)
        prefs.folderName = FolderName.clean(v)
    }

    fun resetFolder() {
        releaseFolder()
        folderUri = ""
        prefs.folderUri = ""
    }

    private fun releaseFolder() {
        if (folderUri.isEmpty()) return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { app.contentResolver.releasePersistableUriPermission(Uri.parse(folderUri), flags) }
    }

    // ── Qué se descarga por defecto ──
    var mode by mutableStateOf(prefs.mode)
        private set
    var rememberLast by mutableStateOf(prefs.rememberLast)
        private set
    var clipboardOffer by mutableStateOf(prefs.clipboardOffer)
        private set
    var coverAudio by mutableStateOf(prefs.coverAudio)
        private set
    var musicNames by mutableStateOf(prefs.musicNames)
        private set
    var coverVideo by mutableStateOf(prefs.coverVideo)
        private set

    // ── Actualización automática ──
    var autoUpdate by mutableStateOf(prefs.autoUpdate)
        private set
    var showUpdateNotice by mutableStateOf(!prefs.updateNoticeSeen)
        private set

    // ── Cuentas (cookies de sesión) ──
    /** Dominios con sesión guardada. */
    var sessions by mutableStateOf<List<String>>(emptyList())
        private set
    var hasImportedCookies by mutableStateOf(false)
        private set
    var accountMessage by mutableStateOf<String?>(null)
        private set

    // ── Aviso "hace falta iniciar sesión" ──
    var loginPrompt by mutableStateOf<LoginPrompt?>(null)
        private set
    private var pendingRetry: LoginPrompt? = null

    fun promptLogin(p: LoginPrompt) {
        loginPrompt = p
    }

    fun dismissLoginPrompt() {
        loginPrompt = null
    }

    /** El usuario aceptó: se abre la página de login y, al guardar la sesión, se reintenta solo. */
    fun acceptLoginPrompt() {
        pendingRetry = loginPrompt
        loginPrompt = null
    }

    fun refreshAccounts() {
        sessions = CookieStore.savedHosts(app)
        hasImportedCookies = CookieStore.hasImported(app)

        // Si el usuario acaba de guardar la sesión que se pedía, reintentar sin que tenga que hacer nada más.
        val p = pendingRetry ?: return
        if (p.host !in sessions) return
        pendingRetry = null
        if (p.quality != null) {
            p.oldItemId?.let(DownloadRepository::remove)
            DownloadService.enqueue(app, p.url, p.quality, p.title, p.thumbnail)
        } else {
            analyze(p.url)
        }
    }

    /** Reintenta una descarga fallida (misma calidad). */
    fun retry(item: com.quietdlp.app.data.DownloadItem) {
        if (item.groupId != null) {
            requeueInPlace(item)
            DownloadService.enqueueExisting(app, listOf(item.id))
            return
        }
        DownloadRepository.remove(item.id)
        DownloadService.enqueue(app, item.url, item.quality, item.title, item.thumbnail, subs = item.subs, trim = item.trim, cover = item.cover, sponsor = item.sponsor, chapters = item.chapters)
    }

    fun logout(host: String) {
        CookieStore.removeSite(app, host)
        accountMessage = null
        refreshAccounts()
    }

    fun removeImportedCookies() {
        CookieStore.removeImported(app)
        accountMessage = null
        refreshAccounts()
    }

    fun importCookies(uri: Uri) {
        accountMessage = if (CookieStore.importFile(app, uri)) {
            "Cookies imported."
        } else {
            "Invalid cookies.txt."
        }
        refreshAccounts()
    }

    // ── Actualizaciones de la propia app ──
    var appUpdateEnabled by mutableStateOf(prefs.appUpdateCheck)
        private set
    var appUpdate by mutableStateOf<AppUpdater.Release?>(null)
        private set
    var showAppUpdate by mutableStateOf(false)
        private set
    /** Buscando o descargando. */
    var appUpdateBusy by mutableStateOf(false)
        private set
    /** 0..100 mientras descarga; -1 si no se está descargando. */
    var appUpdateProgress by mutableIntStateOf(-1)
        private set
    var appUpdateMessage by mutableStateOf<String?>(null)
        private set

    fun changeAppUpdateEnabled(v: Boolean) {
        appUpdateEnabled = v
        prefs.appUpdateCheck = v
    }

    /** "Más tarde": no se vuelve a ofrecer sola esta misma versión. */
    fun dismissAppUpdate() {
        appUpdate?.let { prefs.dismissedAppVersion = it.version }
        showAppUpdate = false
    }

    fun checkAppUpdate(manual: Boolean) {
        if (appUpdateBusy) return
        if (!AppUpdater.isConfigured(app)) {
            if (manual) appUpdateMessage = "No downloads page yet."
            return
        }
        appUpdateBusy = true
        if (manual) appUpdateMessage = null
        viewModelScope.launch {
            try {
                val r = withContext(Dispatchers.IO) { AppUpdater.fetchLatest(app) }
                prefs.lastAppCheckMs = System.currentTimeMillis()
                val current = AppUpdater.currentVersion(app)
                when {
                    r == null -> if (manual) appUpdateMessage = "No releases published yet."
                    !AppUpdater.isNewer(r.version, current) ->
                        if (manual) appUpdateMessage = "You're up to date ($current)."
                    else -> {
                        appUpdate = r
                        if (manual || prefs.dismissedAppVersion != r.version) showAppUpdate = true
                    }
                }
            } catch (t: Throwable) {
                if (manual) appUpdateMessage = "Couldn't check: " + Errors.friendly(t)
            } finally {
                appUpdateBusy = false
            }
        }
    }

    /** Descarga la versión nueva, la verifica y abre el instalador de Android (el usuario confirma). */
    fun installAppUpdate() {
        val r = appUpdate ?: return
        if (appUpdateBusy) return
        if (!AppUpdater.canInstall(app)) {
            appUpdateMessage = "Turn on “Allow from this source” for QuietDLP, then tap Update again."
            app.startActivity(AppUpdater.permissionIntent(app))
            return
        }
        appUpdateBusy = true
        appUpdateProgress = 0
        appUpdateMessage = null
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { AppUpdater.download(app, r) { appUpdateProgress = it } }
                val problem = AppUpdater.checkApk(app, file)
                if (problem != null) {
                    file.delete()
                    appUpdateMessage = problem
                } else {
                    showAppUpdate = false
                    app.startActivity(AppUpdater.installIntent(app, file))
                }
            } catch (t: Throwable) {
                appUpdateMessage = "Download failed: " + (t.message ?: t.toString()).take(160)
            } finally {
                appUpdateBusy = false
                appUpdateProgress = -1
            }
        }
    }

    // ── Segunda entrada de "Compartir" ──
    var shareOptions by mutableStateOf(prefs.shareOptions)
        private set

    fun changeShareOptions(v: Boolean) {
        shareOptions = v
        prefs.shareOptions = v
        applyShareOptions(v)
    }

    private fun applyShareOptions(on: Boolean) {
        runCatching {
            app.packageManager.setComponentEnabledSetting(
                ComponentName(app, "com.quietdlp.app.ShareOptionsAlias"),
                if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP,
            )
        }
    }

    // ── Fotos (gallery-dl) ──
    var photosEnabled by mutableStateOf(prefs.photosEnabled)
        private set

    fun changePhotosEnabled(v: Boolean) {
        photosEnabled = v
        prefs.photosEnabled = v
    }

    var autoPhotoVideo by mutableStateOf(prefs.autoPhotoVideo)
        private set

    fun changeAutoPhotoVideo(v: Boolean) {
        autoPhotoVideo = v
        prefs.autoPhotoVideo = v
    }

    fun changeAutoUpdate(v: Boolean) {
        autoUpdate = v
        prefs.autoUpdate = v
    }

    /** Cierra el aviso inicial. [disable] = el usuario eligió desactivar la actualización automática. */
    fun dismissUpdateNotice(disable: Boolean) {
        if (disable) changeAutoUpdate(false)
        prefs.updateNoticeSeen = true
        showUpdateNotice = false
    }

    // ── Páginas seguidas ──

    var followWifiOnly by mutableStateOf(prefs.followWifiOnly)
        private set

    fun changeFollowWifiOnly(v: Boolean) {
        followWifiOnly = v
        prefs.followWifiOnly = v
        FollowScheduler.update(app)
    }

    /** La clave de una web de fotos (de la cuenta del propio usuario) para poder seguirla. */
    var keySet by mutableStateOf(SiteKeys.category(prefs.siteAddress) != null && SiteKeys.valid(prefs.siteUser, prefs.siteKey))
        private set

    /** Guarda el usuario y la clave. false si no tienen forma de usuario y clave. */
    fun saveSiteKey(address: String, user: String, key: String): Boolean {
        if (SiteKeys.category(address) == null) return false
        val u = SiteKeys.cleanUser(user)
        val k = SiteKeys.cleanKey(key)
        if (!SiteKeys.valid(u, k)) return false
        prefs.siteAddress = address.trim()
        prefs.siteUser = u
        prefs.siteKey = k
        keySet = true
        return true
    }

    fun clearSiteKey() {
        prefs.siteAddress = ""
        prefs.siteUser = ""
        prefs.siteKey = ""
        keySet = false
    }

    /** Lo que se escribe en el cuadro de seguir una página. */
    var followUrl by mutableStateOf("")

    var followProbing by mutableStateOf(false)
        private set

    var followError by mutableStateOf<String?>(null)
        private set

    /** La página que se acaba de mirar, a la espera de que el usuario elija cómo seguirla. */
    var followSetup by mutableStateOf<FollowProbe?>(null)
        private set
    private var followSetupUrl = ""

    /** Las páginas que se están revisando ahora mismo. */
    val followChecking = mutableStateListOf<String>()

    private val followRunning = java.util.concurrent.atomic.AtomicBoolean(false)

    fun startFollow() {
        val u = UrlUtils.extract(followUrl)?.let(FollowUrl::normalize)
        if (u == null) {
            followError = "That's not a link."
            return
        }
        if (FollowStore.pages.value.any { it.url == u }) {
            followError = "You already follow this page."
            return
        }
        followError = null
        followProbing = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val probe = FollowChecker.probe(app, u)
                followSetupUrl = u
                followSetup = probe
            } catch (t: Throwable) {
                followError = Errors.friendly(t)
            } finally {
                followProbing = false
            }
        }
    }

    fun dismissFollowSetup() {
        followSetup = null
    }

    fun confirmFollow(kind: Kind, level: QualityLevel, auto: Boolean, days: Double, latest: Int, includeDuplicates: Boolean) {
        val probe = followSetup ?: return
        followSetup = null
        followUrl = ""
        val page = FollowedPage(
            id = UUID.randomUUID().toString().take(8),
            url = followSetupUrl,
            title = probe.title,
            thumbnail = probe.thumbnail,
            engine = probe.engine,
            quality = level.quality(kind),
            auto = auto,
            days = FollowInterval.clamp(days),
            lastCheck = System.currentTimeMillis(),
        )
        viewModelScope.launch(Dispatchers.IO) {
            FollowStore.add(page)
            // Lo que ya hay se da por visto: solo se baja lo que salga desde ahora (y lo que se pida abajo).
            FollowChecker.markAllSeen(page.id, probe.entries)
            FollowScheduler.update(app)
            if (latest > 0) {
                FollowStore.update(page.id) { it.copy(pending = probe.entries.take(latest)) }
                FollowChecker.release(app, page.id, includeDuplicates)
            }
        }
    }

    fun checkNow(id: String) {
        if (id in followChecking) return
        followChecking.add(id)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FollowChecker.check(app, id)
                if (FollowStore.get(id)?.let { it.auto && it.pending.isNotEmpty() } == true) FollowChecker.release(app, id)
            } finally {
                followChecking.remove(id)
            }
        }
    }

    /** Baja las novedades que estaban esperando (páginas que solo avisan). */
    fun downloadPending(id: String) {
        viewModelScope.launch(Dispatchers.IO) { FollowChecker.release(app, id) }
    }

    fun setFollowAuto(id: String, auto: Boolean) {
        FollowStore.update(id) { it.copy(auto = auto) }
        if (auto) viewModelScope.launch(Dispatchers.IO) { FollowChecker.release(app, id) }
    }

    fun setFollowDays(id: String, days: Double) {
        FollowStore.update(id) { it.copy(days = FollowInterval.clamp(days)) }
        FollowScheduler.update(app)
    }

    fun removeFollow(id: String) {
        FollowStore.remove(id)
        DownloadRepository.clearFresh(id)
        FollowScheduler.update(app)
    }

    /** El usuario abrió lo que llegó de la página: ya no es nuevo. */
    fun markFollowSeen(id: String) {
        DownloadRepository.clearFresh(id)
    }

    /** Al abrir la app: revisar lo que toca y bajar lo que quedó esperando (en primer plano Android sí deja arrancar). */
    fun followAppOpened() {
        if (FollowStore.pages.value.isEmpty() || !followRunning.compareAndSet(false, true)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                FollowChecker.runDue(app)
            } finally {
                followRunning.set(false)
            }
        }
    }

    init {
        // Tras restaurar una copia, la entrada de Compartir tiene que coincidir con el ajuste.
        applyShareOptions(prefs.shareOptions)
        FollowStore.init(app)
        FollowScheduler.update(app)
    }

    init {
        refreshAccounts()
        viewModelScope.launch {
            try {
                Engine.ensureReady(app)
                ytdlpVersion = Engine.version(app)
                engineReady = true
                Engine.autoUpdateIfStale(app, prefs)
                ytdlpVersion = Engine.version(app)
                // Buscar una versión nueva de la app: como máximo una vez al día (en depuración, siempre).
                val stale = System.currentTimeMillis() - prefs.lastAppCheckMs > 24L * 60 * 60 * 1000
                if (prefs.appUpdateCheck && (stale || AppUpdater.isDebuggable(app))) checkAppUpdate(manual = false)
            } catch (t: Throwable) {
                engineError = t.message ?: t.toString()
            }
        }
    }

    /** Cada vez que sube, la pantalla principal vuelve arriba (para que se vea la tarjeta del enlace analizado). */
    var scrollTopRequest by mutableIntStateOf(0)
        private set

    /**
     * "Download again": pone el enlace de una descarga de la lista en la caja de arriba y lo analiza, para poder
     * elegir otra vez el formato o la calidad. Si el archivo sigue en el móvil, al descargar sale el aviso de duplicado.
     */
    fun downloadAgain(item: DownloadItem) {
        if (item.isConversion) return
        clipOffer = null
        analyze(item.url)
        scrollTopRequest++
    }

    /** La lista de reproducción del enlace analizado, si es una (el cuadro de selección sale al pulsar Download). */
    var playlist by mutableStateOf<PlaylistInfo?>(null)
        private set

    /** Lee la lista de un enlace sin descargar nada. null si no es una lista de varios archivos o no se pudo leer. */
    private suspend fun probePlaylist(u: String): PlaylistInfo? = withContext(Dispatchers.IO) {
        runCatching {
            fun request(cookies: Boolean) = YoutubeDLRequest(u).apply {
                addOption("--flat-playlist")
                addOption("-J")
                addOption("--playlist-end", PlaylistProbe.MAX_ENTRIES)
                if (UrlUtils.isSingleYoutubeVideo(u)) addOption("--no-playlist")
                if (cookies) CookieStore.mergedPath(app)?.let { addOption("--cookies", it) }
            }
            val out = try {
                YoutubeDL.execute(request(true)).out
            } catch (t: Exception) {
                // Una sesión guardada caducada hace fallar a algunas webs: se repite sin sesiones.
                if (Errors.isBadSession(t)) YoutubeDL.execute(request(false)).out else throw t
            }
            PlaylistProbe.parse(out, u)
        }.getOrNull()
    }

    fun analyze(raw: String) {
        val links = UrlUtils.extractAll(raw)
        if (links.size >= 2) {
            startManyLinks(links)
            return
        }
        val u = UrlUtils.extract(raw)
        if (u == null) {
            error = "That's not a link."
            return
        }
        url = u
        applyDefaults(u)
        analyzing = true
        error = null
        info = null
        playlist = null
        clearFrames()
        viewModelScope.launch {
            val probe = async { probePlaylist(u) }
            try {
                Engine.ensureReady(app)
                fun request(cookies: Boolean) = YoutubeDLRequest(u).apply {
                    addOption("--no-playlist")
                    // Publicaciones con varios vídeos: basta el primero para mostrar título y calidades.
                    addOption("--playlist-items", "1")
                    // Se lee el JSON a mano (y no con getInfo) porque getInfo no trae los subtítulos.
                    addOption("--dump-json")
                    if (cookies) CookieStore.mergedPath(app)?.let { addOption("--cookies", it) }
                }
                val vi = withContext(Dispatchers.IO) {
                    val out = try {
                        YoutubeDL.execute(request(true)).out
                    } catch (t: Exception) {
                        // Una sesión guardada caducada hace fallar a algunas webs: se repite sin sesiones.
                        if (Errors.isBadSession(t)) YoutubeDL.execute(request(false)).out else throw t
                    }
                    MediaProbe.parse(out)
                } ?: error("Unable to parse video information")
                val heights = vi.heights
                val list = probe.await()
                playlist = list
                info = MediaInfo(
                    url = u,
                    title = list?.title ?: vi.title ?: UrlUtils.shorten(u),
                    thumbnail = list?.entries?.firstNotNullOfOrNull { it.thumbnail } ?: vi.thumbnail,
                    uploader = if (list != null) null else vi.uploader,
                    durationSec = if (list != null) 0 else vi.durationSec,
                    heights = heights,
                    playlistCount = list?.entries?.size ?: 0,
                    subtitles = if (list != null) emptyList() else vi.subtitles,
                    storyboard = if (list != null) null else vi.storyboard,
                    chapterCount = if (list != null) 0 else vi.chapterCount,
                )
                subCodes = SubtitleProbe.preselect(vi.subtitles, prefs.subLangs, Locale.getDefault().language)
                trimOn = false
                trimStart = 0
                trimEnd = if (list != null) 0 else vi.durationSec
                // Si no hay vídeo (solo audio) cambiar a audio automáticamente.
                if (heights.isEmpty()) kind = Kind.AUDIO
                if (height != 0 && height !in heights) height = 0
            } catch (t: Throwable) {
                // Si el primer vídeo no se pudo leer pero el enlace es una lista, se ofrece igual (cada uno se intenta aparte).
                val list = runCatching { probe.await() }.getOrNull()
                if (list != null) {
                    playlist = list
                    info = MediaInfo(
                        url = u,
                        title = list.title,
                        thumbnail = list.entries.firstNotNullOfOrNull { it.thumbnail },
                        uploader = null,
                        durationSec = 0,
                        heights = listOf(1080, 720, 480, 360),
                        playlistCount = list.entries.size,
                    )
                    error = null
                    return@launch
                }
                // Sin vídeo: puede ser una publicación de fotos. Ver cuántas hay con gallery-dl.
                if (prefs.photosEnabled && Errors.isNoVideo(t)) {
                    val n = runCatching {
                        withContext(Dispatchers.IO) { GalleryDl.countImages(app, u) }
                    }.getOrDefault(0)
                    if (n > 0) {
                        info = MediaInfo(
                            url = u,
                            title = if (n == 1) "1 photo" else "$n photos",
                            thumbnail = null,
                            uploader = null,
                            durationSec = 0,
                            heights = emptyList(),
                            photoCount = n,
                        )
                        kind = Kind.PHOTO
                        error = null
                        return@launch
                    }
                }
                error = Errors.friendly(t)
                // Falta iniciar sesión: explicar y ofrecer abrir la página de login.
                if (Errors.isLoginRequired(t)) {
                    UrlUtils.hostOf(u)?.let(UrlUtils::registrableDomain)?.let { host ->
                        loginPrompt = LoginPrompt(host = host, url = u)
                    }
                }
            } finally {
                analyzing = false
            }
        }
    }

    /** La elección con la que se abre cada enlace nuevo: la última (si así se pidió) o la del modo por defecto. */
    private fun applyDefaults(url: String) {
        val site = SiteRules.match(siteRules, url)?.quality
        if (site != null) {
            kind = site.kind
            height = site.maxHeight
            setAudio(site.audioFormat, site.audioBitrate)
        } else if (prefs.rememberLast) {
            kind = prefs.lastKind
            height = prefs.lastHeight
            setAudio(prefs.lastAudioFormat, prefs.lastAudioBitrate)
        } else {
            val q = prefs.mode.quality ?: Quality(Kind.VIDEO)
            kind = q.kind
            height = q.maxHeight
            setAudio(q.audioFormat, q.audioBitrate)
        }
        // El modo simple muestra la calidad (alta, media, baja) que más se parece a lo que quedó elegido.
        level = QualityLevel.fromQuality(Quality(kind, height, audioFormat, audioBitrate))
        cover = prefs.coverFor(kind)
    }

    /** La calidad elegida en el modo simple (alta, media o baja). */
    var level by mutableStateOf(QualityLevel.HIGH)
        private set

    /** Modo simple: elegir la calidad fija el tope de vídeo y el formato de audio de golpe. */
    fun chooseLevel(l: QualityLevel) {
        level = l
        height = l.videoHeight
        setAudio(l.audioFormat, l.audioBitrate)
    }

    /** Vídeo o audio. En el modo simple se vuelve a aplicar la calidad elegida al nuevo tipo. */
    fun chooseKind(k: Kind) {
        kind = k
        cover = prefs.coverFor(k)
        if (!advanced) chooseLevel(level)
    }

    private fun setAudio(format: String, bitrate: Int) {
        val o = AudioOptions.find(format, bitrate)
        audioFormat = o.format
        audioBitrate = o.bitrate
    }

    fun download() {
        val i = info ?: return
        val q = Quality(kind, height, audioFormat, audioBitrate)
        if (prefs.rememberLast && kind != Kind.PHOTO) {
            prefs.lastKind = kind
            prefs.lastHeight = height
            prefs.lastAudioFormat = audioFormat
            prefs.lastAudioBitrate = audioBitrate
        }
        val list = playlist
        if (list != null && kind != Kind.PHOTO) requestGroup(list.title, list.entries, q)
        else {
            val trim = trimChoice(i)
            // Los subtítulos no se recortan con el vídeo, así que con un trozo no se piden.
            requestDownload(i.url, q, i.title, i.thumbnail, if (trim == null) subtitleChoice(i) else null, trim, coverWanted(q),
                sponsorChoice(i), chapterChoice(i))
        }
        info = null
        playlist = null
        clearFrames()
        url = ""
        error = null
    }

    // ── Varios archivos: listas, varios enlaces, cola ──

    /** Varios enlaces pegados a la vez: cada uno es un archivo del grupo, con la elección de siempre para el primero. */
    private fun startManyLinks(links: List<String>) {
        applyDefaults(links.first())
        val q = Quality(kind, height, audioFormat, audioBitrate)
        error = null
        url = ""
        requestGroup("${links.size} links", links.map { PlaylistEntry(it, UrlUtils.shorten(it), null) }, q)
    }

    var selectionPrompt by mutableStateOf<SelectionPrompt?>(null)
        private set

    var dataPrompt by mutableStateOf<DataPrompt?>(null)
        private set

    /** Pregunta cuáles bajar (o baja todo directo si el usuario pidió no preguntar más). */
    private fun requestGroup(title: String, entries: List<PlaylistEntry>, quality: Quality) {
        if (prefs.askMulti) selectionPrompt = SelectionPrompt(title, entries, quality)
        else startGroup(title, entries, quality)
    }

    fun confirmSelection(chosen: List<PlaylistEntry>, quality: Quality, dontAskAgain: Boolean) {
        val p = selectionPrompt ?: return
        selectionPrompt = null
        if (dontAskAgain) changeAskMulti(false)
        if (chosen.isNotEmpty()) startGroup(p.title, chosen, quality)
    }

    fun dismissSelection() {
        selectionPrompt = null
    }

    /** Pone los archivos en la lista y en la cola, en el orden del enlace. */
    private fun startGroup(title: String, entries: List<PlaylistEntry>, quality: Quality) {
        beginDownloads(entries.size) { waitForWifi ->
            val gid = UUID.randomUUID().toString()
            val waiting = (waitForWifi || prefs.wifiOnly) && Connectivity.isMetered(app)
            val items = entries.mapIndexed { i, e ->
                DownloadItem(
                    id = UUID.randomUUID().toString(),
                    url = e.url,
                    title = e.title,
                    thumbnail = e.thumbnail,
                    quality = quality,
                    message = if (waiting) "Waiting for Wi-Fi…" else null,
                    groupId = gid,
                    groupIndex = i,
                    groupTitle = title,
                    wifiOnly = waitForWifi,
                    cover = coverWanted(quality),
                )
            }
            DownloadRepository.addAll(items)
            DownloadService.enqueueExisting(app, items.map { it.id })
            if (items.size >= LONG_QUEUE && !prefs.batteryHintOff && batteryRestricted()) batteryPrompt = true
        }
    }

    /** Con datos móviles y una descarga grande, antes se pregunta (con "solo Wi-Fi" no hace falta: se espera sola). */
    private fun beginDownloads(count: Int, start: (Boolean) -> Unit) {
        if (!prefs.wifiOnly && count >= 2 && Connectivity.isOnline(app) && Connectivity.isMetered(app)) {
            dataPrompt = DataPrompt(start)
        } else {
            start(false)
        }
    }

    fun answerDataPrompt(waitForWifi: Boolean) {
        val p = dataPrompt ?: return
        dataPrompt = null
        p.start(waitForWifi)
    }

    fun dismissDataPrompt() {
        dataPrompt = null
    }

    // ── Batería ──
    var batteryPrompt by mutableStateOf(false)
        private set

    /** true si Android puede pausar la app con la pantalla apagada (la app no está en la lista de "sin restricciones"). */
    fun batteryRestricted(): Boolean {
        val pm = app.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !pm.isIgnoringBatteryOptimizations(app.packageName)
    }

    fun dismissBatteryPrompt(dontAskAgain: Boolean) {
        batteryPrompt = false
        if (dontAskAgain) prefs.batteryHintOff = true
    }

    /** Reintenta los fallidos de un grupo (cada uno en su sitio, sin perder el orden). */
    fun retryFailed(items: List<DownloadItem>) {
        val failed = items.filter { it.state == State.FAILED && !it.isConversion }
        failed.forEach { requeueInPlace(it) }
        DownloadService.enqueueExisting(app, failed.map { it.id })
    }

    fun cancelAll(items: List<DownloadItem>) {
        DownloadService.cancelMany(app, items.filter { it.isActive }.map { it.id })
    }

    private fun requeueInPlace(item: DownloadItem) {
        DownloadRepository.update(item.id) {
            it.copy(state = State.QUEUED, message = null, progress = 0f, etaSeconds = 0, loginHost = null)
        }
    }

    // ── Ajustes de la cola ──
    var maxParallel by mutableIntStateOf(prefs.maxParallel)
        private set

    fun changeMaxParallel(v: Int) {
        maxParallel = v.coerceIn(1, Prefs.MAX_PARALLEL)
        prefs.maxParallel = maxParallel
    }

    var wifiOnly by mutableStateOf(prefs.wifiOnly)
        private set

    fun changeWifiOnly(v: Boolean) {
        wifiOnly = v
        prefs.wifiOnly = v
    }

    var askMulti by mutableStateOf(prefs.askMulti)
        private set

    fun changeAskMulti(v: Boolean) {
        askMulti = v
        prefs.askMulti = v
    }

    // ── Enlaces ya descargados ──
    /** Descarga pendiente de confirmar porque el enlace ya se descargó (o está en curso). */
    class DuplicatePrompt(
        val active: Boolean,
        val url: String,
        val quality: Quality,
        val title: String?,
        val thumbnail: String?,
        val subs: SubtitleChoice? = null,
        val cover: Boolean? = null,
        val sponsor: SponsorChoice? = null,
        val chapters: ChapterMode = ChapterMode.OFF,
    )

    var duplicatePrompt by mutableStateOf<DuplicatePrompt?>(null)
        private set

    fun findDuplicate(url: String, kind: Kind, title: String? = null): DownloadIndex.Dup =
        DownloadIndex.check(app, url, kind, title)

    /** Descarga, pero antes avisa si ese enlace ya está descargado. */
    fun requestDownload(
        url: String,
        quality: Quality,
        title: String? = null,
        thumbnail: String? = null,
        subs: SubtitleChoice? = null,
        trim: TrimRange? = null,
        cover: Boolean? = null,
        sponsor: SponsorChoice? = null,
        chapters: ChapterMode = ChapterMode.OFF,
    ) {
        // Un trozo es otro archivo distinto del completo: no hay aviso de duplicado.
        val dup = if (trim != null) DownloadIndex.Dup.NONE else findDuplicate(url, quality.kind, title)
        if (dup != DownloadIndex.Dup.NONE) {
            duplicatePrompt = DuplicatePrompt(dup == DownloadIndex.Dup.ACTIVE, url, quality, title, thumbnail, subs, cover, sponsor, chapters)
        } else {
            DownloadService.enqueue(app, url, quality, title, thumbnail, subs = subs, trim = trim, cover = cover, sponsor = sponsor, chapters = chapters)
        }
    }

    fun confirmDuplicate() {
        val p = duplicatePrompt ?: return
        duplicatePrompt = null
        DownloadService.enqueue(app, p.url, p.quality, p.title, p.thumbnail, subs = p.subs, cover = p.cover, sponsor = p.sponsor, chapters = p.chapters)
    }

    fun dismissDuplicate() {
        duplicatePrompt = null
    }

    // ── Enlace copiado ──
    var clipOffer by mutableStateOf<String?>(null)
        private set

    /** Al abrir la app: si hay un enlace nuevo copiado, se ofrece descargarlo (una sola vez por cada copia). */
    fun checkClipboard() {
        if (!prefs.clipboardOffer || analyzing || info != null || url.isNotBlank()) return
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val desc = cm.primaryClipDescription ?: return
        if (!desc.hasMimeType("text/*")) return
        // Solo se lee el contenido si es una copia nueva: Android avisa cada vez que una app lo lee.
        val stamp = if (Build.VERSION.SDK_INT >= 26) desc.timestamp else 0L
        if (stamp != 0L && stamp == prefs.lastClipMs) return
        val text = runCatching { cm.primaryClip?.getItemAt(0)?.coerceToText(app)?.toString() }.getOrNull()
        prefs.lastClipMs = stamp
        val link = UrlUtils.extract(text) ?: return
        val key = Duplicates.keyHash(link)
        if (key == prefs.lastClipKey) return
        prefs.lastClipKey = key
        if (findDuplicate(link, prefs.qualityFor(link)?.kind ?: Kind.VIDEO) != DownloadIndex.Dup.NONE) return
        clipOffer = link
    }

    fun dismissClip() {
        clipOffer = null
    }

    fun clipDownload() {
        val link = clipOffer ?: return
        clipOffer = null
        val q = prefs.qualityFor(link)
        if (q != null) requestDownload(link, q) else analyze(link)
    }

    fun clipOptions() {
        val link = clipOffer ?: return
        clipOffer = null
        analyze(link)
    }

    // ── Historial ──
    /** Quita la fila de la lista. Con [deleteFiles] borra también los archivos de la galería. */
    fun delete(item: DownloadItem, deleteFiles: Boolean) {
        if (!deleteFiles) {
            DownloadRepository.remove(item.id)
            return
        }
        viewModelScope.launch {
            val allGone = withContext(Dispatchers.IO) { item.savedUris.map { MediaFiles.delete(app, it) }.all { it } }
            if (allGone) {
                DownloadRepository.remove(item.id)
            } else {
                Toast.makeText(app, "Couldn't delete the file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun cancel(id: String) = DownloadService.cancel(app, id)

    /** Convierte un archivo ya descargado a otro formato; el resultado aparece como un elemento nuevo del historial. */
    fun convert(item: DownloadItem, target: ConvertTarget) = DownloadService.convert(app, item, target)

    /** Hace un vídeo con las fotos de una publicación y su canción. */
    /** [remember] = la casilla "Remember my choice": solo entonces la duración elegida pasa a ser la de por defecto. */
    fun makeVideo(item: DownloadItem, seconds: Int, remember: Boolean) {
        changeRememberSong(remember)
        if (remember) changeSongSeconds(seconds)
        DownloadService.makeVideo(app, item, seconds)
    }

    /** Hasta cuándo suena la canción en el vídeo (0 = entera): con lo que empieza la ventana y lo que usa el modo automático. */
    var songSeconds by mutableIntStateOf(prefs.songSeconds)
        private set

    fun changeSongSeconds(v: Int) {
        songSeconds = v
        prefs.songSeconds = v
    }

    var rememberSong by mutableStateOf(prefs.rememberSong)
        private set

    fun changeRememberSong(v: Boolean) {
        rememberSong = v
        prefs.rememberSong = v
    }

    // ── Búsqueda y selección en el historial ──
    var searchQuery by mutableStateOf("")

    /** Ids seleccionados (solo se pueden elegir descargas ya terminadas). */
    var selected by mutableStateOf<Set<String>>(emptySet())
        private set

    fun toggleSelect(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll(ids: List<String>) {
        selected = ids.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
    }

    /** Quita varias filas; con [deleteFiles] borra también los archivos de la galería. */
    fun deleteMany(items: List<DownloadItem>, deleteFiles: Boolean) {
        clearSelection()
        if (!deleteFiles) {
            items.forEach { DownloadRepository.remove(it.id) }
            return
        }
        viewModelScope.launch {
            var failed = 0
            for (item in items) {
                val allGone = withContext(Dispatchers.IO) { item.savedUris.map { MediaFiles.delete(app, it) }.all { it } }
                if (allGone) DownloadRepository.remove(item.id) else failed++
            }
            if (failed > 0) {
                Toast.makeText(app, "Couldn't delete $failed ${if (failed == 1) "file" else "files"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Orientación de la pantalla ──
    var orientation by mutableStateOf(prefs.orientation)
        private set

    fun changeOrientation(o: AppOrientation) {
        orientation = o
        prefs.orientation = o
    }

    // ── Tarjeta de apoyo ──
    var supportCard by mutableStateOf(prefs.supportCard)
        private set

    fun changeSupportCard(v: Boolean) {
        supportCard = v
        prefs.supportCard = v
    }

    fun remove(id: String) = DownloadRepository.remove(id)
    fun clearFinished() = DownloadRepository.clearFinished()

    /** Actualiza yt-dlp. [then] se ejecuta al terminar (por ejemplo, reintentar la descarga que falló). */
    fun updateEngine(then: (() -> Unit)? = null) {
        if (updating) return
        updating = true
        updateMessage = null
        viewModelScope.launch {
            updateMessage = try {
                Engine.update(app, prefs)
            } catch (t: Throwable) {
                "Couldn't update: " + Errors.friendly(t)
            }
            ytdlpVersion = Engine.version(app)
            updating = false
            then?.invoke()
        }
    }

    // ── Copia de seguridad de los ajustes ──
    var backupMessage by mutableStateOf<String?>(null)
        private set

    fun saveSettingsTo(uri: Uri) {
        viewModelScope.launch {
            backupMessage = withContext(Dispatchers.IO) {
                runCatching {
                    app.contentResolver.openOutputStream(uri, "wt")?.use { it.write(prefs.exportSettings(DownloadRepository.items.value, FollowStore.pages.value).toByteArray()) }
                        ?: error("Couldn't write the file")
                    "Backup saved."
                }.getOrElse { "Couldn't save: ${it.message}" }
            }
        }
    }

    /** Lee todo el archivo, o devuelve null si pasa de [max] bytes (sin cargar un archivo enorme en memoria). */
    private fun readAtMost(input: java.io.InputStream, max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            if (out.size() + n > max) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /** Vuelve a seguir las páginas de la copia que aún no se siguen. Lo que ya hay en ellas cuenta como visto. */
    private fun restoreFollows(restored: List<FollowedPage>): Int {
        val now = System.currentTimeMillis()
        var added = 0
        for (p in restored) {
            if (FollowStore.pages.value.any { it.url == p.url }) continue
            val id = if (FollowStore.get(p.id) == null) p.id else UUID.randomUUID().toString().take(8)
            FollowStore.add(p.copy(id = id, lastCheck = now, pending = emptyList(), lastError = null, counter = 0))
            added++
        }
        if (added > 0) FollowScheduler.update(app)
        return added
    }

    fun restoreSettingsFrom(uri: Uri) {
        viewModelScope.launch {
            backupMessage = withContext(Dispatchers.IO) {
                runCatching {
                    val bytes = app.contentResolver.openInputStream(uri)?.use { readAtMost(it, SettingsBackup.MAX_BYTES) }
                        ?: error("Couldn't read the file")
                    if (bytes == null) return@runCatching "That file is too big to be a QuietDLP backup."
                    val text = String(bytes)
                    val n = prefs.importSettings(text)
                    if (n < 0) return@runCatching "That isn't a QuietDLP backup."
                    val rows = DownloadRepository.mergeHistory(app, SettingsBackup.decodeHistory(text))
                    val pages = restoreFollows(SettingsBackup.decodeFollows(text))
                    buildString {
                        append("Restored $n settings")
                        if (rows > 0) append(" and $rows history ${if (rows == 1) "item" else "items"}")
                        if (pages > 0) append(" and $pages followed ${if (pages == 1) "page" else "pages"}")
                        append(". Close the app and open it again to apply the settings.")
                    }
                }.getOrElse { "Couldn't restore: ${it.message}" }
            }
        }
    }

    fun changeMode(m: DownloadMode) {
        mode = m
        prefs.mode = m
    }

    fun changeRememberLast(v: Boolean) {
        rememberLast = v
        prefs.rememberLast = v
    }

    fun changeMusicNames(v: Boolean) {
        musicNames = v
        prefs.musicNames = v
    }

    fun changeCoverAudio(v: Boolean) {
        coverAudio = v
        prefs.coverAudio = v
    }

    fun changeCoverVideo(v: Boolean) {
        coverVideo = v
        prefs.coverVideo = v
    }

    fun changeClipboardOffer(v: Boolean) {
        clipboardOffer = v
        prefs.clipboardOffer = v
        if (!v) clipOffer = null
    }

    fun chooseAudio(o: AudioOption) {
        setAudio(o.format, o.bitrate)
        prefs.audioFormat = o.format
        prefs.audioBitrate = o.bitrate
    }
}
