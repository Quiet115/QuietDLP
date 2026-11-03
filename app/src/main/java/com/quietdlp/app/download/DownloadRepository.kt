package com.quietdlp.app.download

import android.content.Context
import androidx.core.util.AtomicFile
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.HistoryJson
import com.quietdlp.app.data.State
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.concurrent.Executors

/**
 * Lista de descargas. El servicio escribe, la pantalla observa. Se guarda en el móvil cuando
 * una descarga empieza, termina o se borra (no en cada avance), y se recupera al abrir la app.
 */
object DownloadRepository {
    private val _items = MutableStateFlow<List<DownloadItem>>(emptyList())
    val items: StateFlow<List<DownloadItem>> = _items

    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "history").apply { isDaemon = true } }
    private var file: AtomicFile? = null

    /** Se puede llamar varias veces (app y servicio). Lo cargado se mezcla con lo que ya haya en memoria. */
    @Synchronized
    fun init(ctx: Context) {
        if (file != null) return
        val f = AtomicFile(File(ctx.applicationContext.filesDir, "history.json"))
        file = f
        val loaded = runCatching { HistoryJson.decode(f.openRead().use { it.readBytes().decodeToString() }) }
            .getOrDefault(emptyList())
        _items.update { current ->
            val known = current.map { it.id }.toSet()
            current + loaded.filter { it.id !in known }
        }
        DownloadIndex.seed(ctx, _items.value)
    }

    fun add(item: DownloadItem) {
        _items.update { listOf(item) + it }
        save()
    }

    /** Añade varias a la vez (un grupo), en el orden dado y todas juntas arriba de la lista. */
    fun addAll(items: List<DownloadItem>) {
        _items.update { items + it }
        save()
    }

    /**
     * Pone en la lista las filas de una copia de seguridad que todavía no están (por id), debajo de las actuales: son
     * más viejas. Devuelve cuántas añadió.
     */
    fun mergeHistory(ctx: Context, restored: List<DownloadItem>): Int {
        var added: List<DownloadItem> = emptyList()
        _items.update { current ->
            val known = current.map { it.id }.toSet()
            added = restored.filter { it.id !in known && !it.isActive }
            current + added
        }
        if (added.isNotEmpty()) {
            DownloadIndex.seed(ctx, added)
            save()
        }
        return added.size
    }

    fun get(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    fun update(id: String, block: (DownloadItem) -> DownloadItem) {
        var finished = false
        _items.update { list ->
            finished = false
            list.map {
                if (it.id != id) it else block(it).also { n -> finished = it.isActive && !n.isActive }
            }
        }
        if (finished) save()
    }

    fun remove(id: String) {
        _items.update { list -> list.filterNot { it.id == id } }
        save()
    }

    /** Quita varias a la vez (por ejemplo, las que no se pudieron poner en la cola). */
    fun removeAll(ids: Collection<String>) {
        if (ids.isEmpty()) return
        _items.update { list -> list.filterNot { it.id in ids } }
        save()
    }

    /** El usuario ya vio lo que llegó de una página seguida (de todas si [followId] es null). */
    fun clearFresh(followId: String?) {
        var changed = false
        _items.update { list ->
            changed = false
            list.map {
                if (it.fresh && (followId == null || it.followId == followId)) { changed = true; it.copy(fresh = false) } else it
            }
        }
        if (changed) save()
    }

    fun clearFinished() {
        _items.update { list -> list.filter { it.isActive } }
        save()
    }

    fun hasActive(): Boolean = _items.value.any { it.state != State.DONE && it.isActive }

    private fun save() {
        val f = file ?: return
        writer.execute {
            val json = HistoryJson.encode(_items.value)
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
