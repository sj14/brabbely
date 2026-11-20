package io.github.sj14.lauscher.playback

import android.app.KeyguardManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import android.os.PowerManager
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** The durations offered for the sleep timer, in minutes. */
val SLEEP_TIMER_MINUTES = listOf(5, 10, 15, 30, 45, 60, 90)

/**
 * Whether [minuteOfDay] lies in the window from [start] to [end] (minutes after midnight).
 * The window may span midnight, for example from 22:00 to 06:00.
 */
fun isInTimeWindow(minuteOfDay: Int, start: Int, end: Int): Boolean =
    if (start <= end) minuteOfDay in start until end else minuteOfDay >= start || minuteOfDay < end

sealed interface SleepTimerState {
    data object Off : SleepTimerState

    /** Counts down only while something is playing. */
    data class Countdown(val remainingMs: Long, val durationMs: Long) : SleepTimerState

    data object EndOfEpisode : SleepTimerState
}

/**
 * Pauses playback after a while. Shaking the phone restarts the countdown, so the timer
 * only fires once the listener has stopped moving, presumably because they fell asleep.
 */
class SleepTimer(private val context: Context, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<SleepTimerState>(SleepTimerState.Off)
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    private var player: Player? = null
    private var countdownJob: Job? = null
    /** Acceleration in g that counts as a shake, null if shaking does nothing. */
    private var shakeThresholdG: Float? = null
    private var vibrateOnShake = true
    private var fadeOutMs = 0L
    private var pauseWhileUnlocked = false

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val keyguardManager = context.getSystemService(KeyguardManager::class.java)
    private var shakeListener: SensorEventListener? = null

    /** Called by the playback service, which owns the player. */
    fun attach(player: Player) {
        this.player = player
    }

    fun detach() {
        cancel()
        player = null
    }

    fun configure(shakeThresholdG: Float?, vibrateOnShake: Boolean, fadeOutMs: Long, pauseWhileUnlocked: Boolean) {
        this.shakeThresholdG = shakeThresholdG
        this.vibrateOnShake = vibrateOnShake
        this.fadeOutMs = fadeOutMs
        this.pauseWhileUnlocked = pauseWhileUnlocked
        if (_state.value is SleepTimerState.Countdown) {
            if (shakeThresholdG != null) startShakeDetection() else stopShakeDetection()
        }
    }

    fun start(minutes: Int) {
        val durationMs = minutes * 60_000L
        _state.value = SleepTimerState.Countdown(durationMs, durationMs)
        restoreVolume()
        if (shakeThresholdG != null) startShakeDetection()
        countdownJob?.cancel()
        countdownJob = scope.launch { runCountdown() }
    }

    fun startEndOfEpisode() {
        stopCountdown()
        _state.value = SleepTimerState.EndOfEpisode
    }

    fun cancel() {
        stopCountdown()
        _state.value = SleepTimerState.Off
    }

    /** Restarts the countdown with its full duration. */
    fun reset() {
        val current = _state.value as? SleepTimerState.Countdown ?: return
        _state.value = current.copy(remainingMs = current.durationMs)
        restoreVolume()
    }

    /** Called by the playback service when an episode has ended. Returns true if playback should stop. */
    fun onEpisodeEnded(): Boolean {
        if (_state.value != SleepTimerState.EndOfEpisode) return false
        _state.value = SleepTimerState.Off
        return true
    }

    private suspend fun runCountdown() {
        var last = System.currentTimeMillis()
        while (currentCoroutineContext().isActive) {
            delay(TICK_MS)
            val now = System.currentTimeMillis()
            val elapsed = now - last
            last = now

            val current = _state.value as? SleepTimerState.Countdown ?: return
            val player = player ?: continue
            if (!player.isPlaying) continue
            // Someone who unlocked the phone is awake: hold the timer at its full duration, so it
            // starts over once the phone is locked. A screen woken by a notification stays locked.
            if (pauseWhileUnlocked && isUnlocked()) {
                reset()
                continue
            }

            val remaining = current.remainingMs - elapsed
            if (remaining <= 0) {
                player.pause()
                _state.value = SleepTimerState.Off
                stopShakeDetection()
                restoreVolume()
                countdownJob = null
                return
            }
            _state.value = current.copy(remainingMs = remaining)
            if (remaining < fadeOutMs) {
                player.volume = remaining.toFloat() / fadeOutMs
            }
        }
    }

    private fun isUnlocked(): Boolean = powerManager.isInteractive && !keyguardManager.isKeyguardLocked

    private fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        stopShakeDetection()
        restoreVolume()
    }

    private fun restoreVolume() {
        player?.volume = 1f
    }

    private fun startShakeDetection() {
        if (shakeListener != null) return
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        val listener = object : SensorEventListener {
            private var lastShake = 0L

            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = event.values
                val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                val threshold = shakeThresholdG ?: return
                if (gForce > threshold && now - lastShake > SHAKE_DEBOUNCE_MS) {
                    lastShake = now
                    reset()
                    if (vibrateOnShake) vibrate()
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        shakeListener = listener
    }

    private fun stopShakeDetection() {
        shakeListener?.let(sensorManager::unregisterListener)
        shakeListener = null
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    companion object {
        private const val TICK_MS = 250L
        private const val SHAKE_DEBOUNCE_MS = 1_000L
        private const val VIBRATION_MS = 60L
    }
}
