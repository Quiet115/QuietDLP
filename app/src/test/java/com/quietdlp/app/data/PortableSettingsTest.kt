package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableSettingsTest {
    private fun snapshot() = PortableSettings(
        quickDirect = false,
        quickKind = "AUDIO",
        quickHeight = 1080,
        audioFormat = "mp3",
        audioBitrate = 192,
        autoPhotoVideo = true,
        rememberSong = true,
        songSeconds = 30,
        appUpdateCheck = false,
        photosEnabled = true,
        autoUpdate = false,
        mode = "ask",
        rememberLast = true,
        lastKind = "VIDEO",
        lastHeight = 720,
        lastAudioFormat = "m4a",
        lastAudioBitrate = 128,
        clipboardOffer = false,
        palette = "paper",
        customPalettes = "[]",
        advanced = true,
        folderName = "Test",
        orientation = "portrait",
        supportCard = false,
        maxParallel = 3,
        wifiOnly = true,
        askMulti = false,
        batteryHintOff = true,
        subsOn = true,
        subLangs = "en,es",
        subMode = "file",
        coverAudio = false,
        coverVideo = true,
        musicNames = false,
        nameTemplate = "{title}",
        sponsorMode = "off",
        sponsorCats = "sponsor",
        chapterMode = "embed",
        followSystemMode = true,
        paletteDark = "obsidian",
        paletteLight = "paper",
        language = "es",
        siteRules = "[]",
        followWifi = false,
        shareOptions = false,
    ).values()

    @Test
    fun everyExportedKeyHasTheDeclaredTypeAndEveryDeclaredKeyIsExported() {
        val values = snapshot()
        assertEquals(SettingsBackup.KEYS.keys, values.keys)
        SettingsBackup.KEYS.forEach { (key, type) ->
            val value = values.getValue(key)
            assertTrue("Wrong portable type for $key", when (type) {
                'b' -> value is Boolean
                'i' -> value is Int
                's' -> value is String
                else -> false
            })
        }
    }

    @Test
    fun aCompleteTypedSnapshotSurvivesExportAndImportWithoutLosingAnySetting() {
        val values = snapshot()
        assertEquals(values, SettingsBackup.decode(SettingsBackup.encode(values)))
    }
}

