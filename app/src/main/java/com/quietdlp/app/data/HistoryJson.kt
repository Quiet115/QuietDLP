package com.quietdlp.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Convierte la lista de descargas a JSON y viceversa (funciones puras, para poder probarlas). */
object HistoryJson {
    // Una lista de reproducción larga son muchas filas: el tope tiene que dejar sitio a varias.
    const val MAX_ITEMS = 3000

    fun encode(items: List<DownloadItem>): String {
        val arr = JSONArray()
        items.take(MAX_ITEMS).forEach { i ->
            arr.put(
                JSONObject()
                    .put("id", i.id)
                    .put("url", i.url)
                    .put("title", i.title)
                    .put("thumb", i.thumbnail)
                    .put("kind", i.quality.kind.name)
                    .put("height", i.quality.maxHeight)
                    .put("audioFmt", i.quality.audioFormat)
                    .put("audioBr", i.quality.audioBitrate)
                    .put("state", i.state.name)
                    .put("message", i.message)
                    .put("savedUris", JSONArray(i.savedUris))
                    .put("mime", i.mime)
                    .put("loginHost", i.loginHost)
                    .put("photoVideo", i.photoVideo)
                    .put("groupId", i.groupId)
                    .put("groupIndex", i.groupIndex)
                    .put("groupTitle", i.groupTitle)
                    .put("wifiOnly", i.wifiOnly)
                    .put("subs", i.subs?.encode())
                    .put("trim", i.trim?.encode())
                    .put("cover", i.cover)
                    .put("sponsor", i.sponsor?.encode())
                    .put("chapters", i.chapters.id)
                    .put("followId", i.followId)
                    .put("fresh", i.fresh)
                    .put("photoLimit", i.photoLimit),
            )
        }
        return arr.toString()
    }

    /** Lo que estaba en curso cuando se cerró la app queda como fallido ("Interrupted"). */
    fun decode(json: String): List<DownloadItem> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { n ->
            runCatching {
                val o = arr.getJSONObject(n)
                val state = State.valueOf(o.getString("state"))
                val interrupted = state != State.DONE && state != State.FAILED && state != State.CANCELED
                DownloadItem(
                    id = o.getString("id"),
                    url = o.getString("url"),
                    title = o.getString("title"),
                    thumbnail = o.optStringOrNull("thumb"),
                    quality = Quality(
                        kind = Kind.valueOf(o.getString("kind")),
                        maxHeight = o.optInt("height", 0),
                        audioFormat = o.optString("audioFmt", "m4a"),
                        audioBitrate = o.optInt("audioBr", 0),
                    ),
                    state = if (interrupted) State.FAILED else state,
                    progress = if (state == State.DONE) 100f else 0f,
                    message = if (interrupted) "Interrupted" else o.optStringOrNull("message"),
                    savedUris = savedUrisOf(o),
                    mime = o.optStringOrNull("mime"),
                    loginHost = o.optStringOrNull("loginHost"),
                    photoVideo = o.optBoolean("photoVideo", false),
                    groupId = o.optStringOrNull("groupId"),
                    groupIndex = o.optInt("groupIndex", 0),
                    groupTitle = o.optStringOrNull("groupTitle"),
                    wifiOnly = o.optBoolean("wifiOnly", false),
                    subs = SubtitleChoice.decode(o.optStringOrNull("subs")),
                    trim = TrimRange.decode(o.optStringOrNull("trim")),
                    cover = o.optBoolean("cover", false),
                    sponsor = SponsorChoice.decode(o.optStringOrNull("sponsor")),
                    chapters = ChapterMode.fromId(o.optStringOrNull("chapters")),
                    followId = o.optStringOrNull("followId"),
                    fresh = o.optBoolean("fresh", false),
                    photoLimit = o.optInt("photoLimit", DEFAULT_PHOTO_LIMIT),
                )
            }.getOrNull()
        }
    }

    /** Las versiones antiguas guardaban un solo "savedUri". */
    private fun savedUrisOf(o: JSONObject): List<String> {
        val arr = o.optJSONArray("savedUris")
        if (arr != null) return (0 until arr.length()).map { arr.getString(it) }
        return listOfNotNull(o.optStringOrNull("savedUri"))
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key) || !has(key)) null else getString(key)
}
