package io.github.sj14.brabbely.ui.screens

import android.content.pm.ActivityInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import io.github.sj14.brabbely.MainActivity
import io.github.sj14.brabbely.R
import io.github.sj14.brabbely.ui.LocalContainer
import io.github.sj14.brabbely.ui.components.Artwork
import io.github.sj14.brabbely.ui.components.VideoView
import io.github.sj14.brabbely.ui.components.rememberPlaybackPosition
import io.github.sj14.brabbely.ui.components.skipBackIcon
import io.github.sj14.brabbely.ui.components.skipForwardIcon
import io.github.sj14.brabbely.ui.formatTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CHAPTER_MARK_RADIUS = 1.5.dp
private val CHAPTER_MARK_MIN_GAP = 6.dp

private enum class PlayerSheet { SPEED, SLEEP, CHAPTERS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(onClose: () -> Unit, onShowEpisode: (Long) -> Unit) {
    val container = LocalContainer.current
    val connection = container.playerConnection
    val state by connection.state.collectAsStateWithLifecycle()
    val controller by connection.controller.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    val sleepState by container.sleepTimer.state.collectAsStateWithLifecycle()
    val episodeId = state.episodeId

    val itemFlow = remember(episodeId) { container.repository.observeEpisode(episodeId ?: -1) }
    val item by itemFlow.collectAsStateWithLifecycle(null)
    val chaptersFlow = remember(episodeId) { container.repository.observeChapters(episodeId ?: -1) }
    val chapters by chaptersFlow.collectAsStateWithLifecycle(emptyList())
    val position by rememberPlaybackPosition()
    val currentChapter = chapters.lastOrNull { it.startMs <= position }

    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var fullscreen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val baseColor = MaterialTheme.colorScheme.surface
    val accent by animateColorAsState(rememberArtworkColor(item?.imageUrl) ?: MaterialTheme.colorScheme.primaryContainer)

