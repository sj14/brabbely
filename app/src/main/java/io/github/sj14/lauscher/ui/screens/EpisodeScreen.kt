package io.github.sj14.lauscher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistRemove
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.ui.LocalContainer
import io.github.sj14.lauscher.ui.components.Artwork
import io.github.sj14.lauscher.ui.components.DownloadButton
import io.github.sj14.lauscher.ui.components.HtmlText
import io.github.sj14.lauscher.ui.formatDate
import io.github.sj14.lauscher.ui.formatDuration
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeScreen(episodeId: Long, onBack: () -> Unit, onOpenPodcast: (Long) -> Unit) {
    val container = LocalContainer.current
    val itemFlow = remember(episodeId) { container.repository.observeEpisode(episodeId) }
    val item by itemFlow.collectAsStateWithLifecycle(null)
    val playerState by container.playerConnection.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = {}, navigationIcon = { BackButton(onBack) }) }) { padding ->
        val current = item ?: return@Scaffold
        val episode = current.episode
        val isPlaying = playerState.episodeId == episode.id && playerState.isPlaying

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Artwork(
                    current.imageUrl,
                    Modifier.width(96.dp),
                    cornerRadius = 14.dp,
                    fallbackUrl = current.podcast.imageUrl,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        current.podcast.title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenPodcast(current.podcast.id) },
                    )
                    Text(
                        listOf(formatDate(episode.pubDate), episode.duration.takeIf { it > 0 }?.let(::formatDuration))
                            .filterNot { it.isNullOrEmpty() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(episode.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (isPlaying) container.playerConnection.togglePlayPause()
                        else container.playerConnection.play(episode.id)
                    },
                ) {
                    Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(if (isPlaying) R.string.pause else R.string.play))
                }
                DownloadButton(episode)
                if (episode.queuePosition == null) {
                    IconButton(onClick = { scope.launch { container.repository.enqueue(episode.id) } }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_queue))
                    }
                } else {
                    IconButton(onClick = { scope.launch { container.repository.removeFromQueue(episode.id) } }) {
                        Icon(Icons.Rounded.PlaylistRemove, stringResource(R.string.remove_from_queue))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            HtmlText(episode.description, Modifier.fillMaxWidth())
            Spacer(Modifier.height(32.dp))
        }
    }
}
