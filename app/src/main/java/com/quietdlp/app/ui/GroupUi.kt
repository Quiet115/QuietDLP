package com.quietdlp.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.SelectionPrompt
import com.quietdlp.app.Sharing
import com.quietdlp.app.data.ConvertTargets
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.HistoryRow
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.Layouts
import com.quietdlp.app.data.QualityLevel
import com.quietdlp.app.R
import com.quietdlp.app.Strings

// ───────────────────────── tarjeta de un grupo ─────────────────────────

/** Miniatura con dos más asomando por detrás: avisa de que hay varios archivos en esta tarjeta. */
@Composable
fun ThumbStack(front: DownloadItem, behind: List<Pair<DownloadItem, Int>>, size: Dp = 84.dp) {
    val layers = behind.take(2)
    Box(Modifier.size(size + 16.dp), contentAlignment = Alignment.Center) {
        for (k in layers.indices.reversed()) {
            val (item, index) = layers[k]
            Box(
                Modifier
                    .offset(x = if (k == 0) 6.dp else (-6).dp, y = if (k == 0) 4.dp else 7.dp)
                    .rotate(if (k == 0) 6f else -7f),
            ) { FileThumb(item, size, index) }
        }
        FileThumb(front, size, 0)
    }
}

private fun groupSummary(g: HistoryRow.Group): String = buildList {
    add("${g.total} files")
    if (g.done > 0) add("${g.done} done")
    if (g.failed > 0) add("${g.failed} failed")
}.joinToString(" · ")

