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
 * Una paleta de colores completa. Los colores son ARGB. Todo lo demás (contenedores, contornos y, sobre todo,
 * el color del texto) se calcula a partir de estos cuatro en [Palettes.scheme]: el texto siempre toma el color
 * que más contraste da, elijas los colores que elijas.
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
)

object Palettes {
    /** Id especial: usa los colores del sistema (Android 12 o superior). */
    const val SYSTEM = "system"
    const val DEFAULT = "obsidian"
    const val MAX_CUSTOM = 12

    private const val TEXT_LIGHT = 0xFFE7EBF1.toInt()
    private const val TEXT_DARK = 0xFF14181F.toInt()

    val presets = listOf(
        PaletteSpec("obsidian", "Obsidian", PaletteGroup.DARK, true, 0xFF0D0D0F.toInt(), 0xFF18181C.toInt(), 0xFFE2B857.toInt(), 0xFFD98E73.toInt()),
        PaletteSpec("midnight", "Midnight", PaletteGroup.DARK, true, 0xFF0B0F14.toInt(), 0xFF141A22.toInt(), 0xFF4FD1C5.toInt(), 0xFF8F9BFF.toInt()),
        PaletteSpec("sunset", "Sunset", PaletteGroup.COLORFUL, true, 0xFF150B20.toInt(), 0xFF22122F.toInt(), 0xFFFF6B9D.toInt(), 0xFFFFB86B.toInt()),
        PaletteSpec("neon", "Neon", PaletteGroup.COLORFUL, true, 0xFF0A0B1F.toInt(), 0xFF14173B.toInt(), 0xFFB06CFF.toInt(), 0xFF3DDCFF.toInt()),
        PaletteSpec("paper", "Paper", PaletteGroup.LIGHT, false, 0xFFF4F6F8.toInt(), 0xFFFFFFFF.toInt(), 0xFF0B7A71.toInt(), 0xFF5B5BD6.toInt()),
        PaletteSpec("sky", "Sky", PaletteGroup.LIGHT, false, 0xFFF0F5FF.toInt(), 0xFFFFFFFF.toInt(), 0xFF2A5FE0.toInt(), 0xFFD9542F.toInt()),
    )

    fun find(id: String?, custom: List<PaletteSpec>): PaletteSpec? =
        presets.firstOrNull { it.id == id } ?: custom.firstOrNull { it.id == id }

    // ───────────── colores ─────────────

    private fun contrastOf(a: Int, b: Int): Float {
        val l1 = Color(a).luminance()
        val l2 = Color(b).luminance()
        return (maxOf(l1, l2) + 0.05f) / (minOf(l1, l2) + 0.05f)
    }

