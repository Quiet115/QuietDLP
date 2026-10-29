package com.quietdlp.app

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.content.Context
import android.os.Build
import android.widget.Toast
import com.quietdlp.app.data.AppOrientation
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quietdlp.app.data.Kind
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
)

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
        DownloadRepository.remove(item.id)
        DownloadService.enqueue(app, item.url, item.quality, item.title, item.thumbnail)
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

    // ── Fotos (gallery-dl) ──
    var photosEnabled by mutableStateOf(prefs.photosEnabled)
        private set

    fun changePhotosEnabled(v: Boolean) {
        photosEnabled = v
        prefs.photosEnabled = v
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

    fun analyze(raw: String) {
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
        viewModelScope.launch {
            try {
                Engine.ensureReady(app)
                val req = YoutubeDLRequest(u).apply {
                    addOption("--no-playlist")
                    // Publicaciones con varios vídeos: basta el primero para mostrar título y calidades.
                    addOption("--playlist-items", "1")
                    CookieStore.mergedPath(app)?.let { addOption("--cookies", it) }
                }
                val vi = withContext(Dispatchers.IO) { YoutubeDL.getInfo(req) }
                // Calidad = lado corto (1080×1920 vertical es 1080p), como se acostumbra y como ordena yt-dlp.
                val heights = vi.formats.orEmpty()
                    .filter { (it.vcodec ?: "none") != "none" && it.height > 0 }
                    .map { f -> if (f.width > 0) minOf(f.width, f.height) else f.height }
                    .filter { it >= 144 }
                    .distinct()
                    .sortedDescending()
                info = MediaInfo(
                    url = u,
                    title = vi.title ?: vi.fulltitle ?: UrlUtils.shorten(u),
                    thumbnail = vi.thumbnail,
                    uploader = vi.uploader,
                    durationSec = vi.duration,
                    heights = heights,
                )
                // Si no hay vídeo (solo audio) cambiar a audio automáticamente.
                if (heights.isEmpty()) kind = Kind.AUDIO
                if (height != 0 && height !in heights) height = 0
            } catch (t: Throwable) {
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
        requestDownload(i.url, q, i.title, i.thumbnail)
        info = null
        url = ""
        error = null
    }

    // ── Enlaces ya descargados ──
    /** Descarga pendiente de confirmar porque el enlace ya se descargó (o está en curso). */
    class DuplicatePrompt(val active: Boolean, val url: String, val quality: Quality, val title: String?, val thumbnail: String?)

    var duplicatePrompt by mutableStateOf<DuplicatePrompt?>(null)
        private set

    fun findDuplicate(url: String, kind: Kind, title: String? = null): DownloadIndex.Dup =
        DownloadIndex.check(app, url, kind, title)

    /** Descarga, pero antes avisa si ese enlace ya está descargado. */
    fun requestDownload(url: String, quality: Quality, title: String? = null, thumbnail: String? = null) {
        val dup = findDuplicate(url, quality.kind, title)
        if (dup != DownloadIndex.Dup.NONE) {
            duplicatePrompt = DuplicatePrompt(dup == DownloadIndex.Dup.ACTIVE, url, quality, title, thumbnail)
        } else {
            DownloadService.enqueue(app, url, quality, title, thumbnail)
        }
    }

    fun confirmDuplicate() {
        val p = duplicatePrompt ?: return
        duplicatePrompt = null
        DownloadService.enqueue(app, p.url, p.quality, p.title, p.thumbnail)
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

    fun updateEngine() {
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
