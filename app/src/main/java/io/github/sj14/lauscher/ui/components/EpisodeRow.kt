package io.github.sj14.lauscher.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.lauscher.AppContainer
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.db.DownloadState
import io.github.sj14.lauscher.data.db.Episode
import io.github.sj14.lauscher.ui.LocalContainer
import io.github.sj14.lauscher.ui.formatDate
import io.github.sj14.lauscher.ui.formatDuration
import kotlinx.coroutines.launch

/**
 * One episode in a list. [podcastTitle] is shown in lists that mix podcasts. The podcast cover
 * stands in for episodes without their own image. A long press opens the episode's actions.
 */
@Composable
fun EpisodeRow(
    episode: Episode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    podcastImageUrl: String?,
    podcastTitle: String? = null,
) {
    val container = LocalContainer.current
    val playerState by container.playerConnection.state.collectAsStateWithLifecycle()
    val isCurrent = playerState.episodeId == episode.id
    val isPlaying = isCurrent && playerState.isPlaying
    var menuOpen by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(
                episode.imageUrl,
                Modifier.size(56.dp),
                cornerRadius = 10.dp,
                fallbackUrl = podcastImageUrl,
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .alpha(if (episode.played) 0.55f else 1f),
            ) {
                if (podcastTitle != null) {
                    Text(
                        podcastTitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    episode.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (episode.played) {
                        Icon(Icons.Rounded.CheckCircle, null, Modifier.size(14.dp), MaterialTheme.colorScheme.primary)
                    }
                    DownloadStatusIcon(episode)
                    if (episode.isVideo) {
                        Icon(Icons.Rounded.Videocam, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        episodeInfo(episode),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                if (episode.position > 0 && episode.duration > 0 && !episode.played) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { episode.position.toFloat() / episode.duration },
                        modifier = Modifier.fillMaxWidth(0.6f),
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = {
                    if (isPlaying) {
                        container.playerConnection.togglePlayPause()
                    } else {
                        container.playerConnection.play(episode.id)
                    }
                },
                shape = CircleShape,
            ) {
                Icon(
                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                )
            }
        }
        EpisodeMenu(episode, expanded = menuOpen, onDismiss = { menuOpen = false })
    }
}

@Composable
private fun episodeInfo(episode: Episode): String {
    val parts = mutableListOf<String>()
    formatDate(episode.pubDate).takeIf { it.isNotEmpty() }?.let(parts::add)
    if (episode.duration > 0) {
        val remaining = episode.duration - episode.position
        parts += if (episode.position > 0 && !episode.played) {
            stringResource(R.string.time_left, formatDuration(remaining))
        } else {
            formatDuration(episode.duration)
        }
    }
    return parts.joinToString(" · ")
}

/** A small symbol next to the episode info; nothing if the episode is not downloaded. */
@Composable
private fun DownloadStatusIcon(episode: Episode) {
    val size = Modifier.size(14.dp)
    when (episode.downloadState) {
        DownloadState.NONE -> Unit
        DownloadState.QUEUED -> CircularProgressIndicator(size, strokeWidth = 1.5.dp)
        DownloadState.DOWNLOADING -> CircularProgressIndicator(
            progress = { downloadProgress(episode) },
            modifier = size,
            strokeWidth = 1.5.dp,
        )
        DownloadState.DONE -> Icon(Icons.Rounded.DownloadDone, null, size, MaterialTheme.colorScheme.primary)
        DownloadState.FAILED -> Icon(Icons.Rounded.ErrorOutline, null, size, MaterialTheme.colorScheme.error)
    }
}

private fun downloadProgress(episode: Episode): Float =
    if (episode.fileSize > 0) episode.downloadedBytes.toFloat() / episode.fileSize else 0f

/** Starts, cancels or deletes the download, depending on its state. */
private suspend fun toggleDownload(container: AppContainer, episode: Episode) {
    when (episode.downloadState) {
        DownloadState.NONE, DownloadState.FAILED ->
            container.downloads.enqueue(episode.id, container.settings.current().wifiOnly)
        DownloadState.QUEUED, DownloadState.DOWNLOADING -> container.downloads.cancel(episode.id)
        DownloadState.DONE -> container.downloads.delete(episode)
    }
}

private fun downloadActionLabel(state: DownloadState): Int = when (state) {
    DownloadState.NONE, DownloadState.FAILED -> R.string.download
    DownloadState.QUEUED, DownloadState.DOWNLOADING -> R.string.cancel_download
    DownloadState.DONE -> R.string.delete_download
}

@Composable
fun DownloadButton(episode: Episode) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()

    IconButton(onClick = { scope.launch { toggleDownload(container, episode) } }) {
        when (episode.downloadState) {
            DownloadState.NONE -> Icon(Icons.Rounded.Download, stringResource(R.string.download))
            DownloadState.FAILED -> Icon(
                Icons.Rounded.ErrorOutline,
                stringResource(R.string.download_failed),
                tint = MaterialTheme.colorScheme.error,
            )
            DownloadState.QUEUED -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            DownloadState.DOWNLOADING -> CircularProgressIndicator(
                progress = { downloadProgress(episode) },
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
            )
            DownloadState.DONE -> Icon(
                Icons.Rounded.DownloadDone,
                stringResource(R.string.delete_download),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun EpisodeMenu(episode: Episode, expanded: Boolean, onDismiss: () -> Unit) {
    val container = LocalContainer.current
    val repository = container.repository
    val scope = rememberCoroutineScope()

    fun run(action: suspend () -> Unit) {
        onDismiss()
        scope.launch { action() }
    }

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(downloadActionLabel(episode.downloadState)) { run { toggleDownload(container, episode) } }
        if (episode.queuePosition == null) {
            MenuItem(R.string.play_next) { run { repository.playNext(episode.id) } }
            MenuItem(R.string.add_to_queue) { run { repository.enqueue(episode.id) } }
        } else {
            MenuItem(R.string.remove_from_queue) { run { repository.removeFromQueue(episode.id) } }
        }
        if (episode.played) {
            MenuItem(R.string.mark_unplayed) { run { repository.setPlayed(episode.id, false) } }
        } else {
            MenuItem(R.string.mark_played) { run { repository.setPlayed(episode.id, true) } }
        }
    }
}

@Composable
fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}
