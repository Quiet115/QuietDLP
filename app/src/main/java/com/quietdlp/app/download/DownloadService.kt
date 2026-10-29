package com.quietdlp.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.StatFs
import android.webkit.MimeTypeMap
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.quietdlp.app.MainActivity
import com.quietdlp.app.Sharing
import com.quietdlp.app.data.AudioOptions
import com.quietdlp.app.data.CONVERT_URL_PREFIX
import com.quietdlp.app.data.ConvertTarget
import com.quietdlp.app.data.ConvertTargets
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.FreeSpace
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.Prefs
import com.quietdlp.app.data.Quality
import com.quietdlp.app.data.Slideshow
import com.quietdlp.app.data.State
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Servicio en primer plano que descarga de uno en uno. Al ser "foreground" Android no lo mata
 * aunque cierres la app o apagues la pantalla, y se detiene solo cuando no queda nada pendiente.
 */
class DownloadService : Service() {

    companion object {
        private const val ACTION_ENQUEUE = "com.quietdlp.app.ENQUEUE"
        private const val ACTION_CANCEL = "com.quietdlp.app.CANCEL"
        private const val ACTION_CONVERT = "com.quietdlp.app.CONVERT"
        private const val ACTION_MAKE_VIDEO = "com.quietdlp.app.MAKE_VIDEO"
        private const val EXTRA_SRC_URI = "src_uri"
        private const val EXTRA_SRC_MIME = "src_mime"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_SONG_SECONDS = "song_seconds"
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_THUMB = "thumb"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_HEIGHT = "height"
        private const val EXTRA_AUDIO_FMT = "audio_fmt"
        private const val EXTRA_AUDIO_BR = "audio_br"
        private const val EXTRA_ID = "id"

        private const val CH_PROGRESS = "progress"
        private const val CH_RESULT = "result_quiet"
        private const val NOTIF_PROGRESS = 1
        private const val MAX_NET_WAITS = 6

        fun enqueue(
            ctx: Context,
            url: String,
            quality: Quality,
            title: String? = null,
            thumbnail: String? = null,
        ) {
            val i = Intent(ctx, DownloadService::class.java)
                .setAction(ACTION_ENQUEUE)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_THUMB, thumbnail)
                .putExtra(EXTRA_KIND, quality.kind.name)
                .putExtra(EXTRA_HEIGHT, quality.maxHeight)
                .putExtra(EXTRA_AUDIO_FMT, quality.audioFormat)
                .putExtra(EXTRA_AUDIO_BR, quality.audioBitrate)
            ContextCompat.startForegroundService(ctx, i)
        }

        /** Convierte un archivo ya descargado ([source], con su archivo guardado) al formato [target]. */
        fun convert(ctx: Context, source: DownloadItem, target: ConvertTarget) {
            val uri = source.savedUri ?: return
            val i = Intent(ctx, DownloadService::class.java)
                .setAction(ACTION_CONVERT)
                .putExtra(EXTRA_SRC_URI, uri)
                .putExtra(EXTRA_SRC_MIME, source.mime)
                .putExtra(EXTRA_TARGET, target.id)
                .putExtra(EXTRA_TITLE, source.title)
                .putExtra(EXTRA_THUMB, source.thumbnail)
            ContextCompat.startForegroundService(ctx, i)
        }

