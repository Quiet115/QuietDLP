package com.quietdlp.app.ui

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Tipografía de la app. Para cambiar de fuente basta con cambiar esta línea (por ejemplo por una fuente incluida en res/font). */
private val AppFont = FontFamily.SansSerif

private fun t(size: Int, weight: FontWeight, line: Int, spacing: Double = 0.0) =
    TextStyle(fontFamily = AppFont, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp, letterSpacing = spacing.sp)

private val QuietTypography = Typography(
    headlineMedium = t(28, FontWeight.Bold, 34, -0.3),
    headlineSmall = t(24, FontWeight.Bold, 30, -0.2),
    titleLarge = t(21, FontWeight.SemiBold, 27, -0.1),
    titleMedium = t(17, FontWeight.SemiBold, 23),
    titleSmall = t(15, FontWeight.SemiBold, 20, 0.1),
    bodyLarge = t(16, FontWeight.Normal, 23),
    bodyMedium = t(14, FontWeight.Normal, 20),
    bodySmall = t(12, FontWeight.Normal, 17, 0.1),
    labelLarge = t(14, FontWeight.SemiBold, 19, 0.1),
    labelMedium = t(12, FontWeight.SemiBold, 16, 0.3),
    labelSmall = t(11, FontWeight.Medium, 15, 0.3),
)

private val QuietShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun QuietTheme(
    paletteId: String = Palettes.DEFAULT,
    custom: List<PaletteSpec> = emptyList(),
    content: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useSystem = paletteId == Palettes.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = if (useSystem) {
        if (systemDark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val spec = Palettes.find(paletteId, custom)
            ?: if (paletteId == Palettes.SYSTEM) {
                // Android antiguo sin colores del sistema: paleta clara u oscura según el modo del móvil.
                Palettes.find(if (systemDark) "midnight" else "paper", emptyList())
            } else null
        Palettes.scheme(spec ?: Palettes.presets.first())
    }

    // Los iconos de la barra de estado y de navegación tienen que verse sobre el fondo elegido, no sobre el del sistema.
    val view = LocalView.current
    val darkBackground = colors.background.luminance() < 0.5f
    SideEffect {
        (view.context as? Activity)?.window?.let { w ->
            WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = !darkBackground
                isAppearanceLightNavigationBars = !darkBackground
            }
        }
    }

    MaterialTheme(colorScheme = colors, typography = QuietTypography, shapes = QuietShapes, content = content)
}
