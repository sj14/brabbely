package io.github.sj14.brabbely.ui.screens

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.sj14.brabbely.R
import io.github.sj14.brabbely.data.db.PodcastWithCounts
import io.github.sj14.brabbely.ui.LocalContainer
import io.github.sj14.brabbely.ui.components.Artwork
import io.github.sj14.brabbely.ui.components.EmptyState
import io.github.sj14.brabbely.ui.components.MenuItem
import io.github.sj14.brabbely.ui.components.RefreshAllBox
import io.github.sj14.brabbely.ui.components.rememberRefreshPodcast
import io.github.sj14.brabbely.ui.components.UnsubscribeDialog
import io.github.sj14.brabbely.ui.rememberScreenData
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onOpenPodcast: (Long) -> Unit, onAddPodcast: () -> Unit, onOpenSettings: () -> Unit) {
    val container = LocalContainer.current
    val podcasts by rememberScreenData { container.repository.observeLibrary() }
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library)) },
                actions = {
                    IconButton(onClick = onAddPodcast) {
                        Icon(Icons.Rounded.Add, stringResource(R.string.add_podcast))
                    }
                    SettingsButton(onOpenSettings)
                },
            )
        },
    ) { padding ->
        val list = podcasts ?: return@Scaffold
        val columns = settings?.libraryColumns ?: return@Scaffold
        RefreshAllBox(Modifier.padding(padding)) {
            if (list.isEmpty()) {
                EmptyState(stringResource(R.string.library_empty)) {
                    FilledTonalButton(onClick = onAddPodcast) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.find_podcasts))
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = tileWidth(columns)),
                    contentPadding = PaddingValues(GRID_SPACING),
                    horizontalArrangement = Arrangement.spacedBy(GRID_SPACING),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.podcast.id }) { item ->
                        PodcastTile(item, onClick = { onOpenPodcast(item.podcast.id) })
                    }
                }
            }
        }
    }
}

private val GRID_SPACING = 16.dp

/**
 * The width of a tile so that [columns] tiles fit side by side when the phone is held upright.
 * Held sideways or on a tablet, the grid then fits in more tiles of about this width.
 */
@Composable
private fun tileWidth(columns: Int): Dp {
    val uprightWidth = LocalConfiguration.current.smallestScreenWidthDp.dp
    val spacing = GRID_SPACING * (columns + 1)
    // Rounding to whole pixels must not leave the last tile a fraction too wide to fit.
    val roundingMargin = 1.dp
    return (uprightWidth - spacing) / columns - roundingMargin
}

/** A long press opens the podcast's actions. */
@Composable
private fun PodcastTile(item: PodcastWithCounts, onClick: () -> Unit) {
    val repository = LocalContainer.current.repository
    val refreshPodcast = rememberRefreshPodcast()
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmUnsubscribe by remember { mutableStateOf(false) }

    fun run(action: suspend () -> Unit) {
        menuOpen = false
        scope.launch { action() }
    }

    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })) {
        Box {
            Artwork(item.podcast.imageUrl, Modifier.fillMaxWidth(), cornerRadius = 16.dp)
            if (item.unplayedCount > 0) {
                Badge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        item.unplayedCount.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuItem(R.string.refresh) { run { refreshPodcast(item.podcast) } }
            MenuItem(R.string.mark_all_played) { run { repository.markAllPlayed(item.podcast.id) } }
            MenuItem(R.string.unsubscribe) {
                menuOpen = false
                confirmUnsubscribe = true
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.podcast.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }

    if (confirmUnsubscribe) {
        UnsubscribeDialog(
            onConfirm = {
                confirmUnsubscribe = false
                scope.launch { repository.unsubscribe(item.podcast.id) }
            },
            onDismiss = { confirmUnsubscribe = false },
        )
    }
}
