package com.quietdlp.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.data.Kind
import com.quietdlp.app.R
import com.quietdlp.app.Strings

/** El interruptor de la portada (la miniatura dentro del archivo) en la tarjeta del enlace. */
@Composable
internal fun CoverSection(vm: MainViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(Strings.get(R.string.cover_ui_cover_image), style = MaterialTheme.typography.labelLarge)
            Text(
                if (vm.kind == Kind.AUDIO) Strings.get(R.string.cover_ui_the_thumbnail_as_the_album) else Strings.get(R.string.cover_ui_the_thumbnail_inside_the_file),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = vm.cover, onCheckedChange = vm::changeCover)
    }
}
