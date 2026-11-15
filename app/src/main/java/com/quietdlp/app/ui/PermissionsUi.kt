package com.quietdlp.app.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.R
import com.quietdlp.app.Strings
import com.quietdlp.app.data.AppPermission
import com.quietdlp.app.data.PermState
import com.quietdlp.app.data.PermissionList
import com.quietdlp.app.update.AppUpdater

/** Cómo está cada permiso en este móvil ahora mismo, y qué se enseña de él. */
object PermissionChecks {
    fun state(ctx: Context, p: AppPermission): PermState = when (p) {
        AppPermission.NOTIFICATIONS -> {
            val granted = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            val enabled = (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).areNotificationsEnabled()
            PermissionList.notificationsState(granted, enabled)
        }
        AppPermission.STORAGE ->
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) PermState.GRANTED
            else PermState.DENIED
        AppPermission.BATTERY ->
            if ((ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName)) PermState.GRANTED
            else PermState.DENIED
        AppPermission.INSTALL -> if (AppUpdater.canInstall(ctx)) PermState.GRANTED else PermState.DENIED
    }

    @StringRes fun name(p: AppPermission): Int = when (p) {
        AppPermission.NOTIFICATIONS -> R.string.perm_notifications_name
        AppPermission.STORAGE -> R.string.perm_storage_name
        AppPermission.BATTERY -> R.string.perm_battery_name
        AppPermission.INSTALL -> R.string.perm_install_name
    }

    @StringRes fun why(p: AppPermission): Int = when (p) {
        AppPermission.NOTIFICATIONS -> R.string.perm_notifications_why
        AppPermission.STORAGE -> R.string.perm_storage_why
        AppPermission.BATTERY -> R.string.perm_battery_why
        AppPermission.INSTALL -> R.string.perm_install_why
    }

    /** Qué función queda limitada si no se da (o está limitado). */
    @StringRes fun consequence(p: AppPermission, state: PermState): Int = when (p) {
        AppPermission.NOTIFICATIONS -> if (state == PermState.LIMITED) R.string.perm_notifications_limited else R.string.perm_notifications_missing
        AppPermission.STORAGE -> R.string.perm_storage_missing
        AppPermission.BATTERY -> R.string.perm_battery_missing
        AppPermission.INSTALL -> R.string.perm_install_missing
    }

    fun appNotificationSettings(ctx: Context): Intent =
        if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        else appDetails(ctx)

    fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    fun runtimePermission(p: AppPermission): String? = when (p) {
        AppPermission.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null
        AppPermission.STORAGE -> Manifest.permission.WRITE_EXTERNAL_STORAGE
        else -> null
    }
}