        /** Hace un vídeo con las fotos y la canción de una publicación ya descargada. */
        fun makeVideo(ctx: Context, source: DownloadItem, songSeconds: Int) {
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, DownloadService::class.java).setAction(ACTION_MAKE_VIDEO)
                    .putExtra(EXTRA_ID, source.id).putExtra(EXTRA_SONG_SECONDS, songSeconds),
            )
        }

        fun cancel(ctx: Context, id: String) {
            ctx.startService(
                Intent(ctx, DownloadService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_ID, id),
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val main = Handler(Looper.getMainLooper())

    /** Lo que hace falta para una conversión en cola (solo vive mientras el servicio está activo). */
    private class ConvertJob(val sourceUri: String, val target: ConvertTarget, val sourceTitle: String)

    private val convertJobs = ConcurrentHashMap<String, ConvertJob>()

    /** Lo mismo para un vídeo hecho con las fotos de una publicación y su canción. */
    private class SlideshowJob(val imageUris: List<String>, val audioUri: String, val sourceTitle: String, val maxSeconds: Int)

    private val slideshowJobs = ConcurrentHashMap<String, SlideshowJob>()

    @Volatile
    private var lastStartId = 0
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotifMs = 0L
    private lateinit var prefs: Prefs
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        DownloadRepository.init(this)
        nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannels()

        ServiceCompat.startForeground(
            this,
            NOTIF_PROGRESS,
            baseNotification("QuietDLP", "Getting ready…", -1).build(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuietDLP:download")
            .apply { acquire(2 * 60 * 60 * 1000L) }

        scope.launch {
            for (id in queue) {
                try {
                    process(id)
                } finally {
                    if (pending.decrementAndGet() <= 0) main.post { stopIfIdle() }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        when (intent?.action) {
            ACTION_ENQUEUE -> handleEnqueue(intent)
            ACTION_CONVERT -> handleConvert(intent)
            ACTION_MAKE_VIDEO -> handleMakeVideo(intent)
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_ID)?.let { handleCancel(it) }
        }
        if (pending.get() == 0) stopIfIdle()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        queue.close()
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        super.onDestroy()
    }

    private fun stopIfIdle() {
        if (pending.get() == 0 && stopSelfResult(lastStartId)) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    private fun handleEnqueue(intent: Intent) {
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val kind = runCatching { Kind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "") }
            .getOrDefault(Kind.VIDEO)
        val quality = Quality(
            kind = kind,
            maxHeight = intent.getIntExtra(EXTRA_HEIGHT, 0),
            audioFormat = intent.getStringExtra(EXTRA_AUDIO_FMT) ?: "m4a",
            audioBitrate = intent.getIntExtra(EXTRA_AUDIO_BR, 0),
        )
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = url,
            title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() }
                ?: UrlUtils.shorten(url),
            thumbnail = intent.getStringExtra(EXTRA_THUMB),
            quality = quality,
        )
        DownloadRepository.add(item)
        pending.incrementAndGet()
        queue.trySend(item.id)
    }

    private fun handleConvert(intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_SRC_URI) ?: return
        val target = ConvertTargets.byId(intent.getStringExtra(EXTRA_TARGET)) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE)?.takeIf { it.isNotBlank() } ?: "Converted"
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = CONVERT_URL_PREFIX + uri,
            title = ConvertTargets.outputName(title, target).substringBeforeLast('.'),
            thumbnail = intent.getStringExtra(EXTRA_THUMB),
            quality = target.quality,
            mime = intent.getStringExtra(EXTRA_SRC_MIME),
        )
        convertJobs[item.id] = ConvertJob(uri, target, title)
        DownloadRepository.add(item)
        pending.incrementAndGet()
        queue.trySend(item.id)
    }

    private fun handleMakeVideo(intent: Intent) {
        val source = intent.getStringExtra(EXTRA_ID)?.let { DownloadRepository.get(it) } ?: return
        enqueueSlideshow(source, intent.getIntExtra(EXTRA_SONG_SECONDS, prefs.songSeconds))
    }

    /**
     * Pone en cola un vídeo con las fotos y la canción de [source], con la canción hasta [maxSeconds] (0 = entera).
     * No hace nada si le falta una de las dos.
     */
    private fun enqueueSlideshow(source: DownloadItem, maxSeconds: Int = prefs.songSeconds) {
        val images = source.savedUris.filter { mimeOfUri(it)?.startsWith("image/") == true }.take(Slideshow.MAX_IMAGES)
        val audio = source.savedUris.firstOrNull { mimeOfUri(it)?.startsWith("audio/") == true }
        if (images.isEmpty() || audio == null) return
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = CONVERT_URL_PREFIX + "video:" + images.first(),
            title = Slideshow.outputName(source.title).substringBeforeLast('.'),
            thumbnail = source.thumbnail,
            quality = Quality(Kind.VIDEO),
        )
        slideshowJobs[item.id] = SlideshowJob(images, audio, source.title, maxSeconds)
        DownloadRepository.add(item)
        pending.incrementAndGet()
        queue.trySend(item.id)
    }

    private fun handleCancel(id: String) {
        cancelled.add(id)
        DownloadRepository.update(id) {
            if (it.isActive) it.copy(state = State.CANCELED, message = "Canceled") else it
        }
        // Matar el proceso bloquea un instante: no hacerlo en el hilo principal.
        scope.launch {
            runCatching { YoutubeDL.destroyProcessById(id) }
            GalleryDl.cancel(id)
            Ffmpeg.cancel(id)
        }
    }

    // ───────────────────────── descarga ─────────────────────────

    private suspend fun process(id: String) {
        val item = DownloadRepository.get(id) ?: return
        if (id in cancelled) {
            cancelled.remove(id)
            convertJobs.remove(id)
            slideshowJobs.remove(id)
            return
        }
        convertJobs.remove(id)?.let { job ->
            processConversion(item, job)
            return
        }
        slideshowJobs.remove(id)?.let { job ->
            processSlideshow(item, job)
            return
        }
        val tmp = File(cacheDir, "dl/$id").apply { mkdirs() }
        try {
            var waits = 0
            while (true) {
                try {
                    attempt(item, tmp)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    // Sin internet: esperar a que vuelva y seguir donde se quedó (yt-dlp retoma los .part).
                    if (id in cancelled || waits >= MAX_NET_WAITS || !Errors.isNetwork(t)) throw t
                    waits++
                    val msg = "Waiting for connection…"
                    setState(id, State.QUEUED, msg)
                    notifyProgress(item, msg, -1, force = true)
                    if (!waitForNetwork(id, waits)) throw t
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (id in cancelled) {
                setState(id, State.CANCELED, "Canceled")
            } else {
                // Si falta iniciar sesión, la app guía al usuario (aviso + abrir la página de login).
                val loginHost = if (Errors.isLoginRequired(t)) {
                    UrlUtils.hostOf(item.url)?.let(UrlUtils::registrableDomain)
                } else null
                val hint = if (!prefs.autoUpdate && loginHost == null) " Try updating yt-dlp in Settings." else ""
                val msg = Errors.friendly(t) + hint
                ErrorLog.add(applicationContext, item, t)
                DownloadRepository.update(id) {
                    it.copy(state = State.FAILED, message = msg, loginHost = loginHost)
                }
                notifyResult(
                    id, item.title, ok = false,
                    error = if (loginHost != null) "Sign-in required. Tap to continue." else msg,
                    item = item, loginHost = loginHost,
                )
            }
        } finally {
            cancelled.remove(id)
            tmp.deleteRecursively()
        }
    }


    // ───────────────────────── conversión ─────────────────────────

    /** Copia el archivo original a la carpeta temporal (ffmpeg no lee direcciones `content://`). Devuelve su tamaño. */
    private fun copySource(uri: String, dest: File): Long {
        val input = contentResolver.openInputStream(Uri.parse(uri)) ?: throw FileNotFoundException(uri)
        return input.use { src -> dest.outputStream().use { src.copyTo(it) } }
    }

    private fun conversionMessage(t: Throwable): String {
        val text = (t.message ?: t.toString()).removePrefix("ERROR:").trim()
        return when (t) {
            is FileNotFoundException, is SecurityException -> "The original file isn't available anymore."
            is IllegalStateException -> text.take(220)
            else -> "Couldn't convert: $text".take(220)
        }
    }

    /** Lo que ffmpeg tiene que hacer: el comando, el archivo que saldrá y, si se sabe, cuánto durará (para el avance). */
    private class Plan(val command: List<String>, val output: File, val totalMs: Long = 0L)

    /** Convierte un archivo ya guardado con ffmpeg y guarda el resultado como un archivo nuevo. */
    private suspend fun processConversion(item: DownloadItem, job: ConvertJob) = runFfmpegJob(item) { tmp ->
        val input = File(tmp, "input")
        val size = copySource(job.sourceUri, input)
        // La salida puede pesar tanto como la entrada o más (FLAC, WAV): sitio para las dos y un margen.
        requireSpace(tmp, size * 2 + 50L * 1024 * 1024)
        val out = File(tmp, ConvertTargets.outputName(job.sourceTitle, job.target))
        Plan(job.target.command(input.absolutePath, out.absolutePath), out)
    }

    /** Junta las fotos de una publicación con su canción en un vídeo. */
    private suspend fun processSlideshow(item: DownloadItem, job: SlideshowJob) = runFfmpegJob(item) { tmp ->
        val images = job.imageUris.mapIndexed { i, uri ->
            File(tmp, "img$i.${extensionOf(uri, "jpg")}").also { copySource(uri, it) }
        }
        val audio = File(tmp, "audio.${extensionOf(job.audioUri, "m4a")}").also { copySource(job.audioUri, it) }
        requireSpace(tmp, 200L * 1024 * 1024)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(images.first().absolutePath, bounds)
        val (w, h) = Slideshow.canvas(bounds.outWidth, bounds.outHeight)
        val audioMs = MediaKinds.durationMs(audio)
        val seconds = Slideshow.secondsPerImage(audioMs, images.size, job.maxSeconds)
        val totalMs = Slideshow.totalMs(images.size, seconds)
        val out = File(tmp, Slideshow.outputName(job.sourceTitle))
        Plan(
            Slideshow.command(
                images.map { it.absolutePath }, audio.absolutePath, out.absolutePath, w, h, seconds,
                fadeOut = Slideshow.cutsSong(audioMs, totalMs),
            ),
            out, totalMs,
        )
    }

    private fun requireSpace(dir: File, needed: Long) {
        val free = StatFs(dir.absolutePath).availableBytes
        if (free < needed) error(FreeSpace.message(free))
    }

    private fun mimeOfUri(uri: String): String? =
        contentResolver.getType(Uri.parse(uri))
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(MimeTypeMap.getFileExtensionFromUrl(uri).lowercase())

    private fun extensionOf(uri: String, fallback: String): String =
        mimeOfUri(uri)?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }?.takeIf { it.isNotBlank() } ?: fallback

    /** Hace el trabajo de [prepare] (copiar archivos y armar el comando), ejecuta ffmpeg y guarda el resultado. */
    private suspend fun runFfmpegJob(item: DownloadItem, prepare: (File) -> Plan) {
        val id = item.id
        val tmp = File(cacheDir, "cv/$id").apply { mkdirs() }
        try {
            setState(id, State.PREPARING, "Getting ready…")
            notifyProgress(item, "Getting ready…", -1, force = true)
            Engine.ensureReady(applicationContext)

            val plan = prepare(tmp)
            if (id in cancelled) error("Canceled")

            val out = plan.output
            Ffmpeg.run(applicationContext, id, plan.command, plan.totalMs) { pct ->
                if (id in cancelled) return@run
                if (pct >= 0) {
                    DownloadRepository.update(id) {
                        it.copy(state = State.DOWNLOADING, progress = pct.toFloat(), message = null)
                    }
                    notifyProgress(item, "Converting… $pct%", pct)
                } else {
                    setState(id, State.PROCESSING, "Converting…")
                    notifyProgress(item, "Converting…", -1)
                }
            }
            if (!out.exists() || out.length() == 0L) error("ffmpeg finished but produced no file")

            setState(id, State.PROCESSING, "Saving…")
            val saved = MediaSaver.publish(applicationContext, out)
            DownloadRepository.update(id) {
                it.copy(
                    state = State.DONE, progress = 100f, message = null,
                    savedUris = listOf(saved.uri.toString()), mime = saved.mime,
                )
            }
            notifyResult(id, item.title, ok = true, saved = saved, shareUris = listOf(saved.uri), what = "Conversion")
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (id in cancelled) {
                setState(id, State.CANCELED, "Canceled")
            } else {
                val msg = conversionMessage(t)
                DownloadRepository.update(id) { it.copy(state = State.FAILED, message = msg) }
                notifyResult(id, item.title, ok = false, error = msg, what = "Conversion")
            }
        } finally {
            cancelled.remove(id)
            tmp.deleteRecursively()
        }
    }

    /** Una pasada completa: preparar, descargar y guardar. Lanza si falla. */
    private suspend fun attempt(item: DownloadItem, tmp: File) {
        val id = item.id
        setState(id, State.PREPARING, "Getting ready…")
        notifyProgress(item, "Getting ready…", -1, force = true)

        val free = StatFs(cacheDir.absolutePath).availableBytes
        if (FreeSpace.isLow(free)) error(FreeSpace.message(free))

        Engine.ensureReady(applicationContext)
        Engine.autoUpdateIfStale(applicationContext, prefs)

        if (item.quality.kind == Kind.PHOTO) {
            // El usuario eligió fotos (flujo manual): directo a gallery-dl.
            downloadPhotos(item, tmp)
        } else {
            var retried = false
            while (true) {
                try {
                    runYtDlp(item, tmp)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (id in cancelled) throw e
                    // Sin vídeo (publicación de fotos, galería, web desconocida): probar con gallery-dl.
                    if (prefs.photosEnabled && Errors.isNoVideo(e)) {
                        tmp.listFiles()?.forEach { it.deleteRecursively() }
                        try {
                            downloadPhotos(item, tmp)
                            break
                        } catch (pe: Exception) {
                            if (id in cancelled) throw pe
                            // Si gallery-dl pide iniciar sesión se muestra eso; si no, el error original.
                            throw if (Errors.isLoginRequired(pe)) pe else e
                        }
                    }
                    // Sin internet no es culpa de yt-dlp: lo gestiona la espera de conexión.
                    if (Errors.isNetwork(e)) throw e
                    // Casi siempre es yt-dlp desactualizado: actualizar una vez y reintentar.
                    if (!retried && Engine.canForceUpdate(prefs)) {
                        retried = true
                        notifyProgress(item, "Updating yt-dlp…", -1, force = true)
                        runCatching { Engine.update(applicationContext, prefs) }
                        continue
                    }
                    throw e
                }
            }
            // Una presentación de fotos solo da audio con yt-dlp: si hay fotos, bajarlas también.
            if (item.quality.kind == Kind.VIDEO && prefs.photosEnabled) addPhotosIfAudioOnly(item, tmp)
        }

        setState(id, State.PROCESSING, "Saving…")
        // Una publicación con varios vídeos (p. ej. un carrusel) genera varios archivos:
        // se guardan todos, no solo el más grande.
        val found = tmp.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.sortedBy { it.name }
            .orEmpty()
        if (found.isEmpty()) error("yt-dlp finished but produced no file")

        // Una publicación de fotos puede traer también su canción (Instagram): se reconoce aunque se llame ".mp4"
        // y se deja después de las fotos, para que la miniatura y el título sean los de una foto.
        val hasPhotos = found.any { MediaKinds.isImage(it) }
        val files = if (hasPhotos) {
            found.map { if (MediaKinds.isAudioOnly(it)) MediaKinds.asAudioFile(it) else it }
                .sortedWith(compareBy({ !MediaKinds.isImage(it) }, { it.name }))
        } else found
        val photoVideo = hasPhotos && files.any { !MediaKinds.isImage(it) && MediaKinds.isAudioOnly(it) }

        val savedAll = files.map { MediaSaver.publish(applicationContext, it) }
        val saved = savedAll.first()
        val title = Errors.cleanTitle(files.first()) +
            if (files.size > 1) " (+${files.size - 1} more)" else ""
        DownloadRepository.update(id) {
            it.copy(
                state = State.DONE,
                progress = 100f,
                title = title,
                message = null,
                savedUris = savedAll.map { it.uri.toString() },
                mime = saved.mime,
                photoVideo = photoVideo,
            )
        }
        DownloadIndex.record(applicationContext, item.url, item.quality.kind, savedAll.map { it.uri.toString() })
        notifyResult(id, title, ok = true, saved = saved, shareUris = savedAll.map { it.uri })
        if (photoVideo && prefs.autoPhotoVideo) DownloadRepository.get(id)?.let { enqueueSlideshow(it) }
    }

    /** Espera a que haya internet (hasta 30 min). false = se canceló o se agotó el tiempo. */
    private suspend fun waitForNetwork(id: String, attempt: Int): Boolean {
        val deadline = System.currentTimeMillis() + 30 * 60 * 1000L
        // Si ya hay conexión, el fallo es de la web: pausa más larga en cada intento.
        if (Connectivity.isOnline(this)) delay(5_000L * attempt)
        while (System.currentTimeMillis() < deadline) {
            if (id in cancelled) return false
            if (Connectivity.isOnline(this)) {
                delay(2_000)
                return id !in cancelled
            }
            delay(2_000)
        }
        return false
    }

    private val audioExt = setOf("m4a", "mp3", "opus", "aac", "ogg", "wav", "flac", "weba", "mka")

    private fun downloadPhotos(item: DownloadItem, dir: File) {
        setState(item.id, State.PROCESSING, "Downloading photos…")
        notifyProgress(item, "Downloading photos…", -1, force = true)
        GalleryDl.download(applicationContext, item.id, item.url, dir)
    }

    /** Si yt-dlp solo bajó audio (típico de una presentación de fotos), comprueba si el enlace tiene imágenes y las baja. */
    private fun addPhotosIfAudioOnly(item: DownloadItem, dir: File) {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.endsWith(".part") }.orEmpty()
        if (files.isEmpty() || !files.all { it.extension.lowercase() in audioExt }) return
        if (item.id in cancelled) return
        runCatching {
            if (GalleryDl.countImages(applicationContext, item.url) > 0) downloadPhotos(item, dir)
        }
    }

    private fun buildRequest(item: DownloadItem, dir: File): YoutubeDLRequest {
        val r = YoutubeDLRequest(item.url)
        // El índice solo aparece en publicaciones con varios vídeos, para que no se pisen entre sí.
        r.addOption("-o", "${dir.absolutePath}/%(title).100B%(playlist_index& - {}|)s.%(ext)s")
        r.addOption("--no-playlist")
        r.addOption("--no-mtime")
        r.addOption("--windows-filenames")
        r.addOption("--newline")
        r.addOption("--concurrent-fragments", 4)
        // Sesiones guardadas por el usuario. yt-dlp solo envía cada cookie a su propio sitio.
        CookieStore.mergedPath(applicationContext)?.let { r.addOption("--cookies", it) }

        val q = item.quality
        if (q.kind == Kind.VIDEO) {
            // Excluye el formato "con marca de agua" cuando el sitio ofrece también uno limpio.
            val clean = "[format_note!*=?watermarked]"
            r.addOption("-f", "bv*$clean+ba/b$clean/bv*+ba/b")
            // Resolución primero (con tope si se pidió); a igualdad, preferir h264/aac (compatible con todos los móviles).
            val res = if (q.maxHeight > 0) "res:${q.maxHeight}" else "res"
            r.addOption("-S", "$res,vcodec:h264,acodec:m4a")
            r.addOption("--merge-output-format", "mp4")
        } else {
            AudioOptions.ytDlpOptions(q.audioFormat, q.audioBitrate).forEach { (name, value) ->
                if (value == null) r.addOption(name) else r.addOption(name, value)
            }
        }
        return r
    }

    private fun runYtDlp(item: DownloadItem, dir: File) {
        val id = item.id
        var lastPct = -1f
        YoutubeDL.execute(buildRequest(item, dir), id) { progress, eta, line ->
            if (id in cancelled) return@execute
            val processing = line.contains("[Merger]") || line.contains("[ExtractAudio]") ||
                line.contains("[VideoConvertor]") || line.contains("[Metadata]")
            if (processing) {
                DownloadRepository.update(id) {
                    it.copy(state = State.PROCESSING, message = "Processing…")
                }
                notifyProgress(item, "Processing…", -1)
            } else if (progress >= 0f && progress != lastPct) {
                lastPct = progress
                DownloadRepository.update(id) {
                    it.copy(
                        state = State.DOWNLOADING,
                        progress = progress,
                        etaSeconds = eta,
                        message = null,
                    )
                }
                notifyProgress(item, "Downloading… ${progress.toInt()}%", progress.toInt())
            }
        }
    }

    private fun setState(id: String, state: State, message: String?) {
        DownloadRepository.update(id) { it.copy(state = state, message = message) }
    }

    // ───────────────────────── notificaciones ─────────────────────────

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW),
        )
        // El canal "result" sonaba (importancia por defecto). Android no deja bajar la importancia
        // de un canal ya creado, así que se borra y se usa otro silencioso.
        nm.deleteNotificationChannel("result")
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, "Finished downloads", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun baseNotification(title: String, text: String, percent: Int): NotificationCompat.Builder =
        NotificationCompat.Builder(this, CH_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title.take(60))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .setProgress(100, percent.coerceAtLeast(0), percent < 0)

    private fun notifyProgress(item: DownloadItem, text: String, percent: Int, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotifMs < 700) return
        lastNotifMs = now
        val cancelIntent = PendingIntent.getService(
            this, item.id.hashCode(),
            Intent(this, DownloadService::class.java)
                .setAction(ACTION_CANCEL).putExtra(EXTRA_ID, item.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = baseNotification(item.title, text, percent)
            .addAction(0, "Cancel", cancelIntent)
            .build()
        runCatching { nm.notify(NOTIF_PROGRESS, n) }
    }

    private fun notifyResult(
        id: String,
        title: String,
        ok: Boolean,
        saved: MediaSaver.Saved? = null,
        error: String? = null,
        item: DownloadItem? = null,
        loginHost: String? = null,
        shareUris: List<Uri> = emptyList(),
        what: String = "Download",
    ) {
        val contentIntent = if (!ok && item != null && loginHost != null) {
            // Al tocar el aviso se abre la app directamente con la explicación de inicio de sesión.
            PendingIntent.getActivity(
                this, id.hashCode(),
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_LOGIN_HOST, loginHost)
                    .putExtra(MainActivity.EXTRA_RETRY_URL, item.url)
                    .putExtra(MainActivity.EXTRA_RETRY_ID, item.id)
                    .putExtra(MainActivity.EXTRA_KIND, item.quality.kind.name)
                    .putExtra(MainActivity.EXTRA_HEIGHT, item.quality.maxHeight)
                    .putExtra(MainActivity.EXTRA_AUDIO_FMT, item.quality.audioFormat)
                    .putExtra(MainActivity.EXTRA_AUDIO_BR, item.quality.audioBitrate),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else if (ok && saved != null && saved.uri.scheme == "content") {
            PendingIntent.getActivity(
                this, id.hashCode(),
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(saved.uri, saved.mime)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else openAppIntent()

        val builder = NotificationCompat.Builder(this, CH_RESULT)
            .setSmallIcon(
                if (ok) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error,
            )
            .setContentTitle(if (ok) "$what complete" else "$what failed")
            .setContentText(if (ok) title else (error ?: title))
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (ok) title else (error ?: title)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        if (ok && shareUris.isNotEmpty() && shareUris.all { it.scheme == "content" }) {
            val share = PendingIntent.getActivity(
                this, id.hashCode() + 1, Sharing.chooser(shareUris, saved?.mime),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, "Share", share)
        }
        runCatching { nm.notify(id.hashCode(), builder.build()) }
    }

    @Suppress("unused")
    private fun uriOf(s: String): Uri = Uri.parse(s)
}
