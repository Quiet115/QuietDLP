package com.quietdlp.app.download

import android.content.Context
import com.quietdlp.app.data.FfmpegOutput
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Ejecuta el ffmpeg que ya trae la app (el mismo que usa yt-dlp) como un programa aparte, para convertir
 * archivos ya descargados. Hay que llamarlo desde un hilo de fondo y con el motor ya iniciado
 * ([Engine.ensureReady]), que es quien deja sus bibliotecas en la carpeta privada de la app.
 */
object Ffmpeg {
    private val running = ConcurrentHashMap<String, Process>()

    private fun libs(ctx: Context) = File(ctx.noBackupFilesDir, "youtubedl-android/packages/ffmpeg/usr/lib")

    /**
     * Ejecuta ffmpeg con [command] (sin el nombre del programa). [onPercent] recibe 0..99 mientras avanza
     * (o -1 si no se conoce la duración). Lanza una excepción con la causa si ffmpeg falla.
     */
    fun run(ctx: Context, id: String, command: List<String>, onPercent: (Int) -> Unit) {
        val bin = File(ctx.applicationInfo.nativeLibraryDir, "libffmpeg.so")
        val pb = ProcessBuilder(listOf(bin.absolutePath) + command)
            .redirectErrorStream(true)
            .directory(ctx.cacheDir)
        pb.environment().apply {
            put("LD_LIBRARY_PATH", libs(ctx).path)
            put("TMPDIR", ctx.cacheDir.path)
        }
        val p = pb.start()
        running[id] = p
        try {
            val tail = ArrayDeque<String>()
            var totalMs = 0L
            var lastPercent = -2
            p.inputStream.bufferedReader().forEachLine { line ->
                if (tail.size >= 40) tail.removeFirst()
                tail.addLast(line)
                if (totalMs == 0L) FfmpegOutput.durationMs(line)?.let { totalMs = it }
                FfmpegOutput.doneMs(line)?.let { done ->
                    val pct = FfmpegOutput.percent(done, totalMs)
                    if (pct != lastPercent) {
                        lastPercent = pct
                        onPercent(pct)
                    }
                }
            }
            val code = p.waitFor()
            if (code != 0) throw Exception("ERROR: " + FfmpegOutput.errorLine(tail.toList()))
        } finally {
            running.remove(id)
            p.destroy()
        }
    }

    fun cancel(id: String) {
        running[id]?.destroy()
    }
}
