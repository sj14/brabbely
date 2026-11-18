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

data class AppSettings(
    val skipBackSeconds: Int = 10,
    val skipForwardSeconds: Int = 30,
    val playbackSpeed: Float = 1.0f,
    val skipSilence: Boolean = false,
    val continueWithQueue: Boolean = true,
    val autoDownload: Boolean = true,
    val wifiOnly: Boolean = true,
    val deletePlayed: Boolean = true,
    val refreshIntervalHours: Int = 6,
    val sleepTimerMinutes: Int = 30,
    val sleepShakeToReset: Boolean = true,
    val sleepFadeOut: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
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
        val autoDownload = booleanPreferencesKey("auto_download")
        val wifiOnly = booleanPreferencesKey("wifi_only")
        val deletePlayed = booleanPreferencesKey("delete_played")
        val refreshInterval = intPreferencesKey("refresh_interval")
        val sleepMinutes = intPreferencesKey("sleep_minutes")
        val sleepShake = booleanPreferencesKey("sleep_shake")
        val sleepFade = booleanPreferencesKey("sleep_fade")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
    }

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        val defaults = AppSettings()
        AppSettings(
            skipBackSeconds = prefs[Keys.skipBack] ?: defaults.skipBackSeconds,
            skipForwardSeconds = prefs[Keys.skipForward] ?: defaults.skipForwardSeconds,
            playbackSpeed = prefs[Keys.speed] ?: defaults.playbackSpeed,
            skipSilence = prefs[Keys.skipSilence] ?: defaults.skipSilence,
            continueWithQueue = prefs[Keys.continueWithQueue] ?: defaults.continueWithQueue,
            autoDownload = prefs[Keys.autoDownload] ?: defaults.autoDownload,
            wifiOnly = prefs[Keys.wifiOnly] ?: defaults.wifiOnly,
            deletePlayed = prefs[Keys.deletePlayed] ?: defaults.deletePlayed,
            refreshIntervalHours = prefs[Keys.refreshInterval] ?: defaults.refreshIntervalHours,
            sleepTimerMinutes = prefs[Keys.sleepMinutes] ?: defaults.sleepTimerMinutes,
            sleepShakeToReset = prefs[Keys.sleepShake] ?: defaults.sleepShakeToReset,
            sleepFadeOut = prefs[Keys.sleepFade] ?: defaults.sleepFadeOut,
            themeMode = prefs[Keys.themeMode]?.let { ThemeMode.valueOf(it) } ?: defaults.themeMode,
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
        )
    }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setSkipBack(seconds: Int) = store.edit { it[Keys.skipBack] = seconds }
    suspend fun setSkipForward(seconds: Int) = store.edit { it[Keys.skipForward] = seconds }
    suspend fun setPlaybackSpeed(speed: Float) = store.edit { it[Keys.speed] = speed }
    suspend fun setSkipSilence(enabled: Boolean) = store.edit { it[Keys.skipSilence] = enabled }
    suspend fun setContinueWithQueue(enabled: Boolean) = store.edit { it[Keys.continueWithQueue] = enabled }
    suspend fun setAutoDownload(enabled: Boolean) = store.edit { it[Keys.autoDownload] = enabled }
    suspend fun setWifiOnly(enabled: Boolean) = store.edit { it[Keys.wifiOnly] = enabled }
    suspend fun setDeletePlayed(enabled: Boolean) = store.edit { it[Keys.deletePlayed] = enabled }
    suspend fun setRefreshInterval(hours: Int) = store.edit { it[Keys.refreshInterval] = hours }
    suspend fun setSleepMinutes(minutes: Int) = store.edit { it[Keys.sleepMinutes] = minutes }
    suspend fun setSleepShake(enabled: Boolean) = store.edit { it[Keys.sleepShake] = enabled }
    suspend fun setSleepFade(enabled: Boolean) = store.edit { it[Keys.sleepFade] = enabled }
    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.themeMode] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamicColor] = enabled }
}
