package com.quietdlp.app.data

/**
 * Nombre y etiquetas de la música: el archivo se llama "Artista - Título" y esos mismos datos (y el álbum, si el sitio lo
 * da) quedan dentro. Si el título ya trae el artista ("Queen – Bohemian Rhapsody") se separa; si no, el artista es el
 * canal, sin sufijos como "- Topic" o "VEVO". Se quitan del título los adornos del tipo "(Official Video)".
 */
object MusicNaming {

    /** Guion normal, largo o raya (así separan los canales "Artista – Título"). Escritos como código, sin depender de la codificación. */
    private const val DASH = "[-\\u2013\\u2014]"

    /** "(Official Video)", "[Lyrics]", "(Audio)", "(4K Remaster)"... Se dejan "(feat. X)", "(Live)" o "(2022 Remaster)". */
    const val TITLE_NOISE =
        "(?i)\\s*[\\(\\[]\\s*(official|lyrics?|lyric video|audio|video|music video|hd|hq|\\d+k|visualizer|visualiser)[^\\)\\]]*[\\)\\]]"

    /** Lo que los canales de música añaden al nombre del artista. */
    const val ARTIST_NOISE = "(?i)(\\s*-\\s*topic|vevo|\\s+official)$"

    /**
     * Los argumentos de yt-dlp, en orden, que dejan listos `meta_artist` y `meta_title`. yt-dlp los usa para el nombre del
     * archivo y para las etiquetas (con --embed-metadata). Con un álbum conocido, `album` ya va a su etiqueta solo.
     */
    fun arguments(): List<String> = listOf(
        // Si el título es "Artista - Canción", se parte en dos (si no, no pasa nada).
        "--parse-metadata", "title:(?P<nameA>.+?) $DASH (?P<nameT>.+)",
        // Lo que dé el sitio (artista, canción) manda; si no, lo del título; si no, el canal y el título tal cual.
        "--parse-metadata", "%(artist,nameA,uploader)s:%(meta_artist)s",
        "--parse-metadata", "%(track,nameT,title)s:%(meta_title)s",
        "--replace-in-metadata", "meta_title", TITLE_NOISE, "",
        "--replace-in-metadata", "meta_artist", ARTIST_NOISE, "",
        "--replace-in-metadata", "meta_title", "\\s+$", "",
    )

    /** La plantilla del nombre del archivo. [part] es el tramo si se recortó (" (0m30s-0m40s)"). */
    fun fileTemplate(part: String = ""): String =
        "%(meta_artist).60B - %(meta_title).100B$part%(playlist_index& - {}|)s.%(ext)s"
}
