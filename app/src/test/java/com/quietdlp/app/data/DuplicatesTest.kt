package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicatesTest {

    @Test
    fun youtubeKeepsTheVideoId() {
        assertNotEquals(
            Duplicates.key("https://www.youtube.com/watch?v=AAA"),
            Duplicates.key("https://www.youtube.com/watch?v=BBB"),
        )
        assertEquals(
            Duplicates.key("https://www.youtube.com/watch?v=AAA"),
            Duplicates.key("https://youtube.com/watch?v=AAA&t=30&feature=share"),
        )
    }

    @Test
    fun shortLinkTrackingParamIsIgnored() {
        assertEquals(
            Duplicates.key("https://youtu.be/AAA"),
            Duplicates.key("https://youtu.be/AAA?si=xyz123"),
        )
    }

    @Test
    fun instagramTrackingAndFragmentAndSlashIgnored() {
        assertEquals(
            Duplicates.key("https://www.instagram.com/reel/XYZ/"),
            Duplicates.key("https://instagram.com/reel/XYZ/?igsh=abc&utm_source=ig#top"),
        )
    }

    @Test
    fun pathOnlySitesIgnoreAnyQuery() {
        assertEquals(
            Duplicates.key("https://www.instagram.com/reel/XYZ/"),
            Duplicates.key("https://www.instagram.com/reel/XYZ/?exln=whatever"),
        )
        assertEquals(
            Duplicates.key("https://vm.tiktok.com/AbC/"),
            Duplicates.key("https://vm.tiktok.com/AbC/?_r=1"),
        )
    }

    @Test
    fun differentPostsDiffer() {
        assertNotEquals(
            Duplicates.key("https://www.instagram.com/reel/XYZ/"),
            Duplicates.key("https://www.instagram.com/reel/OTHER/"),
        )
    }

    private fun done(url: String, kind: Kind, uris: List<String> = listOf("content://m/1")) = DownloadItem(
        id = url + kind, url = url, title = "t", quality = Quality(kind), state = State.DONE, savedUris = uris,
    )

    @Test
    fun findsFinishedDownloadOfSameKindWhoseFileExists() {
        val items = listOf(done("https://youtu.be/AAA", Kind.VIDEO))
        assertNotNull(Duplicates.find(items, "https://youtu.be/AAA?si=q", Kind.VIDEO) { true })
        // Otro tipo (audio de un vídeo ya bajado) no es duplicado.
        assertNull(Duplicates.find(items, "https://youtu.be/AAA", Kind.AUDIO) { true })
        // El archivo se borró de la galería: ya no es duplicado.
        assertNull(Duplicates.find(items, "https://youtu.be/AAA", Kind.VIDEO) { false })
    }

    @Test
    fun activeAndFailedItems() {
        val active = done("https://youtu.be/AAA", Kind.VIDEO, emptyList()).copy(state = State.DOWNLOADING)
        assertNotNull(Duplicates.find(listOf(active), "https://youtu.be/AAA", Kind.VIDEO) { false })
        val failed = done("https://youtu.be/AAA", Kind.VIDEO, emptyList()).copy(state = State.FAILED)
        assertNull(Duplicates.find(listOf(failed), "https://youtu.be/AAA", Kind.VIDEO) { true })
    }

    @Test
    fun multiFilePostNeedsAllFiles() {
        val items = listOf(done("https://x.com/a", Kind.PHOTO, listOf("content://m/1", "content://m/2")))
        assertNotNull(Duplicates.find(items, "https://x.com/a", Kind.PHOTO) { true })
        assertNull(Duplicates.find(items, "https://x.com/a", Kind.PHOTO) { it != "content://m/2" })
    }
}

class DuplicatesHashTest {
    @org.junit.Test
    fun hashHidesTheLinkAndMatchesEquivalentLinks() {
        val a = Duplicates.keyHash("https://claude.ai/artifact/ABC?mcp_token=secretvalue")
        org.junit.Assert.assertEquals(64, a.length)
        org.junit.Assert.assertFalse(a.contains("secret"))
        org.junit.Assert.assertEquals(Duplicates.keyHash("https://youtu.be/AAA"), Duplicates.keyHash("https://youtu.be/AAA?si=zz#x"))
        org.junit.Assert.assertNotEquals(Duplicates.keyHash("https://youtu.be/AAA"), Duplicates.keyHash("https://youtu.be/BBB"))
    }
}
