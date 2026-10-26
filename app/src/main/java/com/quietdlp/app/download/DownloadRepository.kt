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
