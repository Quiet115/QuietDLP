package com.quietdlp.app.download

import android.content.Context
import androidx.core.util.AtomicFile
import com.quietdlp.app.data.FollowJson
import com.quietdlp.app.data.FollowedPage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executors

/**
 * Las páginas seguidas. La lista vive en follows.json; lo ya visto de cada página, en un archivo aparte (follow/<id>.seen,
 * un enlace por línea), porque una página de fotos puede acumular decenas de miles.
 */
object FollowStore {
    /** Si un archivo de vistos pasa de esto, se quedan los más nuevos. */
    private const val MAX_SEEN = 200_000
    private const val KEEP_SEEN = 150_000

    private val _pages = MutableStateFlow<List<FollowedPage>>(emptyList())
    val pages: StateFlow<List<FollowedPage>> = _pages

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "follows").apply { isDaemon = true } }
    private var file: AtomicFile? = null
    private var dir: File? = null
    private val seenCache = HashMap<String, MutableSet<String>>()

    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val base = ctx.applicationContext.filesDir
        dir = File(base, "follow").apply { mkdirs() }
        val f = AtomicFile(File(base, "follows.json"))
        file = f
        val loaded = runCatching { FollowJson.decode(f.openRead().use { it.readBytes().decodeToString() }) }
            .getOrDefault(emptyList())
        _pages.update { current ->
            val known = current.map { it.id }.toSet()
            current + loaded.filter { it.id !in known }
        }
    }

    fun get(id: String): FollowedPage? = _pages.value.firstOrNull { it.id == id }

    fun add(page: FollowedPage) {
        _pages.update { it.filterNot { p -> p.id == page.id } + page }
        save()
    }

    fun update(id: String, block: (FollowedPage) -> FollowedPage) {
        _pages.update { list -> list.map { if (it.id == id) block(it) else it } }
        save()
    }

    @Synchronized
    fun remove(id: String) {
        _pages.update { list -> list.filterNot { it.id == id } }
        seenCache.remove(id)
        runCatching { seenFile(id)?.delete() }
        save()
    }

    private fun seenFile(id: String): File? = dir?.let { File(it, "$id.seen") }

    /** Lo ya visto de la página (cargado una vez; se va ampliando con [addSeen]). */
    @Synchronized
    fun seen(id: String): Set<String> = seenCache.getOrPut(id) {
        val f = seenFile(id)
        val lines = if (f != null && f.exists()) runCatching { f.readLines() }.getOrDefault(emptyList()) else emptyList()
        val keep = if (lines.size > MAX_SEEN) lines.takeLast(KEEP_SEEN).also { f?.writeText(it.joinToString("\n", postfix = "\n")) } else lines
        keep.filter { it.isNotBlank() }.toHashSet()
    }

    @Synchronized
    fun addSeen(id: String, urls: Collection<String>) {
        val set = seen(id) as MutableSet<String>
        val added = urls.filter { set.add(it) }
        if (added.isEmpty()) return
        runCatching { seenFile(id)?.appendText(added.joinToString("\n", postfix = "\n")) }
    }

    private fun save() {
        val f = file ?: return
        writer.execute {
            val json = FollowJson.encode(_pages.value)
            val out = runCatching { f.startWrite() }.getOrNull() ?: return@execute
            try {
                out.write(json.toByteArray())
                f.finishWrite(out)
            } catch (_: Exception) {
                f.failWrite(out)
            }
        }
    }
}
