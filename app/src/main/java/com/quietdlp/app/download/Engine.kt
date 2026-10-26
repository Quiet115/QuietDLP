package com.quietdlp.app.download

import android.content.Context
import com.quietdlp.app.data.Prefs
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Prepara y mantiene al día el motor de descarga (yt-dlp + Python + ffmpeg).
 *
 * La versión de yt-dlp que viene dentro de la app envejece rápido (las webs cambian a menudo),
 * por eso se descarga la última desde GitHub al arrancar y cada 24 h.
 */
object Engine {
    private val mutex = Mutex()

    @Volatile
    private var ready = false

    private const val DAY_MS = 24L * 60 * 60 * 1000
    private const val HOUR_MS = 60L * 60 * 1000

    suspend fun ensureReady(ctx: Context) {
        if (ready) return
        mutex.withLock {
            if (ready) return
            withContext(Dispatchers.IO) {
                val app = ctx.applicationContext
                YoutubeDL.init(app)
                FFmpeg.init(app)
            }
            ready = true
        }
    }

    // Mutex aparte del de ensureReady: update() llama a ensureReady() y un mutex no es reentrante.
    private val updateMutex = Mutex()

    private fun isStale(prefs: Prefs) = System.currentTimeMillis() - prefs.lastUpdateMs >= DAY_MS

    /** Actualiza yt-dlp si pasaron más de 24 h desde la última vez. Nunca lanza error (puede no haber red). */
    suspend fun autoUpdateIfStale(ctx: Context, prefs: Prefs) {
        if (!prefs.autoUpdate || !isStale(prefs)) return
        runCatching { update(ctx, prefs, onlyIfStale = true) }
    }

    /** Evita reintentos de actualización en bucle cuando una descarga falla. */
    fun canForceUpdate(prefs: Prefs): Boolean =
        prefs.autoUpdate && System.currentTimeMillis() - prefs.lastUpdateMs > HOUR_MS

    /**
     * Solo una actualización a la vez: si la app y el servicio la piden simultáneamente (primer
     * arranque + compartir enseguida), la segunda espera y, si ya está al día, no repite.
     */
    suspend fun update(ctx: Context, prefs: Prefs, onlyIfStale: Boolean = false): String =
        withContext(Dispatchers.IO) {
            ensureReady(ctx)
            updateMutex.withLock {
                if (onlyIfStale && !isStale(prefs)) return@withLock "yt-dlp is up to date"
                val status = YoutubeDL.updateYoutubeDL(
                    ctx.applicationContext,
                    YoutubeDL.UpdateChannel.STABLE,
                )
                prefs.lastUpdateMs = System.currentTimeMillis()
                val ytdlp = when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "yt-dlp updated"
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlp is up to date"
                    else -> "No changes"
                }
                // El motor de fotos se actualiza junto al de vídeo; si falla, no estropea la actualización principal.
                val photos = if (prefs.photosEnabled) {
                    runCatching { GalleryDl.update(ctx.applicationContext) }.getOrNull()
                } else null
                if (photos != null) "$ytdlp · $photos" else ytdlp
            }
        }

    fun version(ctx: Context): String? =
        runCatching { YoutubeDL.versionName(ctx.applicationContext) }.getOrNull()
}
