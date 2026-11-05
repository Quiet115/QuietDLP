package com.quietdlp.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quietdlp.app.R
import com.quietdlp.app.data.DiagnosticInfo
import com.quietdlp.app.data.Feedback
import com.quietdlp.app.data.FeedbackKind

/** Comentarios: abre un issue de GitHub ya rellenado. Sin servidor, sin claves y sin datos personales. */
@Composable
fun FeedbackTab(language: Language) {
    val context = LocalContext.current
    val info = remember(language) {
        DiagnosticInfo(
            appVersion = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty(),
            android = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}",
            language = if (language == Language.SPANISH) "Spanish" else "English",
        )
    }
    var copied by remember { mutableStateOf(false) }
    fun open(kind: FeedbackKind) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Feedback.url(kind, info)))) }
    }

    Column(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            stringResource(R.string.feedback_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { open(FeedbackKind.BUG) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feedback_bug))
            }
            OutlinedButton(onClick = { open(FeedbackKind.SUGGESTION) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.feedback_suggestion))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsGroupTitle(stringResource(R.string.feedback_data_title))
            Text(stringResource(R.string.feedback_includes), style = MaterialTheme.typography.bodyMedium)
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                Text(info.text(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("QuietDLP diagnostic info", info.text()))
                    copied = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.feedback_copy)) }
            if (copied) {
                Text(stringResource(R.string.feedback_copied), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(
                stringResource(R.string.feedback_account),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
