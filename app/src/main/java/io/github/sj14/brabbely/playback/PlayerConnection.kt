package io.github.sj14.brabbely.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerState(
    val episodeId: Long? = null,
    val isPlaying: Boolean = false,
    /** Playback is wanted, so the play/pause button shows pause, also while the episode still loads. */
    val showPause: Boolean = false,
    /** Playback is wanted but waits for data, e.g. right after tapping play on a streamed episode. */
    val isBuffering: Boolean = false,
    val durationMs: Long = 0,
    val hasVideo: Boolean = false,
)

/** The UI side of playback: a MediaController connected to [PlaybackService]. */
@OptIn(UnstableApi::class)
class PlayerConnection(private val context: Context) {
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _controller = MutableStateFlow<MediaController?>(null)
    val controller: StateFlow<MediaController?> = _controller.asStateFlow()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = updateState(player)
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val controller = future.get()
            controller.addListener(listener)
            _controller.value = controller
            updateState(controller)
        }, MoreExecutors.directExecutor())
    }

    fun release() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        _controller.value = null
    }

    val currentPosition: Long get() = _controller.value?.currentPosition ?: 0

    fun play(episodeId: Long) {
        val controller = _controller.value ?: return
        if (_state.value.episodeId == episodeId) {
            controller.play()
            return
        }
        controller.setMediaItem(MediaItem.Builder().setMediaId(episodeId.toString()).build())
        controller.prepare()
        controller.play()
    }

    /** Pauses the episode if it is the one playing, otherwise plays it. */
    fun playOrPause(episodeId: Long) {
        val state = _state.value
        if (state.episodeId == episodeId && state.showPause) {
            _controller.value?.pause()
        } else {
            play(episodeId)
        }
    }

    fun togglePlayPause() {
        val controller = _controller.value ?: return
        Util.handlePlayPauseButtonAction(controller)
    }

    fun seekTo(positionMs: Long) {
        _controller.value?.seekTo(positionMs)
    }

    fun skipBack() = sendCommand(PlaybackService.COMMAND_SKIP_BACK)

    fun skipForward() = sendCommand(PlaybackService.COMMAND_SKIP_FORWARD)

    private fun sendCommand(action: String) {
        _controller.value?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), Bundle.EMPTY)
    }

    private fun updateState(player: Player) {
        // An episode that is not prepared yet has no duration in the player, only in its metadata.
        val duration = if (player.duration != C.TIME_UNSET) player.duration else player.mediaMetadata.durationMs ?: 0
        _state.value = PlayerState(
            episodeId = player.currentMediaItem?.mediaId?.toLongOrNull(),
            isPlaying = player.isPlaying,
            showPause = !Util.shouldShowPlayButton(player),
            isBuffering = player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
            durationMs = duration,
            hasVideo = player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO),
        )
    }
}
