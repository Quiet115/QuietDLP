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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Badge
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.quietdlp.app.data.CoverOptions
import com.quietdlp.app.data.SponsorChoice
import com.quietdlp.app.download.Errors
import com.quietdlp.app.data.DownloadItem
import com.quietdlp.app.data.DownloadMode
import com.quietdlp.app.data.HistoryFilter
import com.quietdlp.app.data.HistoryGroups
import com.quietdlp.app.data.HistoryRow
import com.quietdlp.app.data.Layouts
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.QualityLevel
import com.quietdlp.app.data.SiteRules
import com.quietdlp.app.data.Slideshow
import com.quietdlp.app.data.State
import com.quietdlp.app.data.resolutionName
import com.quietdlp.app.download.DownloadRepository
import com.quietdlp.app.download.FollowStore
import com.quietdlp.app.download.UrlUtils
import com.quietdlp.app.update.AppUpdater
import com.quietdlp.app.R
import com.quietdlp.app.Strings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel) {
    val ctx = LocalContext.current
    val downloads by DownloadRepository.items.collectAsStateWithLifecycle()
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var tab by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    val followPages by FollowStore.pages.collectAsStateWithLifecycle()
    val withNew = followPagesWithNew(followPages, downloads)
    val visible = HistoryFilter.filter(downloads, vm.searchQuery)
    val rows = HistoryGroups.rows(visible)
    // El grupo abierto en pantalla completa (se busca en la lista cada vez: sus archivos van cambiando).
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val openRow = openGroup?.let { id -> HistoryGroups.rows(downloads).filterIsInstance<HistoryRow.Group>().firstOrNull { it.id == id } }
    val selecting = vm.selected.isNotEmpty()
    BackHandler(enabled = selecting && !showSettings && openRow == null) { vm.clearSelection() }

    // Al volver de la pantalla de inicio de sesión, refrescar qué cuentas hay guardadas.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        vm.refreshAccounts()
        vm.followAppOpened()
    }

    // "Download again" analiza el enlace arriba del todo: la lista vuelve al principio para que se vea.
    val listState = rememberLazyListState()
    LaunchedEffect(vm.scrollTopRequest) {
        if (vm.scrollTopRequest > 0) {
            openGroup = null
            listState.animateScrollToItem(0)
        }
    }

    val notice = rememberUpdateNotice(vm.updateBannerStartedAt, vm.appUpdateBusy, vm::closeUpdateBanner)
    Column(Modifier.fillMaxSize()) {
    Box(
        Modifier.weight(1f).fillMaxWidth().let {
            // Con el aviso a la vista, ya cubre la barra de navegación: lo de arriba no debe reservarla otra vez.
            if (notice.visible) it.consumeWindowInsets(WindowInsets.navigationBars) else it
        },
    ) {
        Scaffold(
            topBar = {
                Column {
                    MainTopBar(vm) { showSettings = true }
                    TabRow(selectedTabIndex = tab) {
                        Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(Strings.get(R.string.main_screen_downloads)) })
                        Tab(
                            selected = tab == 1,
                            onClick = { tab = 1 },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(Strings.get(R.string.main_screen_following))
                                    if (withNew > 0) Badge { Text("$withNew") }
                                }
                            },
                        )
                    }
                }
            },
        ) { pad ->
            if (tab == 1) {
                Box(Modifier.padding(pad).fillMaxSize()) {
                    FollowingTab(vm, followPages, downloads) { gid ->
                        tab = 0
                        openGroup = gid
                        vm.markFollowSeen(gid.removePrefix("follow-"))
                    }
                }
            } else BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
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
                        ) { historySection(vm, rows, visible, selecting) { openGroup = it; seenFollow(vm, it) } }
                    }
                } else {
                    // Una columna, centrada y con un ancho máximo para que no se estire.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(
                            Modifier.widthIn(max = Layouts.MAX_SINGLE_COLUMN_DP.dp).fillMaxWidth().fillMaxHeight(),
                            state = listState,
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            inputSection(vm)
                            historySection(vm, rows, visible, selecting) { openGroup = it; seenFollow(vm, it) }
                        }
                    }
                }
            }
        }

        if (openRow != null && !showSettings) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                GroupScreen(openRow, vm) { openGroup = null }
            }
        }

        if (showSettings) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SettingsScreen(vm) { showSettings = false }
            }
        }

        if (!vm.permissionsWelcomeSeen) PermissionsWelcome(vm::finishPermissionsWelcome)
    }
    // Aviso de versión nueva, abajo y por encima de todas las pantallas: empuja el contenido hacia arriba en vez de taparlo.
    UpdateNoticeCard(vm, notice)
    }

    if (vm.showUpdateNotice && vm.permissionsWelcomeSeen) UpdateNoticeDialog(vm)
    vm.loginPrompt?.let { LoginPromptDialog(vm, it) }
    vm.duplicatePrompt?.let { DuplicateDialog(vm) }
    vm.selectionPrompt?.let { SelectionDialog(vm, it) }
    vm.dataPrompt?.let { DataPromptDialog(vm) }
    if (vm.batteryPrompt) BatteryPromptDialog(vm)
}