/** Una tarjeta para todo el grupo. Tocarla abre la lista de sus archivos. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupCard(
    group: HistoryRow.Group,
    vm: MainViewModel,
    selecting: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggleSelect: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val cover = group.cover
    Card(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selecting) { if (group.active == 0) onToggleSelect() } else onOpen() },
            onLongClick = { if (group.active == 0) onToggleSelect() },
        ),
        colors = CardDefaults.cardColors(containerColor = if (selected) scheme.primaryContainer else scheme.surfaceContainer),
        border = if (selected) BorderStroke(2.dp, scheme.primary) else BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThumbStack(cover, group.items.filter { it.id != cover.id }.take(2).map { it to 0 })
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when {
                        group.active > 0 -> StatusPill("${group.total - group.active} / ${group.total}", Tone.ACTIVE)
                        group.failed > 0 -> StatusPill(Strings.get(R.string.group_ui_done_with_errors), Tone.BAD)
                        group.done == group.total -> StatusPill(Strings.get(R.string.settings_screen_done), Tone.GOOD)
                        else -> StatusPill(Strings.get(R.string.group_ui_stopped), Tone.NEUTRAL)
                    }
                    if (group.items.any { it.fresh }) StatusPill(Strings.get(R.string.group_ui_new), Tone.NEW)
                    Text(
                        groupSummary(group),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (group.active > 0) {
                    group.items.firstOrNull { it.isActive && !it.message.isNullOrBlank() && it.state == com.quietdlp.app.data.State.QUEUED }
                        ?.message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1)
                        }
                    val running = group.items.filter { it.state == com.quietdlp.app.data.State.DOWNLOADING }.sumOf { it.progress.toDouble() } / 100.0
                    LinearProgressIndicator(
                        progress = { (((group.total - group.active) + running) / group.total).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                        strokeCap = StrokeCap.Round,
                    )
                }
                if (!selecting) Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (group.active > 0) TextButton(onClick = { vm.cancelAll(group.items) }) { Text(Strings.get(R.string.group_ui_cancel_all)) }
                    else if (group.failed > 0) OutlinedButton(onClick = { vm.retryFailed(group.items) }) { Text(Strings.get(R.string.group_ui_retry_failed)) }
                    TextButton(onClick = onOpen) { Text(Strings.get(R.string.group_ui_view)) }
                }
            }
        }
    }
}

/** La lista de archivos de un grupo, cada uno con sus botones (abrir, compartir, convertir, reintentar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupScreen(group: HistoryRow.Group, vm: MainViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var askRemove by rememberSaveable { mutableStateOf(false) }
    if (askRemove) {
        AlertDialog(
            onDismissRequest = { askRemove = false },
            title = { Text(Strings.get(R.string.group_ui_remove_files, group.total)) },
            text = { Text(Strings.get(R.string.group_ui_remove_them_from_the_list)) },
            confirmButton = {
                TextButton(onClick = { askRemove = false; vm.deleteMany(group.items, deleteFiles = false) }) { Text(Strings.get(R.string.group_ui_list_only)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askRemove = false }) { Text(Strings.get(R.string.settings_screen_cancel)) }
                    TextButton(onClick = { askRemove = false; vm.deleteMany(group.items, deleteFiles = true) }) { Text(Strings.get(R.string.group_ui_delete_files_too)) }
                }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(group.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = Strings.get(R.string.settings_screen_back)) }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = Layouts.MAX_SINGLE_COLUMN_DP.dp).fillMaxWidth().fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            groupSummary(group),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (group.active > 0) TextButton(onClick = { vm.cancelAll(group.items) }) { Text(Strings.get(R.string.group_ui_cancel_all)) }
                        if (group.failed > 0 && group.active == 0) OutlinedButton(onClick = { vm.retryFailed(group.items) }) { Text(Strings.get(R.string.group_ui_retry_failed)) }
                        if (group.active == 0) TextButton(onClick = { askRemove = true }) { Text(Strings.get(R.string.group_ui_remove_all)) }
                    }
                }
                items(group.items, key = { it.id }) { DownloadRow(it, vm, selecting = false, isSelected = false) }
            }
        }
    }
}

// ───────────────────────── varios archivos de una sola descarga (carrusel) ─────────────────────────

private fun kindOfMime(mime: String?): Kind = when {
    mime?.startsWith("image/") == true -> Kind.PHOTO
    mime?.startsWith("audio/") == true -> Kind.AUDIO
    else -> Kind.VIDEO
}

private fun displayName(ctx: Context, uri: String): String = runCatching {
    ctx.contentResolver.query(Uri.parse(uri), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull() ?: Strings.get(R.string.group_ui_file)

/** Los archivos de una publicación con varias fotos o vídeos, uno por uno: abrir, compartir o convertir cada uno. */
@Composable
fun FilesDialog(item: DownloadItem, vm: MainViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var convert by remember { mutableStateOf<DownloadItem?>(null) }
    convert?.let { f ->
        ConvertDialog(f, onDismiss = { convert = null }) { target ->
            convert = null
            vm.convert(f, target)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.savedUris.size} files") },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(item.savedUris) { _, uri ->
                    val mime = remember(uri) { ctx.contentResolver.getType(Uri.parse(uri)) }
                    val file = item.copy(savedUris = listOf(uri), mime = mime, quality = item.quality.copy(kind = kindOfMime(mime)))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FileThumb(file, 56.dp)
                        Text(
                            remember(uri) { displayName(ctx, uri) },
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            Row {
                                IconButton(onClick = {
                                    runCatching { ctx.startActivity(Sharing.chooser(listOf(Uri.parse(uri)), mime)) }
                                }) { Icon(Icons.Default.Share, contentDescription = Strings.get(R.string.group_ui_share)) }
                                IconButton(onClick = { openFile(ctx, file) }) { Icon(Icons.Default.PlayArrow, contentDescription = Strings.get(R.string.group_ui_open)) }
                            }
                            if (ConvertTargets.canConvert(mime)) TextButton(onClick = { convert = file }) { Text(Strings.get(R.string.group_ui_convert)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.get(R.string.group_ui_close)) } },
    )
}

// ───────────────────────── avisos ─────────────────────────

