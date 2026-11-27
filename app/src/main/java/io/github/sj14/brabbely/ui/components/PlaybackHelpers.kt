package io.github.sj14.brabbely.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Forward30
import androidx.compose.material.icons.rounded.Forward5
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Replay30
import androidx.compose.material.icons.rounded.Replay5
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.brabbely.R
import io.github.sj14.brabbely.data.AppSettings
import io.github.sj14.brabbely.ui.LocalContainer
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

/**
 * The icon of a play/pause button. While the episode still loads, a spinner takes the icon's place,
 * so a tap on play shows at once that it was registered.
 */
@Composable
fun PlayPauseIcon(showPause: Boolean, isLoading: Boolean, size: Dp = 24.dp, describe: Boolean = true) {
    val description = stringResource(if (showPause) R.string.pause else R.string.play)
    if (isLoading) {
        val semantics = if (describe) Modifier.semantics { contentDescription = description } else Modifier
        Box(Modifier.size(size).then(semantics), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(size * 0.8f),
                color = LocalContentColor.current,
                strokeWidth = size / 10,
            )
        }
    } else {
        Icon(
            if (showPause) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (describe) description else null,
            modifier = Modifier.size(size),
        )
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
