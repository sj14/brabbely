package io.github.sj14.lauscher

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.sj14.lauscher.data.AppSettings
import io.github.sj14.lauscher.ui.LauscherNavigation
import io.github.sj14.lauscher.ui.LocalContainer
import io.github.sj14.lauscher.ui.screens.PictureInPictureVideo
import io.github.sj14.lauscher.ui.theme.LauscherTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as LauscherApp).container }

    /** Set when the app is opened from the playback notification. */
    private var openPlayerRequest by mutableStateOf(false)
    private var inPictureInPicture by mutableStateOf(false)

    private val pipListener = Consumer<PictureInPictureModeChangedInfo> { info ->
        inPictureInPicture = info.isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openPlayerRequest = intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)
        addOnPictureInPictureModeChangedListener(pipListener)
        requestNotificationPermission()
        updatePictureInPictureOnPlayback()

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(AppSettings())
            LauscherTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                CompositionLocalProvider(LocalContainer provides container) {
                    if (inPictureInPicture) {
                        PictureInPictureVideo()
                    } else {
                        LauscherNavigation(
                            openPlayer = openPlayerRequest,
                            onPlayerOpened = { openPlayerRequest = false },
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerRequest = true
    }

    override fun onStart() {
        super.onStart()
        container.playerConnection.connect()
    }

    override fun onStop() {
        super.onStop()
        // Stay connected in picture-in-picture, the video is still visible.
        if (!inPictureInPicture) container.playerConnection.release()
    }

    override fun onDestroy() {
        removeOnPictureInPictureModeChangedListener(pipListener)
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Before Android 12 the app has to enter picture-in-picture itself.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && isVideoPlaying()) {
            enterPictureInPictureMode(pictureInPictureParams(autoEnter = false))
        }
    }

    private fun isVideoPlaying(): Boolean {
        val state = container.playerConnection.state.value
        return state.isPlaying && state.hasVideo
    }

    /** From Android 12 on, the system enters picture-in-picture smoothly when the user leaves during a video. */
    private fun updatePictureInPictureOnPlayback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                container.playerConnection.state.collect { state ->
                    setPictureInPictureParams(pictureInPictureParams(autoEnter = state.isPlaying && state.hasVideo))
                }
            }
        }
    }

    private fun pictureInPictureParams(autoEnter: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) builder.setAutoEnterEnabled(autoEnter)
        return builder.build()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
        if (permission == PackageManager.PERMISSION_GRANTED) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
    }
}