/** Abrir el grupo de una página seguida es verlo: deja de ser nuevo. */
private fun seenFollow(vm: MainViewModel, groupId: String) {
    if (groupId.startsWith("follow-")) vm.markFollowSeen(groupId.removePrefix("follow-"))
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
    rows: List<HistoryRow>,
    visible: List<DownloadItem>,
    selecting: Boolean,
    onOpenGroup: (String) -> Unit,
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
                    Text(Strings.get(R.string.main_screen_downloads), style = MaterialTheme.typography.titleMedium)
                    if (rows.isNotEmpty()) StatusPill("${rows.size}", Tone.NEUTRAL)
                }
                if (visible.any { !it.isActive }) {
                    TextButton(onClick = vm::clearFinished) { Text(Strings.get(R.string.main_screen_clear_list)) }
                }
            }
        }
    }
    if (rows.size >= 5 || vm.searchQuery.isNotEmpty()) item {
        OutlinedTextField(
            value = vm.searchQuery,
            onValueChange = { vm.searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(Strings.get(R.string.main_screen_search_history)) },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            trailingIcon = {
                if (vm.searchQuery.isNotEmpty()) TextButton(onClick = { vm.searchQuery = "" }) { Text(Strings.get(R.string.settings_screen_clear)) }
            },
        )
    }
    if (rows.isEmpty() && vm.searchQuery.isEmpty()) item { EmptyState() }
    else if (rows.isEmpty()) item {
        Text(Strings.get(R.string.main_screen_no_matches), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(rows, key = { it.key }) { row ->
        when (row) {
            is HistoryRow.Single -> DownloadRow(row.item, vm, selecting, row.item.id in vm.selected)
            is HistoryRow.Group -> {
                val ids = row.items.map { it.id }
                GroupCard(
                    row, vm, selecting,
                    selected = ids.all { it in vm.selected },
                    onOpen = { onOpenGroup(row.id) },
                    onToggleSelect = {
                        // Elegir un grupo es elegir todos sus archivos (o quitarlos todos si ya estaban).
                        if (ids.all { it in vm.selected }) ids.forEach { vm.toggleSelect(it) }
                        else ids.filter { it !in vm.selected }.forEach { vm.toggleSelect(it) }
                    },
                )
            }
        }
    }
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
        Text(Strings.get(R.string.settings_screen_quietdlp_2), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        Spacer(Modifier.weight(1f))
        ModeSwitch(vm.advanced, vm::changeAdvanced)
        IconButton(onClick = onSettings) {
            Icon(Icons.Default.Settings, contentDescription = Strings.get(R.string.settings_screen_settings))
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
        Text(Strings.get(R.string.main_screen_nothing_here_yet), style = MaterialTheme.typography.titleMedium)
        Text(
            Strings.get(R.string.main_screen_paste_a_link_or_use),
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
                Text(Strings.get(R.string.main_screen_buy_me_a_kebab), style = MaterialTheme.typography.titleSmall)
                Text(
                    Strings.get(R.string.main_screen_quietdlp_is_free_a_kebab),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            fun open(url: String) = runCatching {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { open(Links.DONATE_URL) }) { Text(Strings.get(R.string.main_screen_ko_fi)) }
                if (Links.PAYPAL_URL.isNotBlank()) OutlinedButton(onClick = { open(Links.PAYPAL_URL) }) { Text(Strings.get(R.string.main_screen_paypal)) }
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
            title = { Text(if (chosen.size == 1) Strings.get(R.string.remove_one_item) else Strings.get(R.string.remove_many_items, chosen.size)) },
            text = { Text(Strings.get(R.string.group_ui_remove_them_from_the_list)) },
            confirmButton = {
                TextButton(onClick = { askDelete = false; vm.deleteMany(chosen, deleteFiles = false) }) { Text(Strings.get(R.string.group_ui_list_only)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askDelete = false }) { Text(Strings.get(R.string.settings_screen_cancel)) }
                    TextButton(onClick = { askDelete = false; vm.deleteMany(chosen, deleteFiles = true) }) { Text(Strings.get(R.string.group_ui_delete_files_too)) }
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
        TextButton(onClick = { vm.selectAll(visible.filter { !it.isActive }.map { it.id }) }) { Text(Strings.get(R.string.main_screen_all)) }
        if (uris.isNotEmpty()) {
            IconButton(onClick = {
                runCatching { ctx.startActivity(Sharing.chooser(uris.map(Uri::parse), null)) }
            }) { Icon(Icons.Default.Share, contentDescription = Strings.get(R.string.group_ui_share)) }
        }
        IconButton(onClick = { askDelete = true }, enabled = chosen.isNotEmpty()) {
            Icon(Icons.Default.Delete, contentDescription = Strings.get(R.string.settings_screen_remove))
        }
        TextButton(onClick = vm::clearSelection) { Text(Strings.get(R.string.settings_screen_cancel)) }
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
            Text(Strings.get(R.string.main_screen_copied_link), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                    Button(onClick = vm::clipDownload) { Text(Strings.get(R.string.settings_screen_download)) }
                    OutlinedButton(onClick = vm::clipOptions) { Text(Strings.get(R.string.main_screen_options)) }
                } else {
                    Button(onClick = vm::clipOptions) { Text(Strings.get(R.string.main_screen_options)) }
                }
                TextButton(onClick = vm::dismissClip) { Text(Strings.get(R.string.main_screen_ignore)) }
            }
        }
    }
}

@Composable
private fun DuplicateDialog(vm: MainViewModel) {
    val p = vm.duplicatePrompt ?: return
    AlertDialog(
        onDismissRequest = vm::dismissDuplicate,
        title = { Text(Strings.get(R.string.main_screen_already_downloaded)) },
        text = { Text(if (p.active) Strings.get(R.string.main_screen_this_link_is_already_downloading) else Strings.get(R.string.main_screen_download_it_again)) },
        confirmButton = { TextButton(onClick = vm::confirmDuplicate) { Text(Strings.get(R.string.settings_screen_download)) } },
        dismissButton = { TextButton(onClick = vm::dismissDuplicate) { Text(Strings.get(R.string.settings_screen_cancel)) } },
    )
}

/** Explica por qué hace falta iniciar sesión y abre la página de login de esa web. */
@Composable
private fun LoginPromptDialog(vm: MainViewModel, prompt: LoginPrompt) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = vm::dismissLoginPrompt,
        title = { Text(Strings.get(R.string.main_screen_sign_in_required)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    Strings.get(R.string.main_screen_to_download_this_video_requires, prompt.host) +
                        Strings.get(R.string.main_screen_password_only_the_session_and),
                )
                Text(
                    Strings.get(R.string.main_screen_if_the_site_offers_to),
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
            }) { Text(Strings.get(R.string.main_screen_continue)) }
        },
        dismissButton = {
            TextButton(onClick = vm::dismissLoginPrompt) { Text(Strings.get(R.string.group_ui_not_now)) }
        },
    )
}