    /**
     * Color de texto para unos fondos: primero el claro u oscuro de la paleta; si ninguno se lee bien
     * (fondos de tono medio), blanco o negro puros, que son los que más contraste pueden dar.
     */
    private fun bestText(vararg backgrounds: Int): Int {
        fun worst(t: Int) = backgrounds.minOf { contrastOf(t, it) }
        val soft = if (worst(TEXT_LIGHT) >= worst(TEXT_DARK)) TEXT_LIGHT else TEXT_DARK
        if (worst(soft) >= 4.6f) return soft
        return listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt()).maxByOrNull { worst(it) }!!
    }

    private fun isLight(argb: Int) = Color(argb).luminance() > 0.5f

    private fun mix(a: Int, b: Int, t: Float): Color = Color(b).copy(alpha = t).compositeOver(Color(a))

    private fun Color.toArgb32(): Int {
        fun ch(f: Float) = (f * 255f + 0.5f).toInt().coerceIn(0, 255)
        return (ch(alpha) shl 24) or (ch(red) shl 16) or (ch(green) shl 8) or ch(blue)
    }

    /** Texto blanco, casi negro o negro: el que más contraste dé sobre [c]. */
    private fun onColor(c: Color): Color {
        val bgL = c.luminance() + 0.05f
        return listOf(Color.White, Color(0xFF10141A), Color.Black).maxByOrNull { t ->
            val tl = t.luminance() + 0.05f
            maxOf(bgL, tl) / minOf(bgL, tl)
        }!!
    }

    /** Una paleta propia a partir de cuatro colores cualesquiera. Si es oscura o clara se deduce del fondo. */
    fun fromColors(id: String, name: String, background: Int, surface: Int, accent: Int, accent2: Int): PaletteSpec {
        val bg = background or (0xFF shl 24)
        val sf = surface or (0xFF shl 24)
        return PaletteSpec(
            id = id, name = name.trim().ifBlank { "My palette" }.take(20), group = PaletteGroup.CUSTOM,
            dark = isLight(bestText(sf)),
            background = bg, surface = sf, accent = accent or (0xFF shl 24), accent2 = accent2 or (0xFF shl 24),
        )
    }

    /**
     * Sugiere cuatro colores armoniosos a partir de un tono de acento y un tono de fondo, para que el usuario
     * parta de algo agradable y luego afine cada color. Los valores se limitan a su rango.
     */
    fun suggest(dark: Boolean, accentHue: Int, backgroundHue: Int, tint: Int): List<Int> {
        val ah = accentHue.mod(360).toFloat()
        val bh = backgroundHue.mod(360).toFloat()
        val t = tint.coerceIn(0, 100) / 100f
        val bg = if (dark) hsv(bh, t * 0.35f, 0.07f + 0.05f * t) else hsv(bh, t * 0.12f, 0.98f - 0.03f * t)
        val surface = if (dark) hsv(bh, t * 0.30f, 0.11f + 0.06f * t) else hsv(bh, t * 0.05f, 1.0f)
        fun readable(hue: Float, s: Float, vStart: Float): Int {
            var v = vStart
            var c = hsv(hue, s, v)
            while (!dark && v > 0.2f && (contrastOf(c, bg) < 3.6f || contrastOf(c, 0xFFFFFFFF.toInt()) < 4.7f)) {
                v -= 0.03f
                c = hsv(hue, s, v)
            }
            return c
        }
        val accent = readable(ah, 0.62f, if (dark) 0.95f else 0.78f)
        val accent2 = readable((ah + 40) % 360, 0.55f, if (dark) 0.98f else 0.80f)
        return listOf(bg, surface, accent, accent2)
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

    /** ARGB a [matiz 0..360, saturación 0..1, brillo 0..1]. */
    fun toHsv(argb: Int): FloatArray {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return floatArrayOf(h, if (max == 0f) 0f else d / max, max)
    }

    fun toHex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** "#RRGGBB", "RRGGBB" o "#RGB" a ARGB opaco; null si no es un color. */
    fun parseHex(input: String): Int? {
        var t = input.trim().removePrefix("#")
        if (t.length == 3) t = t.map { "$it$it" }.joinToString("")
        if (t.length != 6 || !t.all { it in "0123456789abcdefABCDEF" }) return null
        return (0xFF shl 24) or t.toInt(16)
    }

    // ───────────── guardado ─────────────

    fun encodeCustom(list: List<PaletteSpec>): String {
        val arr = JSONArray()
        list.filter { it.group == PaletteGroup.CUSTOM }.take(MAX_CUSTOM).forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("bg", it.background).put("surface", it.surface)
                    .put("accent", it.accent).put("accent2", it.accent2),
            )
        }
        return arr.toString()
    }

    fun decodeCustom(json: String): List<PaletteSpec> {
        val arr = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                if (o.has("bg")) {
                    fromColors(o.getString("id"), o.getString("name"), o.getInt("bg"), o.getInt("surface"), o.getInt("accent"), o.getInt("accent2"))
                } else {
                    // Formato de la 0.2.6: tres deslizadores.
                    val c = suggest(o.getBoolean("dark"), o.getInt("ah"), o.getInt("bh"), o.getInt("tint"))
                    fromColors(o.getString("id"), o.getString("name"), c[0], c[1], c[2], c[3])
                }
            }.getOrNull()
        }
    }

    // ───────────── de paleta a esquema de colores de Material ─────────────

    fun scheme(p: PaletteSpec): ColorScheme {
        val bg = Color(p.background)
        val surface = Color(p.surface)
        // Cada texto se elige para su propio fondo: el de las tarjetas y el de la pantalla pueden ser muy distintos.
        val onSurfaceInt = bestText(p.surface)
        val onSurface = Color(onSurfaceInt)
        val onBackground = Color(bestText(p.background))
        val accent = Color(p.accent)
        val accent2 = Color(p.accent2)
        val dark = isLight(onSurfaceInt)
        val base = if (dark) darkColorScheme() else lightColorScheme()
        val primaryContainer = mix(p.surface, p.accent, if (dark) 0.30f else 0.16f)
        val secondaryContainer = mix(p.surface, p.accent2, if (dark) 0.28f else 0.16f)
        // Texto secundario: se atenúa hacia la tarjeta, pero nunca por debajo de un contraste legible.
        var muted = mix(onSurfaceInt, p.surface, 0.30f)
        if (contrastOf(muted.toArgb32(), p.surface) < 4.5f) muted = onSurface
        return base.copy(
            primary = accent,
            onPrimary = onColor(accent),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onColor(primaryContainer),
            secondary = accent2,
            onSecondary = onColor(accent2),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onColor(secondaryContainer),
            tertiary = accent2,
            onTertiary = onColor(accent2),
            background = bg,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = mix(p.surface, onSurfaceInt, 0.07f),
            onSurfaceVariant = muted,
            surfaceContainerLowest = bg,
            surfaceContainerLow = mix(p.background, p.surface, 0.5f),
            surfaceContainer = surface,
            surfaceContainerHigh = mix(p.surface, onSurfaceInt, 0.04f),
            surfaceContainerHighest = mix(p.surface, onSurfaceInt, 0.08f),
            outline = mix(p.surface, onSurfaceInt, 0.32f),
            outlineVariant = mix(p.surface, onSurfaceInt, 0.14f),
            error = if (dark) Color(0xFFFF8A7A) else Color(0xFFBA3B2E),
            onError = if (dark) Color(0xFF3B0A04) else Color.White,
        )
    }
}
