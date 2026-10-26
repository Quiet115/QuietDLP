package com.quietdlp.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.State
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Logo provisional: flecha de bajada sobre una bandeja, en un cuadrado con degradado de la paleta. */
@Composable
fun QuietLogo(size: Dp = 34.dp) {
    val brush = Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary))
    val ink = MaterialTheme.colorScheme.onPrimary
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawRoundRect(brush, cornerRadius = CornerRadius(s * 0.30f))
        val stroke = Stroke(width = s * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(ink, Offset(s * x1, s * y1), Offset(s * x2, s * y2), strokeWidth = stroke.width, cap = StrokeCap.Round)
        line(0.50f, 0.24f, 0.50f, 0.58f)
        line(0.34f, 0.43f, 0.50f, 0.59f)
        line(0.66f, 0.43f, 0.50f, 0.59f)
        line(0.30f, 0.75f, 0.70f, 0.75f)
    }
}

/** Chip de selección con los colores principales de la paleta (el seleccionado destaca). */
@Composable
fun QuietChip(selected: Boolean, onClick: () -> Unit, label: String, enabled: Boolean = true) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        enabled = enabled,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

/** Interruptor Simple / Avanzado, en forma de píldora, para el borde superior. */
@Composable
fun ModeSwitch(advanced: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(3.dp),
    ) {
        Segment("Simple", !advanced) { onChange(false) }
        Segment("Advanced", advanced) { onChange(true) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, label = "seg")
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "segText",
    )
    Box(
        Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg)
    }
}

/** Grupo de ajustes con título de color. */
@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

enum class Tone { NEUTRAL, GOOD, BAD, ACTIVE }

/** Etiqueta pequeña de estado ("Done", "Failed"…). */
@Composable
fun StatusPill(text: String, tone: Tone) {
    val scheme = MaterialTheme.colorScheme
    val (bg, fg) = when (tone) {
        Tone.GOOD -> scheme.primaryContainer to scheme.onPrimaryContainer
        Tone.BAD -> scheme.error.copy(alpha = 0.16f) to scheme.error
        Tone.ACTIVE -> scheme.secondaryContainer to scheme.onSecondaryContainer
        Tone.NEUTRAL -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Box(Modifier.clip(CircleShape).background(bg).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ───────────── miniaturas ─────────────

private val thumbCache = LruCache<String, Bitmap>(80)

private fun loadThumb(ctx: Context, uri: String): Bitmap? {
    thumbCache.get(uri)?.let { return it }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val bmp = runCatching { ctx.contentResolver.loadThumbnail(Uri.parse(uri), Size(256, 256), null) }.getOrNull()
    if (bmp != null) thumbCache.put(uri, bmp)
    return bmp
}

/** Miniatura cuadrada de una descarga: la del archivo guardado, la de la web o un recuadro con la inicial del tipo. */
@Composable
fun FileThumb(item: DownloadItem, size: Dp = 72.dp) {
    val ctx = LocalContext.current
    val shape = RoundedCornerShape(16.dp)
    val first = item.savedUris.firstOrNull()
    val bitmap by produceState<Bitmap?>(null, first) {
        value = if (first == null) null else withContext(Dispatchers.IO) { loadThumb(ctx, first) }
    }
    Box(
        Modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = bitmap
        when {
            bmp != null -> androidx.compose.foundation.Image(
                bmp.asImageBitmap(), null, Modifier.size(size), contentScale = ContentScale.Crop,
            )
            item.thumbnail != null -> AsyncImage(
                item.thumbnail, null, Modifier.size(size), contentScale = ContentScale.Crop,
            )
            else -> Text(
                when (item.quality.kind) { Kind.AUDIO -> "♪"; Kind.PHOTO -> "▣"; Kind.VIDEO -> "▶" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        // Distintivo del tipo, abajo a la izquierda.
        if (bmp != null || item.thumbnail != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    when (item.quality.kind) { Kind.AUDIO -> "AUDIO"; Kind.PHOTO -> "PHOTO"; Kind.VIDEO -> "VIDEO" },
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
        }
    }
}

/** Recuadro con degradado de la paleta: se ve mientras carga la miniatura o si no se pudo cargar. */
@Composable
fun ThumbPlaceholder() {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.secondaryContainer),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) { QuietLogo(56.dp) }
}

fun statusTone(item: DownloadItem): Tone = when (item.state) {
    State.DONE -> Tone.GOOD
    State.FAILED -> Tone.BAD
    State.CANCELED -> Tone.NEUTRAL
    else -> Tone.ACTIVE
}

// ───────────── paletas ─────────────

/** Muestra de una paleta: dos círculos de color sobre su fondo y su nombre. */
@Composable
fun PaletteSwatch(spec: PaletteSpec, selected: Boolean, onClick: () -> Unit) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .width(86.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(if (selected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .background(Color(spec.background))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(spec.accent)))
            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(spec.accent2)))
            Box(
                Modifier.size(24.dp).clip(CircleShape).background(Color(spec.surface))
                    .border(1.dp, Color(spec.accent).copy(alpha = 0.4f), CircleShape),
            )
        }
        Text(
            spec.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (spec.dark) Color(0xFFE7EBF1) else Color(0xFF14181F),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GapV(h: Dp) = Spacer(Modifier.height(h))