    if (fullscreen && state.hasVideo) {
        FullscreenVideo(onExit = { fullscreen = false })
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), baseColor, baseColor))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.close_player), Modifier.size(32.dp))
                }
                Spacer(Modifier.weight(1f))
                if (episodeId != null) {
                    IconButton(onClick = { onShowEpisode(episodeId) }) {
                        Icon(Icons.Rounded.Info, stringResource(R.string.show_notes))
                    }
                }
            }

            Spacer(Modifier.weight(0.5f))
            if (state.hasVideo) {
                VideoSurface(Modifier.fillMaxWidth()) {
                    IconButton(onClick = { fullscreen = true }, Modifier.align(Alignment.BottomEnd)) {
                        Icon(Icons.Rounded.Fullscreen, stringResource(R.string.fullscreen), tint = Color.White)
                    }
                }
            } else {
                // A chapter with an image of its own shows it instead of the cover while it plays.
                Artwork(
                    currentChapter?.imageUrl ?: item?.imageUrl,
                    Modifier
                        .fillMaxWidth(0.85f)
                        .shadow(24.dp, RoundedCornerShape(24.dp)),
                    cornerRadius = 24.dp,
                    fallbackUrl = item?.imageUrl,
                )
            }
            Spacer(Modifier.weight(0.5f))

            Text(
                item?.episode?.title.orEmpty(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                item?.podcast?.title.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                currentChapter?.title.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.height(20.dp),
            )

            SeekBar(position, state.durationMs, chapters.map { it.startMs }, onSeek = connection::seekTo)

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = connection::skipBack, Modifier.size(64.dp)) {
                    Icon(skipBackIcon(), stringResource(R.string.skip_back), Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = connection::togglePlayPause,
                    modifier = Modifier.size(84.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        stringResource(if (state.isPlaying) R.string.pause else R.string.play),
                        Modifier.size(48.dp),
                    )
                }
                IconButton(onClick = connection::skipForward, Modifier.size(64.dp)) {
                    Icon(skipForwardIcon(), stringResource(R.string.skip_forward), Modifier.size(36.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val speed = settings?.playbackSpeed ?: 1f
                AssistChip(
                    onClick = { sheet = PlayerSheet.SPEED },
                    label = { Text(formatSpeed(speed)) },
                    leadingIcon = { Icon(Icons.Rounded.Speed, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
                AssistChip(
                    onClick = { sheet = PlayerSheet.SLEEP },
                    label = { Text(sleepLabel(sleepState)) },
                    leadingIcon = { Icon(Icons.Rounded.Bedtime, null, Modifier.size(AssistChipDefaults.IconSize)) },
                )
                if (chapters.isNotEmpty()) {
                    AssistChip(
                        onClick = { sheet = PlayerSheet.CHAPTERS },
                        label = { Text(stringResource(R.string.chapters)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.size(AssistChipDefaults.IconSize))
                        },
                    )
                }
            }
            Spacer(Modifier.weight(0.3f))
        }
    }

    sheet?.let { current ->
        ModalBottomSheet(onDismissRequest = { sheet = null }) {
            when (current) {
                PlayerSheet.SPEED -> SpeedSheet(settings?.playbackSpeed ?: 1f) { speed ->
                    scope.launch { container.settings.setPlaybackSpeed(speed) }
                    sheet = null
                }
                PlayerSheet.SLEEP -> SleepSheet(sleepState) { sheet = null }
                PlayerSheet.CHAPTERS -> ChapterSheet(chapters, position, item?.imageUrl) { chapter ->
                    connection.seekTo(chapter.startMs)
                    sheet = null
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeekBar(position: Long, duration: Long, chapterStarts: List<Long>, onSeek: (Long) -> Unit) {
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val shown = dragPosition?.toLong() ?: position
    val enabled = duration > 0
    val colors = SliderDefaults.colors()
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown.toFloat().coerceIn(0f, duration.toFloat().coerceAtLeast(1f)),
            onValueChange = { dragPosition = it },
            onValueChangeFinished = {
                dragPosition?.let { onSeek(it.toLong()) }
                dragPosition = null
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            enabled = enabled,
            colors = colors,
            track = { sliderState ->
                Box {
                    // Without the dot at the end, which would look like one more chapter mark.
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        enabled = enabled,
                        colors = colors,
                        drawStopIndicator = null,
                    )
                    ChapterMarks(chapterStarts, duration, position = sliderState.value, colors = colors)
                }
            },
        )
        Row(Modifier.fillMaxWidth()) {
            Text(formatTime(shown), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text("-" + formatTime(duration - shown), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * Small dots on the track where a chapter starts. The track of the slider spans from the thumb's
 * leftmost to its rightmost centre, so a fraction of its width is the same fraction of the episode.
 * A mark too close to the previous one is left out, so many short chapters do not run together
 * into a line; the list of chapters still has them all.
 */
@Composable
private fun BoxScope.ChapterMarks(starts: List<Long>, duration: Long, position: Float, colors: SliderColors) {
    if (duration <= 0) return
    Canvas(Modifier.matchParentSize()) {
        val radius = CHAPTER_MARK_RADIUS.toPx()
        val minGap = CHAPTER_MARK_MIN_GAP.toPx()
        var lastX = Float.NEGATIVE_INFINITY
        for (start in starts) {
            // The first chapter starts with the episode and needs no mark.
            if (start <= 0 || start >= duration) continue
            val x = size.width * start / duration
            if (x - lastX < minGap) continue
            lastX = x
            val color = if (start <= position) colors.activeTickColor else colors.inactiveTickColor
            drawCircle(color, radius, Offset(x, center.y))
        }
    }
}

@Composable
private fun VideoSurface(modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit) {
    // Tells the activity where the video is, so picture-in-picture can grow out of it.
    val activity = LocalActivity.current as? MainActivity
    DisposableEffect(activity) {
        onDispose { activity?.setVideoBounds(null) }
    }
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .onGloballyPositioned { activity?.setVideoBounds(it.boundsInWindow().roundToIntRect().toAndroidRect()) }
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black),
    ) {
        VideoView(Modifier.fillMaxSize())
        overlay()
    }
}

/** A dialog window of its own, so the video also covers the navigation bar of the app. */
@Composable
private fun FullscreenVideo(onExit: () -> Unit) {
    Dialog(
        onDismissRequest = onExit,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val activity = LocalActivity.current
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val insets = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
            insets?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insets?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }

        val connection = LocalContainer.current.playerConnection
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = connection::togglePlayPause),
        ) {
            VideoView(Modifier.fillMaxSize())
            IconButton(onClick = onExit, Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                Icon(Icons.Rounded.FullscreenExit, stringResource(R.string.exit_fullscreen), tint = Color.White)
            }
        }
    }
}

/** The most vivid color of the cover art, used to tint the player background. */
@Composable
private fun rememberArtworkColor(imageUrl: String?): Color? {
    val context = LocalContext.current
    var color by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(imageUrl) {
        if (imageUrl == null) return@LaunchedEffect
        val request = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
        val result = context.imageLoader.execute(request) as? SuccessResult ?: return@LaunchedEffect
        val palette = withContext(Dispatchers.Default) { Palette.from(result.image.toBitmap()).generate() }
        val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: return@LaunchedEffect
        color = Color(swatch.rgb)
    }
    return color
}
