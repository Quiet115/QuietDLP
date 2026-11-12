package com.quietdlp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.quietdlp.app.R

/** Lo que hace la app hoy: título y dos o tres líneas por función. Al añadir una función, se añade aquí su entrada. */
private val features = listOf(
    R.string.feature_share_title to R.string.feature_share_body,
    R.string.feature_quality_title to R.string.feature_quality_body,
    R.string.feature_sites_title to R.string.feature_sites_body,
    R.string.feature_photos_title to R.string.feature_photos_body,
    R.string.feature_profile_title to R.string.feature_profile_body,
    R.string.feature_groups_title to R.string.feature_groups_body,
    R.string.feature_subtitles_title to R.string.feature_subtitles_body,
    R.string.feature_trim_title to R.string.feature_trim_body,
    R.string.feature_music_title to R.string.feature_music_body,
    R.string.feature_ringtones_title to R.string.feature_ringtones_body,
    R.string.feature_cover_title to R.string.feature_cover_body,
    R.string.feature_convert_title to R.string.feature_convert_body,
    R.string.feature_makevideo_title to R.string.feature_makevideo_body,
    R.string.feature_follow_title to R.string.feature_follow_body,
    R.string.feature_keys_title to R.string.feature_keys_body,
    R.string.feature_advanced_title to R.string.feature_advanced_body,
    R.string.feature_sessions_title to R.string.feature_sessions_body,
    R.string.feature_history_title to R.string.feature_history_body,
    R.string.feature_backup_title to R.string.feature_backup_body,
    R.string.feature_updates_title to R.string.feature_updates_body,
    R.string.feature_permissions_title to R.string.feature_permissions_body,
    R.string.feature_search_settings_title to R.string.feature_search_settings_body,
    R.string.feature_appearance_title to R.string.feature_appearance_body,
    R.string.feature_official_title to R.string.feature_official_body,
)

@Composable
fun FeaturesTab() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        features.forEach { (title, body) ->
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
