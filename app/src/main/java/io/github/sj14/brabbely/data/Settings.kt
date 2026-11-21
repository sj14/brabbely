package io.github.sj14.brabbely.data

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

/** Where "Add to queue" puts an episode. */
enum class QueueAddPosition { TOP, BOTTOM }

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
    /** An episode paused or left with at most this much remaining counts as played, 0 to turn off. */
    val markPlayedRemainingSeconds: Int = 30,
    val queueAddPosition: QueueAddPosition = QueueAddPosition.BOTTOM,
    val pauseOnZeroVolume: Boolean = true,
    val autoDownload: Boolean = false,
    val wifiOnly: Boolean = true,
    val deletePlayed: Boolean = true,
    val refreshIntervalHours: Int = 6,
    /** Unplayed episodes published within this many days count as new. */
    val newEpisodeDays: Int = 30,
    val sleepTimerMinutes: Int = 30,
    val sleepAutoMode: SleepAutoMode = SleepAutoMode.OFF,
    /** Start and end of the time window for [SleepAutoMode.TIME_WINDOW], in minutes after midnight. */
    val sleepAutoStartMinutes: Int = 22 * 60,
    val sleepAutoEndMinutes: Int = 6 * 60,
    val sleepShakeSensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM,
    val sleepShakeVibrate: Boolean = true,
    val sleepVolumeReset: Boolean = true,
    /** The sleep timer only counts down while the phone is locked and starts over while it is unlocked. */
    val sleepPauseWhileUnlocked: Boolean = false,
    /** How long the volume fades out before the sleep timer pauses, 0 for no fade-out. */
    val sleepFadeSeconds: Int = 15,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    /** How many podcasts the library shows side by side when the phone is held upright. */
    val libraryColumns: Int = 3,
    /** Folder (a document tree URI) for daily backups, null if automatic backups are off. */
    val autoBackupFolder: String? = null,
)

/**
 * The enum constant with the given name, or null if there is none. Unlike valueOf it does not throw
 * for an unknown name, such as one restored from the backup of a newer version.
 */
private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    enumValues<T>().firstOrNull { it.name == name }

/**
 * The value of [key], or null if it is missing or has another type. A backup of a newer version
 * could have written a setting with another type; reading it with `prefs[key]` would then throw
 * at every start of the app.
 */
