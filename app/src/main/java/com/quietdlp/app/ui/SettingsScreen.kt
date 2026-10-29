package com.quietdlp.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.quietdlp.app.Links
import com.quietdlp.app.LoginActivity
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.data.AppOrientation
import com.quietdlp.app.data.AudioTier
import com.quietdlp.app.data.DownloadMode
import com.quietdlp.app.data.Layouts
import com.quietdlp.app.data.ErrorLogText
import com.quietdlp.app.data.Kind
import com.quietdlp.app.data.Quality
import com.quietdlp.app.data.SiteRule
import com.quietdlp.app.data.SiteRules
import com.quietdlp.app.data.Slideshow
import com.quietdlp.app.download.CookieStore
import com.quietdlp.app.download.ErrorLog
import com.quietdlp.app.update.AppUpdater

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: MainViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) vm.chooseFolder(uri)
    }
    val importCookies = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importCookies(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = Layouts.MAX_SINGLE_COLUMN_DP.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ───── Descargas ─────
            SectionCard("Download") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Default download", style = MaterialTheme.typography.titleSmall)
                    Hint("For Share and copied links.")
                    ModePicker(vm.mode, includeAsk = true) { vm.changeMode(it) }
                }
                SwitchRow(
                    "Remember last choice",
                    if (vm.rememberLast) "Links opened in the app start with your last choice." else "Off. Starts from the default.",
                    vm.rememberLast, vm::changeRememberLast,
                )
                SwitchRow("Offer copied links", "When you open the app with a link copied.", vm.clipboardOffer, vm::changeClipboardOffer)
                SwitchRow(
                    "Photos and galleries",
                    if (vm.photosEnabled) "If there's no video, it downloads the photos." else "Off.",
                    vm.photosEnabled, vm::changePhotosEnabled,
                )
                SwitchRow(
                    "Video from photos and music",
                    if (vm.autoPhotoVideo) "Makes the video by itself when a post has a song." else "Off. Use Make video on the download.",
                    vm.autoPhotoVideo, vm::changeAutoPhotoVideo,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Song length in videos", style = MaterialTheme.typography.titleSmall)
                    Hint("How long the song plays in a video from photos. As posted is the part the author chose, when the post says.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Slideshow.SONG_LENGTHS.forEach { (seconds, label) ->
                            QuietChip(vm.songSeconds == seconds, { vm.changeSongSeconds(seconds) }, label)
                        }
                    }
                }
                SwitchRow(
                    "Remember my choice",
                    if (vm.rememberSong) "Make video saves the length you pick." else "Off. Make video starts from the length above.",
                    vm.rememberSong, vm::changeRememberSong,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Save to", style = MaterialTheme.typography.titleSmall)
                    Hint(
                        if (vm.folderUri.isEmpty()) "Gallery album “${vm.folderName}” (music in Music › ${vm.folderName})."
                        else "${folderLabel(vm.folderUri)} › ${vm.folderName}",
                    )
                    OutlinedTextField(
                        value = vm.folderName,
                        onValueChange = vm::changeFolderName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Folder name") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { pickFolder.launch(null) }) { Text("Choose location") }
                        if (vm.folderUri.isNotEmpty()) TextButton(onClick = vm::resetFolder) { Text("Use default") }
                    }
                }
            }

            // ───── Reglas por sitio ─────
            SitesSection(vm)

            // ───── Aspecto ─────
            AppearanceSection(vm)

            // ───── Actualizaciones ─────
            SectionCard("Updates") {
                Text("QuietDLP ${AppUpdater.currentVersion(ctx)}", style = MaterialTheme.typography.titleSmall)
                if (AppUpdater.isConfigured(ctx)) {
                    SwitchRow("Notify me of new versions", if (vm.appUpdateEnabled) "On." else "Off.", vm.appUpdateEnabled, vm::changeAppUpdateEnabled)
                    OutlinedButton(onClick = { vm.checkAppUpdate(manual = true) }, enabled = !vm.appUpdateBusy) {
                        Text(if (vm.appUpdateBusy) "Checking…" else "Check now")
                    }
                } else {
                    Hint("Available once there's a downloads page.")
                }
                vm.appUpdateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                SwitchRow("Update the download engine automatically", if (vm.autoUpdate) "On. At most once a day." else "Off.", vm.autoUpdate, vm::changeAutoUpdate)
                if (vm.advanced) {
                    Hint("Engine (yt-dlp): ${vm.ytdlpVersion ?: "unknown"}")
                    OutlinedButton(onClick = vm::updateEngine, enabled = !vm.updating && vm.engineReady) {
                        Text(if (vm.updating) "Updating…" else "Update engine now")
                    }
                    vm.updateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            // ───── Solo en modo avanzado ─────
            if (vm.advanced) {
                var siteAddress by remember { mutableStateOf("") }
                SectionCard("Sessions") {
                    Hint("For content that requires signing in.")
                    OutlinedTextField(
                        value = siteAddress,
                        onValueChange = { siteAddress = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Website address") },
                        placeholder = { Text("example.com") },
                        singleLine = true,
                    )
                    OutlinedButton(
                        onClick = {
                            val host = CookieStore.normalizeHost(siteAddress)
                            if (host == null) {
                                Toast.makeText(ctx, "Enter a valid address, like example.com", Toast.LENGTH_SHORT).show()
                            } else {
                                ctx.startActivity(Intent(ctx, LoginActivity::class.java).putExtra(LoginActivity.EXTRA_HOST, host))
                            }
                        },
                        enabled = siteAddress.isNotBlank(),
                    ) { Text("Sign in") }
                    vm.sessions.forEach { host ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(host, style = MaterialTheme.typography.bodyMedium)
                                Hint("Session saved")
                            }
                            TextButton(onClick = { vm.logout(host) }) { Text("Sign out") }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { importCookies.launch(arrayOf("*/*")) }) {
                            Text(if (vm.hasImportedCookies) "Replace cookies.txt" else "Import cookies.txt")
                        }
                        if (vm.hasImportedCookies) TextButton(onClick = vm::removeImportedCookies) { Text("Remove") }
                    }
                    Hint("cookies.txt, for sites that don't allow signing in here. Best with a secondary account.")
                    vm.accountMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }

                var errorLog by remember { mutableStateOf(ErrorLog.read(ctx)) }
                SectionCard("Error log") {
                    val n = ErrorLogText.count(errorLog)
                    Hint(if (n == 0) "No errors." else "$n recent ${if (n == 1) "error" else "errors"}.")
                    if (n > 0) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("QuietDLP log", errorLog))
                                Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                            }) { Text("Copy") }
                            TextButton(onClick = { ErrorLog.clear(ctx); errorLog = "" }) { Text("Clear") }
                        }
                    }
                }
            }

            // ───── Apoyo y licencia ─────
            SectionCard("About") {
                if (Links.DONATE_URL.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(Links.DONATE_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Buy me a kebab (\$5)") }
                    SwitchRow("Show the support card", "On the main screen, below the link box.", vm.supportCard, vm::changeSupportCard)
                }
                Hint("Free software (GPL-3.0). Download only content you have the right to save.")
            }
            Box(Modifier.height(24.dp))
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SitesSection(vm: MainViewModel) {
    var editing by remember { mutableStateOf<SiteRule?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    SectionCard("Sites") {
        Hint("Pick a website and always download the same thing from it, whatever the default is. Example: audio from a music site.")
        vm.siteRules.forEach { rule ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { editing = rule }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(rule.host, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                StatusPill(rule.quality.label(), Tone.ACTIVE)
            }
        }
        OutlinedButton(onClick = { adding = true }) { Text("Add site") }
    }
    if (adding) SiteRuleEditor(null, onDismiss = { adding = false }, onRemove = null) { host, q ->
        vm.saveSiteRule(host, q); adding = false
    }
    editing?.let { rule ->
        SiteRuleEditor(rule, onDismiss = { editing = null }, onRemove = { vm.removeSiteRule(rule.host); editing = null }) { host, q ->
            vm.saveSiteRule(host, q); editing = null
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SiteRuleEditor(
    initial: SiteRule?,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)?,
    onSave: (String, Quality) -> Unit,
) {
    var address by remember { mutableStateOf(initial?.host ?: "") }
    val modes = DownloadMode.entries.filter { it.quality != null }
    var picked by remember {
        mutableStateOf(modes.firstOrNull { it.quality == initial?.quality } ?: DownloadMode.MUSIC_BEST)
    }
    val host = SiteRules.normalizeHost(address)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add site" else "Edit site") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    enabled = initial == null,
                    label = { Text("Website address") },
                    placeholder = { Text("example.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Always download", style = MaterialTheme.typography.labelMedium)
                ModePicker(picked, includeAsk = false) { picked = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(host ?: return@TextButton, picked.quality!!) }, enabled = host != null) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** Los modos de descarga en grupos: vídeo, luego el audio de mejor a menor calidad y, si se pide, "preguntar". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModePicker(selected: DownloadMode?, includeAsk: Boolean, onPick: (DownloadMode) -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Video", style = MaterialTheme.typography.labelMedium, color = muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DownloadMode.entries.filter { it.quality?.kind == Kind.VIDEO }.forEach { m ->
                QuietChip(selected == m, { onPick(m) }, m.label.substringAfter(" · ").replaceFirstChar { it.uppercase() })
            }
        }
        AudioTier.entries.forEach { tier ->
            val modes = DownloadMode.entries.filter { it.audio?.tier == tier }
            if (modes.isNotEmpty()) {
                Text("Audio · ${tier.label}", style = MaterialTheme.typography.labelMedium, color = muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { m -> QuietChip(selected == m, { onPick(m) }, m.audio!!.label) }
                }
            }
        }
        if (includeAsk) {
            FlowRow { QuietChip(selected == DownloadMode.ASK, { onPick(DownloadMode.ASK) }, DownloadMode.ASK.label) }
        }
    }
}

@Composable
private fun Hint(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Hint(subtitle)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** "primary:Download/Videos" → "Download/Videos". */
private fun folderLabel(uri: String): String = runCatching {
    val id = DocumentsContract.getTreeDocumentId(Uri.parse(uri))
    val path = id.substringAfter(':', "")
    if (path.isEmpty()) "Internal storage" else path
}.getOrDefault("Chosen folder")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(vm: MainViewModel) {
    var editing by remember { mutableStateOf<PaletteSpec?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    SectionCard("Appearance") {
        Text("Screen orientation", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOrientation.entries.forEach { o -> QuietChip(vm.orientation == o, { vm.changeOrientation(o) }, o.label) }
        }
        Hint("Auto follows your phone's rotation lock.")
        val groups = listOf(PaletteGroup.DARK, PaletteGroup.COLORFUL, PaletteGroup.LIGHT)
        groups.forEach { g ->
            Text(g.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Palettes.presets.filter { it.group == g }.forEach { p ->
                    PaletteSwatch(p, vm.paletteId == p.id) { vm.choosePalette(p.id) }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text("System", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow {
                val sys = PaletteSpec(
                    Palettes.SYSTEM, "Wallpaper", PaletteGroup.DARK, true,
                    0xFF1B1B1F.toInt(), 0xFF2A2A30.toInt(), 0xFFA8C7FA.toInt(), 0xFFE8B4C8.toInt(),
                )
                PaletteSwatch(sys, vm.paletteId == Palettes.SYSTEM) { vm.choosePalette(Palettes.SYSTEM) }
            }
        }
        Text(PaletteGroup.CUSTOM.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            vm.customPalettes.forEach { p ->
                PaletteSwatch(p, vm.paletteId == p.id) { vm.choosePalette(p.id) }
            }
            if (vm.customPalettes.size < Palettes.MAX_CUSTOM) {
                Box(
                    Modifier
                        .size(width = 86.dp, height = 68.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable { creating = true },
                    contentAlignment = Alignment.Center,
                ) { Text("+ New", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
        }
        vm.customPalettes.firstOrNull { it.id == vm.paletteId }?.let { sel ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = sel }) { Text("Edit “${sel.name}”") }
                TextButton(onClick = { vm.deleteCustomPalette(sel.id) }) { Text("Delete") }
            }
        }
    }
    if (creating) {
        PaletteEditor(null, onDismiss = { creating = false }) { vm.saveCustomPalette(it); creating = false }
    }
    editing?.let { sel ->
        PaletteEditor(sel, onDismiss = { editing = null }) { vm.saveCustomPalette(it); editing = null }
    }
}

/** Crea o edita una paleta propia: cuatro colores a elegir, con un punto de partida sugerido. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaletteEditor(initial: PaletteSpec?, onDismiss: () -> Unit, onSave: (PaletteSpec) -> Unit) {
    val start = initial ?: Palettes.presets.first()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var bg by remember { mutableIntStateOf(start.background) }
    var surface by remember { mutableIntStateOf(start.surface) }
    var accent by remember { mutableIntStateOf(start.accent) }
    var accent2 by remember { mutableIntStateOf(start.accent2) }
    var open by remember { mutableIntStateOf(-1) }
    var dark by remember { mutableStateOf(start.dark) }
    var hue by remember { mutableFloatStateOf(Palettes.toHsv(start.accent)[0]) }
    val id = remember { initial?.id ?: "c" + System.currentTimeMillis() }
    val preview = Palettes.fromColors(id, name, bg, surface, accent, accent2)
    val scheme = Palettes.scheme(preview)

    fun suggest() {
        val c = Palettes.suggest(dark, hue.toInt(), hue.toInt(), 40)
        bg = c[0]; surface = c[1]; accent = c[2]; accent2 = c[3]
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New palette" else "Edit palette") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                // Vista previa con los colores elegidos; el texto toma solo el color de mejor contraste.
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(preview.background)).padding(12.dp)) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(preview.surface)).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("QuietDLP", color = scheme.onSurface, style = MaterialTheme.typography.titleMedium)
                        Text("Save a video, song or photos", color = scheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.clip(CircleShape).background(Color(preview.accent)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                                Text("Download", color = scheme.onPrimary, style = MaterialTheme.typography.labelLarge)
                            }
                            Box(Modifier.clip(CircleShape).background(Color(preview.accent2)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                                Text("Done", color = scheme.onSecondary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                Text("Start from", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuietChip(dark, { dark = true; suggest() }, "Dark")
                    QuietChip(!dark, { dark = false; suggest() }, "Light")
                }
                Slider(value = hue, onValueChange = { hue = it; suggest() }, valueRange = 0f..359f)

                Text("Colors", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ColorRow("Background", bg, open == 0, { open = if (open == 0) -1 else 0 }) { bg = it }
                ColorRow("Cards", surface, open == 1, { open = if (open == 1) -1 else 1 }) { surface = it }
                ColorRow("Accent", accent, open == 2, { open = if (open == 2) -1 else 2 }) { accent = it }
                ColorRow("Second accent", accent2, open == 3, { open = if (open == 3) -1 else 3 }) { accent2 = it }
                Hint("Text color is chosen automatically for the best contrast.")
            }
        },
        confirmButton = { TextButton(onClick = { onSave(preview) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Una fila de color: muestra el color y, al tocarla, deja ajustarlo con deslizadores o escribiendo su código. */
@Composable
private fun ColorRow(label: String, color: Int, expanded: Boolean, onToggle: () -> Unit, onChange: (Int) -> Unit) {
    val hsv = Palettes.toHsv(color)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(Color(color))
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            )
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(Palettes.toHex(color), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (expanded) {
            Column(Modifier.padding(start = 4.dp, end = 4.dp, bottom = 6.dp)) {
                Text("Hue", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[0], onValueChange = { onChange(Palettes.hsv(it, hsv[1], hsv[2])) }, valueRange = 0f..359f)
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[1], onValueChange = { onChange(Palettes.hsv(hsv[0], it, hsv[2])) }, valueRange = 0f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(value = hsv[2], onValueChange = { onChange(Palettes.hsv(hsv[0], hsv[1], it)) }, valueRange = 0f..1f)
                var hex by remember(color) { mutableStateOf(Palettes.toHex(color)) }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { v ->
                        hex = v.take(7)
                        Palettes.parseHex(hex)?.let(onChange)
                    },
                    label = { Text("Code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
