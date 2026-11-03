package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupTest {

    private val values = mapOf<String, Any?>(
        "mode" to "audio", "advanced" to true, "max_parallel" to 3, "sub_langs" to "en,es",
        "folder_uri" to "content://tree/primary%3AMovies", "last_update_ms" to 123L, "last_clip_key" to "abc",
        "unknown_setting" to "x",
    )

    @Test
    fun roundTripKeepsTheKnownSettings() {
        val back = SettingsBackup.decode(SettingsBackup.encode(values))!!
        assertEquals("audio", back["mode"])
        assertEquals(true, back["advanced"])
        assertEquals(3, back["max_parallel"])
        assertEquals("en,es", back["sub_langs"])
    }

    @Test
    fun thingsThatBelongToThisPhoneAreNotSaved() {
        val back = SettingsBackup.decode(SettingsBackup.encode(values))!!
        assertFalse("folder_uri" in back)
        assertFalse("last_update_ms" in back)
        assertFalse("last_clip_key" in back)
        assertFalse("unknown_setting" in back)
    }

    @Test
    fun theListNeverCarriesThePhoneSpecificKeys() {
        listOf("folder_uri", "last_app_check_ms", "dismissed_app_version", "update_notice_seen", "last_update_ms",
            "last_clip_key", "last_clip_ms").forEach { assertFalse(it, it in SettingsBackup.KEYS) }
    }

    @Test
    fun aValueWithTheWrongTypeIsIgnoredNotApplied() {
        val json = """{"app":"QuietDLP","format":1,"settings":{"advanced":"yes","max_parallel":"3","mode":"audio","wifi_only":true}}"""
        val back = SettingsBackup.decode(json)!!
        assertEquals(mapOf<String, Any>("mode" to "audio", "wifi_only" to true), back)
    }

    @Test
    fun aFileFromAnotherAppOrGarbageGivesNull() {
        assertNull(SettingsBackup.decode(""))
        assertNull(SettingsBackup.decode("not json"))
        assertNull(SettingsBackup.decode("""{"app":"Other","format":1,"settings":{}}"""))
        assertNull(SettingsBackup.decode("""{"app":"QuietDLP","format":99,"settings":{}}"""))
        assertNull(SettingsBackup.decode("""{"app":"QuietDLP","format":1}"""))
    }

    private fun row(id: String, state: State) = DownloadItem(
        id = id, url = "https://example.com/$id", title = "Title $id", thumbnail = null,
        quality = Quality(Kind.VIDEO), state = state, savedUris = if (state == State.DONE) listOf("content://media/1") else emptyList(),
    )

    @Test
    fun theHistoryTravelsWithTheBackupButNotWhatIsStillRunning() {
        val json = SettingsBackup.encode(
            emptyMap<String, Any?>(),
            listOf(row("a", State.DONE), row("b", State.DOWNLOADING), row("c", State.FAILED), row("d", State.QUEUED)),
        )
        val back = SettingsBackup.decodeHistory(json)
        assertEquals(listOf("a", "c"), back.map { it.id })
        assertEquals(listOf("content://media/1"), back.first().savedUris)
    }

    @Test
    fun aBackupWithoutHistoryOrFromAnotherAppGivesNoRows() {
        assertTrue(SettingsBackup.decodeHistory(SettingsBackup.encode(emptyMap<String, Any?>())).isEmpty())
        assertTrue(SettingsBackup.decodeHistory("""{"app":"Other","format":1,"history":[{"id":"x"}]}""").isEmpty())
        assertTrue(SettingsBackup.decodeHistory("garbage").isEmpty())
    }

    @Test
    fun aLongHistoryFitsInTheSizeLimit() {
        val rows = (1..HistoryJson.MAX_ITEMS).map { row("i$it", State.DONE) }
        val json = SettingsBackup.encode(emptyMap<String, Any?>(), rows)
        assertTrue("backup is ${json.length} bytes", json.length < SettingsBackup.MAX_BYTES)
        assertEquals(HistoryJson.MAX_ITEMS, SettingsBackup.decodeHistory(json).size)
    }

    @Test
    fun anEmptyBackupIsValidAndChangesNothing() {
        assertTrue(SettingsBackup.decode("""{"app":"QuietDLP","format":1,"settings":{}}""")!!.isEmpty())
    }

    @Test
    fun aHugeStringIsNotAccepted() {
        val big = "x".repeat(250_000)
        val back = SettingsBackup.decode(SettingsBackup.encode(mapOf("site_rules" to big)))!!
        assertFalse("site_rules" in back)
    }
}
