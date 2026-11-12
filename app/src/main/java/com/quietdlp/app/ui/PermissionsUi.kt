package com.quietdlp.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)

    fun appDetails(ctx: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))

    fun runtimePermission(p: AppPermission): String? = when (p) {
        AppPermission.NOTIFICATIONS -> Manifest.permission.POST_NOTIFICATIONS
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
    var askedAt by remember { mutableStateOf(0L) }
    var asking by remember { mutableStateOf<AppPermission?>(null) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refresh++
        // Si Android ya no muestra el diálogo (se rechazó dos veces), responde al instante: abrir los ajustes de la app.
        if (!granted && SystemClock.elapsedRealtime() - askedAt < 400) {
            val p = asking
            runCatching {
                ctx.startActivity(if (p == AppPermission.NOTIFICATIONS) PermissionChecks.appNotificationSettings(ctx) else PermissionChecks.appDetails(ctx))
            }
        }
    }

    SectionCard(Strings.get(R.string.settings_permissions), Modifier.settingAnchor(SettingId.PERMISSIONS)) {
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
                                if (state == PermState.LIMITED) runCatching { ctx.startActivity(PermissionChecks.appNotificationSettings(ctx)) }
                                else {
                                    asking = p; askedAt = SystemClock.elapsedRealtime()
                                    ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            AppPermission.STORAGE -> {
                                asking = p; askedAt = SystemClock.elapsedRealtime()
                                ask.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
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

/**
 * Pantalla de bienvenida de permisos, solo la primera vez. «Conceder permisos» los pide en secuencia: los avisos del
 * sistema uno a uno y los especiales abriendo su pantalla de Ajustes. Con «Ahora no» la app sigue funcionando.
 */
@Composable
fun PermissionsWelcome(onFinish: () -> Unit) {
    val ctx = LocalContext.current
    val pending = remember { PermissionList.applicable(Build.VERSION.SDK_INT).filter { PermissionChecks.state(ctx, it) != PermState.GRANTED } }
    if (pending.isEmpty()) {
        LaunchedEffect(Unit) { onFinish() }
        return
    }
    var step by remember { mutableIntStateOf(-1) }
    var waitingForSettings by remember { mutableStateOf(false) }

    fun launch(p: AppPermission, runtime: (String) -> Unit) {
        val permission = PermissionChecks.runtimePermission(p)
        if (permission != null) {
            runtime(permission)
            return
        }
        waitingForSettings = true
        runCatching {
            when (p) {
                AppPermission.BATTERY -> openBatterySetting(ctx)
                AppPermission.INSTALL -> ctx.startActivity(AppUpdater.permissionIntent(ctx))
                else -> {}
            }
        }.onFailure { waitingForSettings = false }
    }

    lateinit var next: () -> Unit
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { next() }
    next = {
        step++
        if (step >= pending.size) onFinish() else launch(pending[step]) { request.launch(it) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (waitingForSettings) {
            waitingForSettings = false
            next()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.statusBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(Strings.get(R.string.perm_welcome_title), style = MaterialTheme.typography.headlineSmall)
            Text(Strings.get(R.string.perm_welcome_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Strings.get(R.string.perm_welcome_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (p in pending) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(Strings.get(PermissionChecks.name(p)), style = MaterialTheme.typography.titleSmall)
                    Text(Strings.get(PermissionChecks.why(p)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(onClick = { next() }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                Text(Strings.get(R.string.perm_grant))
            }
            TextButton(onClick = onFinish, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)) {
                Text(Strings.get(R.string.perm_not_now))
            }
        }
    }
}
