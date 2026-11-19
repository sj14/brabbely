package io.github.sj14.lauscher.ui.screens

import android.content.Intent
import android.text.format.DateFormat
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
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
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.lauscher.BuildConfig
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.PodcastRepository
import io.github.sj14.lauscher.data.QueueAddPosition
import io.github.sj14.lauscher.data.backup.BackupFormat
import io.github.sj14.lauscher.data.ShakeSensitivity
import io.github.sj14.lauscher.data.SleepAutoMode
import io.github.sj14.lauscher.playback.SLEEP_TIMER_MINUTES
import io.github.sj14.lauscher.data.ThemeMode
import io.github.sj14.lauscher.ui.LocalContainer
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val SKIP_SECONDS = listOf(5, 10, 15, 30, 45, 60)
private val REFRESH_HOURS = listOf(1, 3, 6, 12, 24)
private val FADE_SECONDS = listOf(0, 5, 10, 15, 30, 60)
private val MARK_PLAYED_SECONDS = listOf(0, 15, 30, 60, 120, 300)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpenDownloads: () -> Unit) {
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

    // Imports and exports run in the app scope, so leaving the screen does not cut them off halfway.
    // Importing many feeds takes a while.
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
        container.appScope.launch {
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
    val antennaPodImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runImport(uri, backupRestoreRunning, container.repository::importAntennaPod)
    }
    val autoBackupFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Keeps access to the folder after a restart, so the daily backup can write there.
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        appContext.contentResolver.takePersistableUriPermission(uri, flags)
        update { repo.setAutoBackupFolder(uri.toString()) }
    }
    val backupCreateLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFormat.MIME_TYPE)) { uri ->
        if (uri != null) runExport(uri, backupCreated, container.repository::createBackup)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.settings)) })
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
            ChoiceSetting(
                title = stringResource(R.string.mark_played_near_end),
                description = stringResource(R.string.mark_played_near_end_summary),
                options = MARK_PLAYED_SECONDS,
                selected = settings.markPlayedRemainingSeconds,
                label = { remainingLabel(it) },
                onSelect = { update { repo.setMarkPlayedRemaining(it) } },
            )
            ChoiceSetting(
                title = stringResource(R.string.queue_add_position),
                options = QueueAddPosition.entries,
                selected = settings.queueAddPosition,
                label = {
                    when (it) {
                        QueueAddPosition.TOP -> stringResource(R.string.queue_add_top)
                        QueueAddPosition.BOTTOM -> stringResource(R.string.queue_add_bottom)
                    }
                },
                onSelect = { update { repo.setQueueAddPosition(it) } },
            )
            SwitchSetting(
                title = stringResource(R.string.continue_with_queue),
                summary = stringResource(R.string.continue_with_queue_summary),
                checked = settings.continueWithQueue,
                onChange = { update { repo.setContinueWithQueue(it) } },
            )

            Section(stringResource(R.string.sleep_timer))
            ChoiceSetting(
                title = stringResource(R.string.sleep_duration),
                description = stringResource(R.string.sleep_duration_summary),
                options = SLEEP_TIMER_MINUTES,
                selected = settings.sleepTimerMinutes,
                label = { pluralStringResource(R.plurals.minutes, it, it) },
                onSelect = { update { repo.setSleepMinutes(it) } },
            )
            ChoiceSetting(
                title = stringResource(R.string.sleep_auto),
                description = stringResource(R.string.sleep_auto_summary),
                options = SleepAutoMode.entries,
                selected = settings.sleepAutoMode,
                label = {
                    when (it) {
                        SleepAutoMode.OFF -> stringResource(R.string.off)
                        SleepAutoMode.ALWAYS -> stringResource(R.string.sleep_auto_always)
                        SleepAutoMode.TIME_WINDOW -> stringResource(R.string.sleep_auto_time_window)
                    }
                },
                onSelect = { update { repo.setSleepAutoMode(it) } },
            )
            if (settings.sleepAutoMode == SleepAutoMode.TIME_WINDOW) {
                TimeSetting(
                    title = stringResource(R.string.sleep_auto_from),
                    minuteOfDay = settings.sleepAutoStartMinutes,
                    onSelect = { update { repo.setSleepAutoStart(it) } },
                )
                TimeSetting(
                    title = stringResource(R.string.sleep_auto_until),
                    minuteOfDay = settings.sleepAutoEndMinutes,
                    onSelect = { update { repo.setSleepAutoEnd(it) } },
                )
            }
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
                backupCreateLauncher.launch(BackupFormat.fileName(LocalDate.now()))
            }
            ClickSetting(stringResource(R.string.backup_restore), stringResource(R.string.backup_restore_summary)) {
                backupRestoreLauncher.launch(arrayOf("*/*"))
            }
            val backupFolder = settings.autoBackupFolder
            ClickSetting(
                stringResource(R.string.auto_backup),
                if (backupFolder == null) stringResource(R.string.auto_backup_off) else folderName(backupFolder),
            ) {
                autoBackupFolderLauncher.launch(null)
            }
            if (backupFolder != null) {
                ClickSetting(stringResource(R.string.auto_backup_disable)) {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    appContext.contentResolver.releasePersistableUriPermission(backupFolder.toUri(), flags)
                    update { repo.setAutoBackupFolder(null) }
                }
            }
            ClickSetting(stringResource(R.string.antennapod_import), stringResource(R.string.antennapod_import_summary)) {
                antennaPodImportLauncher.launch(arrayOf("*/*"))
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
private fun remainingLabel(seconds: Int): String = when {
    seconds == 0 -> stringResource(R.string.mark_played_at_end)
    seconds < 60 -> stringResource(R.string.remaining, pluralStringResource(R.plurals.seconds, seconds, seconds))
    else -> stringResource(R.string.remaining, pluralStringResource(R.plurals.minutes, seconds / 60, seconds / 60))
}

/** The display name of a folder chosen with the system file picker. */
@Composable
private fun folderName(treeUri: String): String {
    val context = LocalContext.current
    return remember(treeUri) {
        DocumentFile.fromTreeUri(context, treeUri.toUri())?.name ?: treeUri
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeSetting(title: String, minuteOfDay: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))) },
        modifier = Modifier.clickable { open = true },
    )
    if (!open) return
    val state = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = { TimePicker(state) },
        confirmButton = {
            TextButton(onClick = {
                onSelect(state.hour * 60 + state.minute)
                open = false
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
        },
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