/** Aviso que se muestra una sola vez: la actualización automática viene activada por defecto. */
@Composable
private fun UpdateNoticeDialog(vm: MainViewModel) {
    AlertDialog(
        onDismissRequest = { vm.dismissUpdateNotice(disable = false) },
        title = { Text(Strings.get(R.string.main_screen_automatic_updates_are_on)) },
        text = {
            Text(Strings.get(R.string.main_screen_quietdlp_updates_yt_dlp_by))
        },
        confirmButton = {
            TextButton(onClick = { vm.dismissUpdateNotice(disable = false) }) { Text(Strings.get(R.string.main_screen_got_it)) }
        },
        dismissButton = {
            TextButton(onClick = { vm.dismissUpdateNotice(disable = true) }) { Text(Strings.get(R.string.main_screen_turn_off)) }
        },
    )
}

@Composable
private fun EngineBanner(vm: MainViewModel) {
    when {
        vm.engineError != null -> Text(
            Strings.get(R.string.main_screen_couldn_t_start, vm.engineError),
            color = MaterialTheme.colorScheme.error,
        )
        !vm.engineReady -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(
                Strings.get(R.string.main_screen_getting_ready),
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
            Text(Strings.get(R.string.settings_screen_save_a_video_song_or), style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = vm.url,
                onValueChange = { vm.url = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(Strings.get(R.string.main_screen_paste_a_link)) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    TextButton(onClick = { clipboard.getText()?.text?.let { vm.url = it } }) { Text(Strings.get(R.string.main_screen_paste)) }
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
                    Text(Strings.get(R.string.main_screen_analyze))
                }
            }
        }
    }
}

