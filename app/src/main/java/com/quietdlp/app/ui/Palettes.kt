package com.quietdlp.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import org.json.JSONArray
import org.json.JSONObject

/** Familia a la que pertenece una paleta, para agruparlas en Ajustes. */
enum class PaletteGroup(val label: String) { DARK("Dark"), COLORFUL("Colorful"), LIGHT("Light"), CUSTOM("Mine") }

/**
 * Una paleta de colores completa. Los colores son ARGB. Las paletas propias guardan además los
 * valores de los deslizadores con los que se crearon ([accentHue], [backgroundHue], [tint]) para poder editarlas.
 */
data class PaletteSpec(
    val id: String,
    val name: String,
    val group: PaletteGroup,
    val dark: Boolean,
    val background: Int,
    val surface: Int,
    val accent: Int,
    val accent2: Int,
    val accentHue: Int = 0,
    val backgroundHue: Int = 0,
    val tint: Int = 0,
)

object Palettes {
    /** Id especial: usa los colores del sistema (Android 12 o superior). */
    const val SYSTEM = "system"
    const val DEFAULT = "midnight"

    val presets = listOf(
        PaletteSpec("midnight", "Midnight", PaletteGroup.DARK, true, 0xFF0B0F14.toInt(), 0xFF141A22.toInt(), 0xFF4FD1C5.toInt(), 0xFF8F9BFF.toInt()),
        PaletteSpec("obsidian", "Obsidian", PaletteGroup.DARK, true, 0xFF0D0D0F.toInt(), 0xFF18181C.toInt(), 0xFFE2B857.toInt(), 0xFFD98E73.toInt()),
        PaletteSpec("sunset", "Sunset", PaletteGroup.COLORFUL, true, 0xFF150B20.toInt(), 0xFF22122F.toInt(), 0xFFFF6B9D.toInt(), 0xFFFFB86B.toInt()),
        PaletteSpec("neon", "Neon", PaletteGroup.COLORFUL, true, 0xFF0A0B1F.toInt(), 0xFF14173B.toInt(), 0xFFB06CFF.toInt(), 0xFF3DDCFF.toInt()),
        PaletteSpec("paper", "Paper", PaletteGroup.LIGHT, false, 0xFFF4F6F8.toInt(), 0xFFFFFFFF.toInt(), 0xFF0B7A71.toInt(), 0xFF5B5BD6.toInt()),
        PaletteSpec("sky", "Sky", PaletteGroup.LIGHT, false, 0xFFF0F5FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF2A5FE0.toInt(), 0xFFD9542F.toInt()),
    )

    fun find(id: String?, custom: List<PaletteSpec>): PaletteSpec? =
        presets.firstOrNull { it.id == id } ?: custom.firstOrNull { it.id == id }

    // ───────────── paletas propias ─────────────

    /** Crea una paleta a partir de tres deslizadores. Los valores se limitan a su rango. */
    fun custom(id: String, name: String, dark: Boolean, accentHue: Int, backgroundHue: Int, tint: Int): PaletteSpec {
        val ah = accentHue.mod(360)
        val bh = backgroundHue.mod(360)
        val t = tint.coerceIn(0, 100) / 100f
        val bg = if (dark) hsv(bh.toFloat(), t * 0.35f, 0.07f + 0.05f * t)
        else hsv(bh.toFloat(), t * 0.12f, 0.98f - 0.03f * t)
        val surface = if (dark) hsv(bh.toFloat(), t * 0.30f, 0.11f + 0.06f * t)
        else hsv(bh.toFloat(), t * 0.05f, 1.0f)
        // En una paleta clara, los tonos vivos (amarillo, lima) se oscurecen hasta que se lean sobre el fondo.
        fun readable(hue: Float, s: Float, vStart: Float): Int {
            var v = vStart
            var c = hsv(hue, s, v)
            while (!dark && v > 0.2f && (contrastOf(c, bg) < 3.6f || contrastOf(c, 0xFFFFFFFF.toInt()) < 4.7f)) {
                v -= 0.03f
                c = hsv(hue, s, v)
            }
            return c
        }
        val accent = readable(ah.toFloat(), 0.62f, if (dark) 0.95f else 0.78f)
        val accent2 = readable(((ah + 40) % 360).toFloat(), 0.55f, if (dark) 0.98f else 0.80f)
        return PaletteSpec(
            id = id, name = name.trim().ifBlank { "My palette" }.take(20), group = PaletteGroup.CUSTOM, dark = dark,
            background = bg, surface = surface, accent = accent, accent2 = accent2,
            accentHue = ah, backgroundHue = bh, tint = (t * 100).toInt(),
        )
    }