/** Pestaña «Permisos» de Ajustes: una fila por permiso con su estado; tocarla abre donde se cambia. */
@Composable
fun PermissionsSection() {
    val ctx = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    var asking by rememberSaveable { mutableStateOf<AppPermission?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        val permission = asking?.let(PermissionChecks::runtimePermission)
        if (!granted && permission != null && ctx.permissionActivity()?.shouldShowRequestPermissionRationale(permission) == false) {
            val p = asking
            runCatching {
                ctx.startActivity(if (p == AppPermission.NOTIFICATIONS) PermissionChecks.appNotificationSettings(ctx) else PermissionChecks.appDetails(ctx))
            }
        }
    }

    SectionCard(Strings.get(SettingId.PERMISSIONS.title), Modifier.settingAnchor(SettingId.PERMISSIONS)) {
        for (p in PermissionList.applicable(Build.VERSION.SDK_INT)) {
            val state = remember(refresh, p) { PermissionChecks.state(ctx, p) }
            val name = Strings.get(PermissionChecks.name(p))
            val stateText = when (state) {
                PermState.GRANTED -> Strings.get(R.string.perm_granted)
                PermState.DENIED -> Strings.get(R.string.perm_not_granted)
                PermState.LIMITED -> Strings.get(R.string.perm_limited)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button) {
                        when (p) {
                            AppPermission.NOTIFICATIONS ->
                                if (Build.VERSION.SDK_INT < 33 || state != PermState.DENIED) runCatching { ctx.startActivity(PermissionChecks.appNotificationSettings(ctx)) }
                                else {
                                    asking = p
                                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            AppPermission.STORAGE -> {
                                asking = p
                                if (state == PermState.GRANTED) runCatching { ctx.startActivity(PermissionChecks.appDetails(ctx)) }
                                else ask.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                            AppPermission.BATTERY -> openBatterySetting(ctx, listOnly = state == PermState.GRANTED)
                            AppPermission.INSTALL -> runCatching { ctx.startActivity(AppUpdater.permissionIntent(ctx)) }
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(name, style = MaterialTheme.typography.titleSmall)
                    Hint(Strings.get(PermissionChecks.why(p)))
                    if (state == PermState.LIMITED) Hint(Strings.get(PermissionChecks.consequence(p, state)))
                    else if (state == PermState.DENIED) Hint(Strings.get(R.string.perm_without_it, Strings.get(PermissionChecks.consequence(p, state))))
                    Hint(Strings.get(R.string.perm_tap_change))
                }
                StatusPill(stateText, if (state == PermState.GRANTED) Tone.GOOD else if (state == PermState.LIMITED) Tone.ACTIVE else Tone.BAD)
            }
        }
    }
}

private fun Context.permissionActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

// Los resultados de Android continúan la secuencia; la recreación conserva los intentos.
@Composable
fun PermissionsWelcome(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val listed = remember { PermissionList.applicable(Build.VERSION.SDK_INT) }
    var tried by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var inFlight by rememberSaveable { mutableStateOf(false) }
    var review by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val onReturn = remember { mutableStateOf({}) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onReturn.value() }
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onReturn.value() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    val missing = remember(refresh) { listed.filter { PermissionChecks.state(ctx, it) != PermState.GRANTED } }

    fun advance() {
        val next = listed.firstOrNull { it.name !in tried && PermissionChecks.state(ctx, it) != PermState.GRANTED }
        if (next == null) {
            inFlight = false
            refresh++
            if (listed.all { PermissionChecks.state(ctx, it) == PermState.GRANTED }) onFinish() else review = true
            return
        }
        tried = tried + next.name
        inFlight = true
        val permission = PermissionChecks.runtimePermission(next)
        val launched = if (permission != null) {
            runCatching { request.launch(permission) }.isSuccess
        } else {
            val intents = when (next) {
                AppPermission.BATTERY -> batterySettingIntents(ctx)
                AppPermission.INSTALL -> listOf(AppUpdater.permissionIntent(ctx), PermissionChecks.appDetails(ctx))
                AppPermission.NOTIFICATIONS -> listOf(PermissionChecks.appNotificationSettings(ctx))
                else -> emptyList()
            }
            intents.any { runCatching { settings.launch(it) }.isSuccess }
        }
        if (!launched) advance()
    }
    SideEffect { onReturn.value = { inFlight = false; refresh++; advance() } }
    LaunchedEffect(missing.isEmpty()) {
        if (missing.isEmpty() && !inFlight) onFinish()
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(Strings.get(if (review) R.string.perm_review_title else R.string.perm_welcome_title), style = MaterialTheme.typography.headlineSmall)
            Text(Strings.get(R.string.perm_welcome_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Strings.get(R.string.perm_welcome_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (p in missing) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(Strings.get(PermissionChecks.name(p)), style = MaterialTheme.typography.titleSmall)
                    val explanation = if (review) PermissionChecks.consequence(p, PermissionChecks.state(ctx, p)) else PermissionChecks.why(p)
                    Text(Strings.get(explanation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (review) {
                Button(onClick = onFinish, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                    Text(Strings.get(R.string.perm_continue))
                }
            } else {
                Button(onClick = { advance() }, enabled = !inFlight, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                    Text(Strings.get(R.string.perm_grant))
                }
                TextButton(onClick = onFinish, enabled = !inFlight, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                    Text(Strings.get(R.string.perm_not_now))
                }
            }
        }
    }
}
