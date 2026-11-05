package com.quietdlp.app.download

import android.content.Context
import com.quietdlp.app.data.PlaylistEntry
import com.quietdlp.app.data.Prefs
import com.quietdlp.app.data.SiteKeys
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipInputStream

/**
 * Descarga de fotos y galerías con gallery-dl (https://github.com/mikf/gallery-dl, GPL-2.0).
 *
 * yt-dlp solo descarga vídeo y audio; cuando un enlace no tiene vídeo (publicaciones de fotos, galerías),
 * gallery-dl hace ese trabajo. Se ejecuta como un PROGRAMA INDEPENDIENTE (un proceso aparte con el Python
 * que ya trae la app), no se enlaza ni se mezcla su código con el de QuietDLP.
 *
 * El paquete inicial (gallery-dl + sus dependencias, todo Python puro) viaja en assets/gdl_pack.zip y se
 * extrae a la carpeta privada de la app. Después se actualiza solo desde PyPI, verificando el SHA-256.
 *
 * Todas las funciones son bloqueantes: llamarlas desde un hilo de fondo, con el motor ya iniciado
 * ([Engine.ensureReady]).
 */
object GalleryDl {
    /** Cambia cuando cambia el contenido de assets/gdl_pack.zip, para volver a extraerlo. */
    private const val BUNDLE_VERSION = "gdl-1.32.18-pack1"

    private val running = ConcurrentHashMap<String, Process>()
    private val IMAGE_URL = Regex("""^https?://\S+\.(jpe?g|png|webp|gif|avif|heic)(\?\S*)?$""", RegexOption.IGNORE_CASE)

    // Máximo de elementos por enlace: un perfil entero podría tener miles.
    private const val MAX_ITEMS = "1-100"

    private fun packages(ctx: Context) = File(ctx.noBackupFilesDir, "youtubedl-android/packages")
    private fun siteDir(ctx: Context) = File(ctx.filesDir, "gdl_site")

    @Synchronized
    fun ensureInstalled(ctx: Context) {
        val site = siteDir(ctx)
        val marker = File(site, ".bundle")
        if (marker.exists() && marker.readText() == BUNDLE_VERSION) return
        site.deleteRecursively()
        site.mkdirs()
        ctx.assets.open("gdl_pack.zip").use { unzip(it, site, prefix = null) }
        marker.writeText(BUNDLE_VERSION)
    }

