package com.quietdlp.app.download

import android.content.Context
import com.quietdlp.app.data.SongClip
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

    private fun cookiesArgs(ctx: Context): List<String> =
        CookieStore.mergedPath(ctx)?.let { listOf("--cookies", it) }.orEmpty()

    /**
     * Arranque de gallery-dl. Hace lo mismo que `python -m gallery_dl`, pero antes añade a los metadatos de las
     * publicaciones de Instagram la parte de la canción que eligió el autor (dónde empieza y cuánto dura), que
     * gallery-dl no publica por sí solo. Si algo no encaja (otra versión de gallery-dl), no hace nada y todo sigue igual.
     */
    private const val WRAPPER = """import sys
try:
    from gallery_dl.extractor import instagram as _ig
    _orig = _ig.InstagramExtractor._extract_audio

    def _extract_audio(self, src, dest, info):
        try:
            c = (info or {}).get("music_consumption_info") or {}
            dest["audio_clip_start_ms"] = c.get("audio_asset_start_time_in_ms")
            dest["audio_clip_length_ms"] = c.get("overlap_duration_in_ms")
            dest["audio_clip_raw"] = c
        except Exception:
            pass
        return _orig(self, src, dest, info)

    _ig.InstagramExtractor._extract_audio = _extract_audio
except Exception:
    pass
import gallery_dl
sys.exit(gallery_dl.main())
"""

    private fun wrapper(ctx: Context): File {
        val f = File(ctx.filesDir, "gdl_run.py")
        if (!f.exists() || f.readText() != WRAPPER) f.writeText(WRAPPER)
        return f
    }

    /** Lo que se leyó del trozo de canción de cada descarga de Instagram: el trozo (si se pudo) y los datos crudos. */
    private val clips = ConcurrentHashMap<String, Pair<SongClip?, String?>>()

    fun takeClip(id: String): Pair<SongClip?, String?>? = clips.remove(id)

    private fun readClip(id: String, dir: File) {
        var clip: SongClip? = null
        var raw: String? = null
        dir.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { f ->
            val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
            if (clip == null) clip = SongClip.parse(text)
            if (raw == null) raw = SongClip.rawOf(text)
        }
        clips[id] = clip to raw
    }

    private fun builder(ctx: Context, args: List<String>): ProcessBuilder {
        ensureInstalled(ctx)
        val py = File(packages(ctx), "python/usr")
        val ff = File(packages(ctx), "ffmpeg/usr")
        val libpython = File(ctx.applicationInfo.nativeLibraryDir, "libpython.so")
        val pb = ProcessBuilder(listOf(libpython.absolutePath, wrapper(ctx).absolutePath) + args)
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

    /** Descarga las fotos del enlace en [dir] y devuelve los archivos. Lanza una excepción si no se obtuvo ninguno. */
    fun download(ctx: Context, id: String, url: String, dir: File): List<File> {
        dir.mkdirs()
        // En Instagram, una publicación de fotos puede traer una canción: "merged" la baja directamente como un
        // archivo más (el otro modo usaría yt-dlp, que gallery-dl no tiene aquí).
        val instagram = url.contains("instagram.com", ignoreCase = true)
        // En Instagram se piden también los metadatos (un .json por archivo) para leer el trozo de canción elegido.
        val args = listOf("-D", dir.absolutePath, "--no-mtime", "--range", MAX_ITEMS, "-o", "extractor.instagram.audio=merged") +
            (if (instagram) listOf("--write-metadata") else emptyList()) + cookiesArgs(ctx) + url
        val (code, out) = exec(ctx, args, id)
        if (instagram) {
            readClip(id, dir)
            dir.listFiles()?.filter { it.name.endsWith(".json") }?.forEach { it.delete() }
        }
        val files = dir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") }
            ?.sortedBy { it.name }
            .orEmpty()
        if (files.isEmpty()) throw Exception(describeFailure(out, code))
        return files
    }

    fun cancel(id: String) {
        running[id]?.destroy()
    }

    /** Convierte la salida de gallery-dl en un mensaje que [Errors] sepa clasificar (p. ej. "falta iniciar sesión"). */
    private fun describeFailure(out: String, code: Int): String {
        val lines = out.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val l = out.lowercase()
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
