package com.quietdlp.app.data

object FolderName {
    const val DEFAULT = "QuietDLP"
    private val illegal = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

    fun clean(input: String): String =
        input.replace(illegal, "").trim().trim('.').take(40).trim().ifEmpty { DEFAULT }
}
