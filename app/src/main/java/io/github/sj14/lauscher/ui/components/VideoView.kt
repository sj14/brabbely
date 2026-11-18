package io.github.sj14.lauscher.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import io.github.sj14.lauscher.ui.LocalContainer

/** The video of the current episode, fitted into the available space. */
@OptIn(UnstableApi::class)
@Composable
fun VideoView(modifier: Modifier = Modifier) {
    val controller by LocalContainer.current.playerConnection.controller.collectAsStateWithLifecycle()
    val presentation = rememberPresentationState(controller)
    Box(modifier, contentAlignment = Alignment.Center) {
        PlayerSurface(
            player = controller,
            surfaceType = SURFACE_TYPE_SURFACE_VIEW,
            modifier = Modifier.resizeWithContentScale(ContentScale.Fit, presentation.videoSizeDp),
        )
    }
}
