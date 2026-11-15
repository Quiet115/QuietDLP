package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

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
        assertTrue(back.first().savedUris.isEmpty())
    }

    @Test
    fun importedHistoryCannotSupplyFileActionsOrDuplicateRows() {
        val original = row("a", State.DONE).copy(savedUris = listOf("file:///synthetic/private-marker", "content://media/1"))
        val json = SettingsBackup.encode(emptyMap<String, Any?>(), listOf(original, original))
        val back = SettingsBackup.decodeHistory(json)
        assertEquals(1, back.size)
        assertTrue(back.single().savedUris.isEmpty())
        assertFalse(json.contains("private-marker"))
        val oldProfile = JSONObject(json)
        oldProfile.getJSONArray("history").getJSONObject(0)
            .put("savedUris", JSONArray(listOf("file:///synthetic/private-marker")))
        assertTrue(SettingsBackup.decodeHistory(oldProfile.toString()).single().savedUris.isEmpty())
    }

    @Test
    fun conversionSourcesAndLocalThumbnailsDoNotTravelBetweenPhones() {
        val local = row("conversion", State.DONE).copy(url = CONVERT_URL_PREFIX + "file:///synthetic/source",
            thumbnail = "file:///synthetic/thumbnail")
        val json = SettingsBackup.encode(emptyMap<String, Any?>(), listOf(local))
        assertFalse(json.contains("/synthetic/"))
        val oldProfile = JSONObject(json)
        oldProfile.getJSONArray("history").getJSONObject(0)
            .put("url", CONVERT_URL_PREFIX + "file:///synthetic/source")
            .put("thumb", "file:///synthetic/thumbnail")
        val restored = SettingsBackup.decodeHistory(oldProfile.toString()).single()
        assertEquals(CONVERT_URL_PREFIX, restored.url)
        assertNull(restored.thumbnail)
    }

    @Test
    fun importedDownloadAndFollowLinksMustBeWebLinks() {
        val root = JSONObject(SettingsBackup.encode(emptyMap<String, Any?>(), listOf(row("a", State.DONE))))
        root.getJSONArray("history").getJSONObject(0).put("url", "file:///synthetic/source")
        assertEquals(SettingsBackup.Check.NotAProfile, SettingsBackup.check(root.toString()))
        val followed = JSONObject(SettingsBackup.encode(emptyMap<String, Any?>(), follows = listOf(FollowedPage("p", "https://example.com/page", "Page"))))
        followed.getJSONArray("follows").getJSONObject(0).put("url", "content://synthetic/source")
        assertEquals(SettingsBackup.Check.NotAProfile, SettingsBackup.check(followed.toString()))
    }

    @Test
    fun exportedFollowedPagesRoundTripWithoutPendingItemsOrErrors() {
        val page = FollowedPage("page", "https://example.com/page", "Page", thumbnail = "file:///synthetic/page-thumb", lastError = "synthetic error",
            counter = 10, pending = listOf(PlaylistEntry("https://example.com/item", "Item", null)))
        val json = SettingsBackup.encode(mapOf("language" to "es"), follows = listOf(page))
        assertFalse(json.contains("/synthetic/"))
        val ready = SettingsBackup.check(json) as SettingsBackup.Check.Ready
        assertEquals(1, ready.follows)
        val restored = SettingsBackup.decodeFollows(json).single()
        assertEquals(page.url, restored.url)
        assertTrue(restored.pending.isEmpty())
        assertNull(restored.lastError)
        assertEquals(0, restored.counter)
    }

    @Test
    fun aProfileOverTheByteLimitIsRejectedBeforeItCanBeImported() {
        val json = SettingsBackup.encode(emptyMap<String, Any?>()).dropLast(1) +
            ",\"padding\":\"" + "é".repeat(SettingsBackup.MAX_BYTES / 2) + "\"}"
        assertEquals(SettingsBackup.Check.NotAProfile, SettingsBackup.check(json))
    }

    @Test
    fun historyBeyondTheSupportedLimitIsNotSilentlyExported() {
        val rows = (1..HistoryJson.MAX_ITEMS + 1).map { row("i$it", State.DONE) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup.encode(emptyMap<String, Any?>(), rows)
        }
    }

    @Test
    fun anExportOverTheImportLimitFailsInsteadOfCreatingAnUnusableCopy() {
        val rows = (1..50).map { row("i$it", State.DONE).copy(title = "é".repeat(100_000)) }
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup.encode(emptyMap<String, Any?>(), rows)
        }
    }

    @Test
    fun damagedSectionsAndNonIntegerFormatAreRejected() {
        listOf("\"history\":{}", "\"follows\":true", "\"history\":[{}]").forEach { section ->
            assertEquals(SettingsBackup.Check.NotAProfile,
                SettingsBackup.check("""{"app":"QuietDLP","format":1,"settings":{},$section}"""))
        }
        listOf("1.5", "\"1\"", "true").forEach { version ->
            assertEquals(SettingsBackup.Check.NotAProfile,
                SettingsBackup.check("""{"app":"QuietDLP","format":$version,"settings":{}}"""))
        }
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
    fun anEmptySettingsObjectIsAValidProfile() {
        assertTrue(SettingsBackup.decode("""{"app":"QuietDLP","format":1,"settings":{}}""")!!.isEmpty())
    }

    @Test
    fun aHugeStringIsNotAccepted() {
        val big = "x".repeat(250_000)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            SettingsBackup.encode(mapOf("site_rules" to big))
        }
    }

    @Test
    fun everySavedSettingBelongsToATab() {
        SettingsBackup.KEYS.keys.forEach { assertTrue(it, SettingsBackup.groupOf(it) in SettingsBackup.Group.entries) }
        assertEquals(SettingsBackup.Group.LANGUAGE, SettingsBackup.groupOf("language"))
        assertEquals(SettingsBackup.Group.APPEARANCE, SettingsBackup.groupOf("palette_dark"))
        assertTrue("language" in SettingsBackup.KEYS)
        assertTrue("follow_system_mode" in SettingsBackup.KEYS)
    }

    @Test
    fun secretsAndPhoneThingsNeverEnterAProfile() {
        val all = mapOf<String, Any?>("site_key" to "secret", "site_user" to "u", "site_address" to "a", "folder_uri" to "content://x", "perm_welcome_seen" to true, "mode" to "audio")
        val json = SettingsBackup.encode(all)
        listOf("site_key", "site_user", "site_address", "folder_uri", "perm_welcome_seen").forEach { assertFalse(it, json.contains(it)) }
    }

    @Test
    fun aValidProfileIsReadyAndCountsWhatChanges() {
        val json = SettingsBackup.encode(mapOf("mode" to "audio", "language" to "es", "max_parallel" to 3))
        val ready = SettingsBackup.check(json) as SettingsBackup.Check.Ready
        assertEquals("es", ready.values["language"])
        val changes = SettingsBackup.changes(ready.values, mapOf("mode" to "audio", "language" to "en", "max_parallel" to 2))
        assertEquals(mapOf(SettingsBackup.Group.LANGUAGE to 1, SettingsBackup.Group.QUEUE to 1), changes)
    }

    @Test
    fun damagedAndForeignFilesAreRejected() {
        assertEquals(SettingsBackup.Check.NotAProfile, SettingsBackup.check("not json {"))
        assertEquals(SettingsBackup.Check.NotAProfile, SettingsBackup.check("""{"app":"Other","format":1,"settings":{}}"""))
        assertEquals(SettingsBackup.Check.NotAProfile, SettingsBackup.check("""{"app":"QuietDLP","format":0,"settings":{}}"""))
    }

    @Test
    fun aProfileFromAFutureVersionIsRejectedWithItsOwnMessage() {
        assertEquals(SettingsBackup.Check.FromNewerVersion, SettingsBackup.check("""{"app":"QuietDLP","format":99,"settings":{}}"""))
    }

    @Test
    fun unknownAndWronglyTypedSettingsAreIgnored() {
        val json = """{"app":"QuietDLP","format":1,"settings":{"mode":"audio","brand_new":"x","max_parallel":"wrong type"}}"""
        val ready = SettingsBackup.check(json) as SettingsBackup.Check.Ready
        assertEquals(mapOf<String, Any>("mode" to "audio"), ready.values)
    }

    @Test
    fun restoringResetsOmittedSettingsButPreservesSecretsAndDeviceState() {
        val current = mutableMapOf<String, Any>("mode" to "video", "wifi_only" to true,
            "language" to "es", "site_key" to "synthetic-secret", "folder_uri" to "content://synthetic/folder")
        SettingsBackup.restore(SettingsBackup.encode(mapOf("mode" to "audio"))) { key, value ->
            if (value == null) current.remove(key) else current[key] = value
        }
        assertEquals(mapOf("mode" to "audio", "language" to "en", "site_key" to "synthetic-secret",
            "folder_uri" to "content://synthetic/folder"), current)
    }

    @Test
    fun invalidProfilesDoNotStartChangingSettings() {
        var writes = 0
        assertEquals(-1, SettingsBackup.restore("not json") { _, _ -> writes++ })
        assertEquals(0, writes)
    }

    @Test
    fun previewIncludesSettingsThatWillReturnToDefaults() {
        val changes = SettingsBackup.changes(mapOf("mode" to "audio"), mapOf("mode" to "audio", "wifi_only" to true, "site_key" to "synthetic"))
        assertEquals(mapOf(SettingsBackup.Group.QUEUE to 1), changes)
    }
}
