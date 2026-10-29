package com.quietdlp.app.data

/** Lee lo que escribe ffmpeg (con `-progress pipe:1`) para saber cuánto falta y qué falló. */
object FfmpegOutput {
    private val DURATION = Regex("""Duration:\s*(\d+):(\d{2}):(\d{2}(?:\.\d+)?)""")
    private val OUT_TIME = Regex("""^out_time_us=(\d+)$""")
    private val PROGRESS_LINE = Regex("""^[a-z_0-9]+=\S*$""")

    /** Duración total en milisegundos, de la línea "Duration: 00:03:21.45, start: ..." */
    fun durationMs(line: String): Long? {
        val m = DURATION.find(line) ?: return null
        val (h, min, s) = m.destructured
        return ((h.toLong() * 3600 + min.toLong() * 60) * 1000 + (s.toDouble() * 1000).toLong())
    }

    /** Lo ya procesado en milisegundos, de la línea "out_time_us=1234567". */
    fun doneMs(line: String): Long? = OUT_TIME.find(line.trim())?.groupValues?.get(1)?.toLongOrNull()?.div(1000)

    /** 0..99 mientras trabaja (el 100 se marca al terminar). -1 si no se conoce la duración. */
    fun percent(doneMs: Long, totalMs: Long): Int =
        if (totalMs <= 0) -1 else (doneMs * 100 / totalMs).toInt().coerceIn(0, 99)

    /** La última línea que no es de progreso: casi siempre la causa del error. */
    fun errorLine(lines: List<String>): String =
        lines.map { it.trim() }.lastOrNull { it.isNotEmpty() && !PROGRESS_LINE.matches(it) }
            ?.let(::withoutLinkerPath)?.take(200) ?: "ffmpeg failed"

    // El cargador de Android escribe la ruta entera de la app antes de la causa; la causa es lo útil.
    private val LINKER = Regex("""^CANNOT LINK EXECUTABLE ".*?":\s*""")

    private fun withoutLinkerPath(line: String) = line.replace(LINKER, "Can't start ffmpeg: ")
}
