package com.quietdlp.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.input.KeyboardType
import com.quietdlp.app.MainViewModel
import com.quietdlp.app.ProfileWhat
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.quietdlp.app.data.ProfileRef

/** La tarjeta de un enlace de perfil: la foto de perfil (y el banner) o sus publicaciones. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSection(vm: MainViewModel, ref: ProfileRef) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuietChip(vm.profileWhat == ProfileWhat.PHOTO, { vm.profileWhat = ProfileWhat.PHOTO }, "Profile photo")
        QuietChip(vm.profileWhat == ProfileWhat.POSTS, { vm.profileWhat = ProfileWhat.POSTS }, "Posts")
    }
    if (vm.profileWhat == ProfileWhat.PHOTO) {
        if (vm.advanced && ref.bannerUrl != null) {
            QuietChip(vm.profileBanner, { vm.profileBanner = !vm.profileBanner }, "Also the banner")
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = if (vm.postsAll) "" else vm.postsText,
                    onValueChange = { vm.postsText = it.filter(Char::isDigit).take(6); vm.postsAll = false },
                    modifier = androidx.compose.ui.Modifier.weight(1f),
                    label = { Text("How many") },
                    placeholder = { Text(if (vm.postsAll) "All" else "") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                QuietChip(vm.postsAll, { vm.postsAll = !vm.postsAll }, "All")
            }
        }
    }
}
