package com.quietdlp.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {

    private fun contrast(a: Color, b: Color): Float {
        val l1 = maxOf(a.luminance(), b.luminance()) + 0.05f
        val l2 = minOf(a.luminance(), b.luminance()) + 0.05f
        return l1 / l2
    }

    @Test
    fun hsvPrimariesAndRoundTrip() {
        assertEquals(0xFFFF0000.toInt(), Palettes.hsv(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), Palettes.hsv(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), Palettes.hsv(240f, 1f, 1f))
        assertEquals(0xFF000000.toInt(), Palettes.hsv(200f, 0.5f, 0f))
        for (c in listOf(0xFFE2B857.toInt(), 0xFF4FD1C5.toInt(), 0xFF123456.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt())) {
            val h = Palettes.toHsv(c)
            assertEquals(c, Palettes.hsv(h[0], h[1], h[2]))
        }
    }

    @Test
    fun hexParsingAndFormatting() {
        assertEquals(0xFFE2B857.toInt(), Palettes.parseHex("#e2b857"))
        assertEquals(0xFFE2B857.toInt(), Palettes.parseHex("E2B857"))
        assertEquals(0xFFAABBCC.toInt(), Palettes.parseHex("#abc"))
        assertNull(Palettes.parseHex("#12"))
        assertNull(Palettes.parseHex("#GGGGGG"))
        assertNull(Palettes.parseHex(""))
        assertEquals("#E2B857", Palettes.toHex(0xFFE2B857.toInt()))
    }

    @Test
    fun obsidianIsTheDefaultAndPresetsAreReadable() {
        assertEquals("obsidian", Palettes.DEFAULT)
        assertEquals("obsidian", Palettes.presets.first().id)
        assertEquals(Palettes.presets.size, Palettes.presets.map { it.id }.toSet().size)
        assertTrue(Palettes.presets.none { it.id == Palettes.SYSTEM })
        assertNotNull(Palettes.find(Palettes.DEFAULT, emptyList()))
        Palettes.presets.forEach { p ->
            val s = Palettes.scheme(p)
            assertTrue("${p.id} text on background", contrast(s.onBackground, s.background) >= 7f)
            assertTrue("${p.id} text on card", contrast(s.onSurface, s.surfaceContainer) >= 7f)
            assertTrue("${p.id} accent on background", contrast(s.primary, s.background) >= 4.5f)
            assertTrue("${p.id} text on accent", contrast(s.onPrimary, s.primary) >= 4.5f)
            assertTrue("${p.id} muted text on card", contrast(s.onSurfaceVariant, s.surfaceContainer) >= 4.5f)
        }
    }

    @Test
    fun systemPaletteIsCalledWallpaperAndOldIdIsMigrated() {
        assertEquals("wallpaper", Palettes.SYSTEM)
        assertEquals(Palettes.SYSTEM, Palettes.normalizeId("system"))
        assertEquals(Palettes.SYSTEM, Palettes.normalizeId("wallpaper"))
        assertEquals("sky", Palettes.normalizeId("sky"))
        assertEquals("c1", Palettes.normalizeId("c1"))
        assertEquals(Palettes.DEFAULT, Palettes.normalizeId(null))
    }

    @Test
    fun textIsReadableOnAnyColorsTheUserPicks() {
        // Combinaciones de todo tipo, también las feas: el texto debe leerse siempre.
        val picks = listOf(
            0xFF000000, 0xFFFFFFFF, 0xFF808080, 0xFF777777, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00,
            0xFFFF00FF, 0xFF00FFFF, 0xFF7F7F00, 0xFF336699, 0xFFCC9966, 0xFF1A1A2E, 0xFFEEEEDD, 0xFF8A2BE2,
        ).map { it.toInt() }
        for (bg in picks) for (surface in picks) for (accent in picks.filterIndexed { i, _ -> i % 3 == 0 }) {
            val s = Palettes.scheme(Palettes.fromColors("x", "x", bg, surface, accent, accent))
            val tag = "bg=${Palettes.toHex(bg)} surface=${Palettes.toHex(surface)} accent=${Palettes.toHex(accent)}"
            assertTrue("$tag text on background", contrast(s.onBackground, s.background) >= 4.5f)
            assertTrue("$tag text on card", contrast(s.onSurface, s.surface) >= 4.5f)
            assertTrue("$tag muted on card", contrast(s.onSurfaceVariant, s.surface) >= 4.5f)
            assertTrue("$tag text on accent", contrast(s.onPrimary, s.primary) >= 4.5f)
            assertTrue("$tag text on primary container", contrast(s.onPrimaryContainer, s.primaryContainer) >= 4.5f)
        }
    }

    @Test
    fun darkOrLightComesFromTheBackground() {
        assertTrue(Palettes.fromColors("a", "a", 0xFF101010.toInt(), 0xFF202020.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()).dark)
        assertTrue(!Palettes.fromColors("a", "a", 0xFFF5F5F5.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF000000.toInt()).dark)
        assertEquals("My palette", Palettes.fromColors("a", "  ", 0, 0, 0, 0).name)
        assertEquals(20, Palettes.fromColors("a", "x".repeat(50), 0, 0, 0, 0).name.length)
    }

    @Test
    fun suggestedColorsAreReadable() {
        for (dark in listOf(true, false)) for (h in 0..359 step 30) for (t in listOf(0, 50, 100)) {
            val c = Palettes.suggest(dark, h, (h + 120) % 360, t)
            val s = Palettes.scheme(Palettes.fromColors("x", "x", c[0], c[1], c[2], c[3]))
            assertTrue("dark=$dark h=$h t=$t text", contrast(s.onBackground, s.background) >= 7f)
            assertTrue("dark=$dark h=$h t=$t accent", contrast(s.primary, s.background) >= 4.5f)
            assertTrue("dark=$dark h=$h t=$t onPrimary", contrast(s.onPrimary, s.primary) >= 4.5f)
        }
        // Valores fuera de rango no rompen nada.
        assertEquals(4, Palettes.suggest(true, 900, -50, 500).size)
    }

    @Test
    fun customPalettesRoundTrip() {
        val a = Palettes.fromColors("c1", "Sea", 0xFF0A1A2A.toInt(), 0xFF12263A.toInt(), 0xFF4FD1C5.toInt(), 0xFF8F9BFF.toInt())
        val b = Palettes.fromColors("c2", "Cream", 0xFFF5EFE0.toInt(), 0xFFFFFFFF.toInt(), 0xFF8A4B08.toInt(), 0xFF3B6EA5.toInt())
        assertEquals(listOf(a, b), Palettes.decodeCustom(Palettes.encodeCustom(listOf(a, b))))
    }

    @Test
    fun readsPalettesSavedByVersion026() {
        val old = """[{"id":"c9","name":"Old","dark":true,"ah":170,"bh":220,"tint":40}]"""
        val p = Palettes.decodeCustom(old).single()
        assertEquals("c9", p.id)
        assertEquals("Old", p.name)
        assertTrue(p.dark)
    }

    @Test
    fun savedListIsCappedAndGarbageIsIgnored() {
        val many = (1..Palettes.MAX_CUSTOM + 5).map { Palettes.fromColors("c$it", "n$it", 0xFF111111.toInt(), 0xFF222222.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt()) }
        assertEquals(Palettes.MAX_CUSTOM, Palettes.decodeCustom(Palettes.encodeCustom(many)).size)
        assertTrue(Palettes.decodeCustom("nope").isEmpty())
        assertEquals(1, Palettes.decodeCustom("""[{"id":"a","name":"A","bg":1,"surface":2,"accent":3,"accent2":4},{"id":"bad"}]""").size)
    }
}
