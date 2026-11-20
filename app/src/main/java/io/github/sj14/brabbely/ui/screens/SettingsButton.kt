package io.github.sj14.brabbely.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.sj14.brabbely.R

/** The gear at the top right of each tab, the same on all of them. */
@Composable
fun SettingsButton(onOpenSettings: () -> Unit) {
    IconButton(onClick = onOpenSettings) {
        Icon(Icons.Rounded.Settings, stringResource(R.string.settings))
    }
}
