package com.quietdlp.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/*
 * Piezas comunes de la pantalla de Ajustes. Las demás apps Quiet copian estos componentes tal cual,
 * junto con SwitchRow.kt, para que Ajustes se vea igual en todas.
 */

/** Fila de pestañas de Ajustes: chips con scroll horizontal. [labels] son recursos de texto. */
@Composable
fun SettingsChips(labels: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = selected == index,
                onClick = { onSelect(index) },
                label = { Text(stringResource(label)) },
            )
        }
    }
}

/** Título de un grupo de ajustes dentro de una pestaña. */
@Composable
fun SettingsGroupTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = modifier)
}