    /** Extrae un zip (opcionalmente solo las entradas que empiezan por [prefix]) protegiéndose de rutas maliciosas. */
    private fun unzip(input: InputStream, dest: File, prefix: String?) {
        val root = dest.canonicalFile
        ZipInputStream(input).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory && (prefix == null || e.name.startsWith(prefix))) {
                    val out = File(root, e.name).canonicalFile
                    require(out.path.startsWith(root.path + File.separator)) { "Invalid path in zip" }
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                e = zis.nextEntry
            }
        }
    }

    private fun cookiesArgs(ctx: Context): List<String> {
        val prefs = Prefs(ctx)
        return CookieStore.mergedPath(ctx)?.let { listOf("--cookies", it) }.orEmpty() +
            SiteKeys.galleryArgs(prefs.siteAddress, prefs.siteUser, prefs.siteKey)
    }

    private fun builder(ctx: Context, args: List<String>): ProcessBuilder {
        ensureInstalled(ctx)
        val py = File(packages(ctx), "python/usr")
        val ff = File(packages(ctx), "ffmpeg/usr")
        val libpython = File(ctx.applicationInfo.nativeLibraryDir, "libpython.so")
        val pb = ProcessBuilder(listOf(libpython.absolutePath, "-m", "gallery_dl") + args)
            .redirectErrorStream(true)
            .directory(ctx.filesDir)
        pb.environment().apply {
            put("LD_LIBRARY_PATH", "${py.path}/lib:${ff.path}/lib")
            put("PYTHONHOME", py.path)
            put("HOME", py.path)
            put("SSL_CERT_FILE", File(py, "etc/tls/cert.pem").path)
            put("TMPDIR", ctx.cacheDir.path)
            put("PYTHONPATH", siteDir(ctx).path)
            put("PYTHONDONTWRITEBYTECODE", "1")
            put("PYTHONIOENCODING", "utf-8")
        }
        return pb
    }

    private fun exec(ctx: Context, args: List<String>, id: String? = null): Pair<Int, String> {
        val p = builder(ctx, args).start()
        if (id != null) running[id] = p
        try {
            val out = p.inputStream.bufferedReader().readText()
            return p.waitFor() to out
        } finally {
            if (id != null) running.remove(id)
        }
    }

    /** Cuántas imágenes tiene el enlace (sin descargarlas). 0 si no hay o gallery-dl no lo soporta. */
    fun countImages(ctx: Context, url: String): Int {
        val (_, out) = exec(ctx, listOf("-g", "--range", MAX_ITEMS) + cookiesArgs(ctx) + url)
        return out.lines().count { IMAGE_URL.matches(it.trim()) }
    }

    /** Los archivos que lista el enlace (los más nuevos primero), sin descargarlos. Lanza una excepción si no hay ninguno y falló. */
    fun listFiles(ctx: Context, url: String, max: Int): List<PlaylistEntry> {
        val (code, out) = exec(ctx, listOf("-g", "--range", "1-$max") + cookiesArgs(ctx) + url)
        val urls = out.lines().map { it.trim() }.filter { it.startsWith("http") && ' ' !in it }.distinct()
        if (urls.isEmpty() && code != 0) throw Exception(describeFailure(out, code))
        return urls.map { u ->
            PlaylistEntry(u, u.substringBefore('?').substringAfterLast('/').ifBlank { u }, u.takeIf { IMAGE_URL.matches(it) })
        }
    }

    /** Descarga las fotos del enlace en [dir] y devuelve los archivos. Lanza una excepción si no se obtuvo ninguno. */
    fun download(ctx: Context, id: String, url: String, dir: File, limit: Int = 100): List<File> {
        dir.mkdirs()
        // En Instagram, una publicación de fotos puede traer una canción: "merged" la baja directamente como un
        // archivo más (el otro modo usaría yt-dlp, que gallery-dl no tiene aquí).
        // limit 0 = todas las fotos del enlace.
        val range = if (limit > 0) listOf("--range", "1-$limit") else emptyList()
        val args = listOf("-D", dir.absolutePath, "--no-mtime") + range + listOf("-o", "extractor.instagram.audio=merged") +
            cookiesArgs(ctx) + url
        val (code, out) = exec(ctx, args, id)
        val files = dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) throw Exception(describeFailure(out, code))
        return files
    }

    fun cancel(id: String) {
        running[id]?.destroy()
        direct[id]?.set(true)
    }

    private val direct = ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicBoolean>()
    private const val DIRECT_MAX_BYTES = 500L * 1024 * 1024

    private val EXT_BY_TYPE = mapOf(
        "image/jpeg" to "jpg", "image/png" to "png", "image/gif" to "gif", "image/webp" to "webp", "image/avif" to "avif",
        "video/mp4" to "mp4", "video/webm" to "webm", "video/quicktime" to "mov",
    )
    private val KNOWN_EXT = setOf("jpg", "jpeg", "png", "gif", "webp", "avif", "heic", "mp4", "webm", "mov", "mkv", "m4v")

    /**
     * Baja un archivo por su enlace directo. Las galerías que se siguen dan el enlace de cada archivo y algunas webs lo dan
     * sin extensión, que gallery-dl no acepta: aquí se baja tal cual (con la web de origen como referencia, como hace un
     * navegador) y la extensión sale del tipo que declara el servidor.
     */
    fun fetchDirect(id: String, url: String, dir: File): File {
        require(url.startsWith("https://")) { "ERROR: only https links can be downloaded" }
        dir.mkdirs()
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        direct[id] = cancelled
        try {
            var current = url
            repeat(5) {
                val c = URL(current).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = false
                c.connectTimeout = 15_000
                c.readTimeout = 30_000
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) QuietDLP")
                c.setRequestProperty("Referer", URL(current).let { "${it.protocol}://${it.host}/" })
                try {
                    val code = c.responseCode
                    if (code in 300..399) {
                        current = URL(URL(current), c.getHeaderField("Location") ?: error("ERROR: bad redirect")).toString()
                        require(current.startsWith("https://")) { "ERROR: redirect to a non-https link" }
                        return@repeat
                    }
                    require(code == 200) { "ERROR: couldn't download the file (HTTP $code)" }
                    val type = c.contentType?.substringBefore(';')?.trim()?.lowercase()
                    val raw = current.substringBefore('?').substringAfterLast('/').ifBlank { "file" }
                    val fromUrl = raw.substringAfterLast('.', "").lowercase().takeIf { it in KNOWN_EXT }
                    val ext = fromUrl ?: EXT_BY_TYPE[type] ?: error("ERROR: not a photo or video file")
                    val base = (if (fromUrl != null) raw.substringBeforeLast('.') else raw).replace(Regex("""[^\w.\- ]"""), "_").take(80).ifBlank { "file" }
                    val out = File(dir, "$base.$ext")
                    var total = 0L
                    c.inputStream.use { input ->
                        out.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                if (cancelled.get()) error("ERROR: canceled")
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                                require(total <= DIRECT_MAX_BYTES) { "ERROR: file too big" }
                                o.write(buf, 0, n)
                            }
                        }
                    }
                    require(total > 0) { "ERROR: empty file" }
                    return out
                } finally {
                    c.disconnect()
                }
            }
            error("ERROR: too many redirects")
        } finally {
            direct.remove(id)
        }
    }

    /** Convierte la salida de gallery-dl en un mensaje que [Errors] sepa clasificar (p. ej. "falta iniciar sesión"). */
    private fun describeFailure(out: String, code: Int): String {
        val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val l = out.lowercase()
        if ("api-key" in l || "api_key" in l) return "ERROR: this page needs your key. Add it in Settings, Site keys."
        val needsLogin = "authrequired" in l || "login" in l || "sessionid" in l || "401" in l ||
            "cookies" in l || "private" in l || "nsfw" in l && "log in" in l
        val last = lines.lastOrNull { "error" in it.lowercase() || "warning" in it.lowercase() } ?: lines.lastOrNull().orEmpty()
        val cleaned = last.replace(Regex("""^\[[^\]]+]\[[^\]]+]\s*"""), "").take(200)
        return if (needsLogin) "ERROR: login required (gallery-dl, code $code): $cleaned"
        else "ERROR: couldn't download the photos: $cleaned"
    }

    // ───────────────────────── actualización ─────────────────────────

    fun version(ctx: Context): String? = runCatching {
        ensureInstalled(ctx)
        Regex("""__version__\s*=\s*"([^"]+)"""")
            .find(File(siteDir(ctx), "gallery_dl/version.py").readText())?.groupValues?.get(1)
    }.getOrNull()

    private fun httpGet(url: String, maxBytes: Int): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "QuietDLP")
        try {
            require(c.responseCode == 200) { "HTTP ${c.responseCode}" }
            val buf = c.inputStream.use { it.readNBytesCompat(maxBytes + 1) }
            require(buf.size <= maxBytes) { "Response too large" }
            return buf
        } finally {
            c.disconnect()
        }
    }

    private fun InputStream.readNBytesCompat(limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < limit) {
            val n = read(buf, 0, minOf(buf.size, limit - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Busca la última versión en PyPI y, si es más nueva, descarga el paquete, verifica su SHA-256
     * y reemplaza solo el código de gallery-dl. Devuelve un texto para mostrar al usuario.
     */
    @Synchronized
    fun update(ctx: Context): String {
        ensureInstalled(ctx)
        val current = version(ctx)
        val meta = JSONObject(String(httpGet("https://pypi.org/pypi/gallery-dl/json", 2_000_000)))
        val latest = meta.getJSONObject("info").getString("version")
        if (latest == current) return "gallery-dl is up to date ($latest)"

        val files = meta.getJSONArray("urls")
        var wheelUrl: String? = null
        var sha256: String? = null
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            if (f.getString("packagetype") == "bdist_wheel" && f.getString("filename").endsWith("none-any.whl")) {
                wheelUrl = f.getString("url")
                sha256 = f.getJSONObject("digests").getString("sha256")
                break
            }
        }
        require(wheelUrl != null && sha256 != null) { "PyPI has no compatible package" }
        require(wheelUrl.startsWith("https://files.pythonhosted.org/")) { "Unexpected package origin" }

        val bytes = httpGet(wheelUrl, 8_000_000)
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        require(actual.equals(sha256, ignoreCase = true)) { "Package SHA-256 check failed" }

        val staging = File(ctx.cacheDir, "gdl_new").apply { deleteRecursively(); mkdirs() }
        try {
            unzip(bytes.inputStream(), staging, prefix = "gallery_dl/")
            val newCode = File(staging, "gallery_dl")
            require(File(newCode, "version.py").exists()) { "Incomplete package" }
            val target = File(siteDir(ctx), "gallery_dl")
            target.deleteRecursively()
            require(newCode.renameTo(target) || newCode.copyRecursively(target, overwrite = true)) { "Couldn't install" }
        } finally {
            staging.deleteRecursively()
        }
        return "gallery-dl updated to $latest"
    }
}
