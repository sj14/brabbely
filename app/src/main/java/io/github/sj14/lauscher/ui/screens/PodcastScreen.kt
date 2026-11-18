package io.github.sj14.lauscher.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.db.Podcast
import io.github.sj14.lauscher.ui.LocalContainer
import io.github.sj14.lauscher.ui.components.Artwork
import io.github.sj14.lauscher.ui.components.EpisodeRow
import io.github.sj14.lauscher.ui.components.HtmlText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastScreen(podcastId: Long, onBack: () -> Unit, onOpenEpisode: (Long) -> Unit) {
    val container = LocalContainer.current
    val repository = container.repository
    val podcastFlow = remember(podcastId) { repository.observePodcast(podcastId) }
    val episodesFlow = remember(podcastId) { repository.observeEpisodes(podcastId) }
    val podcast by podcastFlow.collectAsStateWithLifecycle(null)
    val episodes by episodesFlow.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmUnsubscribe by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { BackButton(onBack) },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, stringResource(R.string.more))
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.mark_all_played)) },
                                leadingIcon = { Icon(Icons.Rounded.DoneAll, null) },
                                onClick = {
                                    menuOpen = false
                                    scope.launch { repository.markAllPlayed(podcastId) }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { padding ->
        val current = podcast ?: return@Scaffold
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                PodcastHeader(
                    podcast = current,
                    onSubscribe = { scope.launch { repository.subscribe(podcastId) } },
                    onUnsubscribe = { confirmUnsubscribe = true },
                )
            }
            item {
                Text(
                    pluralStringResource(R.plurals.episode_count, episodes.size, episodes.size),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(episodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    onClick = { onOpenEpisode(episode.id) },
                    podcastImageUrl = current.imageUrl,
                    modifier = Modifier.animateItem(),
                )
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            }
        }
    }

    if (confirmUnsubscribe) {
        AlertDialog(
            onDismissRequest = { confirmUnsubscribe = false },
            title = { Text(stringResource(R.string.unsubscribe_title)) },
            text = { Text(stringResource(R.string.unsubscribe_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnsubscribe = false
                    scope.launch {
                        repository.unsubscribe(podcastId)
                        onBack()
                    }
                }) { Text(stringResource(R.string.unsubscribe)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnsubscribe = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun PodcastHeader(podcast: Podcast, onSubscribe: () -> Unit, onUnsubscribe: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val gradient = Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.colorScheme.surface),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(gradient)
            .padding(16.dp),
    ) {
        Row {
            Artwork(podcast.imageUrl, Modifier.width(132.dp), cornerRadius = 18.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(podcast.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    podcast.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(8.dp))
                if (podcast.subscribed) {
                    FilledTonalButton(onClick = onUnsubscribe) {
                        Icon(Icons.Rounded.Check, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.subscribed))
                    }
                } else {
                    Button(onClick = onSubscribe) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.subscribe))
                    }
                }
            }
        }
        if (podcast.description.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            HtmlText(
                podcast.description,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                modifier = Modifier
                    .animateContentSize()
                    .clickable { expanded = !expanded },
            )
        }
    }
}
