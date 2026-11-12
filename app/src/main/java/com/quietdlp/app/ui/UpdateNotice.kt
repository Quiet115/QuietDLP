package com.quietdlp.app.ui

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.R
import com.quietdlp.app.Strings
import com.quietdlp.app.data.UpdateNoticeTimer
import kotlinx.coroutines.delay

/** ¿La persona pidió reducir las animaciones? Entonces el anillo baja a saltos en vez de vaciarse de forma continua. */
@Composable
private fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
}

/**
 * Lo que hace falta para dibujar el aviso: si se ve y cuánto del anillo queda. Mientras se ve, avanza con el reloj del
 * sistema desde el instante guardado, así que rotar la pantalla o cambiar el tema no reinicia los 20 segundos.
 * Mientras se descarga la versión, el aviso no se va.
 */
class UpdateNoticeUi(val visible: Boolean, val fraction: Float, val secondsLeft: Int)

@Composable
fun rememberUpdateNotice(startedAt: Long, busy: Boolean, onExpired: () -> Unit): UpdateNoticeUi {
    val active = startedAt != 0L
    val reduced = rememberReducedMotion()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt, reduced) {
        while (startedAt != 0L) {
            now = SystemClock.elapsedRealtime()
            if (UpdateNoticeTimer.remainingMs(startedAt, now) <= 0L && !busy) {
                onExpired()
                break
            }
            delay(if (reduced) 250L else 33L)
        }
    }
    if (!active) return UpdateNoticeUi(false, 0f, 0)
    var fraction = UpdateNoticeTimer.fraction(startedAt, now)
    if (reduced) fraction = UpdateNoticeTimer.steppedFraction(fraction)
    return UpdateNoticeUi(true, fraction, UpdateNoticeTimer.secondsLeft(startedAt, now))
}

/** El aviso: «¡Hay una actualización disponible!», un anillo del color de acento que se vacía y dos botones. Sin notas de versión. */
@Composable
fun UpdateNoticeCard(vm: MainViewModel, ui: UpdateNoticeUi) {
    AnimatedVisibility(
        visible = ui.visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.navigationBarsPadding().imePadding().fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CountdownRing(ui.fraction, ui.secondsLeft)
                    Text(
                        Strings.get(R.string.update_banner_title),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vm.appUpdateProgress >= 0) {
                    LinearProgressIndicator(progress = { vm.appUpdateProgress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text(Strings.get(R.string.main_screen_downloading, vm.appUpdateProgress), style = MaterialTheme.typography.bodySmall)
                }
                vm.appUpdateMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = vm::installAppUpdate, enabled = !vm.appUpdateBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(Strings.get(R.string.update_banner_install))
                    }
                    TextButton(onClick = vm::closeUpdateBanner, enabled = !vm.appUpdateBusy, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(Strings.get(R.string.update_banner_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun CountdownRing(fraction: Float, secondsLeft: Int) {
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val description = Strings.get(R.string.update_banner_ring_desc, secondsLeft)
    Box(Modifier.size(40.dp).semantics { contentDescription = description }) {
        Canvas(Modifier.size(40.dp)) {
            val width = 5.dp.toPx()
            val inset = width / 2
            val arcSize = Size(size.width - width, size.height - width)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(width))
            drawArc(accent, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(width, cap = StrokeCap.Round))
        }
    }
}
