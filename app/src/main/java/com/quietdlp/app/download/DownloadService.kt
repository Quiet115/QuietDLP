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
import com.quietdlp.app.data.DEFAULT_PHOTO_LIMIT
import com.quietdlp.app.data.ProfilePhoto
import com.quietdlp.app.data.ConcurrencyGate
import com.quietdlp.app.data.HistoryGroups
import com.quietdlp.app.data.ConvertTarget
import com.quietdlp.app.data.ChapterMode
import com.quietdlp.app.data.ChapterOptions
import com.quietdlp.app.data.ConvertTargets
import com.quietdlp.app.data.CoverOptions
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.FileNameTemplate
import com.quietdlp.app.data.FreeSpace
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.MusicNaming
import com.quietdlp.app.data.Prefs
import com.quietdlp.app.data.Quality
import com.quietdlp.app.data.Slideshow
import com.quietdlp.app.data.SponsorChoice
import com.quietdlp.app.data.State
import com.quietdlp.app.data.SubtitleChoice
import com.quietdlp.app.data.SubtitleMode
import com.quietdlp.app.data.SubtitleOptions
import com.quietdlp.app.data.TrimRange
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
import com.quietdlp.app.R
import com.quietdlp.app.Strings

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
        private const val ACTION_REQUEUE = "com.quietdlp.app.REQUEUE"
        private const val ACTION_CANCEL_MANY = "com.quietdlp.app.CANCEL_MANY"
        private const val ACTION_CANCEL_ALL = "com.quietdlp.app.CANCEL_ALL"
        private const val EXTRA_IDS = "ids"
        private const val EXTRA_WIFI_ONLY = "wifi_only"
        private const val EXTRA_SUBS = "subs"
        private const val EXTRA_TRIM = "trim"
        private const val EXTRA_COVER = "cover"
        private const val EXTRA_SPONSOR = "sponsor"
        private const val EXTRA_CHAPTERS = "chapters"
        private const val EXTRA_PHOTO_LIMIT = "photo_limit"
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
        private const val MAX_RATE_WAITS = 4
        private const val RATE_WAIT_MS = 15_000L
        private const val WAKE_LOCK_MS = 8 * 60 * 60 * 1000L

        fun enqueue(
            ctx: Context,
            url: String,
            quality: Quality,
            title: String? = null,
            thumbnail: String? = null,
            wifiOnly: Boolean = false,
            subs: SubtitleChoice? = null,
            trim: TrimRange? = null,
            cover: Boolean? = null,
            sponsor: SponsorChoice? = null,
            chapters: ChapterMode = ChapterMode.OFF,
            photoLimit: Int = DEFAULT_PHOTO_LIMIT,
        ) {
            val i = Intent(ctx, DownloadService::class.java)
                .setAction(ACTION_ENQUEUE)
                .putExtra(EXTRA_PHOTO_LIMIT, photoLimit)
                .putExtra(EXTRA_WIFI_ONLY, wifiOnly)
                .putExtra(EXTRA_SUBS, subs?.encode())
                .putExtra(EXTRA_TRIM, trim?.encode())
                .apply { if (cover != null) putExtra(EXTRA_COVER, cover) }
                .putExtra(EXTRA_SPONSOR, sponsor?.encode())
                .putExtra(EXTRA_CHAPTERS, chapters.id)
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

        fun cancelMany(ctx: Context, ids: List<String>) {
            if (ids.isEmpty()) return
            ctx.startService(
                Intent(ctx, DownloadService::class.java)
                    .setAction(ACTION_CANCEL_MANY)
                    .putStringArrayListExtra(EXTRA_IDS, ArrayList(ids)),
            )
        }

        /**
         * Pone en la cola descargas que ya están en la lista: las de un grupo recién creado (en el orden dado)
         * o las que fallaron y se reintentan. El servicio las busca por id, así no hace falta pasar sus datos.
         */
        fun enqueueExisting(ctx: Context, ids: List<String>) {
            if (ids.isEmpty()) return
            ContextCompat.startForegroundService(
                ctx,
                Intent(ctx, DownloadService::class.java)
                    .setAction(ACTION_REQUEUE)
                    .putStringArrayListExtra(EXTRA_IDS, ArrayList(ids)),
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private val pending = AtomicInteger(0)
    private val cancelled = ConcurrentHashMap.newKeySet<String>()
    private val main = Handler(Looper.getMainLooper())

    /** Cuántas descargas van a la vez. Baja a 1 si una web empieza a rechazar por ir muy rápido. */
    private lateinit var gate: ConcurrencyGate

    /** Grupos de los que ya se avisó que terminaron (un solo aviso por grupo, no uno por archivo). */
    private val notifiedGroups = ConcurrentHashMap.newKeySet<String>()

    /** Descargas que ya salieron de la cola y están en marcha (ver [HistoryGroups.mustWait]). */
    private val started = ConcurrentHashMap.newKeySet<String>()

    /** Descargas a las que yt-dlp ya les metió los subtítulos dentro del vídeo. */
    private val embeddedSubs = ConcurrentHashMap.newKeySet<String>()

    /** Lo mismo con la portada, y las que se repiten sin portada porque meterla falló. */
    private val embeddedCover = ConcurrentHashMap.newKeySet<String>()
    private val coverOff = ConcurrentHashMap.newKeySet<String>()

    /** Descargas que se repiten sin las sesiones guardadas, porque una caducada hacía fallar a la web. */
    private val cookiesOff = ConcurrentHashMap.newKeySet<String>()

    /** Ids puestos en la cola por [handleRequeue] que aún no salieron de ella. */
    private val queuedIds = ConcurrentHashMap.newKeySet<String>()

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
            baseNotification(Strings.get(R.string.settings_screen_quietdlp_2), Strings.get(R.string.download_service_getting_ready), -1).build(),
            if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "QuietDLP:download")
            .apply { acquire(WAKE_LOCK_MS) }

        // Lo que dejó una descarga interrumpida (el sistema cerró la app): al arrancar el servicio no hay ninguna en marcha.
        listOf("dl", "cv").forEach { File(cacheDir, it).deleteRecursively() }

        gate = ConcurrencyGate(prefs.maxParallel)
        // Varios trabajadores sacan de la misma cola en orden. Cada uno pide su hueco ANTES de sacar la siguiente,
        // así nunca se salta el orden y el tope (1 a 5) se puede bajar en caliente.
        repeat(Prefs.MAX_PARALLEL) {
            scope.launch {
                while (true) {
                    gate.acquire()
                    val id = queue.receiveCatching().getOrNull()
                    if (id == null) {
                        gate.release()
                        break
                    }
                    queuedIds -= id
                    started += id
                    try {
                        process(id)
                    } finally {
                        started -= id
                        gate.release()
                        finishGroup(id)
                        if (pending.decrementAndGet() <= 0) main.post { stopIfIdle() }
                    }
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
            ACTION_REQUEUE -> handleRequeue(intent)
            ACTION_CANCEL -> intent.getStringExtra(EXTRA_ID)?.let { handleCancel(it) }
            ACTION_CANCEL_MANY -> intent.getStringArrayListExtra(EXTRA_IDS)?.forEach { handleCancel(it) }
            ACTION_CANCEL_ALL -> DownloadRepository.items.value.filter { it.isActive }.forEach { handleCancel(it.id) }
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
            wifiOnly = intent.getBooleanExtra(EXTRA_WIFI_ONLY, false),
            subs = SubtitleChoice.decode(intent.getStringExtra(EXTRA_SUBS)),
            trim = TrimRange.decode(intent.getStringExtra(EXTRA_TRIM)),
            // Sin pedirlo (Compartir, enlaces copiados) vale lo que diga Ajustes.
            cover = if (intent.hasExtra(EXTRA_COVER)) intent.getBooleanExtra(EXTRA_COVER, false) else prefs.coverFor(kind),
            sponsor = SponsorChoice.decode(intent.getStringExtra(EXTRA_SPONSOR)),
            chapters = ChapterMode.fromId(intent.getStringExtra(EXTRA_CHAPTERS)),
            photoLimit = intent.getIntExtra(EXTRA_PHOTO_LIMIT, DEFAULT_PHOTO_LIMIT),
        )
        DownloadRepository.add(item)
        pending.incrementAndGet()
        queue.trySend(item.id)
    }

    /** Pone en cola, en el orden recibido, descargas que ya están en la lista (un grupo nuevo o reintentos). */
    private fun handleRequeue(intent: Intent) {
        val ids = intent.getStringArrayListExtra(EXTRA_IDS) ?: return
        for (id in ids) {
            val item = DownloadRepository.get(id) ?: continue
            item.groupId?.let(notifiedGroups::remove)
            cancelled.remove(id)
            // Si todavía está en la cola (se canceló y se reintenta enseguida) basta con quitarle la cancelación.
            if (!queuedIds.add(id)) continue
            pending.incrementAndGet()
            queue.trySend(id)
        }
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
            if (it.isActive) it.copy(state = State.CANCELED, message = Strings.get(R.string.main_screen_canceled)) else it
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
            var slowed = 0
            while (true) {
                // Con "solo Wi-Fi" no se empieza (ni se retoma) con datos móviles.
                if (!awaitNetworkAllowed(item)) error(Strings.get(R.string.main_screen_canceled))
                try {
                    attempt(item, tmp)
                    return
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    if (id in cancelled) throw t
                    // La web rechaza por ir muy rápido: pasar a una descarga a la vez y reintentar con calma, en este mismo hueco.
                    if (Errors.isRateLimited(t) && slowed < MAX_RATE_WAITS) {
                        slowed++
                        gate.limit = 1
                        val msg = Strings.get(R.string.download_service_site_is_busy_slowing_down)
                        setState(id, State.QUEUED, msg)
                        notifyProgress(item, msg, -1, force = true)
                        delay(RATE_WAIT_MS * slowed)
                        continue
                    }
                    // Sin internet: esperar a que vuelva y seguir donde se quedó (yt-dlp retoma los .part).
                    if (waits >= MAX_NET_WAITS || !Errors.isNetwork(t)) throw t
                    waits++
                    val msg = Strings.get(R.string.download_service_waiting_for_connection)
                    setState(id, State.QUEUED, msg)
                    notifyProgress(item, msg, -1, force = true)
                    if (!waitForNetwork(id, waits)) throw t
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (id in cancelled) {
                setState(id, State.CANCELED, Strings.get(R.string.main_screen_canceled))
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
                // Los archivos de un grupo avisan todos juntos al terminar (ver finishGroup), no uno por uno.
                if (item.groupId == null) {
                    notifyResult(
                        id, item.title, ok = false,
                        error = if (loginHost != null) Strings.get(R.string.download_service_sign_in_required_tap_to) else msg,
                        item = item, loginHost = loginHost,
                    )
                }
            }
        } finally {
            cancelled.remove(id)
            tmp.deleteRecursively()
        }
    }

    /**
     * Espera a que se pueda descargar con la red actual. Solo frena con "solo Wi-Fi" (el ajuste, o "Wait for Wi-Fi" en esa
     * descarga) mientras la red sea de pago por uso o no haya. false = se canceló mientras esperaba.
     */
    private suspend fun awaitNetworkAllowed(item: DownloadItem): Boolean {
        val id = item.id
        var shown = false
        while (true) {
            if (id in cancelled) return false
            if (!(item.wifiOnly || prefs.wifiOnly) || !Connectivity.isMetered(this)) return true
            if (!shown) {
                shown = true
                val msg = Strings.get(R.string.main_view_model_waiting_for_wi_fi)
                setState(id, State.QUEUED, msg)
                notifyProgress(item, msg, -1, force = true)
            }
            delay(3_000)
        }
    }

    /** Cuando el último archivo de un grupo termina, un solo aviso con el resultado de todos. */
    private fun finishGroup(id: String) {
        val gid = DownloadRepository.get(id)?.groupId ?: return
        val members = DownloadRepository.items.value.filter { it.groupId == gid }
        if (members.isEmpty() || members.any { it.isActive }) return
        if (!notifiedGroups.add(gid)) return
        val done = members.count { it.state == State.DONE }
        val failed = members.count { it.state == State.FAILED }
        val text = buildString {
            append(Strings.get(R.string.download_service_of_saved, done, members.size))
            if (failed > 0) append(" · " + Strings.get(R.string.notif_n_failed, failed))
        }
        val title = members.firstNotNullOfOrNull { it.groupTitle } ?: Strings.get(R.string.main_screen_downloads)
        val builder = NotificationCompat.Builder(this, CH_RESULT)
            .setSmallIcon(if (failed == 0) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_notify_error)
            .setContentTitle(if (failed == 0) Strings.get(R.string.download_service_downloads_complete) else Strings.get(R.string.download_service_downloads_finished))
            .setContentText("$title — $text")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$title\n$text"))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
        runCatching { nm.notify(gid.hashCode(), builder.build()) }
    }

    /** Antes de guardar su archivo, un archivo de un grupo espera a los anteriores del mismo grupo: así se guardan en orden. */
    private suspend fun awaitTurn(item: DownloadItem) {
        val id = item.id
        var shown = false
        while (id !in cancelled) {
            val me = DownloadRepository.get(id) ?: return
            if (!HistoryGroups.mustWait(DownloadRepository.items.value, me) { it in started }) return
            if (!shown) {
                shown = true
                setState(id, State.PROCESSING, Strings.get(R.string.download_service_waiting_for_the_files_before))
            }
            delay(300)
        }
        error(Strings.get(R.string.main_screen_canceled))
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
            is FileNotFoundException, is SecurityException -> Strings.get(R.string.download_service_the_original_file_isn_t)
            is IllegalStateException -> text.take(220)
            else -> Strings.get(R.string.download_service_couldn_t_convert, text).take(220)
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
            setState(id, State.PREPARING, Strings.get(R.string.download_service_getting_ready))
            notifyProgress(item, Strings.get(R.string.download_service_getting_ready), -1, force = true)
            Engine.ensureReady(applicationContext)

            val plan = prepare(tmp)
            if (id in cancelled) error(Strings.get(R.string.main_screen_canceled))

            val out = plan.output
            Ffmpeg.run(applicationContext, id, plan.command, plan.totalMs) { pct ->
                if (id in cancelled) return@run
                if (pct >= 0) {
                    DownloadRepository.update(id) {
                        it.copy(state = State.DOWNLOADING, progress = pct.toFloat(), message = null)
                    }
                    notifyProgress(item, Strings.get(R.string.download_service_converting, pct), pct)
                } else {
                    setState(id, State.PROCESSING, Strings.get(R.string.download_service_converting_2))
                    notifyProgress(item, Strings.get(R.string.download_service_converting_2), -1)
                }
            }
            if (!out.exists() || out.length() == 0L) error("ffmpeg finished but produced no file")

            setState(id, State.PROCESSING, Strings.get(R.string.download_service_saving))
            val saved = MediaSaver.publish(applicationContext, out)
            DownloadRepository.update(id) {
                it.copy(
                    state = State.DONE, progress = 100f, message = null,
                    savedUris = listOf(saved.uri.toString()), mime = saved.mime,
                )
            }
            notifyResult(id, item.title, ok = true, saved = saved, shareUris = listOf(saved.uri), conversion = true)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            if (id in cancelled) {
                setState(id, State.CANCELED, Strings.get(R.string.main_screen_canceled))
            } else {
                val msg = conversionMessage(t)
                DownloadRepository.update(id) { it.copy(state = State.FAILED, message = msg) }
                notifyResult(id, item.title, ok = false, error = msg, conversion = true)
            }
        } finally {
            cancelled.remove(id)
            tmp.deleteRecursively()
        }
    }

    /** Una pasada completa: preparar, descargar y guardar. Lanza si falla. */
    private suspend fun attempt(item: DownloadItem, tmp: File) {
        val id = item.id
        embeddedSubs -= id
        embeddedCover -= id
        setState(id, State.PREPARING, Strings.get(R.string.download_service_getting_ready))
        notifyProgress(item, Strings.get(R.string.download_service_getting_ready), -1, force = true)

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
                    // Una sesión caducada hace que la web responda vacío: se repite una vez sin sesiones (los enlaces públicos sí van).
                    if (id !in cookiesOff && Errors.isBadSession(e) && CookieStore.mergedPath(applicationContext) != null) {
                        cookiesOff += id
                        tmp.listFiles()?.forEach { it.deleteRecursively() }
                        continue
                    }
                    // La portada es un extra: si no se puede meter, se repite la descarga sin ella.
                    if (item.cover && id !in coverOff && Errors.isCoverProblem(e)) {
                        coverOff += id
                        tmp.listFiles()?.forEach { it.deleteRecursively() }
                        continue
                    }
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
                        notifyProgress(item, Strings.get(R.string.download_service_updating_yt_dlp), -1, force = true)
                        runCatching { Engine.update(applicationContext, prefs) }
                        continue
                    }
                    throw e
                }
            }
            // Una presentación de fotos solo da audio con yt-dlp: si hay fotos, bajarlas también.
            if (item.quality.kind == Kind.VIDEO && prefs.photosEnabled) addPhotosIfAudioOnly(item, tmp)
        }

        awaitTurn(item)
        setState(id, State.PROCESSING, Strings.get(R.string.download_service_saving))
        // Una publicación con varios vídeos (p. ej. un carrusel) genera varios archivos:
        // se guardan todos, no solo el más grande.
        val all = tmp.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.sortedBy { it.name }
            .orEmpty()
        // Los subtítulos que quedan sueltos (cuando se pidieron aparte) no son el archivo principal: se guardan junto a él.
        val subFiles = all.filter { it.extension.lowercase() in SubtitleOptions.FILE_EXTENSIONS }
        val whole = all - subFiles.toSet()
        // Dividido por capítulos: se guardan los capítulos y no el vídeo entero.
        val chapterFiles = whole.filter { ChapterOptions.isChapterFile(it.name) }
        val found = (if (item.chapters == ChapterMode.SPLIT && chapterFiles.size >= 2) chapterFiles else whole).let { f ->
            // La foto y el banner de un perfil se guardan como "usuario (profile)" y no con el nombre del servidor.
            if (item.quality.kind == Kind.PHOTO && ProfilePhoto.isProfileTitle(item.title) && f.size == 1) {
                val safe = item.title.replace(Regex("""[\/:*?"<>|]"""), "_").take(100)
                val target = File(f[0].parentFile, "$safe.${f[0].extension.ifBlank { "jpg" }}")
                listOf(if (f[0].renameTo(target)) target else f[0])
            } else f
        }
        if (found.isEmpty()) error("yt-dlp finished but produced no file")

        // Una publicación de fotos puede traer también su canción (Instagram): se reconoce aunque se llame ".mp4"
        // y se deja después de las fotos, para que la miniatura y el título sean los de una foto.
        val hasPhotos = found.any { MediaKinds.isImage(it) }
        val files = if (hasPhotos) {
            found.map { if (MediaKinds.isAudioOnly(it)) MediaKinds.asAudioFile(it) else it }
                .sortedWith(compareBy({ !MediaKinds.isImage(it) }, { it.name }))
        } else found
        val photoVideo = hasPhotos && files.any { !MediaKinds.isImage(it) && MediaKinds.isAudioOnly(it) }

        val wanted = item.subs?.takeIf { it.codes.isNotEmpty() && item.trim == null }
        // Vídeo y .srt van juntos, en la misma carpeta, para que los reproductores los emparejen.
        val together = wanted?.mode == SubtitleMode.SEPARATE && subFiles.isNotEmpty()
        val savedAll = files.map { MediaSaver.publish(applicationContext, it, together) }
        if (together) subFiles.forEach { runCatching { MediaSaver.publish(applicationContext, it, together = true) } }
        val noSubs = wanted != null &&
            if (wanted.mode == SubtitleMode.SEPARATE) subFiles.isEmpty() else id !in embeddedSubs
        embeddedSubs -= id
        val noCover = item.cover && !hasPhotos && CoverOptions.available(item.quality.kind, item.quality.audioFormat) &&
            id !in embeddedCover
        embeddedCover -= id
        coverOff -= id
        cookiesOff -= id
        val saved = savedAll.first()
        val title = Errors.cleanTitle(files.first()) +
            if (files.size > 1) " " + Strings.get(R.string.n_more_files, files.size - 1) else ""
        DownloadRepository.update(id) {
            it.copy(
                state = State.DONE,
                progress = 100f,
                title = title,
                message = missingExtras(noSubs, noCover),
                savedUris = savedAll.map { it.uri.toString() },
                mime = saved.mime,
                photoVideo = photoVideo,
            )
        }
        DownloadIndex.record(applicationContext, item.url, item.quality.kind, savedAll.map { it.uri.toString() })
        if (item.groupId == null) notifyResult(id, title, ok = true, saved = saved, shareUris = savedAll.map { it.uri })
        if (photoVideo && prefs.autoPhotoVideo) DownloadRepository.get(id)?.let { enqueueSlideshow(it) }
    }

    /** El aviso de lo que se pidió y no se pudo meter en el archivo (null si todo salió). */
    private fun missingExtras(noSubs: Boolean, noCover: Boolean): String? = when {
        noSubs && noCover -> Strings.get(R.string.download_service_saved_without_subtitles_or_cover)
        noSubs -> Strings.get(R.string.download_service_saved_without_subtitles)
        noCover -> Strings.get(R.string.download_service_saved_without_cover_image)
        else -> null
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
        setState(item.id, State.PROCESSING, Strings.get(R.string.download_service_downloading_photos))
        notifyProgress(item, Strings.get(R.string.download_service_downloading_photos), -1, force = true)
        // Lo que llega de una galería seguida es el enlace directo de cada archivo.
        if (item.followId != null && item.quality.kind == Kind.PHOTO) GalleryDl.fetchDirect(item.id, item.url, dir)
        else GalleryDl.download(applicationContext, item.id, item.url, dir, item.photoLimit)
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
        // Un trozo lleva su tiempo en el nombre, para que no pise la descarga completa.
        val part = item.trim?.fileLabel().orEmpty()
        // La música se llama "Artista - Título" y lleva esos datos dentro; el resto, el título del vídeo.
        val musicNames = item.quality.kind == Kind.AUDIO && prefs.musicNames
        val nameBase = FileNameTemplate.toYtDlp(prefs.nameTemplate)
        val template = if (musicNames) MusicNaming.fileTemplate(part) else "$nameBase$part%(playlist_index& - {}|)s.%(ext)s"
        r.addOption("-o", "${dir.absolutePath}/$template")
        if (musicNames) r.addCommands(MusicNaming.arguments())
        r.addOption("--no-playlist")
        r.addOption("--no-mtime")
        r.addOption("--windows-filenames")
        r.addOption("--newline")
        r.addOption("--concurrent-fragments", 4)
        // Sesiones guardadas por el usuario. yt-dlp solo envía cada cookie a su propio sitio.
        if (item.id !in cookiesOff) CookieStore.mergedPath(applicationContext)?.let { r.addOption("--cookies", it) }

        // Solo se baja ese trozo. El corte exacto es obligatorio: sin él, el audio salía del doble de largo.
        item.trim?.let { t ->
            r.addOption("--download-sections", t.section())
            r.addOption("--force-keyframes-at-cuts")
        }
        // Los subtítulos no se recortan con el vídeo (salen desfasados): con un trozo no se piden.
        val subs = item.subs.takeIf { item.trim == null }
        // SponsorBlock y capítulos no se mezclan con un trozo: sus tiempos son los del vídeo entero.
        if (item.trim == null && item.quality.kind != Kind.PHOTO) {
            r.addCommands(ChapterOptions.arguments(item.sponsor, item.chapters, dir.absolutePath))
        }
        if (item.cover && item.id !in coverOff) {
            CoverOptions.ytDlpOptions(item.quality.kind, item.quality.audioFormat).forEach { (name, value) ->
                if (value == null) r.addOption(name) else r.addOption(name, value)
            }
        }

        val q = item.quality
        if (q.kind == Kind.VIDEO) {
            // Excluye el formato "con marca de agua" cuando el sitio ofrece también uno limpio.
            val clean = "[format_note!*=?watermarked]"
            r.addOption("-f", "bv*$clean+ba/b$clean/bv*+ba/b")
            // Resolución primero (con tope si se pidió); a igualdad, preferir h264/aac (compatible con todos los móviles).
            val res = if (q.maxHeight > 0) "res:${q.maxHeight}" else "res"
            r.addOption("-S", "$res,vcodec:h264,acodec:m4a")
            // Con subtítulos en un MKV el vídeo final cambia de contenedor (también si el sitio da un solo archivo).
            val container = SubtitleOptions.container(subs)
            r.addOption("--merge-output-format", container)
            if (container != "mp4") r.addOption("--remux-video", container)
            SubtitleOptions.ytDlpOptions(subs).forEach { (name, value) ->
                if (value == null) r.addOption(name) else r.addOption(name, value)
            }
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
            if (line.contains("[EmbedSubtitle]")) embeddedSubs += id
            if (line.contains("[EmbedThumbnail]")) embeddedCover += id
            val processing = line.contains("[Merger]") || line.contains("[ExtractAudio]") ||
                line.contains("[VideoConvertor]") || line.contains("[Metadata]") ||
                line.contains("[EmbedSubtitle]") || line.contains("[SubtitlesConvertor]") ||
                line.contains("[EmbedThumbnail]") || line.contains("[ThumbnailsConvertor]")
            if (processing) {
                DownloadRepository.update(id) {
                    it.copy(state = State.PROCESSING, message = Strings.get(R.string.download_service_processing))
                }
                notifyProgress(item, Strings.get(R.string.download_service_processing), -1)
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
                notifyProgress(item, Strings.get(R.string.main_screen_downloading, progress.toInt()), progress.toInt())
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
            NotificationChannel(CH_PROGRESS, Strings.get(R.string.download_service_downloads_in_progress), NotificationManager.IMPORTANCE_LOW),
        )
        // El canal "result" sonaba (importancia por defecto). Android no deja bajar la importancia
        // de un canal ya creado, así que se borra y se usa otro silencioso.
        nm.deleteNotificationChannel("result")
        nm.createNotificationChannel(
            NotificationChannel(CH_RESULT, Strings.get(R.string.download_service_finished_downloads), NotificationManager.IMPORTANCE_LOW).apply {
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
        // Con varias a la vez la notificación cuenta las que faltan, y "Cancel" las cancela todas.
        val left = DownloadRepository.items.value.count { it.isActive }
        val multi = left > 1
        val cancelIntent = PendingIntent.getService(
            this, if (multi) 0 else item.id.hashCode(),
            Intent(this, DownloadService::class.java)
                .setAction(if (multi) ACTION_CANCEL_ALL else ACTION_CANCEL).putExtra(EXTRA_ID, item.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n: Notification = (
            if (multi) baseNotification(Strings.get(R.string.settings_screen_quietdlp_2), Strings.get(R.string.download_service_downloads_left, left), -1)
            else baseNotification(item.title, text, percent)
            )
            .addAction(0, if (multi) Strings.get(R.string.group_ui_cancel_all) else Strings.get(R.string.settings_screen_cancel), cancelIntent)
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
        conversion: Boolean = false,
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
            .setContentTitle(
                Strings.get(
                    when {
                        conversion && ok -> R.string.notif_conversion_complete
                        conversion -> R.string.notif_conversion_failed
                        ok -> R.string.notif_download_complete
                        else -> R.string.notif_download_failed
                    },
                ),
            )
            .setContentText(if (ok) title else (error ?: title))
            .setStyle(NotificationCompat.BigTextStyle().bigText(if (ok) title else (error ?: title)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
        if (ok && shareUris.isNotEmpty() && shareUris.all { it.scheme == "content" }) {
            val share = PendingIntent.getActivity(
                this, id.hashCode() + 1, Sharing.chooser(shareUris, saved?.mime),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(0, Strings.get(R.string.group_ui_share), share)
        }
        runCatching { nm.notify(id.hashCode(), builder.build()) }
    }

    @Suppress("unused")
    private fun uriOf(s: String): Uri = Uri.parse(s)
}
