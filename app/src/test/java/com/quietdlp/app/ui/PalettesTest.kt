package com.quietdlp.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PalettesTest {

    private fun contrast(a: Color, b: Color): Float {
        val l1 = maxOf(a.luminance(), b.luminance()) + 0.05f
        val l2 = minOf(a.luminance(), b.luminance()) + 0.05f
        return l1 / l2
    }

    @Test
    fun hsvPrimaries() {
        assertEquals(0xFFFF0000.toInt(), Palettes.hsv(0f, 1f, 1f))
        assertEquals(0xFF00FF00.toInt(), Palettes.hsv(120f, 1f, 1f))
        assertEquals(0xFF0000FF.toInt(), Palettes.hsv(240f, 1f, 1f))
        assertEquals(0xFF000000.toInt(), Palettes.hsv(200f, 0.5f, 0f))
    }

    @Test
    fun presetsAreUniqueAndReadable() {
        assertEquals(Palettes.presets.size, Palettes.presets.map { it.id }.toSet().size)
        assertTrue(Palettes.presets.none { it.id == Palettes.SYSTEM })
        assertTrue(Palettes.find(Palettes.DEFAULT, emptyList()) != null)
        Palettes.presets.forEach { p ->
            val s = Palettes.scheme(p)
            assertTrue("${p.id} text on background", contrast(s.onBackground, s.background) >= 7f)
            assertTrue("${p.id} text on card", contrast(s.onSurface, s.surfaceContainer) >= 7f)
            assertTrue("${p.id} accent on background", contrast(s.primary, s.background) >= 3f)
            assertTrue("${p.id} text on accent", contrast(s.onPrimary, s.primary) >= 4.5f)
            assertTrue("${p.id} muted text on card", contrast(s.onSurfaceVariant, s.surfaceContainer) >= 4.5f)
        }
    }

    @Test
    fun customPaletteIsClampedAndReadableAtAnySliderValue() {
        val p = Palettes.custom("c1", "  ", dark = true, accentHue = 400, backgroundHue = -10, tint = 500)
        assertEquals(40, p.accentHue)
        assertEquals(350, p.backgroundHue)
        assertEquals(100, p.tint)
        assertEquals("My palette", p.name)
        for (dark in listOf(true, false)) for (h in 0..359 step 30) for (t in listOf(0, 50, 100)) {
            val s = Palettes.scheme(Palettes.custom("x", "x", dark, h, (h + 120) % 360, t))
            assertTrue("dark=$dark h=$h t=$t text", contrast(s.onBackground, s.background) >= 7f)
            assertTrue("dark=$dark h=$h t=$t accent", contrast(s.primary, s.background) >= 3f)
            assertTrue("dark=$dark h=$h t=$t onPrimary", contrast(s.onPrimary, s.primary) >= 4.5f)
        }
    }

    @Test
    fun customPalettesRoundTrip() {
        val a = Palettes.custom("c1", "Sea", true, 180, 210, 60)
        val b = Palettes.custom("c2", "Cream", false, 20, 40, 30)
        val back = Palettes.decodeCustom(Palettes.encodeCustom(listOf(a, b)))
        assertEquals(listOf(a, b), back)
    }

    @Test
    fun savedListIsCappedAndGarbageIsIgnored() {
        val many = (1..Palettes.MAX_CUSTOM + 5).map { Palettes.custom("c$it", "n$it", true, it, it, 10) }
        assertEquals(Palettes.MAX_CUSTOM, Palettes.decodeCustom(Palettes.encodeCustom(many)).size)
        assertTrue(Palettes.decodeCustom("nope").isEmpty())
        assertEquals(1, Palettes.decodeCustom("""[{"id":"a","name":"A","dark":true,"ah":1,"bh":2,"tint":3},{"id":"bad"}]""").size)
    }
}
