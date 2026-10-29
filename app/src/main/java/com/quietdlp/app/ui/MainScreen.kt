package com.quietdlp.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.quietdlp.app.Links
import com.quietdlp.app.LoginActivity
import com.quietdlp.app.LoginPrompt
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.MediaInfo
import com.quietdlp.app.Sharing
import com.quietdlp.app.data.AudioOptions
import com.quietdlp.app.data.ConvertTarget
import com.quietdlp.app.data.ConvertTargets
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.DownloadMode
import com.quietdlp.app.data.HistoryFilter
import com.quietdlp.app.data.Layouts
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.SiteRules
import com.quietdlp.app.data.State
import com.quietdlp.app.data.resolutionName
import com.quietdlp.app.download.DownloadRepository
import com.quietdlp.app.download.UrlUtils
import com.quietdlp.app.update.AppUpdater

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val downloads by DownloadRepository.items.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val visible = HistoryFilter.filter(downloads, vm.searchQuery)
    val selecting = vm.selected.isNotEmpty()
    BackHandler(enabled = selecting && !showSettings) { vm.clearSelection() }

    // Al volver de la pantalla de inicio de sesión, refrescar qué cuentas hay guardadas.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshAccounts() }

    // Permisos: notificaciones (Android 13+) y almacenamiento (solo Android 9 o anterior).
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }
    LaunchedEffect(Unit) {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            if (Build.VERSION.SDK_INT <= 28) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }.filter { ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = { MainTopBar(vm) { showSettings = true } },
        ) { pad ->
            BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
                if (Layouts.twoColumns(maxWidth.value.toInt())) {
                    // Pantalla ancha (horizontal, tableta, plegable): a la izquierda el enlace, a la derecha las descargas.
                    Row(Modifier.fillMaxSize()) {
                        LazyColumn(
                            Modifier.weight(0.42f).fillMaxHeight(),
                            contentPadding = PaddingValues(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) { inputSection(vm) }
                        LazyColumn(
                            Modifier.weight(0.58f).fillMaxHeight(),
                            contentPadding = PaddingValues(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) { historySection(vm, downloads, visible, selecting) }
                    }
                } else {
                    // Una columna, centrada y con un ancho máximo para que no se estire.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(
                            Modifier.widthIn(max = Layouts.MAX_SINGLE_COLUMN_DP.dp).fillMaxWidth().fillMaxHeight(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            inputSection(vm)
                            historySection(vm, downloads, visible, selecting)
                        }
                    }
                }
            }
        }

        if (showSettings) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SettingsScreen(vm) { showSettings = false }
            }
        }
    }

    if (vm.showUpdateNotice) UpdateNoticeDialog(vm)
    vm.loginPrompt?.let { LoginPromptDialog(vm, it) }
    vm.duplicatePrompt?.let { DuplicateDialog(vm) }
    if (vm.showAppUpdate) vm.appUpdate?.let { AppUpdateDialog(vm, it) }
}

