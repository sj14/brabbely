package io.github.sj14.lauscher.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.lauscher.data.AppSettings
import io.github.sj14.lauscher.ui.LocalContainer
import kotlinx.coroutines.delay

/** The playback position, updated twice a second while the calling composable is shown. */
@Composable
fun rememberPlaybackPosition(): State<Long> {
    val connection = LocalContainer.current.playerConnection
    return produceState(connection.currentPosition) {
        while (true) {
            value = connection.currentPosition
            delay(500)
        }
    }
}

@Composable
fun skipBackIcon(): ImageVector {
    val settings by LocalContainer.current.settings.settings.collectAsStateWithLifecycle(null)
    return when (settings?.skipBackSeconds ?: AppSettings().skipBackSeconds) {
        5 -> Icons.Rounded.Replay5
        10 -> Icons.Rounded.Replay10
        30 -> Icons.Rounded.Replay30
        else -> Icons.Rounded.FastRewind
    }
}

@Composable
fun skipForwardIcon(): ImageVector {
    val settings by LocalContainer.current.settings.settings.collectAsStateWithLifecycle(null)
    return when (settings?.skipForwardSeconds ?: AppSettings().skipForwardSeconds) {
        5 -> Icons.Rounded.Forward5
        10 -> Icons.Rounded.Forward10
        30 -> Icons.Rounded.Forward30
        else -> Icons.Rounded.FastForward
    }
}
