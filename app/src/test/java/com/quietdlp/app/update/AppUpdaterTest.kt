package com.quietdlp.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {

    // Respuesta REAL de la API de GitHub (releases/latest) de un proyecto público, recortada.
    private val sample: String by lazy {
        javaClass.classLoader!!.getResource("release_sample.json")!!.readText()
    }

    @Test
    fun newerComparesNumberByNumber() {
        assertTrue(AppUpdater.isNewer("0.10.0", "0.9.2"))
        assertTrue(AppUpdater.isNewer("v1.0.1", "1.0.0"))
        assertTrue(AppUpdater.isNewer("1.2", "1.1.9"))
        assertTrue(AppUpdater.isNewer("0.2.1-beta", "0.2.0"))
        assertFalse(AppUpdater.isNewer("1.0.0", "1.0.0"))
        assertFalse(AppUpdater.isNewer("1.2.0", "1.2"))
        assertFalse(AppUpdater.isNewer("0.2.0", "0.10.0"))
    }

    @Test
    fun parsesARealGithubRelease() {
        val r = AppUpdater.parseRelease(sample, "deniscerri/ytdlnis")
        assertNotNull(r)
        assertEquals("1.9.1.1", r!!.version)
        assertTrue(r.apkUrl.startsWith("https://github.com/deniscerri/ytdlnis/releases/download/"))
        assertTrue(r.apkUrl.endsWith(".apk"))
        assertNotNull(r.sha256)
        assertEquals(64, r.sha256!!.length)
        assertTrue(r.sizeBytes > 0)
    }

    @Test
    fun rejectsDownloadsThatDoNotBelongToTheRepo() {
        // Si el JSON apunta a otro sitio, no se acepta (evita que una respuesta manipulada mande a otro servidor).
        assertNull(AppUpdater.parseRelease(sample, "otro/repositorio"))
    }

    @Test
    fun ignoresDraftsAndPrereleases() {
        assertNull(AppUpdater.parseRelease(sample.replace("\"draft\": false", "\"draft\": true"), "deniscerri/ytdlnis"))
        assertNull(AppUpdater.parseRelease(sample.replace("\"prerelease\": false", "\"prerelease\": true"), "deniscerri/ytdlnis"))
    }

    @Test
    fun acceptsTheLocalDevSourceOnlyWhenAskedTo() {
        val local = """{"tag_name":"v0.2.1","draft":false,"prerelease":false,"body":"x","assets":[
            {"name":"QuietDLP.apk","browser_download_url":"http://127.0.0.1:8765/QuietDLP.apk","size":10,"digest":"sha256:${"a".repeat(64)}"}]}"""
        assertNull(AppUpdater.parseRelease(local, "usuario/QuietDLP"))
        val r = AppUpdater.parseRelease(local, "usuario/QuietDLP", listOf("http://127.0.0.1:8765/"))
        assertNotNull(r)
        assertEquals("0.2.1", r!!.version)
    }

    @Test
    fun prefersTheReleaseApkOverADebugOne() {
        val repo = "usuario/QuietDLP"
        val base = "https://github.com/$repo/releases/download/v1.0.0"
        val json = """{"tag_name":"v1.0.0","draft":false,"prerelease":false,"body":"","assets":[
            {"name":"QuietDLP-1.0.0-debug.apk","browser_download_url":"$base/QuietDLP-1.0.0-debug.apk","size":1},
            {"name":"QuietDLP-1.0.0.apk","browser_download_url":"$base/QuietDLP-1.0.0.apk","size":1}]}"""
        assertEquals("$base/QuietDLP-1.0.0.apk", AppUpdater.parseRelease(json, repo)!!.apkUrl)
    }
}