/** Parte de arriba: estado del motor, enlace copiado, cuadro del enlace, análisis y tarjeta de apoyo. */
private fun LazyListScope.inputSection(vm: MainViewModel) {
    item { EngineBanner(vm) }
    vm.clipOffer?.let { link -> item { ClipCard(vm, link) } }
    item { UrlCard(vm) }
    vm.info?.let { item { InfoCard(vm, it) } }
    vm.error?.let {
        item { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
    if (vm.supportCard && Links.DONATE_URL.isNotBlank()) item { SupportCard() }
}

/** Parte de abajo: título (o barra de selección), búsqueda y lista de descargas. */
private fun LazyListScope.historySection(
    vm: MainViewModel,
    downloads: List<DownloadItem>,
    visible: List<DownloadItem>,
    selecting: Boolean,
) {
    item {
        if (selecting) {
            SelectionBar(vm, visible)
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Downloads", style = MaterialTheme.typography.titleMedium)
                    if (downloads.isNotEmpty()) StatusPill("${downloads.size}", Tone.NEUTRAL)
                }
                if (downloads.any { !it.isActive }) {
                    TextButton(onClick = vm::clearFinished) { Text("Clear list") }
                }
            }
        }
    }
    if (downloads.size >= 5 || vm.searchQuery.isNotEmpty()) item {
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search history") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = {
                if (vm.searchQuery.isNotEmpty()) TextButton(onClick = { vm.searchQuery = "" }) { Text("Clear") }
            },
        )
    }
    if (downloads.isEmpty()) item { EmptyState() }
    else if (visible.isEmpty()) item {
        Text("No matches.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(visible, key = { it.id }) { DownloadRow(it, vm, selecting, it.id in vm.selected) }
}

@Composable
private fun MainTopBar(vm: MainViewModel, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuietLogo(32.dp)
        Text("QuietDLP", style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Spacer(Modifier.weight(1f))
        ModeSwitch(vm.advanced, vm::changeAdvanced)
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QuietLogo(72.dp)
        Text("Nothing here yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Paste a link, or use Share and pick QuietDLP.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tarjeta de apoyo entre el enlace y las descargas: un guiño, con el enlace a la página de apoyo. */
@Composable
private fun SupportCard() {
    val ctx = LocalContext.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
    ) {
        Row(
            Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Buy me a kebab", style = MaterialTheme.typography.titleSmall)
                Text(
                    "QuietDLP is free. A kebab is \$5.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fun open(url: String) = runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { open(Links.DONATE_URL) }) { Text("Ko-fi") }
                if (Links.PAYPAL_URL.isNotBlank()) OutlinedButton(onClick = { open(Links.PAYPAL_URL) }) { Text("PayPal") }
            }
        }
    }
}

/** Barra que sustituye al título de "Downloads" cuando hay descargas seleccionadas. */
@Composable
private fun SelectionBar(vm: MainViewModel, visible: List<DownloadItem>) {
    val ctx = LocalContext.current
    val chosen = visible.filter { it.id in vm.selected }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Remove ${chosen.size} ${if (chosen.size == 1) "item" else "items"}") },
            text = { Text("Remove them from the list only, or delete the files too.") },
            confirmButton = {
                TextButton(onClick = { askDelete = false; vm.deleteMany(chosen, deleteFiles = false) }) { Text("List only") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askDelete = false }) { Text("Cancel") }
                    TextButton(onClick = { askDelete = false; vm.deleteMany(chosen, deleteFiles = true) }) { Text("Delete files too") }
                }
            },
        )
    }
    val uris = chosen.filter { it.state == State.DONE }.flatMap { it.savedUris }
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("${chosen.size} selected", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = { vm.selectAll(visible.filter { !it.isActive }.map { it.id }) }) { Text("All") }
        if (uris.isNotEmpty()) {
            IconButton(onClick = {
                runCatching { ctx.startActivity(Sharing.chooser(uris.map(Uri::parse), null)) }
            }) { Icon(Icons.Default.Share, contentDescription = "Share") }
        }
        IconButton(onClick = { askDelete = true }, enabled = chosen.isNotEmpty()) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
        TextButton(onClick = vm::clearSelection) { Text("Cancel") }
    }
}

/** Hay un enlace copiado: se ofrece descargarlo con el modo por defecto o elegir opciones. */
@Composable
private fun ClipCard(vm: MainViewModel, link: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Copied link", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                // Sin la parte tras "?": puede llevar claves personales.
                UrlUtils.shorten(link.substringBefore('?').substringBefore('#')),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.canQuickDownload(link)) {
                    Button(onClick = vm::clipDownload) { Text("Download") }
                    OutlinedButton(onClick = vm::clipOptions) { Text("Options") }
                } else {
                    Button(onClick = vm::clipOptions) { Text("Options") }
                }
                TextButton(onClick = vm::dismissClip) { Text("Ignore") }
            }
        }
    }
}