private inline fun <reified T> Preferences.read(key: Preferences.Key<T>): T? = asMap()[key] as? T

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val store = context.dataStore

    private object Keys {
        val skipBack = intPreferencesKey("skip_back")
        val skipForward = intPreferencesKey("skip_forward")
        val speed = floatPreferencesKey("speed")
        val skipSilence = booleanPreferencesKey("skip_silence")
        val continueWithQueue = booleanPreferencesKey("continue_with_queue")
        val markPlayedRemaining = intPreferencesKey("mark_played_remaining")
        val queueAddPosition = stringPreferencesKey("queue_add_position")
        val pauseOnZeroVolume = booleanPreferencesKey("pause_on_zero_volume")
        val sleepVolumeReset = booleanPreferencesKey("sleep_volume_reset")
        val sleepPauseWhileUnlocked = booleanPreferencesKey("sleep_pause_while_unlocked")
        val autoDownload = booleanPreferencesKey("auto_download")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val deletePlayed = booleanPreferencesKey("delete_played")
        val refreshInterval = intPreferencesKey("refresh_interval")
        val newEpisodeDays = intPreferencesKey("new_episode_days")
        val sleepMinutes = intPreferencesKey("sleep_minutes")
        val sleepAutoMode = stringPreferencesKey("sleep_auto_mode")
        val sleepAutoStart = intPreferencesKey("sleep_auto_start")
        val sleepAutoEnd = intPreferencesKey("sleep_auto_end")
        val sleepShake = stringPreferencesKey("sleep_shake_sensitivity")
        val sleepFade = intPreferencesKey("sleep_fade_seconds")
        val sleepShakeVibrate = booleanPreferencesKey("sleep_shake_vibrate")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val libraryColumns = intPreferencesKey("library_columns")
        val autoBackupFolder = stringPreferencesKey("auto_backup_folder")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            skipBackSeconds = prefs.read(Keys.skipBack) ?: defaults.skipBackSeconds,
            skipForwardSeconds = prefs.read(Keys.skipForward) ?: defaults.skipForwardSeconds,
            playbackSpeed = prefs.read(Keys.speed) ?: defaults.playbackSpeed,
            skipSilence = prefs.read(Keys.skipSilence) ?: defaults.skipSilence,
            continueWithQueue = prefs.read(Keys.continueWithQueue) ?: defaults.continueWithQueue,
            markPlayedRemainingSeconds = prefs.read(Keys.markPlayedRemaining) ?: defaults.markPlayedRemainingSeconds,
            queueAddPosition = enumOrNull<QueueAddPosition>(prefs.read(Keys.queueAddPosition))
                ?: defaults.queueAddPosition,
            pauseOnZeroVolume = prefs.read(Keys.pauseOnZeroVolume) ?: defaults.pauseOnZeroVolume,
            sleepVolumeReset = prefs.read(Keys.sleepVolumeReset) ?: defaults.sleepVolumeReset,
            sleepPauseWhileUnlocked = prefs.read(Keys.sleepPauseWhileUnlocked) ?: defaults.sleepPauseWhileUnlocked,
            autoDownload = prefs.read(Keys.autoDownload) ?: defaults.autoDownload,
            wifiOnly = prefs.read(Keys.wifiOnly) ?: defaults.wifiOnly,
            deletePlayed = prefs.read(Keys.deletePlayed) ?: defaults.deletePlayed,
            refreshIntervalHours = prefs.read(Keys.refreshInterval) ?: defaults.refreshIntervalHours,
            newEpisodeDays = prefs.read(Keys.newEpisodeDays) ?: defaults.newEpisodeDays,
            sleepTimerMinutes = prefs.read(Keys.sleepMinutes) ?: defaults.sleepTimerMinutes,
            sleepAutoMode = enumOrNull<SleepAutoMode>(prefs.read(Keys.sleepAutoMode)) ?: defaults.sleepAutoMode,
            sleepAutoStartMinutes = prefs.read(Keys.sleepAutoStart) ?: defaults.sleepAutoStartMinutes,
            sleepAutoEndMinutes = prefs.read(Keys.sleepAutoEnd) ?: defaults.sleepAutoEndMinutes,
            sleepShakeSensitivity = enumOrNull<ShakeSensitivity>(prefs.read(Keys.sleepShake))
                ?: defaults.sleepShakeSensitivity,
            sleepFadeSeconds = prefs.read(Keys.sleepFade) ?: defaults.sleepFadeSeconds,
            sleepShakeVibrate = prefs.read(Keys.sleepShakeVibrate) ?: defaults.sleepShakeVibrate,
            themeMode = enumOrNull<ThemeMode>(prefs.read(Keys.themeMode)) ?: defaults.themeMode,
            dynamicColor = prefs.read(Keys.dynamicColor) ?: defaults.dynamicColor,
            libraryColumns = prefs.read(Keys.libraryColumns) ?: defaults.libraryColumns,
            autoBackupFolder = prefs.read(Keys.autoBackupFolder),
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
    suspend fun setSleepPauseWhileUnlocked(enabled: Boolean) =
        store.edit { it[Keys.sleepPauseWhileUnlocked] = enabled }
    suspend fun setQueueAddPosition(position: QueueAddPosition) =
        store.edit { it[Keys.queueAddPosition] = position.name }
    suspend fun setMarkPlayedRemaining(seconds: Int) = store.edit { it[Keys.markPlayedRemaining] = seconds }
    suspend fun setContinueWithQueue(enabled: Boolean) = store.edit { it[Keys.continueWithQueue] = enabled }
    suspend fun setAutoDownload(enabled: Boolean) = store.edit { it[Keys.autoDownload] = enabled }
    suspend fun setWifiOnly(enabled: Boolean) = store.edit { it[Keys.wifiOnly] = enabled }
    suspend fun setDeletePlayed(enabled: Boolean) = store.edit { it[Keys.deletePlayed] = enabled }
    suspend fun setRefreshInterval(hours: Int) = store.edit { it[Keys.refreshInterval] = hours }
    suspend fun setNewEpisodeDays(days: Int) = store.edit { it[Keys.newEpisodeDays] = days }
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
    suspend fun setLibraryColumns(columns: Int) = store.edit { it[Keys.libraryColumns] = columns }
}
