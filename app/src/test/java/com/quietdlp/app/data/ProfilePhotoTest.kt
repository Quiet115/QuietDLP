package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProfilePhotoTest {
    @Test
    fun profilesGetTheirPhotoAndBannerAddresses() {
        val ig = ProfilePhoto.parse("https://www.instagram.com/some.user/?hl=en")!!
        assertEquals("Instagram", ig.network)
        assertEquals("some.user", ig.user)
        assertEquals("https://www.instagram.com/some.user/avatar/", ig.avatarUrl)
        assertNull(ig.bannerUrl)

        val x = ProfilePhoto.parse("https://twitter.com/NASA")!!
        assertEquals("https://x.com/NASA/photo", x.avatarUrl)
        assertEquals("https://x.com/NASA/header_photo", x.bannerUrl)

        val bs = ProfilePhoto.parse("https://bsky.app/profile/bsky.app")!!
        assertEquals("https://bsky.app/profile/bsky.app/avatar", bs.avatarUrl)
        assertEquals("https://bsky.app/profile/bsky.app/banner", bs.bannerUrl)

        assertEquals("https://www.tiktok.com/@tiktok/avatar", ProfilePhoto.parse("https://www.tiktok.com/@tiktok")!!.avatarUrl)
        assertEquals("https://vsco.co/vsco/avatar", ProfilePhoto.parse("https://vsco.co/vsco/")!!.avatarUrl)
        assertEquals("https://www.snapchat.com/@nasa/avatar", ProfilePhoto.parse("https://www.snapchat.com/@nasa")!!.avatarUrl)
        assertEquals("https://www.facebook.com/nasa/avatar", ProfilePhoto.parse("https://m.facebook.com/nasa")!!.avatarUrl)
        assertEquals("https://www.deviantart.com/spyed/banner/", ProfilePhoto.parse("https://www.deviantart.com/spyed")!!.bannerUrl)
        assertEquals("spyed", ProfilePhoto.parse("https://spyed.deviantart.com/")!!.user)
    }

    @Test
    fun postsAndOtherPagesAreNotProfiles() {
        assertNull(ProfilePhoto.parse("https://www.instagram.com/reel/DfSF8i5xFdu/"))
        assertNull(ProfilePhoto.parse("https://www.instagram.com/p/abc123/"))
        assertNull(ProfilePhoto.parse("https://www.instagram.com/explore/"))
        assertNull(ProfilePhoto.parse("https://x.com/NASA/status/123"))
        assertNull(ProfilePhoto.parse("https://x.com/home"))
        assertNull(ProfilePhoto.parse("https://www.tiktok.com/@tiktok/video/123"))
        assertNull(ProfilePhoto.parse("https://bsky.app/profile/bsky.app/post/abc"))
        assertNull(ProfilePhoto.parse("https://www.facebook.com/watch/?v=1"))
        assertNull(ProfilePhoto.parse("https://www.facebook.com/profile.php?id=1"))
        assertNull(ProfilePhoto.parse("https://www.youtube.com/@canal"))
        assertNull(ProfilePhoto.parse("https://example.com/user"))
        assertNull(ProfilePhoto.parse("not a link"))
    }

    @Test
    fun onlyTheProfilePhotoAndBannerTitlesAreRenamed() {
        assertEquals(true, ProfilePhoto.isProfileTitle("nasa (profile)"))
        assertEquals(true, ProfilePhoto.isProfileTitle("nasa (banner)"))
        assertEquals(false, ProfilePhoto.isProfileTitle("nasa (posts)"))
        assertEquals(false, ProfilePhoto.isProfileTitle("My video"))
    }

    @Test
    fun aHostThatOnlyEndsLikeANetworkIsNotThatNetwork() {
        assertNull(ProfilePhoto.parse("https://notinstagram.com/user"))
        assertNull(ProfilePhoto.parse("https://instagram.com.evil.example/user"))
        assertNotNull(ProfilePhoto.parse("https://instagram.com/user"))
    }
}