@Composable
private fun DuplicateDialog(vm: MainViewModel) {
    val p = vm.duplicatePrompt ?: return
    AlertDialog(
        onDismissRequest = vm::dismissDuplicate,
        title = { Text("Already downloaded") },
        text = { Text(if (p.active) "This link is already downloading." else "Download it again?") },
        confirmButton = { TextButton(onClick = vm::confirmDuplicate) { Text("Download") } },
        dismissButton = { TextButton(onClick = vm::dismissDuplicate) { Text("Cancel") } },
    )
}

/** Aviso de versión nueva de la propia app: descarga, verifica y abre el instalador de Android. */
@Composable
private fun AppUpdateDialog(vm: MainViewModel, release: AppUpdater.Release) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { if (!vm.appUpdateBusy) vm.dismissAppUpdate() },
        title = { Text("New version available") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Version ${release.version}", style = MaterialTheme.typography.titleSmall)
                if (release.notes.isNotBlank()) {
                    Text(release.notes.take(600), style = MaterialTheme.typography.bodyMedium)
                }
                if (vm.appUpdateProgress >= 0) {
                    LinearProgressIndicator(
                        progress = { vm.appUpdateProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Downloading… ${vm.appUpdateProgress}%", style = MaterialTheme.typography.bodySmall)
                }
                vm.appUpdateMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = vm::installAppUpdate, enabled = !vm.appUpdateBusy) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = vm::dismissAppUpdate, enabled = !vm.appUpdateBusy) { Text("Later") }
        },
    )
}

/** Explica por qué hace falta iniciar sesión y abre la página de login de esa web. */
@Composable
private fun LoginPromptDialog(vm: MainViewModel, prompt: LoginPrompt) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = vm::dismissLoginPrompt,
        title = { Text("Sign-in required") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "To download this video, ${prompt.host} requires signing in. QuietDLP doesn't store your " +
                        "password, only the session, and you can sign out any time in Settings › Sessions.",
                )
                Text(
                    "If the site offers to save your login, choose “Not now”.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.acceptLoginPrompt()
                ctx.startActivity(
                    Intent(ctx, LoginActivity::class.java)
                        .putExtra(LoginActivity.EXTRA_HOST, prompt.host),
                )
            }) { Text("Continue") }
        },
        dismissButton = {
            TextButton(onClick = vm::dismissLoginPrompt) { Text("Not now") }
        },
    )
}

/** Aviso que se muestra una sola vez: la actualización automática viene activada por defecto. */
@Composable
private fun UpdateNoticeDialog(vm: MainViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dismissUpdateNotice(disable = false) },
        title = { Text("Automatic updates are on") },
        text = {
            Text("QuietDLP updates yt-dlp by itself, at most once a day. You can turn this off in Settings.")
        },
        confirmButton = {
            TextButton(onClick = { vm.dismissUpdateNotice(disable = false) }) { Text("Got it") }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissUpdateNotice(disable = true) }) { Text("Turn off") }
        },
    )
}

@Composable
private fun EngineBanner(vm: MainViewModel) {
    when {
        vm.engineError != null -> Text(
            "Couldn't start: ${vm.engineError}",
            color = MaterialTheme.colorScheme.error,
        )
        !vm.engineReady -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                "  Getting ready…",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}


@Composable
private fun UrlCard(vm: MainViewModel) {
    val clipboard = LocalClipboardManager.current
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Save a video, song or photos", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = vm.url,
                onValueChange = { vm.url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a link") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    TextButton(onClick = { clipboard.getText()?.text?.let { vm.url = it } }) { Text("Paste") }
                },
            )
            Button(
                onClick = { vm.analyze(vm.url) },
                enabled = vm.url.isNotBlank() && !vm.analyzing,
                modifier = Modifier.fillMaxWidth().height(50.dp),
            ) {
                if (vm.analyzing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Text("  Analyzing…")
                } else {
                    Text("Analyze")
                }
            }
        }
    }
}

