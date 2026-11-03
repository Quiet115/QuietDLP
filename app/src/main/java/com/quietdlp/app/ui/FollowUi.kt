package com.quietdlp.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.FollowEngine
import com.quietdlp.app.data.FollowInterval
import com.quietdlp.app.data.FollowedPage
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.Layouts
import com.quietdlp.app.data.QualityLevel
import com.quietdlp.app.download.FollowProbe
import kotlinx.coroutines.delay

/** Cuántas novedades tiene la página: las que esperan sin bajar y las que llegaron y el usuario aún no ha visto. */
fun followNewCount(page: FollowedPage, downloads: List<DownloadItem>): Int =
    page.pending.size + downloads.count { it.followId == page.id && it.fresh }

/** Cuántas páginas tienen novedades (el número de la pestaña, como los chats sin leer). */
fun followPagesWithNew(pages: List<FollowedPage>, downloads: List<DownloadItem>): Int =
    pages.count { followNewCount(it, downloads) > 0 }

private fun ago(ms: Long, now: Long): String {
    if (ms <= 0) return "Not checked yet"
    val min = ((now - ms) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "Checked just now"
        min < 60 -> "Checked $min min ago"
        min < 60 * 24 -> "Checked ${min / 60} h ago"
        else -> "Checked ${min / (60 * 24)} d ago"
    }
}

/** La pestaña "Following": el cuadro para seguir una página y la lista de las que ya se siguen. */
@Composable
fun FollowingTab(
    vm: MainViewModel,
    pages: List<FollowedPage>,
    downloads: List<DownloadItem>,
    onOpenGroup: (String) -> Unit,
) {
    // Para que "Checked 2 min ago" siga la hora mientras la pantalla está abierta.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(30_000)
            value = System.currentTimeMillis()
        }
    }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            Modifier.widthIn(max = Layouts.MAX_SINGLE_COLUMN_DP.dp).fillMaxWidth().fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { AddFollowCard(vm) }
            if (pages.isEmpty()) {
                item {
                    Text(
                        "Follow a channel, a playlist or a gallery. What's new is downloaded for you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(pages, key = { it.id }) { page ->
                FollowCard(vm, page, followNewCount(page, downloads), downloads.any { it.followId == page.id }, now, onOpenGroup)
            }
        }
    }
    vm.followSetup?.let { FollowSetupDialog(vm, it) }
}

@Composable
private fun AddFollowCard(vm: MainViewModel) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = vm.followUrl,
                onValueChange = { vm.followUrl = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Link of a page to follow") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Button(onClick = vm::startFollow, enabled = vm.followUrl.isNotBlank() && !vm.followProbing) {
                Text(if (vm.followProbing) "Looking…" else "Follow")
            }
            if (vm.followProbing) LinearProgressIndicator(Modifier.fillMaxWidth())
            vm.followError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FollowCard(vm: MainViewModel, page: FollowedPage, news: Int, hasFiles: Boolean, now: Long, onOpenGroup: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val checking = page.id in vm.followChecking
    var menu by remember { mutableStateOf(false) }
    var intervalDialog by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(page.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${ago(page.lastCheck, now)} · every ${FollowInterval.label(page.days)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
                if (news > 0) StatusPill(if (page.auto && page.pending.isEmpty()) "New" else "$news new", Tone.NEW)
                Column {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Change time between checks") }, onClick = { menu = false; intervalDialog = true })
                        DropdownMenuItem(text = { Text("Stop following") }, onClick = { menu = false; confirmRemove = true })
                    }
                }
            }
            page.lastError?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = scheme.error, modifier = Modifier.padding(end = 10.dp))
            }
            Text(
                (if (page.engine == FollowEngine.GALLERY) "Photos" else page.quality.label()) +
                    if (page.pending.isNotEmpty() && !page.auto) " · ${page.pending.size} waiting" else "",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
            )
            Row(Modifier.padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Download new ones", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = page.auto, onCheckedChange = { vm.setFollowAuto(page.id, it) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.checkNow(page.id) }, enabled = !checking) {
                    if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Check now")
                }
                if (page.pending.isNotEmpty() && !page.auto) {
                    Button(onClick = { vm.downloadPending(page.id) }) { Text("Download ${page.pending.size}") }
                }
                if (hasFiles) TextButton(onClick = { onOpenGroup(page.groupId) }) { Text("Open") }
            }
        }
    }
    if (intervalDialog) IntervalDialog(page.days, { intervalDialog = false }) { vm.setFollowDays(page.id, it); intervalDialog = false }
    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Stop following?") },
            text = { Text("What you already downloaded stays.") },
            confirmButton = { TextButton(onClick = { vm.removeFollow(page.id); confirmRemove = false }) { Text("Stop") } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun IntervalDialog(current: Double, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(FollowInterval.text(current)) }
    val parsed = FollowInterval.parse(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Check every") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    suffix = { Text("days") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                Text(
                    if (parsed == null) "Type a number." else "Every ${FollowInterval.label(parsed)}. Under 1 day you can use decimals (0.25 = 6 h).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { parsed?.let(onSave) }, enabled = parsed != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FollowSetupDialog(vm: MainViewModel, probe: FollowProbe) {
    var kind by remember { mutableStateOf(Kind.VIDEO) }
    var level by remember { mutableStateOf(QualityLevel.HIGH) }
    var auto by remember { mutableStateOf(true) }
    var daysText by remember { mutableStateOf("1") }
    var latestText by remember { mutableStateOf("0") }
    var duplicates by remember { mutableStateOf(false) }
    val days = FollowInterval.parse(daysText)
    val latest = latestText.toIntOrNull()?.coerceIn(0, probe.entries.size)
    AlertDialog(
        onDismissRequest = vm::dismissFollowSetup,
        title = { Text(probe.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "What's there now is marked as seen. Only new things are downloaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (probe.engine == FollowEngine.YTDLP) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietChip(kind == Kind.VIDEO, { kind = Kind.VIDEO }, "Video")
                        QuietChip(kind == Kind.AUDIO, { kind = Kind.AUDIO }, "Audio")
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QualityLevel.entries.forEach { QuietChip(level == it, { level = it }, it.label) }
                    }
                } else {
                    Text("Photos and videos of the page.", style = MaterialTheme.typography.bodyMedium)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Download new ones", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = auto, onCheckedChange = { auto = it })
                }
                OutlinedTextField(
                    value = daysText,
                    onValueChange = { daysText = it },
                    label = { Text("Check every (days)") },
                    supportingText = { Text(if (days == null) "Type a number." else "Every ${FollowInterval.label(days)}.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = latestText,
                    onValueChange = { latestText = it.filter(Char::isDigit).take(3) },
                    label = { Text("Also download the latest") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if ((latest ?: 0) > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = duplicates, onCheckedChange = { duplicates = it })
                        Text("Download all, even ones I already have", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { vm.confirmFollow(kind, level, auto, days ?: FollowInterval.DEFAULT_DAYS, latest ?: 0, duplicates) },
                enabled = days != null,
            ) { Text("Follow") }
        },
        dismissButton = { TextButton(onClick = vm::dismissFollowSetup) { Text("Cancel") } },
    )
}
