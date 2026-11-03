package com.quietdlp.app.data

/** Qué se hace con las partes que la gente marcó en SponsorBlock (solo YouTube). */
enum class SponsorMode(val id: String, val label: String) {
    REMOVE("remove", "Remove them"),
    MARK("mark", "Mark as chapters");

    companion object {
        fun fromId(id: String?): SponsorMode? = entries.firstOrNull { it.id == id }
    }
}

/** Las clases de partes que SponsorBlock distingue: (código de yt-dlp, texto que se ve). */
object SponsorCategories {
    val ALL: List<Pair<String, String>> = listOf(
        "sponsor" to "Sponsor",
        "selfpromo" to "Self-promotion",
        "interaction" to "Like and subscribe reminder",
        "intro" to "Intro",
        "outro" to "Outro",
        "preview" to "Preview or recap",
        "filler" to "Filler",
        "music_offtopic" to "Part without music",
    )

    /** Lo que se quita si no se elige otra cosa: la publicidad y los avisos que no aportan nada. */
    val DEFAULT: List<String> = listOf("sponsor", "selfpromo", "interaction")

    private val KNOWN = ALL.map { it.first }.toSet()

    /** Solo los códigos que existen, sin repetir y en el orden de la lista. */
    fun clean(codes: Collection<String>): List<String> = ALL.map { it.first }.filter { it in codes && it in KNOWN }
}

/** Lo pedido para un vídeo: [mode] con las [categories] elegidas. */
data class SponsorChoice(val mode: SponsorMode, val categories: List<String>) {
    /** Para guardarlo en el historial: "remove|sponsor,selfpromo". */
    fun encode(): String = "${mode.id}|${categories.joinToString(",")}"

    companion object {
        fun decode(text: String?): SponsorChoice? {
            if (text.isNullOrBlank()) return null
            val p = text.split("|")
            if (p.size != 2) return null
            val mode = SponsorMode.fromId(p[0]) ?: return null
            val cats = SponsorCategories.clean(p[1].split(",").toSet())
            return if (cats.isEmpty()) null else SponsorChoice(mode, cats)
        }

        /** SponsorBlock solo tiene datos de YouTube. */
        fun supports(url: String): Boolean {
            val host = url.substringAfter("://", "").substringBefore('/').substringBefore(':').lowercase()
            return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" || host.endsWith(".youtube-nocookie.com")
        }
    }
}

/** Qué hacer con los capítulos que trae un vídeo. */
enum class ChapterMode(val id: String, val label: String) {
    OFF("off", "Ignore"),
    EMBED("embed", "Keep in the file"),
    SPLIT("split", "One file per chapter");

    companion object {
        fun fromId(id: String?): ChapterMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

/** Los argumentos de yt-dlp para SponsorBlock y capítulos, en una lista plana (cada elemento, un argumento). */
object ChapterOptions {

    /**
     * Cómo se llaman los archivos de un vídeo dividido por capítulos: "Título - 03 Nombre del capítulo". La marca
     * " - NN " sirve para reconocerlos y distinguirlos del vídeo entero ([isChapterFile]). Lleva la carpeta [dir] porque
     * yt-dlp, si no, los deja en su carpeta de trabajo y no en la de la descarga.
     */
    fun chapterTemplate(dir: String): String =
        "chapter:$dir/%(title).50B - %(section_number)02d %(section_title).60B.%(ext)s"

    private val CHAPTER_FILE = Regex(""" - \d{2} """)

    fun isChapterFile(name: String): Boolean = CHAPTER_FILE.containsMatchIn(name)

    fun arguments(sponsor: SponsorChoice?, chapters: ChapterMode, dir: String): List<String> {
        val out = ArrayList<String>()
        if (sponsor != null && sponsor.categories.isNotEmpty()) {
            val flag = if (sponsor.mode == SponsorMode.REMOVE) "--sponsorblock-remove" else "--sponsorblock-mark"
            out += flag
            out += sponsor.categories.joinToString(",")
        }
        // Marcar partes solo vale si los capítulos van dentro del archivo; yt-dlp no los pone solo.
        val embed = chapters == ChapterMode.EMBED || sponsor?.mode == SponsorMode.MARK
        if (embed && chapters != ChapterMode.SPLIT) out += "--embed-chapters"
        if (chapters == ChapterMode.SPLIT) {
            out += "--split-chapters"
            out += "-o"
            out += chapterTemplate(dir)
        }
        return out
    }
}