/** Formatos de audio en grupos, de la mejor calidad a la más pequeña. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioFormatPicker(vm: MainViewModel) {
    Text("Format", style = MaterialTheme.typography.labelLarge)
    AudioOptions.byTier().forEach { (tier, options) ->
        Text(tier.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                QuietChip(vm.audioFormat == o.format && vm.audioBitrate == o.bitrate, { vm.chooseAudio(o) }, o.label)
            }
        }
    }
    Text(
        AudioOptions.find(vm.audioFormat, vm.audioBitrate).hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(vm: MainViewModel, info: MediaInfo) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            if (info.thumbnail != null) {
                SubcomposeAsyncImage(
                    model = info.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    loading = { ThumbPlaceholder() },
                    error = { ThumbPlaceholder() },
                    onError = { android.util.Log.w("QuietDLP", "thumbnail failed: ${it.result.throwable}") },
                )
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    val meta = listOfNotNull(info.uploader, formatDuration(info.durationSec)).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (info.photoCount > 0) {
                    StatusPill("Photos", Tone.ACTIVE)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietChip(vm.kind == Kind.VIDEO, { vm.kind = Kind.VIDEO }, "Video", enabled = info.heights.isNotEmpty(),
                        )
                        QuietChip(vm.kind == Kind.AUDIO, { vm.kind = Kind.AUDIO }, "Audio only")
                    }
                    // Modo simple: solo vídeo o audio, con la mejor calidad. Los detalles están en el modo avanzado.
                    if (vm.advanced && vm.kind == Kind.VIDEO) {
                        Text("Quality", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuietChip(vm.height == 0, { vm.height = 0 }, "Best")
                            info.heights.forEach { h ->
                                QuietChip(vm.height == h, { vm.height = h }, resolutionName(h))
                            }
                        }
                    } else if (vm.advanced) {
                        AudioFormatPicker(vm)
                    }
                }

                Button(onClick = vm::download, modifier = Modifier.fillMaxWidth().height(50.dp)) { Text("Download") }
                if (info.photoCount == 0) {
                    val ctx = LocalContext.current
                    val host = SiteRules.normalizeHost(info.url)
                    if (host != null) {
                        TextButton(
                            onClick = {
                                vm.rememberForSite(info)?.let {
                                    Toast.makeText(ctx, "Saved for $it", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text("Always use this for $host") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DownloadRow(item: DownloadItem, vm: MainViewModel, selecting: Boolean, isSelected: Boolean) {
    val ctx = LocalContext.current
    var askDelete by rememberSaveable { mutableStateOf(false) }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text("Remove from history") },
            text = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { askDelete = false; vm.delete(item, deleteFiles = false) }) { Text("List only") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askDelete = false }) { Text("Cancel") }
                    TextButton(onClick = { askDelete = false; vm.delete(item, deleteFiles = true) }) { Text("Delete file too") }
                }
            },
        )
    }
    var askConvert by rememberSaveable { mutableStateOf(false) }
    if (askConvert) {
        ConvertDialog(item, onDismiss = { askConvert = false }) { target ->
            askConvert = false
            vm.convert(item, target)
        }
    }

    Card(
        Modifier.fillMaxWidth().combinedClickable(
            onClick = { if (selecting && !item.isActive) vm.toggleSelect(item.id) },
            onLongClick = { if (!item.isActive) vm.toggleSelect(item.id) },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FileThumb(item, 84.dp)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(statusLabel(item), statusTone(item))
                        Text(
                            item.quality.label(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (item.state == State.FAILED && !item.message.isNullOrBlank()) {
                        Text(item.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }

                    when (item.state) {
                        State.DOWNLOADING -> LinearProgressIndicator(
                            progress = { (item.progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            strokeCap = StrokeCap.Round,
                        )
                        State.PREPARING, State.PROCESSING -> LinearProgressIndicator(
                            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            strokeCap = StrokeCap.Round,
                        )
                        else -> {}
                    }

                    if (!selecting) Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        when {
                            item.isActive -> TextButton(onClick = { vm.cancel(item.id) }) { Text("Cancel") }
                            item.state == State.DONE -> {
                                if (!item.isConversion) LinkMenu(item.url)
                                if (item.savedUris.isNotEmpty()) {
                                    IconButton(onClick = {
                                        runCatching { ctx.startActivity(Sharing.chooser(item.savedUris.map(Uri::parse), item.mime)) }
                                    }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                                    IconButton(onClick = { openFile(ctx, item) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = "Open")
                                    }
                                    if (ConvertTargets.canConvert(item.mime)) {
                                        TextButton(onClick = { askConvert = true }) { Text("Convert") }
                                    }
                                    if (item.photoVideo) {
                                        TextButton(onClick = { vm.makeVideo(item) }) { Text("Make video") }
                                    }
                                }
                                IconButton(onClick = {
                                    if (item.savedUris.isNotEmpty()) askDelete = true else vm.remove(item.id)
                                }) { Icon(Icons.Default.Delete, contentDescription = "Remove from list") }
                            }
                            else -> {
                                if (!item.isConversion) LinkMenu(item.url)
                                if (item.state == State.FAILED && item.loginHost != null) {
                                    Button(onClick = {
                                        vm.promptLogin(
                                            LoginPrompt(
                                                host = item.loginHost, url = item.url, quality = item.quality,
                                                title = item.title, thumbnail = item.thumbnail, oldItemId = item.id,
                                            ),
                                        )
                                    }) { Text("Sign in") }
                                }
                                if (!item.isConversion) OutlinedButton(onClick = { vm.retry(item) }) { Text("Retry") }
                                IconButton(onClick = { vm.remove(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove from list")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Menú de tres puntos de cada descarga: abrir el enlace original (para volver a la publicación) o copiarlo. */
