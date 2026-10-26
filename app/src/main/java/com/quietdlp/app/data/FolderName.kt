package com.quietdlp.app.data

/** Nombre de la carpeta donde se guardan las descargas. */
object FolderName {
    const val DEFAULT = "QuietDLP"
    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    /** Quita lo que no se puede usar en el nombre de una carpeta; si no queda nada, vuelve al nombre por defecto. */
    fun clean(input: String): String =
        input.replace(illegal, "").trim().trim('.').take(40).trim().ifEmpty { DEFAULT }
}
