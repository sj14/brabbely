package io.github.sj14.lauscher.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.db.EpisodeWithPodcast
import io.github.sj14.lauscher.ui.LocalContainer
import io.github.sj14.lauscher.ui.components.EmptyState
import io.github.sj14.lauscher.ui.components.EpisodeRow
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlinx.coroutines.launch

/** Unplayed episodes of the last weeks across all subscriptions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewEpisodesScreen(onOpenEpisode: (Long) -> Unit) {
    val container = LocalContainer.current
    val episodesFlow = remember { container.repository.observeNew() }
    val episodes by episodesFlow.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.new_episodes)) }) }) { padding ->
        val list = episodes ?: return@Scaffold
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    container.repository.refreshAll()
                    refreshing = false
                }
            },
            modifier = Modifier.padding(padding),
        ) {
            if (list.isEmpty()) {
                EmptyState(stringResource(R.string.new_empty), Icons.Rounded.NewReleases)
            } else {
                EpisodeList(list, onOpenEpisode)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(onOpenEpisode: (Long) -> Unit) {
    val container = LocalContainer.current
    val queueFlow = remember { container.repository.observeQueue() }
    val queue by queueFlow.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()

    // A local copy, so the list follows the finger while dragging; it is saved when the drag ends.
    var items by remember { mutableStateOf(emptyList<EpisodeWithPodcast>()) }
    LaunchedEffect(queue) { queue?.let { items = it } }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    fun saveOrder() {
        val ids = items.map { it.episode.id }
        scope.launch { container.repository.reorderQueue(ids) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.queue)) }) }) { padding ->
        if (queue == null) return@Scaffold
        if (items.isEmpty()) {
            Box(Modifier.padding(padding)) {
                EmptyState(stringResource(R.string.queue_empty), Icons.AutoMirrored.Rounded.PlaylistPlay)
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), state = listState) {
            items(items, key = { it.episode.id }) { item ->
                ReorderableItem(reorderState, key = item.episode.id) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp)
                    Surface(shadowElevation = elevation) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            EpisodeRow(
                                episode = item.episode,
                                podcastTitle = item.podcast.title,
                                podcastImageUrl = item.podcast.imageUrl,
                                onClick = { onOpenEpisode(item.episode.id) },
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Rounded.DragHandle,
                                contentDescription = stringResource(R.string.move),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .draggableHandle(onDragStopped = ::saveOrder)
                                    .padding(start = 4.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit, onOpenEpisode: (Long) -> Unit) {
    val container = LocalContainer.current
    val downloadsFlow = remember { container.repository.observeDownloads() }
    val downloads by downloadsFlow.collectAsStateWithLifecycle(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        val list = downloads ?: return@Scaffold
        Box(Modifier.padding(padding)) {
            if (list.isEmpty()) {
                EmptyState(stringResource(R.string.downloads_empty), Icons.Rounded.DownloadForOffline)
            } else {
                EpisodeList(list, onOpenEpisode)
            }
        }
    }
}

@Composable
private fun EpisodeList(list: List<EpisodeWithPodcast>, onOpenEpisode: (Long) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(list, key = { it.episode.id }) { item ->
            EpisodeRow(
                episode = item.episode,
                podcastTitle = item.podcast.title,
                podcastImageUrl = item.podcast.imageUrl,
                onClick = { onOpenEpisode(item.episode.id) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}
