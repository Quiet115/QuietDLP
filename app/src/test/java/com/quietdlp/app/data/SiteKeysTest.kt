package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteKeysTest {
    private val key = "0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun theKeyIsFoundInTheCredentialsLine() {
        val page = "Your API Access Credentials: &api_key=$key&user_id=1234567 Generate a new key"
        assertEquals("1234567" to key, SiteKeys.parse(page))
    }

    @Test
    fun theKeyIsFoundInSeparateFields() {
        val page = "User ID: 4242\nAPI key: $key"
        assertEquals("4242" to key, SiteKeys.parse(page))
    }

    @Test
    fun nothingIsReturnedWhenAPartIsMissingOrWrong() {
        assertNull(SiteKeys.parse("&user_id=123"))
        assertNull(SiteKeys.parse("&api_key=$key"))
        assertNull(SiteKeys.parse("&api_key=short&user_id=123"))
        assertNull(SiteKeys.parse("nothing here"))
    }

    @Test
    fun typedValuesAreCleanedAndChecked() {
        assertEquals("1234", SiteKeys.cleanUser(" 12 34\n"))
        assertEquals(key, SiteKeys.cleanKey("  $key \n"))
        assertTrue(SiteKeys.valid("1234", key))
        assertFalse(SiteKeys.valid("", key))
        assertFalse(SiteKeys.valid("12ab", key))
        assertFalse(SiteKeys.valid("1234", "tooshort"))
    }

    @Test
    fun theKeyGoesToGalleryDlQuotedAndOnlyWhenValid() {
        val a = SiteKeys.galleryArgs("https://www.photos.example/x", "1234", key)
        assertEquals(
            listOf("-o", "extractor.photos.user-id=\"1234\"", "-o", "extractor.photos.api-key=\"$key\""),
            a,
        )
        assertTrue(SiteKeys.galleryArgs("photos.example", "", key).isEmpty())
        assertTrue(SiteKeys.galleryArgs("photos.example", "1234", "x").isEmpty())
        assertTrue(SiteKeys.galleryArgs("", "1234", key).isEmpty())
    }

    @Test
    fun theSiteNameComesFromTheAddressTheUserTypes() {
        assertEquals("photos", SiteKeys.category("photos.example"))
        assertEquals("photos", SiteKeys.category("https://www.photos.example/index?x=1"))
        assertNull(SiteKeys.category("nonsense"))
        assertNull(SiteKeys.category(""))
    }

    @Test
    fun theKeysAreNeverInTheBackup() {
        assertFalse(SettingsBackup.KEYS.keys.any { it.contains("key") || it.contains("user") })
    }
}
