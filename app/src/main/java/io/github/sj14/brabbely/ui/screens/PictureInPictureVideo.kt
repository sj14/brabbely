package io.github.sj14.brabbely.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.sj14.brabbely.ui.components.VideoView

/** Only the video, for the small picture-in-picture window. */
@Composable
fun PictureInPictureVideo() {
    VideoView(Modifier.fillMaxSize().background(Color.Black))
}
