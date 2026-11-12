package com.quietdlp.app.data

// La orientación automática respeta el bloqueo de rotación del teléfono.
enum class AppOrientation(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    PORTRAIT("portrait", "Portrait"),
    LANDSCAPE("landscape", "Landscape");

    companion object {
        fun fromKey(key: String?): AppOrientation = entries.firstOrNull { it.key == key } ?: AUTO
    }
}

object Layouts {
    const val TWO_COLUMNS_FROM_DP = 840
    const val MAX_SINGLE_COLUMN_DP = 640

    fun twoColumns(widthDp: Int): Boolean = widthDp >= TWO_COLUMNS_FROM_DP
}