/** Qué significa cada calidad del modo simple, en una línea. */
private fun levelHint(kind: Kind, level: QualityLevel): String = when (kind) {
    Kind.AUDIO -> when (level) {
        QualityLevel.HIGH -> Strings.get(R.string.main_screen_m4a_the_best_quality)
        QualityLevel.MEDIUM -> Strings.get(R.string.main_screen_mp3_192k)
        QualityLevel.LOW -> Strings.get(R.string.main_screen_mp3_128k_the_smallest)
    }
    else -> when (level) {
        QualityLevel.HIGH -> Strings.get(R.string.main_screen_the_best_quality_the_site)
        QualityLevel.MEDIUM -> "720p."
        QualityLevel.LOW -> Strings.get(R.string.main_screen_480p_the_smallest)
    }
}

/** Formatos de audio en grupos, de la mejor calidad a la más pequeña. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AudioFormatPicker(vm: MainViewModel) {
    Text(Strings.get(R.string.main_screen_format), style = MaterialTheme.typography.labelLarge)
    AudioOptions.byTier().forEach { (tier, options) ->
        Text(Strings.tr(tier.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { o ->
                QuietChip(vm.audioFormat == o.format && vm.audioBitrate == o.bitrate, { vm.chooseAudio(o) }, Strings.tr(o.label))
            }
        }
    }
    Text(
        Strings.tr(AudioOptions.find(vm.audioFormat, vm.audioBitrate).hint),
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
                    val meta = listOfNotNull(
                        info.uploader,
                        formatDuration(info.durationSec),
                        if (info.playlistCount > 0) "${info.playlistCount} videos" else null,
                    ).joinToString(" · ")
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (info.profile != null) {
                    ProfileSection(vm, info.profile)
                } else if (info.photoCount > 0) {
                    StatusPill(Strings.get(R.string.main_screen_photos), Tone.ACTIVE)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuietChip(vm.kind == Kind.VIDEO, { vm.chooseKind(Kind.VIDEO) }, Strings.get(R.string.settings_screen_video), enabled = info.heights.isNotEmpty())
                        QuietChip(vm.kind == Kind.AUDIO, { vm.chooseKind(Kind.AUDIO) }, Strings.get(R.string.group_ui_audio))
                    }
                    if (!vm.advanced) {
                        // Modo simple: vídeo o audio y una calidad (alta, media o baja). El detalle está en el modo avanzado.
                        Text(Strings.get(R.string.main_screen_quality), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QualityLevel.entries.forEach { QuietChip(vm.level == it, { vm.chooseLevel(it) }, Strings.tr(it.label)) }
                        }
                        Text(
                            levelHint(vm.kind, vm.level),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (vm.kind == Kind.VIDEO) {
                        Text(Strings.get(R.string.main_screen_quality), style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QuietChip(vm.height == 0, { vm.height = 0 }, Strings.get(R.string.main_screen_best))
                            info.heights.forEach { h ->
                                QuietChip(vm.height == h, { vm.height = h }, resolutionName(h))
                            }
                        }
                    } else {
                        AudioFormatPicker(vm)
                    }
                    // Recortar vale para vídeo y audio, pero no para listas, fotos ni directos.
                    if (vm.canTrim(info)) TrimSection(vm, info)
                    // La portada vale para vídeo y audio (menos WAV, que no puede llevar imagen).
                    if (CoverOptions.available(vm.kind, vm.audioFormat)) CoverSection(vm)
                    // Los subtítulos son de vídeo, y una lista (que trae varios vídeos) no los ofrece.
                    if (vm.kind == Kind.VIDEO && info.playlistCount == 0) SubtitlesSection(vm, info)
                    // Modo Avanzado: SponsorBlock (YouTube) y capítulos. Con un trozo no se ofrecen.
                    if (vm.canSponsor(info)) SponsorSection(vm)
                    if (vm.canChapters(info)) ChaptersSection(vm)
                    if (vm.advanced && vm.trimOn && (SponsorChoice.supports(info.url) || info.chapterCount > 0)) {
                        Text(
                            Strings.get(R.string.main_screen_sponsorblock_and_chapters_aren_t),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Button(onClick = vm::download, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text(if (info.playlistCount > 0 && vm.kind != Kind.PHOTO) Strings.get(R.string.main_screen_download_videos, info.playlistCount) else Strings.get(R.string.settings_screen_download))
                }
                if (info.photoCount == 0 && info.profile == null) {
                    val ctx = LocalContext.current
                    val host = SiteRules.normalizeHost(info.url)
                    if (host != null) {
                        TextButton(
                            onClick = {
                                vm.rememberForSite(info)?.let {
                                    Toast.makeText(ctx, Strings.get(R.string.main_screen_saved_for, it), Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) { Text(Strings.get(R.string.main_screen_always_use_this_for, host)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
internal fun DownloadRow(item: DownloadItem, vm: MainViewModel, selecting: Boolean, isSelected: Boolean) {
    val ctx = LocalContext.current
    var showFiles by rememberSaveable { mutableStateOf(false) }
    if (showFiles) FilesDialog(item, vm) { showFiles = false }
    var askDelete by rememberSaveable { mutableStateOf(false) }
    if (askDelete) {
        AlertDialog(
            onDismissRequest = { askDelete = false },
            title = { Text(Strings.get(R.string.main_screen_remove_from_history)) },
            text = { Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton(onClick = { askDelete = false; vm.delete(item, deleteFiles = false) }) { Text(Strings.get(R.string.group_ui_list_only)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askDelete = false }) { Text(Strings.get(R.string.settings_screen_cancel)) }
                    TextButton(onClick = { askDelete = false; vm.delete(item, deleteFiles = true) }) { Text(Strings.get(R.string.main_screen_delete_file_too)) }
                }
            },
        )
    }
    var askVideo by rememberSaveable { mutableStateOf(false) }
    if (askVideo) {
        MakeVideoDialog(vm.songSeconds, vm.rememberSong, onDismiss = { askVideo = false }) { seconds, remember ->
            askVideo = false
            vm.makeVideo(item, seconds, remember)
        }
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
                if (item.savedUris.size > 1) {
                    // Varias fotos o vídeos en una publicación: las miniaturas se apilan.
                    ThumbStack(item, (1..minOf(2, item.savedUris.size - 1)).map { item to it })
                } else {
                    FileThumb(item, 84.dp)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(statusLabel(item), statusTone(item))
                        if (item.fresh) StatusPill(Strings.get(R.string.group_ui_new), Tone.NEW)
                        Text(
                            Strings.tr(item.quality.label()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                    if (item.state == State.FAILED && !item.message.isNullOrBlank()) {
                        Text(Errors.localize(item.message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    } else if (item.state == State.DONE && !item.message.isNullOrBlank()) {
                        Text(Errors.localize(item.message), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

                    // Los botones pasan a otra línea si no caben (una publicación de fotos con canción trae muchos).
                    if (!selecting) FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                    ) {
                        when {
                            item.isActive -> TextButton(onClick = { vm.cancel(item.id) }) { Text(Strings.get(R.string.settings_screen_cancel)) }
                            item.state == State.DONE -> {
                                if (!item.isConversion) LinkMenu(item.url) { vm.downloadAgain(item) }
                                if (item.savedUris.isNotEmpty()) {
                                    IconButton(onClick = {
                                        runCatching { ctx.startActivity(Sharing.chooser(item.savedUris.map(Uri::parse), item.mime)) }
                                    }) { Icon(Icons.Default.Share, contentDescription = Strings.get(R.string.group_ui_share)) }
                                    IconButton(onClick = { openFile(ctx, item) }) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = Strings.get(R.string.group_ui_open))
                                    }
                                    if (item.savedUris.size == 1 && QuietRingtones.isAudio(item.mime)) {
                                        SendToRingtonesButton(item.savedUris[0], item.mime, item.title)
                                    }
                                    if (item.savedUris.size > 1) {
                                        TextButton(onClick = { showFiles = true }) { Text("${item.savedUris.size} files") }
                                    } else if (ConvertTargets.canConvert(item.mime)) {
                                        TextButton(onClick = { askConvert = true }) { Text(Strings.get(R.string.group_ui_convert)) }
                                    }
                                    if (item.photoVideo) {
                                        TextButton(onClick = { askVideo = true }) { Text(Strings.get(R.string.main_screen_make_video)) }
                                    }
                                }
                                IconButton(onClick = {
                                    if (item.savedUris.isNotEmpty()) askDelete = true else vm.remove(item.id)
                                }) { Icon(Icons.Default.Delete, contentDescription = Strings.get(R.string.main_screen_remove_from_list)) }
                            }
                            else -> {
                                if (!item.isConversion) LinkMenu(item.url) { vm.downloadAgain(item) }
                                if (item.state == State.FAILED && item.loginHost != null) {
                                    Button(onClick = {
                                        vm.promptLogin(
                                            LoginPrompt(
                                                host = item.loginHost, url = item.url, quality = item.quality,
                                                title = item.title, thumbnail = item.thumbnail, oldItemId = item.id,
                                            ),
                                        )
                                    }) { Text(Strings.get(R.string.settings_screen_sign_in)) }
                                }
                                if (item.state == State.FAILED && Errors.needsEngineUpdate(item.message)) {
                                    // Lo más probable es un yt-dlp viejo: se actualiza y se reintenta de una vez.
                                    Button(onClick = { vm.updateEngine { vm.retry(item) } }, enabled = !vm.updating) {
                                        Text(if (vm.updating) Strings.get(R.string.settings_screen_updating) else Strings.get(R.string.main_screen_update_engine))
                                    }
                                }
                                if (!item.isConversion) OutlinedButton(onClick = { vm.retry(item) }) { Text(Strings.get(R.string.main_screen_retry)) }
                                IconButton(onClick = { vm.remove(item.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = Strings.get(R.string.main_screen_remove_from_list))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Menú de tres puntos de cada descarga: volver a descargarla (eligiendo otra vez el formato), abrir el enlace
 * original (para volver a la publicación) o copiarlo.
 */
