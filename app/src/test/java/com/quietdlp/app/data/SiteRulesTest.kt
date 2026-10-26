package com.quietdlp.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteRulesTest {
    private val music = Quality(Kind.AUDIO, audioFormat = "m4a")
    private val hd = Quality(Kind.VIDEO, maxHeight = 1080)

    @Test
    fun normalizesAddresses() {
        assertEquals("example.com", SiteRules.normalizeHost("https://www.Example.com/a/b?x=1#t"))
        assertEquals("example.com", SiteRules.normalizeHost("m.example.com"))
        assertEquals("sub.example.com", SiteRules.normalizeHost(" sub.example.com:8080/path "))
        assertNull(SiteRules.normalizeHost(""))
        assertNull(SiteRules.normalizeHost("localhost"))
        assertNull(SiteRules.normalizeHost("not an address"))
    }

    @Test
    fun matchesSiteAndSubdomainsButNotLookalikes() {
        val rules = listOf(SiteRule("example.com", music))
        assertEquals(music, SiteRules.match(rules, "https://example.com/song")?.quality)
        assertEquals(music, SiteRules.match(rules, "https://artist.example.com/track/1")?.quality)
        assertNull(SiteRules.match(rules, "https://notexample.com/x"))
        assertNull(SiteRules.match(rules, "https://example.org/x"))
    }

    @Test
    fun mostSpecificRuleWins() {
        val rules = listOf(SiteRule("example.com", hd), SiteRule("music.example.com", music))
        assertEquals(music, SiteRules.match(rules, "https://music.example.com/a")?.quality)
        assertEquals(hd, SiteRules.match(rules, "https://www.example.com/a")?.quality)
    }

    @Test
    fun upsertReplacesAndRemoveDeletes() {
        var rules = SiteRules.upsert(emptyList(), SiteRule("example.com", hd))
        rules = SiteRules.upsert(rules, SiteRule("example.com", music))
        assertEquals(1, rules.size)
        assertEquals(music, rules.single().quality)
        assertTrue(SiteRules.remove(rules, "example.com").isEmpty())
    }

    @Test
    fun roundTripAndCap() {
        val rules = listOf(
            SiteRule("a.com", Quality(Kind.AUDIO, 0, "mp3", 128)),
            SiteRule("b.com", Quality(Kind.VIDEO, 720)),
        )
        assertEquals(rules, SiteRules.decode(SiteRules.encode(rules)))
        val many = (1..SiteRules.MAX + 10).map { SiteRule("site$it.com", hd) }
        assertEquals(SiteRules.MAX, SiteRules.decode(SiteRules.encode(many)).size)
        assertTrue(SiteRules.decode("garbage").isEmpty())
        assertEquals(1, SiteRules.decode("""[{"host":"ok.com","kind":"AUDIO"},{"host":"bad"}]""").size)
    }
}
