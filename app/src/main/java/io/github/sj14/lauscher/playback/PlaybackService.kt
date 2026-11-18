package io.github.sj14.lauscher.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import io.github.sj14.lauscher.LauscherApp
import io.github.sj14.lauscher.MainActivity
import io.github.sj14.lauscher.data.AppSettings
import io.github.sj14.lauscher.data.db.EpisodeWithPodcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

// Media button preferences and the session callbacks are still marked unstable in Media3.
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    private val container by lazy { (application as LauscherApp).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var player: ExoPlayer
    private lateinit var session: MediaSession
    private var settings = AppSettings()
    private var positionSaver: Job? = null

    /** When playback was paused because the volume reached zero, so turning it up can resume it. */
    private var pausedByVolumeAt: Long? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            // Lets the player report changes of the device volume, see onDeviceVolumeChanged.
            .setDeviceVolumeControlEnabled(true)
            .build()
        player.addListener(PlayerListener())

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setSessionActivity(openApp)
            .setMediaButtonPreferences(mediaButtons())
            .build()

        container.sleepTimer.attach(player)
        scope.launch {
            container.settings.settings.collect { applySettings(it) }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing when the app is swiped away, but stop the service if nothing plays.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePosition()
        container.sleepTimer.detach()
        scope.cancel()
        session.release()
        player.release()
        super.onDestroy()
    }

    private fun applySettings(new: AppSettings) {
        val buttonsChanged = new.skipBackSeconds != settings.skipBackSeconds ||
            new.skipForwardSeconds != settings.skipForwardSeconds
        settings = new
        player.playbackParameters = PlaybackParameters(new.playbackSpeed)
        player.skipSilenceEnabled = new.skipSilence
        container.sleepTimer.configure(
            shakeThresholdG = new.sleepShakeSensitivity.thresholdG,
            vibrateOnShake = new.sleepShakeVibrate,
            fadeOutMs = new.sleepFadeSeconds * 1000L,
        )
        if (buttonsChanged) session.setMediaButtonPreferences(mediaButtons())
    }

    private fun mediaButtons(): List<CommandButton> = listOf(
        CommandButton.Builder(skipBackIcon(settings.skipBackSeconds))
            .setDisplayName("-${settings.skipBackSeconds}s")
            .setSessionCommand(SessionCommand(COMMAND_SKIP_BACK, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_BACK)
            .build(),
        CommandButton.Builder(skipForwardIcon(settings.skipForwardSeconds))
            .setDisplayName("+${settings.skipForwardSeconds}s")
            .setSessionCommand(SessionCommand(COMMAND_SKIP_FORWARD, Bundle.EMPTY))
            .setSlots(CommandButton.SLOT_FORWARD)
            .build(),
    )

    private fun onVolumeChanged(volume: Int, muted: Boolean) {
        if (settings.sleepVolumeReset) container.sleepTimer.reset()
        if (!settings.pauseOnZeroVolume) return

        val silent = volume == 0 || muted
        if (silent && player.isPlaying) {
            player.pause()
            pausedByVolumeAt = System.currentTimeMillis()
            return
        }
        val pausedAt = pausedByVolumeAt ?: return
        if (!silent) {
            pausedByVolumeAt = null
            if (System.currentTimeMillis() - pausedAt < RESUME_AFTER_VOLUME_PAUSE_MS) player.play()
        }
    }

    private fun skipBy(milliseconds: Long) {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        player.seekTo((player.currentPosition + milliseconds).coerceIn(0, duration))
    }

    private fun currentEpisodeId(): Long? = player.currentMediaItem?.mediaId?.toLongOrNull()

    private fun savePosition() {
        val episodeId = currentEpisodeId() ?: return
        // A finished episode is marked as played instead; its position is reset to the start.
        if (player.playbackState == Player.STATE_ENDED) return
        val position = player.currentPosition
        // The app scope outlives the service, so the last position is saved even in onDestroy.
        container.appScope.launch {
            container.database.episodeDao().setPosition(episodeId, position, System.currentTimeMillis())
        }
    }

    private suspend fun resolve(mediaItem: MediaItem): MediaItem? {
        val id = mediaItem.mediaId.toLongOrNull() ?: return null
        val item = container.database.episodeDao().getWithPodcast(id) ?: return null
        return item.toMediaItem()
    }

    private suspend fun playFromQueue() {
        val next = container.database.episodeDao().getQueue().firstOrNull() ?: return
        val item = container.database.episodeDao().getWithPodcast(next.id) ?: return
        container.repository.removeFromQueue(next.id)
        player.setMediaItem(item.toMediaItem(), item.episode.position)
        player.prepare()
        player.play()
    }

    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            // Playback resumed some other way, so a later volume change must not resume it again.
            if (isPlaying) pausedByVolumeAt = null
            savePosition()
            positionSaver?.cancel()
            if (!isPlaying) return
            positionSaver = scope.launch {
                while (isActive) {
                    delay(POSITION_SAVE_INTERVAL_MS)
                    savePosition()
                }
            }
        }

        override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) = onVolumeChanged(volume, muted)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val episodeId = mediaItem?.mediaId?.toLongOrNull() ?: return
            scope.launch {
                container.repository.removeFromQueue(episodeId)
                val episode = container.database.episodeDao().get(episodeId) ?: return@launch
                container.repository.loadChapters(episode)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val episodeId = currentEpisodeId() ?: return
            when (playbackState) {
                Player.STATE_READY -> {
                    val duration = player.duration
                    if (duration != C.TIME_UNSET) {
                        scope.launch { container.database.episodeDao().setDuration(episodeId, duration) }
                    }
                }
                Player.STATE_ENDED -> scope.launch {
                    container.repository.setPlayed(episodeId, true)
                    val stop = container.sleepTimer.onEpisodeEnded()
                    if (!stop && settings.continueWithQueue) playFromQueue()
                }
                else -> Unit
            }
        }
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(SessionCommand(COMMAND_SKIP_BACK, Bundle.EMPTY))
                .add(SessionCommand(COMMAND_SKIP_FORWARD, Bundle.EMPTY))
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session, controller)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                COMMAND_SKIP_BACK -> skipBy(-settings.skipBackSeconds * 1000L)
                COMMAND_SKIP_FORWARD -> skipBy(settings.skipForwardSeconds * 1000L)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Controllers only send the media id; the URI and metadata come from the database.
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            savePosition()
            val id = mediaItems.firstOrNull()?.mediaId?.toLongOrNull()
            val item = id?.let { container.database.episodeDao().getWithPodcast(it) }
                ?: return@future MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0)
            MediaSession.MediaItemsWithStartPosition(listOf(item.toMediaItem()), 0, item.episode.position)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> = scope.future {
            mediaItems.mapNotNull { resolve(it) }
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val item = container.database.episodeDao().getLastPlayed()
                ?: throw UnsupportedOperationException("Nothing played yet")
            MediaSession.MediaItemsWithStartPosition(listOf(item.toMediaItem()), 0, item.episode.position)
        }
    }

    companion object {
        const val COMMAND_SKIP_BACK = "skip_back"
        const val COMMAND_SKIP_FORWARD = "skip_forward"
        private const val POSITION_SAVE_INTERVAL_MS = 10_000L
        private const val RESUME_AFTER_VOLUME_PAUSE_MS = 2 * 60_000L

        private fun skipBackIcon(seconds: Int) = when (seconds) {
            5 -> CommandButton.ICON_SKIP_BACK_5
            10 -> CommandButton.ICON_SKIP_BACK_10
            15 -> CommandButton.ICON_SKIP_BACK_15
            30 -> CommandButton.ICON_SKIP_BACK_30
            else -> CommandButton.ICON_SKIP_BACK
        }

        private fun skipForwardIcon(seconds: Int) = when (seconds) {
            5 -> CommandButton.ICON_SKIP_FORWARD_5
            10 -> CommandButton.ICON_SKIP_FORWARD_10
            15 -> CommandButton.ICON_SKIP_FORWARD_15
            30 -> CommandButton.ICON_SKIP_FORWARD_30
            else -> CommandButton.ICON_SKIP_FORWARD
        }
    }
}

fun EpisodeWithPodcast.toMediaItem(): MediaItem {
    // Stream if the downloaded file is gone or empty, for example after it was cut off.
    val localFile = episode.localPath?.let(::File)?.takeIf { it.length() > 0 }
    val uri = localFile?.let { "file://${it.path}" } ?: episode.mediaUrl
    val metadata = MediaMetadata.Builder()
        .setTitle(episode.title)
        .setArtist(podcast.title)
        .setAlbumTitle(podcast.title)
        .setArtworkUri(imageUrl?.toUri())
        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
        .build()
    return MediaItem.Builder()
        .setMediaId(episode.id.toString())
        .setUri(uri)
        .setMimeType(episode.mimeType.ifEmpty { null })
        .setMediaMetadata(metadata)
        .build()
}