@Composable
private fun LinkMenu(link: String, onDownloadAgain: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, contentDescription = Strings.get(R.string.main_screen_more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(Strings.get(R.string.main_screen_download_again)) },
                onClick = {
                    open = false
                    onDownloadAgain()
                },
            )
            DropdownMenuItem(
                text = { Text(Strings.get(R.string.main_screen_open_original_link)) },
                onClick = {
                    open = false
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(ctx, Strings.get(R.string.main_screen_no_app_can_open_this), Toast.LENGTH_SHORT).show()
                    }
                },
            )
            DropdownMenuItem(
                text = { Text(Strings.get(R.string.settings_screen_copy_link)) },
                onClick = {
                    open = false
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("link", link))
                    Toast.makeText(ctx, Strings.get(R.string.main_screen_link_copied), Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

/** Ventana de "Make video": hasta cuándo suena la canción. Empieza con la última elección. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MakeVideoDialog(current: Int, rememberChoice: Boolean, onDismiss: () -> Unit, onMake: (Int, Boolean) -> Unit) {
    var picked by remember { mutableStateOf(current) }
    var remembered by remember { mutableStateOf(rememberChoice) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.get(R.string.main_screen_make_video)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Strings.get(R.string.main_screen_how_long_should_the_song), style = MaterialTheme.typography.labelMedium, color = muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Slideshow.SONG_LENGTHS.forEach { (seconds, label) ->
                        QuietChip(picked == seconds, { picked = seconds }, label)
                    }
                }
                Text(
                    Strings.get(R.string.main_screen_each_photo_shows_for_at),
                    style = MaterialTheme.typography.bodySmall, color = muted,
                )
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { remembered = !remembered },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = remembered, onCheckedChange = { remembered = it })
                    Text(Strings.get(R.string.settings_screen_remember_my_choice), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onMake(picked, remembered) }) { Text(Strings.get(R.string.main_screen_make_video)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.get(R.string.settings_screen_cancel)) } },
    )
}

/** Ventana para elegir a qué formato convertir un archivo ya descargado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConvertDialog(item: DownloadItem, onDismiss: () -> Unit, onConvert: (ConvertTarget) -> Unit) {
    var picked by remember { mutableStateOf<ConvertTarget?>(null) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.get(R.string.main_screen_convert_to)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                ConvertTargets.sectionsFor(item.mime).forEach { section ->
                    Text(Strings.tr(section.title), style = MaterialTheme.typography.labelMedium, color = muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        section.targets.forEach { t -> QuietChip(picked == t, { picked = t }, Strings.tr(t.label)) }
                    }
                }
                picked?.let { t ->
                    Text(Strings.tr(t.hint), style = MaterialTheme.typography.bodySmall, color = muted)
                    ConvertTargets.warning(t, item.mime)?.let { Strings.tr(it) }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { picked?.let(onConvert) }, enabled = picked != null) { Text(Strings.get(R.string.group_ui_convert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.get(R.string.settings_screen_cancel)) } },
    )
}

private fun statusLabel(item: DownloadItem): String = when (item.state) {
    State.QUEUED -> item.message?.let(Errors::localize) ?: Strings.get(R.string.main_screen_queued)
    State.PREPARING -> Strings.get(R.string.main_screen_getting_ready_2)
    State.DOWNLOADING -> buildString {
        append("${item.progress.toInt()}%")
        if (item.etaSeconds > 0) append(" · " + Strings.get(R.string.time_left, formatDuration(item.etaSeconds.toInt())))
    }
    State.PROCESSING -> (item.message?.let(Errors::localize) ?: Strings.get(R.string.main_screen_processing)).removeSuffix("…")
    State.DONE -> Strings.get(R.string.settings_screen_done)
    State.FAILED -> Strings.get(R.string.main_screen_failed)
    State.CANCELED -> Strings.get(R.string.main_screen_canceled)
}

private fun formatDuration(totalSeconds: Int): String? {
    if (totalSeconds <= 0) return null
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

internal fun openFile(ctx: Context, item: DownloadItem) {
    val uri = Uri.parse(item.savedUri ?: return)
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, item.mime ?: "video/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, Strings.get(R.string.main_screen_no_app_can_open_this_2), Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        Toast.makeText(ctx, Strings.get(R.string.main_screen_couldn_t_open_the_file), Toast.LENGTH_SHORT).show()
    }
}

