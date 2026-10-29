package com.quietdlp.app.data

/** Cómo se orienta la pantalla de la app. [AUTO] sigue el giro del teléfono (y su bloqueo de rotación). */
enum class AppOrientation(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    PORTRAIT("portrait", "Portrait"),
    LANDSCAPE("landscape", "Landscape");

    companion object {
        fun fromKey(key: String?): AppOrientation = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

/** Desde qué ancho (en dp) la pantalla principal pasa a dos columnas. */
object Layouts {
    const val TWO_COLUMNS_FROM_DP = 840
    const val MAX_SINGLE_COLUMN_DP = 640

    fun twoColumns(widthDp: Int): Boolean = widthDp >= TWO_COLUMNS_FROM_DP
}
