package com.quietdlp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.MediaInfo
import com.quietdlp.app.data.TrimRange
import kotlin.math.roundToInt
import com.quietdlp.app.R
import com.quietdlp.app.Strings

/** Bajar solo un trozo: un interruptor y, al encenderlo, un selector de rango y dos cajas con el tiempo. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrimSection(vm: MainViewModel, info: MediaInfo) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(Strings.get(R.string.trim_ui_trim), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Switch(checked = vm.trimOn, onCheckedChange = vm::changeTrimOn)
    }
    if (!vm.trimOn) return

    val sb = info.storyboard
    val ratio = if (sb != null) sb.width.toFloat() / sb.height else 16f / 9f
    if (sb != null) {
        // Una tira con fotogramas de todo el vídeo; lo que queda fuera del trozo se oscurece.
        val frames = 6
        val scrim = Color.Black.copy(alpha = 0.6f)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).drawWithContent {
                drawContent()
                val a = vm.trimStart.toFloat() / info.durationSec
                val b = vm.trimEnd.toFloat() / info.durationSec
                drawRect(scrim, Offset.Zero, Size(size.width * a, size.height))
                drawRect(scrim, Offset(size.width * b, 0f), Size(size.width * (1f - b), size.height))
            },
        ) {
            repeat(frames) { n ->
                FrameBox(vm.frameAt(((n + 0.5f) / frames * info.durationSec).toInt()), ratio, Modifier.weight(1f))
            }
        }
    } else {
        Text(
            Strings.get(R.string.trim_ui_frames_aren_t_available_for),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    RangeSlider(
        value = vm.trimStart.toFloat()..vm.trimEnd.toFloat(),
        onValueChange = { vm.setTrim(it.start.roundToInt(), it.endInclusive.roundToInt()) },
        valueRange = 0f..info.durationSec.toFloat(),
        modifier = Modifier.fillMaxWidth(),
    )
    // Cada caja de tiempo lleva encima el fotograma de ese momento.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (sb != null) FrameBox(vm.frameAt(vm.trimStart), ratio)
            TimeField(Strings.get(R.string.trim_ui_from), vm.trimStart, vm::setTrimStart, Modifier.fillMaxWidth())
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (sb != null) FrameBox(vm.frameAt(vm.trimEnd), ratio)
            TimeField(Strings.get(R.string.trim_ui_to), vm.trimEnd, vm::setTrimEnd, Modifier.fillMaxWidth())
        }
    }
    val all = vm.trimStart == 0 && vm.trimEnd == info.durationSec
    Text(
        if (all) Strings.get(R.string.trim_ui_the_whole_file) else Strings.get(R.string.trim_ui_only_is_downloaded, TrimRange.formatTime(vm.trimEnd - vm.trimStart)),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Un fotograma; gris mientras no se ha bajado. */
@Composable
private fun FrameBox(frame: ImageBitmap?, ratio: Float, modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (frame != null) {
            Image(bitmap = frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
    }
}

/**
 * Una caja de tiempo ("1:30"). El valor se aplica al terminar de escribir (Hecho o al salir de la caja) y se muestra
 * ya corregido; si no se entiende, queda en rojo hasta que se arregle.
 */
@Composable
private fun TimeField(label: String, seconds: Int, onCommit: (Int) -> Int, modifier: Modifier) {
    var text by remember(seconds) { mutableStateOf(TrimRange.formatTime(seconds)) }
    var bad by remember { mutableStateOf(false) }
    var focusedOnce by remember { mutableStateOf(false) }

    fun commit() {
        val value = TrimRange.parseTime(text)
        if (value == null) {
            bad = true
        } else {
            bad = false
            text = TrimRange.formatTime(onCommit(value))
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it; bad = false },
        label = { Text(label) },
        singleLine = true,
        isError = bad,
        supportingText = if (bad) ({ Text(Strings.get(R.string.trim_ui_use_m_ss_like_1)) }) else null,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        modifier = modifier.onFocusChanged {
            if (it.isFocused) focusedOnce = true else if (focusedOnce) commit()
        },
    )
}
