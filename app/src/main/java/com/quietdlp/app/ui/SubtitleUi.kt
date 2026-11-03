package com.quietdlp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.MediaInfo
import com.quietdlp.app.data.SubtitleMode

/** Qué significa cada lugar donde pueden ir los subtítulos, en una línea. */
private fun modeHint(mode: SubtitleMode): String = when (mode) {
    SubtitleMode.INSIDE -> "A track inside the MP4. Turn it on in your player."
    SubtitleMode.MKV -> "VLC turns it on by itself. WhatsApp can't play MKV."
    SubtitleMode.SEPARATE -> "A .srt file next to the video. Without a chosen folder, both go to Downloads."
}

/** Subtítulos en la tarjeta del enlace: un interruptor y, al encenderlo, el idioma. Solo para un vídeo. */
@Composable
internal fun SubtitlesSection(vm: MainViewModel, info: MediaInfo) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) SubtitleDialog(vm, info) { picking = false }

    // Los subtítulos no se recortan con el vídeo (saldrían desfasados): con un trozo no se ofrecen.
    if (info.subtitles.isNotEmpty() && (vm.trimOn || vm.removingParts(info))) {
        Text(
            if (vm.trimOn) "Subtitles aren't available when trimming." else "Subtitles aren't available when removing sponsor parts.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    if (info.subtitles.isEmpty()) {
        Text("No subtitles for this video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Subtitles", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        Switch(checked = vm.subsOn, onCheckedChange = vm::changeSubsOn)
    }
    if (!vm.subsOn) return
    val chosen = vm.subCodes.mapNotNull { c -> info.subtitles.firstOrNull { it.code == c }?.label }
    TextButton(onClick = { picking = true }) {
        Text(if (chosen.isEmpty()) "Choose a language" else chosen.joinToString(", "))
    }
    val hint = when {
        info.subtitles.all { it.auto } -> "Automatic captions only."
        else -> modeHint(vm.effectiveSubMode)
    }
    Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Elegir idioma. En el modo simple, uno; en el avanzado, varios y dónde se guardan. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubtitleDialog(vm: MainViewModel, info: MediaInfo, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitles") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                info.subtitles.forEach { t ->
                    val selected = t.code in vm.subCodes
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (vm.advanced) {
                                vm.toggleSubtitle(t.code)
                            } else {
                                vm.chooseSubtitle(t.code)
                                onDismiss()
                            }
                        }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (vm.advanced) Checkbox(checked = selected, onCheckedChange = null)
                        else RadioButton(selected = selected, onClick = null)
                        Text(t.label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
                if (vm.advanced) {
                    Text(
                        "Save as",
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubtitleMode.entries.forEach { m ->
                            QuietChip(vm.subMode == m, { vm.chooseSubMode(m) }, m.label)
                        }
                    }
                    Text(
                        modeHint(vm.subMode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
