package com.quietdlp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.data.ChapterMode
import com.quietdlp.app.data.SponsorCategories
import com.quietdlp.app.data.SponsorMode
import com.quietdlp.app.R
import com.quietdlp.app.Strings

/** SponsorBlock (solo YouTube): quitar o marcar las partes que la gente señaló como publicidad. Modo Avanzado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SponsorSection(vm: MainViewModel) {
    var picking by rememberSaveable { mutableStateOf(false) }
    if (picking) SponsorDialog(vm) { picking = false }

    Text(Strings.get(R.string.extras_ui_sponsorblock), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuietChip(vm.sponsorMode == null, { vm.chooseSponsorMode(null) }, Strings.get(R.string.extras_ui_off))
        SponsorMode.entries.forEach { m -> QuietChip(vm.sponsorMode == m, { vm.chooseSponsorMode(m) }, Strings.tr(m.label)) }
    }
    if (vm.sponsorMode != null) {
        val names = SponsorCategories.ALL.filter { it.first in vm.sponsorCats }.map { it.second }
        TextButton(onClick = { picking = true }) {
            Text(if (names.isEmpty()) Strings.get(R.string.extras_ui_choose_what_to_look_for) else names.joinToString(", "))
        }
        Text(
            if (vm.sponsorMode == SponsorMode.REMOVE) Strings.get(R.string.extras_ui_cuts_them_out_of_the)
            else Strings.get(R.string.extras_ui_adds_them_as_chapters_nothing),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SponsorDialog(vm: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.get(R.string.extras_ui_sponsorblock)) },
        text = {
            Column {
                SponsorCategories.ALL.forEach { (code, label) ->
                    Row(
                        Modifier.fillMaxWidth().clickable { vm.toggleSponsorCategory(code) }.padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = code in vm.sponsorCats, onCheckedChange = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Strings.get(R.string.settings_screen_done)) } },
    )
}

/** Qué hacer con los capítulos del vídeo (solo si trae). Modo Avanzado. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChaptersSection(vm: MainViewModel) {
    Text(Strings.get(R.string.extras_ui_chapters), style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ChapterMode.entries.forEach { m -> QuietChip(vm.chapterMode == m, { vm.chooseChapterMode(m) }, Strings.tr(m.label)) }
    }
    Text(
        when (vm.chapterMode) {
            ChapterMode.OFF -> Strings.get(R.string.extras_ui_chapters_aren_t_saved)
            ChapterMode.EMBED -> Strings.get(R.string.extras_ui_players_show_them_as_a)
            ChapterMode.SPLIT -> Strings.get(R.string.extras_ui_each_chapter_becomes_its_own)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
