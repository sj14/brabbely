package io.github.sj14.lauscher.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.lauscher.BuildConfig
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.PodcastRepository
import io.github.sj14.lauscher.data.ShakeSensitivity
import io.github.sj14.lauscher.data.ThemeMode
import io.github.sj14.lauscher.ui.LocalContainer
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate

private val SKIP_SECONDS = listOf(5, 10, 15, 30, 45, 60)
private val REFRESH_HOURS = listOf(1, 3, 6, 12, 24)
private val FADE_SECONDS = listOf(0, 5, 10, 15, 30, 60)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenDownloads: () -> Unit) {
    val container = LocalContainer.current
    val repo = container.settings
    val settings = container.settings.settings.collectAsStateWithLifecycle(null).value
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current

    fun update(action: suspend () -> Unit) {
        scope.launch { action() }
    }

    val failed = stringResource(R.string.error_loading)
    val appContext = context.applicationContext

    // Importing many feeds takes a while; the app scope keeps it running when the user leaves the screen.
    fun runImport(uri: Uri, runningMessage: String, import: suspend (InputStream) -> PodcastRepository.ImportResult) {
        Toast.makeText(appContext, runningMessage, Toast.LENGTH_LONG).show()
        container.appScope.launch {
            val message = try {
                val result = appContext.contentResolver.openInputStream(uri)!!.use { import(it) }
                var text = resources.getQuantityString(R.plurals.import_result, result.imported, result.imported)
                if (result.failed > 0) {
                    text += "\n" + resources.getQuantityString(R.plurals.import_failed_feeds, result.failed, result.failed)
                }
                text
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Importing $uri failed", e)
                failed
            }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    fun runExport(uri: Uri, doneMessage: String, export: suspend (OutputStream) -> Unit) {
        scope.launch {
            val message = try {
                appContext.contentResolver.openOutputStream(uri)!!.use { export(it) }
                doneMessage
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Exporting to $uri failed", e)
                failed
            }
            Toast.makeText(appContext, message, Toast.LENGTH_LONG).show()
        }
    }

    val opmlImportRunning = stringResource(R.string.opml_import_running)
    val opmlExported = stringResource(R.string.opml_exported)
    val backupRestoreRunning = stringResource(R.string.backup_restore_running)
    val backupCreated = stringResource(R.string.backup_created)
    val opmlImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport(uri, opmlImportRunning, container.repository::importOpml)
    }
    val opmlExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) runExport(uri, opmlExported, container.repository::exportOpml)
    }
    val backupRestoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport(uri, backupRestoreRunning, container.repository::restoreBackup)
    }
    val backupCreateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runExport(uri, backupCreated, container.repository::createBackup)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings)) }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding ->
        if (settings == null) return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Section(stringResource(R.string.section_playback))
            ChoiceSetting(
                title = stringResource(R.string.skip_back),
                options = SKIP_SECONDS,
                selected = settings.skipBackSeconds,
                label = { pluralStringResource(R.plurals.seconds, it, it) },
                onSelect = { update { repo.setSkipBack(it) } },
            )
            ChoiceSetting(
                title = stringResource(R.string.skip_forward),
                options = SKIP_SECONDS,
                selected = settings.skipForwardSeconds,
                label = { pluralStringResource(R.plurals.seconds, it, it) },
                onSelect = { update { repo.setSkipForward(it) } },
            )
            SwitchSetting(
                title = stringResource(R.string.skip_silence),
                summary = stringResource(R.string.skip_silence_summary),
                checked = settings.skipSilence,
                onChange = { update { repo.setSkipSilence(it) } },
            )
            SwitchSetting(
                title = stringResource(R.string.pause_on_zero_volume),
                summary = stringResource(R.string.pause_on_zero_volume_summary),
                checked = settings.pauseOnZeroVolume,
                onChange = { update { repo.setPauseOnZeroVolume(it) } },
            )
            SwitchSetting(
                title = stringResource(R.string.continue_with_queue),
                summary = stringResource(R.string.continue_with_queue_summary),
                checked = settings.continueWithQueue,
                onChange = { update { repo.setContinueWithQueue(it) } },
            )

            Section(stringResource(R.string.sleep_timer))
            ChoiceSetting(
                title = stringResource(R.string.sleep_shake),
                description = stringResource(R.string.sleep_shake_summary),
                options = ShakeSensitivity.entries,
                selected = settings.sleepShakeSensitivity,
                label = {
                    when (it) {
                        ShakeSensitivity.OFF -> stringResource(R.string.off)
                        ShakeSensitivity.HIGH -> stringResource(R.string.shake_high)
                        ShakeSensitivity.MEDIUM -> stringResource(R.string.shake_medium)
                        ShakeSensitivity.LOW -> stringResource(R.string.shake_low)
                    }
                },
                onSelect = { update { repo.setSleepShake(it) } },
            )
            if (settings.sleepShakeSensitivity != ShakeSensitivity.OFF) {
                SwitchSetting(
                    title = stringResource(R.string.sleep_shake_vibrate),
                    summary = stringResource(R.string.sleep_shake_vibrate_summary),
                    checked = settings.sleepShakeVibrate,
                    onChange = { update { repo.setSleepShakeVibrate(it) } },
                )
            }
            SwitchSetting(
                title = stringResource(R.string.sleep_volume_reset),
                summary = stringResource(R.string.sleep_volume_reset_summary),
                checked = settings.sleepVolumeReset,
                onChange = { update { repo.setSleepVolumeReset(it) } },
            )
            ChoiceSetting(
                title = stringResource(R.string.sleep_fade),
                description = stringResource(R.string.sleep_fade_summary),
                options = FADE_SECONDS,
                selected = settings.sleepFadeSeconds,
                label = { if (it == 0) stringResource(R.string.off) else pluralStringResource(R.plurals.seconds, it, it) },
                onSelect = { update { repo.setSleepFade(it) } },
            )

            Section(stringResource(R.string.section_downloads))
            SwitchSetting(
                title = stringResource(R.string.auto_download),
                summary = stringResource(R.string.auto_download_summary),
                checked = settings.autoDownload,
                onChange = { update { repo.setAutoDownload(it) } },
            )
            SwitchSetting(
                title = stringResource(R.string.wifi_only),
                checked = settings.wifiOnly,
                onChange = { update { repo.setWifiOnly(it) } },
            )
            SwitchSetting(
                title = stringResource(R.string.delete_played),
                checked = settings.deletePlayed,
                onChange = { update { repo.setDeletePlayed(it) } },
            )
            ChoiceSetting(
                title = stringResource(R.string.refresh_interval),
                options = REFRESH_HOURS,
                selected = settings.refreshIntervalHours,
                label = { pluralStringResource(R.plurals.hours, it, it) },
                onSelect = { update { repo.setRefreshInterval(it) } },
            )
            ClickSetting(stringResource(R.string.downloads), onClick = onOpenDownloads)

            Section(stringResource(R.string.section_appearance))
            ChoiceSetting(
                title = stringResource(R.string.theme),
                options = ThemeMode.entries,
                selected = settings.themeMode,
                label = {
                    when (it) {
                        ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                        ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                        ThemeMode.DARK -> stringResource(R.string.theme_dark)
                    }
                },
                onSelect = { update { repo.setThemeMode(it) } },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SwitchSetting(
                    title = stringResource(R.string.dynamic_color),
                    summary = stringResource(R.string.dynamic_color_summary),
                    checked = settings.dynamicColor,
                    onChange = { update { repo.setDynamicColor(it) } },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ClickSetting(stringResource(R.string.language), stringResource(R.string.language_summary)) {
                    val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    context.startActivity(intent)
                }
            }

            Section(stringResource(R.string.section_subscriptions))
            ClickSetting(stringResource(R.string.backup_create), stringResource(R.string.backup_create_summary)) {
                backupCreateLauncher.launch("lauscher-backup-${LocalDate.now()}.json")
            }
            ClickSetting(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_summary)) {
                backupRestoreLauncher.launch(arrayOf("*/*"))
            }
            ClickSetting(stringResource(R.string.opml_import), stringResource(R.string.opml_import_summary)) {
                opmlImportLauncher.launch(arrayOf("*/*"))
            }
            ClickSetting(stringResource(R.string.opml_export)) {
                opmlExportLauncher.launch("lauscher.opml")
            }

            Section(stringResource(R.string.section_about))
            ListItem(
                headlineContent = { Text(stringResource(R.string.app_name)) },
                supportingContent = { Text(stringResource(R.string.version, BuildConfig.VERSION_NAME)) },
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchSetting(title: String, checked: Boolean, onChange: (Boolean) -> Unit, summary: String? = null) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun ClickSetting(title: String, summary: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = summary?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    description: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(label(selected)) },
        modifier = Modifier.clickable { open = true },
    )
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = {
            Column {
                if (description != null) {
                    Text(description, Modifier.padding(bottom = 8.dp))
                }
                for (option in options) {
                    ListItem(
                        headlineContent = { Text(label(option)) },
                        leadingContent = { RadioButton(selected = option == selected, onClick = null) },
                        // The list item would otherwise draw its own background on top of the dialog's.
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.selectable(selected = option == selected) {
                            onSelect(option)
                            open = false
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
        },
    )
}