@Composable
private fun LinkMenu(link: String) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Open original link") },
                onClick = {
                    open = false
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(ctx, "No app can open this link", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            DropdownMenuItem(
                text = { Text("Copy link") },
                onClick = {
                    open = false
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("link", link))
                    Toast.makeText(ctx, "Link copied", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/** Ventana para elegir a qué formato convertir un archivo ya descargado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConvertDialog(item: DownloadItem, onDismiss: () -> Unit, onConvert: (ConvertTarget) -> Unit) {
    var picked by remember { mutableStateOf<ConvertTarget?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert to") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ConvertTargets.sectionsFor(item.mime).forEach { section ->
                    Text(section.title, style = MaterialTheme.typography.labelMedium, color = muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        section.targets.forEach { t -> QuietChip(picked == t, { picked = t }, t.label) }
                    }
                }
                picked?.let { t ->
                    Text(t.hint, style = MaterialTheme.typography.bodySmall, color = muted)
                    ConvertTargets.warning(t, item.mime)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picked?.let(onConvert) }, enabled = picked != null) { Text("Convert") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun statusLabel(item: DownloadItem): String = when (item.state) {
    State.QUEUED -> item.message ?: "Queued"
    State.PREPARING -> "Getting ready"
    State.DOWNLOADING -> buildString {
        append("${item.progress.toInt()}%")
        if (item.etaSeconds > 0) append(" · ${formatDuration(item.etaSeconds.toInt())} left")
    }
    State.PROCESSING -> (item.message ?: "Processing").removeSuffix("…")
    State.DONE -> "Done"
    State.FAILED -> "Failed"
    State.CANCELED -> "Canceled"
}

private fun formatDuration(totalSeconds: Int): String? {
    if (totalSeconds <= 0) return null
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun openFile(ctx: Context, item: DownloadItem) {
    val uri = Uri.parse(item.savedUri ?: return)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, item.mime ?: "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(ctx, "Couldn't open the file", Toast.LENGTH_SHORT).show()
    }
}