/** "¿Descargar todos?": un enlace con varios archivos. Se puede quitar alguno y elegir el formato de todos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SelectionDialog(vm: MainViewModel, prompt: SelectionPrompt) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var chosen by remember(prompt) { mutableStateOf(prompt.entries.map { it.url }.toSet()) }
    var quality by remember(prompt) { mutableStateOf(prompt.quality) }
    var dontAsk by remember(prompt) { mutableStateOf(false) }
    val kind = if (quality.kind == Kind.AUDIO) Kind.AUDIO else Kind.VIDEO
    val level = QualityLevel.fromQuality(quality)
    AlertDialog(
        onDismissRequest = vm::dismissSelection,
        title = { Text(Strings.get(R.string.group_ui_download_files, prompt.entries.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(prompt.title, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(Strings.get(R.string.group_ui_format_for_all), style = MaterialTheme.typography.labelMedium, color = muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietChip(kind == Kind.VIDEO, { quality = level.quality(Kind.VIDEO) }, Strings.get(R.string.settings_screen_video))
                    QuietChip(kind == Kind.AUDIO, { quality = level.quality(Kind.AUDIO) }, Strings.get(R.string.group_ui_audio))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QualityLevel.entries.forEach { l -> QuietChip(level == l, { quality = l.quality(kind) }, Strings.tr(l.label)) }
                }
                Text(Strings.tr(quality.label()), style = MaterialTheme.typography.bodySmall, color = muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { chosen = prompt.entries.map { it.url }.toSet() }) { Text(Strings.get(R.string.group_ui_select_all)) }
                    TextButton(onClick = { chosen = emptySet() }) { Text(Strings.get(R.string.group_ui_none)) }
                }
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(prompt.entries, key = { it.url }) { e ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                                chosen = if (e.url in chosen) chosen - e.url else chosen + e.url
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = e.url in chosen, onCheckedChange = {
                                chosen = if (it) chosen + e.url else chosen - e.url
                            })
                            Text(e.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { dontAsk = !dontAsk },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Column {
                        Text(Strings.get(R.string.group_ui_don_t_ask_again), style = MaterialTheme.typography.bodyMedium)
                        Text(Strings.get(R.string.group_ui_change_it_any_time_in), style = MaterialTheme.typography.bodySmall, color = muted)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.confirmSelection(prompt.entries.filter { it.url in chosen }, quality, dontAsk) },
                enabled = chosen.isNotEmpty(),
            ) { Text(Strings.get(R.string.group_ui_download, chosen.size)) }
        },
        dismissButton = { TextButton(onClick = vm::dismissSelection) { Text(Strings.get(R.string.settings_screen_cancel)) } },
    )
}

/** Hay datos móviles y una descarga grande: bajar ya o esperar al Wi-Fi. */
@Composable
fun DataPromptDialog(vm: MainViewModel) {
    AlertDialog(
        onDismissRequest = vm::dismissDataPrompt,
        title = { Text(Strings.get(R.string.group_ui_mobile_data)) },
        text = { Text(Strings.get(R.string.group_ui_you_re_on_mobile_data)) },
        confirmButton = { TextButton(onClick = { vm.answerDataPrompt(false) }) { Text(Strings.get(R.string.group_ui_download_now)) } },
        dismissButton = { TextButton(onClick = { vm.answerDataPrompt(true) }) { Text(Strings.get(R.string.group_ui_wait_for_wi_fi)) } },
    )
}

/** Abre el ajuste de Android para dejar a QuietDLP sin restricciones de batería (si no se puede ir directo, la lista). */
fun openBatterySetting(ctx: Context, listOnly: Boolean = false) {
    val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
    val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(Uri.parse("package:${ctx.packageName}"))
    val ok = !listOnly && runCatching { ctx.startActivity(direct) }.isSuccess
    if (!ok && runCatching { ctx.startActivity(list) }.isFailure) {
        Toast.makeText(ctx, Strings.get(R.string.group_ui_couldn_t_open_the_battery), Toast.LENGTH_SHORT).show()
    }
}

/** Cola larga: sugerir quitar la restricción de batería para que Android no pause las descargas. */
@Composable
fun BatteryPromptDialog(vm: MainViewModel) {
    val ctx = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    var dontAsk by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { vm.dismissBatteryPrompt(dontAsk) },
        title = { Text(Strings.get(R.string.group_ui_long_queue)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.get(R.string.group_ui_android_may_pause_quietdlp_when))
                Text(Strings.get(R.string.group_ui_you_can_undo_this_in), style = MaterialTheme.typography.bodySmall, color = muted)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { dontAsk = !dontAsk },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = dontAsk, onCheckedChange = { dontAsk = it })
                    Text(Strings.get(R.string.group_ui_don_t_remind_me), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.dismissBatteryPrompt(dontAsk)
                openBatterySetting(ctx)
            }) { Text(Strings.get(R.string.group_ui_open_setting)) }
        },
        dismissButton = { TextButton(onClick = { vm.dismissBatteryPrompt(dontAsk) }) { Text(Strings.get(R.string.group_ui_not_now)) } },
    )
}
