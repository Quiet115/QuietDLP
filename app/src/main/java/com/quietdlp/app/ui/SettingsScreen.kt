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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.quietdlp.app.data.DownloadMode
import com.quietdlp.app.data.ErrorLogText
import com.quietdlp.app.data.Quality
import com.quietdlp.app.data.SiteRule
import com.quietdlp.app.data.SiteRules
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
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ───── Descargas ─────
            SectionCard("Download") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Default download", style = MaterialTheme.typography.titleSmall)
                    Hint("For Share and copied links.")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DownloadMode.entries.forEach { m ->
                            QuietChip(vm.mode == m, { vm.changeMode(m) }, m.label)
                        }
                    }
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
                    ) { Text("♥ Support the project") }
                }
                Hint("Free software (GPL-3.0). Download only content you have the right to save.")
            }
            Box(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SitesSection(vm: MainViewModel) {
    var editing by remember { mutableStateOf<SiteRule?>(null) }
    var adding by remember { mutableStateOf(false) }
    SectionCard("Sites") {
        Hint("Always use one choice for a website. It beats the default download.")
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    modes.forEach { m -> QuietChip(picked == m, { picked = m }, m.label) }
                }
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
    var creating by remember { mutableStateOf(false) }
    SectionCard("Appearance") {
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

/** Crea o edita una paleta propia con tres deslizadores y una vista previa en vivo. */
@Composable
private fun PaletteEditor(initial: PaletteSpec?, onDismiss: () -> Unit, onSave: (PaletteSpec) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var dark by remember { mutableStateOf(initial?.dark ?: true) }
    var accent by remember { mutableFloatStateOf((initial?.accentHue ?: 170).toFloat()) }
    var bg by remember { mutableFloatStateOf((initial?.backgroundHue ?: 220).toFloat()) }
    var tint by remember { mutableFloatStateOf((initial?.tint ?: 40).toFloat()) }
    val id = remember { initial?.id ?: "c" + System.currentTimeMillis() }
    val preview = Palettes.custom(id, name, dark, accent.toInt(), bg.toInt(), tint.toInt())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New palette" else "Edit palette") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) },
                    label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dark", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    Switch(checked = dark, onCheckedChange = { dark = it })
                }
                // Vista previa: fondo, tarjeta y los dos colores de acento.
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(preview.background)).padding(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(preview.surface)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(preview.accent)))
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(preview.accent2)))
                        Text(
                            "QuietDLP", color = if (dark) Color(0xFFE7EBF1) else Color(0xFF14181F),
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
                Text("Accent color", style = MaterialTheme.typography.labelMedium)
                Slider(value = accent, onValueChange = { accent = it }, valueRange = 0f..359f)
                Text("Background color", style = MaterialTheme.typography.labelMedium)
                Slider(value = bg, onValueChange = { bg = it }, valueRange = 0f..359f)
                Text("Background tint", style = MaterialTheme.typography.labelMedium)
                Slider(value = tint, onValueChange = { tint = it }, valueRange = 0f..100f)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(preview) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
