package io.github.sj14.lauscher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** When the sleep timer starts by itself as soon as playback starts. */
enum class SleepAutoMode { OFF, ALWAYS, TIME_WINDOW }

/** How hard the phone has to be shaken to restart the sleep timer, as acceleration in g. */
enum class ShakeSensitivity(val thresholdG: Float?) {
    OFF(null),
    HIGH(1.2f),
    MEDIUM(1.4f),
    LOW(1.8f),
}

data class AppSettings(
    val skipBackSeconds: Int = 10,
    val skipForwardSeconds: Int = 30,
    val playbackSpeed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val continueWithQueue: Boolean = true,
    val pauseOnZeroVolume: Boolean = true,
    val autoDownload: Boolean = false,
    val wifiOnly: Boolean = true,
    val deletePlayed: Boolean = true,
    val refreshIntervalHours: Int = 6,
    val sleepTimerMinutes: Int = 30,
    val sleepAutoMode: SleepAutoMode = SleepAutoMode.OFF,
    /** Start and end of the time window for [SleepAutoMode.TIME_WINDOW], in minutes after midnight. */
    val sleepAutoStartMinutes: Int = 22 * 60,
    val sleepAutoEndMinutes: Int = 6 * 60,
    val sleepShakeSensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM,
    val sleepShakeVibrate: Boolean = true,
    val sleepVolumeReset: Boolean = true,
    /** How long the volume fades out before the sleep timer pauses, 0 for no fade-out. */
    val sleepFadeSeconds: Int = 15,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** Folder (a document tree URI) for daily backups, null if automatic backups are off. */
    val autoBackupFolder: String? = null,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.dataStore

    private object Keys {
        val skipBack = intPreferencesKey("skip_back")
        val skipForward = intPreferencesKey("skip_forward")
        val speed = floatPreferencesKey("speed")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val continueWithQueue = booleanPreferencesKey("continue_with_queue")
        val pauseOnZeroVolume = booleanPreferencesKey("pause_on_zero_volume")
        val sleepVolumeReset = booleanPreferencesKey("sleep_volume_reset")
        val autoDownload = booleanPreferencesKey("auto_download")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val deletePlayed = booleanPreferencesKey("delete_played")
        val refreshInterval = intPreferencesKey("refresh_interval")
        val sleepMinutes = intPreferencesKey("sleep_minutes")
        val sleepAutoMode = stringPreferencesKey("sleep_auto_mode")
        val sleepAutoStart = intPreferencesKey("sleep_auto_start")
        val sleepAutoEnd = intPreferencesKey("sleep_auto_end")
        val sleepShake = stringPreferencesKey("sleep_shake_sensitivity")
        val sleepFade = intPreferencesKey("sleep_fade_seconds")
        val sleepShakeVibrate = booleanPreferencesKey("sleep_shake_vibrate")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val autoBackupFolder = stringPreferencesKey("auto_backup_folder")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            skipBackSeconds = prefs[Keys.skipBack] ?: defaults.skipBackSeconds,
            skipForwardSeconds = prefs[Keys.skipForward] ?: defaults.skipForwardSeconds,
            playbackSpeed = prefs[Keys.speed] ?: defaults.playbackSpeed,
            skipSilence = prefs[Keys.skipSilence] ?: defaults.skipSilence,
            continueWithQueue = prefs[Keys.continueWithQueue] ?: defaults.continueWithQueue,
            pauseOnZeroVolume = prefs[Keys.pauseOnZeroVolume] ?: defaults.pauseOnZeroVolume,
            sleepVolumeReset = prefs[Keys.sleepVolumeReset] ?: defaults.sleepVolumeReset,
            autoDownload = prefs[Keys.autoDownload] ?: defaults.autoDownload,
            wifiOnly = prefs[Keys.wifiOnly] ?: defaults.wifiOnly,
            deletePlayed = prefs[Keys.deletePlayed] ?: defaults.deletePlayed,
            refreshIntervalHours = prefs[Keys.refreshInterval] ?: defaults.refreshIntervalHours,
            sleepTimerMinutes = prefs[Keys.sleepMinutes] ?: defaults.sleepTimerMinutes,
            sleepAutoMode = prefs[Keys.sleepAutoMode]?.let { SleepAutoMode.valueOf(it) } ?: defaults.sleepAutoMode,
            sleepAutoStartMinutes = prefs[Keys.sleepAutoStart] ?: defaults.sleepAutoStartMinutes,
            sleepAutoEndMinutes = prefs[Keys.sleepAutoEnd] ?: defaults.sleepAutoEndMinutes,
            sleepShakeSensitivity = prefs[Keys.sleepShake]?.let { ShakeSensitivity.valueOf(it) }
                ?: defaults.sleepShakeSensitivity,
            sleepFadeSeconds = prefs[Keys.sleepFade] ?: defaults.sleepFadeSeconds,
            sleepShakeVibrate = prefs[Keys.sleepShakeVibrate] ?: defaults.sleepShakeVibrate,
            themeMode = prefs[Keys.themeMode]?.let { ThemeMode.valueOf(it) } ?: defaults.themeMode,
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
            autoBackupFolder = prefs[Keys.autoBackupFolder],
        )
    }

    suspend fun current(): AppSettings = settings.first()

    /**
     * All stored settings by key, for the backup. Settings left at their default are not included,
     * nor is the backup folder: access to it is granted per device and would not work after restoring.
     */
    suspend fun export(): Map<String, Any> = store.data.first().asMap()
        .filterKeys { it != Keys.autoBackupFolder }
        .mapKeys { it.key.name }

    /** Writes back settings from a backup. */
    suspend fun import(values: Map<String, Any>) = store.edit { prefs ->
        for ((name, value) in values) {
            when (value) {
                is Boolean -> prefs[booleanPreferencesKey(name)] = value
                is Int -> prefs[intPreferencesKey(name)] = value
                is Float -> prefs[floatPreferencesKey(name)] = value
                is String -> prefs[stringPreferencesKey(name)] = value
            }
        }
    }

    suspend fun setSkipBack(seconds: Int) = store.edit { it[Keys.skipBack] = seconds }
    suspend fun setSkipForward(seconds: Int) = store.edit { it[Keys.skipForward] = seconds }
    suspend fun setPlaybackSpeed(speed: Float) = store.edit { it[Keys.speed] = speed }
    suspend fun setSkipSilence(enabled: Boolean) = store.edit { it[Keys.skipSilence] = enabled }
    suspend fun setPauseOnZeroVolume(enabled: Boolean) = store.edit { it[Keys.pauseOnZeroVolume] = enabled }
    suspend fun setSleepVolumeReset(enabled: Boolean) = store.edit { it[Keys.sleepVolumeReset] = enabled }
    suspend fun setContinueWithQueue(enabled: Boolean) = store.edit { it[Keys.continueWithQueue] = enabled }
    suspend fun setAutoDownload(enabled: Boolean) = store.edit { it[Keys.autoDownload] = enabled }
    suspend fun setWifiOnly(enabled: Boolean) = store.edit { it[Keys.wifiOnly] = enabled }
    suspend fun setDeletePlayed(enabled: Boolean) = store.edit { it[Keys.deletePlayed] = enabled }
    suspend fun setRefreshInterval(hours: Int) = store.edit { it[Keys.refreshInterval] = hours }
    suspend fun setSleepMinutes(minutes: Int) = store.edit { it[Keys.sleepMinutes] = minutes }
    suspend fun setSleepAutoMode(mode: SleepAutoMode) = store.edit { it[Keys.sleepAutoMode] = mode.name }
    suspend fun setSleepAutoStart(minutes: Int) = store.edit { it[Keys.sleepAutoStart] = minutes }
    suspend fun setSleepAutoEnd(minutes: Int) = store.edit { it[Keys.sleepAutoEnd] = minutes }
    suspend fun setSleepShake(sensitivity: ShakeSensitivity) = store.edit { it[Keys.sleepShake] = sensitivity.name }
    suspend fun setSleepShakeVibrate(enabled: Boolean) = store.edit { it[Keys.sleepShakeVibrate] = enabled }
    suspend fun setSleepFade(seconds: Int) = store.edit { it[Keys.sleepFade] = seconds }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.themeMode] = mode.name }
    suspend fun setAutoBackupFolder(uri: String?) = store.edit { prefs ->
        if (uri == null) prefs.remove(Keys.autoBackupFolder) else prefs[Keys.autoBackupFolder] = uri
    }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamicColor] = enabled }
}
