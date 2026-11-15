package com.quietdlp.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.quietdlp.app.R
import com.quietdlp.app.Strings
import com.quietdlp.app.data.SearchEntry
import com.quietdlp.app.data.SettingsSearch

// El índice se construye al abrir Ajustes, con títulos y términos de los dos idiomas.
internal fun buildSettingsIndex(advanced: Boolean, text: (Language, Int) -> String = Strings::getInLanguage): List<SearchEntry> =
    SettingId.entries
        .filter { !it.advancedOnly || advanced }
        .map { id ->
            SearchEntry(
                id = id.name,
                titles = Language.entries.map { text(it, id.title) },
                keywords = Language.entries.flatMap { language ->
                    listOfNotNull(id.keywords, id.page.label, id.description).map { text(language, it) }
                },
            )
        }

@Composable
internal fun SettingsSearchBar(query: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 4.dp),
        placeholder = { Text(Strings.get(R.string.settings_search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = Strings.get(R.string.settings_search_clear))
                }
            }
        },
        singleLine = true,
    )
}

/** Los ajustes que coinciden: cada uno con su nombre y su pestaña. Tocar uno abre la pestaña y lo resalta. */
@Composable
internal fun SettingsSearchResults(
    query: String,
    index: List<SearchEntry>,
    onPick: (SettingId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val found = SettingsSearch.search(query, index).mapNotNull { e -> SettingId.entries.firstOrNull { it.name == e.id } }
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (found.isEmpty()) {
            Text(Strings.get(R.string.settings_search_none, query.trim()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (id in found) {
            Column(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).clickable(role = Role.Button) { onPick(id) }.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(Strings.get(id.title), style = MaterialTheme.typography.titleSmall)
                Text(Strings.get(R.string.settings_search_result_in, Strings.get(id.page.label)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
