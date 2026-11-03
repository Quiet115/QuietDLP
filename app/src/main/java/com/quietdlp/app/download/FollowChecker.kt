package com.quietdlp.app.download

import android.content.Context
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.FollowDiff
import com.quietdlp.app.data.FollowEngine
import com.quietdlp.app.data.FollowInterval
import com.quietdlp.app.data.FollowUrl
import com.quietdlp.app.data.FollowedPage
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.PlaylistEntry
import com.quietdlp.app.data.PlaylistProbe
import com.quietdlp.app.data.Prefs
import com.quietdlp.app.data.Quality
import com.quietdlp.app.data.State
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.runBlocking
import java.net.URI
import java.util.UUID

/** Lo que se encontró al mirar una página para seguirla. */
class FollowProbe(val engine: FollowEngine, val title: String, val thumbnail: String?, val entries: List<PlaylistEntry>)

/**
 * Mira las páginas seguidas, apunta lo nuevo y lo baja. Todo es bloqueante: llamarlo desde un hilo de fondo.
 * Una revisión solo APUNTA las novedades ([FollowedPage.pending]); [release] las convierte en descargas.
 */
object FollowChecker {
    /** Cuántas entradas se leen en cada revisión (las más nuevas de un canal o galería). */
    private const val LIST_MAX = 50
    private const val GALLERY_MAX = 100

    /** Una lista de reproducción puede traer lo nuevo al final, así que se lee entera. */
    private const val PLAYLIST_MAX = PlaylistProbe.MAX_ENTRIES

    /** Tope de novedades guardadas sin bajar. */
    private const val PENDING_MAX = 500

    private fun isPlaylistUrl(url: String) = "list=" in url || "/playlist" in url

    private fun ensureEngine(ctx: Context) = runBlocking { Engine.ensureReady(ctx.applicationContext) }