    private fun contrastOf(a: Int, b: Int): Float {
        val l1 = Color(a).luminance()
        val l2 = Color(b).luminance()
        return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
    }

    /** HSV (h 0..360, s y v 0..1) a ARGB, sin depender de android.graphics para poder probarlo. */
    fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        fun ch(f: Float) = ((f + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
    }

    // ───────────── guardado ─────────────

    const val MAX_CUSTOM = 12

    fun encodeCustom(list: List<PaletteSpec>): String {
        val arr = JSONArray()
        list.filter { it.group == PaletteGroup.CUSTOM }.take(MAX_CUSTOM).forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name).put("dark", it.dark)
                    .put("ah", it.accentHue).put("bh", it.backgroundHue).put("tint", it.tint),
            )
        }
        return arr.toString()
    }

    fun decodeCustom(json: String): List<PaletteSpec> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                custom(o.getString("id"), o.getString("name"), o.getBoolean("dark"), o.getInt("ah"), o.getInt("bh"), o.getInt("tint"))
            }.getOrNull()
        }
    }

    // ───────────── de paleta a esquema de colores de Material ─────────────

    private fun mix(a: Int, b: Int, t: Float): Color = Color(b).copy(alpha = t).compositeOver(Color(a))

    /** Texto oscuro o blanco, el que más contraste dé sobre [c]. */
    private fun onColor(c: Color): Color {
        val ink = Color(0xFF10141A)
        val vsWhite = 1.05f / (c.luminance() + 0.05f)
        val vsInk = (c.luminance() + 0.05f) / (ink.luminance() + 0.05f)
        return if (vsInk >= vsWhite) ink else Color.White
    }

    fun scheme(p: PaletteSpec): ColorScheme {
        val bg = Color(p.background)
        val surface = Color(p.surface)
        val onSurface = if (p.dark) Color(0xFFE7EBF1) else Color(0xFF14181F)
        val accent = Color(p.accent)
        val accent2 = Color(p.accent2)
        val base = if (p.dark) darkColorScheme() else lightColorScheme()
        val primaryContainer = mix(p.surface, p.accent, if (p.dark) 0.30f else 0.16f)
        val secondaryContainer = mix(p.surface, p.accent2, if (p.dark) 0.28f else 0.16f)
        return base.copy(
            primary = accent,
            onPrimary = onColor(accent),
            primaryContainer = primaryContainer,
            onPrimaryContainer = if (p.dark) mix(p.accent, 0xFFFFFFFF.toInt(), 0.55f) else mix(p.accent, 0xFF000000.toInt(), 0.45f),
            secondary = accent2,
            onSecondary = onColor(accent2),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = if (p.dark) mix(p.accent2, 0xFFFFFFFF.toInt(), 0.55f) else mix(p.accent2, 0xFF000000.toInt(), 0.45f),
            tertiary = accent2,
            onTertiary = onColor(accent2),
            background = bg,
            onBackground = onSurface,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = mix(p.surface, onSurface.toArgb32(), 0.07f),
            onSurfaceVariant = mix(onSurface.toArgb32(), p.surface, 0.35f),
            surfaceContainerLowest = bg,
            surfaceContainerLow = mix(p.background, p.surface, 0.5f),
            surfaceContainer = surface,
            surfaceContainerHigh = mix(p.surface, onSurface.toArgb32(), 0.04f),
            surfaceContainerHighest = mix(p.surface, onSurface.toArgb32(), 0.08f),
            outline = mix(p.surface, onSurface.toArgb32(), 0.32f),
            outlineVariant = mix(p.surface, onSurface.toArgb32(), 0.14f),
            error = if (p.dark) Color(0xFFFF8A7A) else Color(0xFFBA3B2E),
            onError = if (p.dark) Color(0xFF3B0A04) else Color.White,
        )
    }

    private fun Color.toArgb32(): Int {
        fun ch(f: Float) = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (ch(alpha) shl 24) or (ch(red) shl 16) or (ch(green) shl 8) or ch(blue)
    }
}