    private fun listYtDlp(ctx: Context, url: String, max: Int): com.quietdlp.app.data.PlaylistInfo? {
        val req = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("-J")
            addOption("--playlist-end", max)
            CookieStore.mergedPath(ctx)?.let { addOption("--cookies", it) }
        }
        return PlaylistProbe.parseForFollow(YoutubeDL.execute(req).out)
    }

    private fun hostOf(url: String): String =
        runCatching { URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("").ifBlank { UrlUtils.shorten(url) }

    /**
     * Mira qué es la página: una lista que lee yt-dlp o una galería que lee gallery-dl. Lanza una excepción con un
     * mensaje claro si no se puede seguir.
     */
    fun probe(ctx: Context, url: String): FollowProbe {
        ensureEngine(ctx)
        var firstError: Throwable? = null
        val playlist = isPlaylistUrl(url)
        val y = runCatching { listYtDlp(ctx, url, if (playlist) PLAYLIST_MAX else LIST_MAX) }.onFailure { firstError = it }.getOrNull()
        if (y != null && y.entries.isNotEmpty()) {
            // Lo más nuevo primero (en una lista de reproducción lo añadido después queda al final).
            val entries = if (playlist) y.entries.asReversed() else y.entries
            return FollowProbe(FollowEngine.YTDLP, FollowUrl.cleanTitle(y.title), entries.firstNotNullOfOrNull { it.thumbnail }, entries)
        }

        if (Prefs(ctx).photosEnabled) {
            val g = runCatching { GalleryDl.listFiles(ctx, url, GALLERY_MAX) }
            val files = g.getOrNull()
            if (!files.isNullOrEmpty()) {
                return FollowProbe(FollowEngine.GALLERY, FollowUrl.galleryTitle(url), files.firstNotNullOfOrNull { it.thumbnail }, files)
            }
            val ge = g.exceptionOrNull()
            if (ge != null && (Errors.isLoginRequired(ge) || firstError == null || Errors.isNoVideo(firstError!!))) throw ge
        }
        firstError?.let { if (Errors.isLoginRequired(it)) throw it }
        throw Exception(if (y != null) "There's nothing to follow on this page yet." else "This page doesn't have a list to follow.")
    }

    /** Las entradas de la página, las más nuevas primero. */
    private fun list(ctx: Context, page: FollowedPage): List<PlaylistEntry> = when (page.engine) {
        FollowEngine.GALLERY -> GalleryDl.listFiles(ctx, page.url, GALLERY_MAX)
        FollowEngine.YTDLP -> {
            val playlist = isPlaylistUrl(page.url)
            val info = listYtDlp(ctx, page.url, if (playlist) PLAYLIST_MAX else LIST_MAX)
                ?: throw Exception("This page doesn't have a list to follow.")
            // En una lista de reproducción lo añadido después queda al final.
            if (playlist) info.entries.asReversed() else info.entries
        }
    }

    /** Apunta como visto todo lo que hay ahora (al empezar a seguir): no se baja nada de lo que ya existía. */
    fun markAllSeen(id: String, entries: List<PlaylistEntry>) {
        FollowStore.addSeen(id, entries.map { it.url })
    }

    /** Revisa una página y deja lo nuevo en [FollowedPage.pending]. Devuelve cuántas novedades hay ahora sin bajar. */
    @Synchronized
    fun check(ctx: Context, id: String): Int {
        val page = FollowStore.get(id) ?: return 0
        val now = System.currentTimeMillis()
        try {
            ensureEngine(ctx)
            val listed = list(ctx, page)
            val seen = FollowStore.seen(id)
            // Sin lista de vistos (se perdió el archivo) no se sabe qué es nuevo: se toma todo como visto y no se baja un montón.
            val fresh = if (seen.isEmpty() && page.lastCheck > 0) emptyList() else FollowDiff.fresh(listed, seen)
            FollowStore.addSeen(id, listed.map { it.url })
            FollowStore.update(id) {
                it.copy(
                    lastCheck = now,
                    lastError = null,
                    pending = (fresh + it.pending).distinctBy { e -> e.url }.take(PENDING_MAX),
                )
            }
        } catch (e: Exception) {
            FollowStore.update(id) { it.copy(lastCheck = now, lastError = Errors.friendly(e)) }
        }
        return FollowStore.get(id)?.pending?.size ?: 0
    }

    /**
     * Convierte las novedades sin bajar en descargas y las pone en la cola. Devuelve cuántas puso, o -1 si Android no
     * dejó arrancar la descarga (en segundo plano no deja): quedan apuntadas para cuando se abra la app.
     */
    @Synchronized
    fun release(ctx: Context, id: String, includeDuplicates: Boolean = true): Int {
        val page = FollowStore.get(id) ?: return 0
        val prefs = Prefs(ctx)
        DownloadRepository.init(ctx)
        val photo = page.engine == FollowEngine.GALLERY
        val quality = if (photo) Quality(Kind.PHOTO) else page.quality
        val entries = if (includeDuplicates) page.pending
        else page.pending.filter { DownloadIndex.check(ctx, it.url, quality.kind, it.title) == DownloadIndex.Dup.NONE }
        if (entries.isEmpty()) {
            if (page.pending.isNotEmpty()) FollowStore.update(id) { it.copy(pending = emptyList()) }
            return 0
        }
        val items = entries.mapIndexed { j, e ->
            DownloadItem(
                id = UUID.randomUUID().toString(),
                url = e.url,
                title = e.title,
                thumbnail = e.thumbnail,
                quality = quality,
                groupId = page.groupId,
                // Las más nuevas arriba del grupo y primero en bajarse.
                groupIndex = -(page.counter + entries.size - j),
                groupTitle = page.title,
                wifiOnly = prefs.followWifiOnly,
                cover = !photo && prefs.coverFor(quality.kind),
                followId = page.id,
                fresh = true,
            )
        }
        DownloadRepository.addAll(items)
        try {
            DownloadService.enqueueExisting(ctx, items.map { it.id })
        } catch (e: Exception) {
            DownloadRepository.removeAll(items.map { it.id })
            return -1
        }
        FollowStore.update(id) { it.copy(pending = emptyList(), counter = it.counter + items.size) }
        return items.size
    }

    /** Las páginas a las que les toca revisar (todas si [force]). */
    fun due(force: Boolean = false): List<FollowedPage> {
        val now = System.currentTimeMillis()
        return FollowStore.pages.value.filter { force || FollowInterval.isDue(it, now) }
    }

    /**
     * Revisa las que toca y baja lo nuevo de las que se bajan solas. [shouldStop] corta entre página y página.
     * Es lo que hace la tarea en segundo plano y lo que se hace al abrir la app.
     */
    fun runDue(ctx: Context, shouldStop: () -> Boolean = { false }) {
        FollowStore.init(ctx)
        for (p in due()) {
            if (shouldStop()) return
            check(ctx, p.id)
        }
        // También lo que quedó sin bajar de antes (por ejemplo, porque Android no dejó arrancar en segundo plano).
        for (p in FollowStore.pages.value) {
            if (shouldStop()) return
            if (p.auto && p.pending.isNotEmpty()) release(ctx, p.id)
        }
    }

    /** Los vídeos de una página que están bajándose ahora mismo. */
    fun activeCount(id: String): Int =
        DownloadRepository.items.value.count { it.followId == id && it.state != State.DONE && it.isActive }
}
