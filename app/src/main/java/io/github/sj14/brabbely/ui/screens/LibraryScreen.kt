package io.github.sj14.brabbely.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ListItem
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sj14.brabbely.data.PodcastRepository
import io.github.sj14.brabbely.data.db.EpisodeWithPodcast
import io.github.sj14.brabbely.ui.components.EpisodeRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
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

/** A search at least this many characters long shows results instead of the grid. */
private const val MIN_SEARCH_LENGTH = 2

/** Waits for a pause in typing before querying the episodes. */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * Holds the search in the library, so it is still there when the user comes back from a
 * podcast or an episode they opened from the results.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibrarySearchViewModel(repository: PodcastRepository) : ViewModel() {
    var open by mutableStateOf(false)
    var query by mutableStateOf("")

    /** The episodes whose title matches [query], null until the first search has run. */
    val episodes: StateFlow<List<EpisodeWithPodcast>?> = snapshotFlow { query.trim() }
        .distinctUntilChanged()
        .debounce(SEARCH_DEBOUNCE_MS)
        .flatMapLatest { text ->
            if (text.length < MIN_SEARCH_LENGTH) flowOf(emptyList()) else repository.searchEpisodes(text)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun close() {
        open = false
        query = ""
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPodcast: (Long) -> Unit,
    onOpenEpisode: (Long) -> Unit,
    onAddPodcast: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalContainer.current
    val podcasts by rememberScreenData { container.repository.observeLibrary() }
    val settings by container.settings.settings.collectAsStateWithLifecycle(null)
    val search = viewModel { LibrarySearchViewModel(container.repository) }

    BackHandler(enabled = search.open) { search.close() }

    Scaffold(
        topBar = {
            if (search.open) {
                SearchBar(search)
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.library)) },
                    actions = {
                        IconButton(onClick = { search.open = true }) {
                            Icon(Icons.Rounded.Search, stringResource(R.string.library_search))
                        }
                        IconButton(onClick = onAddPodcast) {
                            Icon(Icons.Rounded.Add, stringResource(R.string.add_podcast))
                        }
                        SettingsButton(onOpenSettings)
                    },
                )
            }
        },
    ) { padding ->
        val list = podcasts ?: return@Scaffold
        val columns = settings?.libraryColumns ?: return@Scaffold
        val text = search.query.trim()
        if (search.open && text.length >= MIN_SEARCH_LENGTH) {
            val matchingPodcasts = list.filter { it.podcast.title.contains(text, ignoreCase = true) }
            val episodes by search.episodes.collectAsStateWithLifecycle()
            Box(Modifier.padding(padding)) {
                SearchResults(matchingPodcasts, episodes, onOpenPodcast, onOpenEpisode)
            }
            return@Scaffold
        }
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

/** Replaces the top bar while searching. Back closes the search. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(search: LibrarySearchViewModel) {
    val focusRequester = remember { FocusRequester() }
    // Only a search that was just opened gets the keyboard, not one the user comes back to.
    LaunchedEffect(Unit) {
        if (search.query.isEmpty()) focusRequester.requestFocus()
    }
    TopAppBar(
        navigationIcon = { BackButton(search::close) },
        title = {
            TextField(
                value = search.query,
                onValueChange = { search.query = it },
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusRequester.freeFocus() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        },
        actions = {
            if (search.query.isNotEmpty()) {
                IconButton(onClick = { search.query = "" }) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.clear_search))
                }
            }
        },
    )
}

/** Matching podcasts first, then matching episodes, newest first. */
@Composable
private fun SearchResults(
    podcasts: List<PodcastWithCounts>,
    episodes: List<EpisodeWithPodcast>?,
    onOpenPodcast: (Long) -> Unit,
    onOpenEpisode: (Long) -> Unit,
) {
    // Before the first episode query has run, an empty list does not yet mean "nothing found".
    if (episodes == null && podcasts.isEmpty()) return
    if (podcasts.isEmpty() && episodes.isNullOrEmpty()) {
        EmptyState(stringResource(R.string.library_search_no_results), Icons.Rounded.Search)
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        if (podcasts.isNotEmpty()) {
            item(key = "podcasts-header") { SectionHeader(stringResource(R.string.podcasts)) }
            items(podcasts, key = { "podcast-${it.podcast.id}" }) { item ->
                ListItem(
                    headlineContent = {
                        Text(item.podcast.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = {
                        Artwork(item.podcast.imageUrl, Modifier.size(56.dp), cornerRadius = 10.dp)
                    },
                    modifier = Modifier.clickable { onOpenPodcast(item.podcast.id) },
                )
            }
        }
        if (!episodes.isNullOrEmpty()) {
            item(key = "episodes-header") { SectionHeader(stringResource(R.string.episodes)) }
            items(episodes, key = { "episode-${it.episode.id}" }) { item ->
                EpisodeRow(
                    episode = item.episode,
                    podcastTitle = item.podcast.title,
                    podcastImageUrl = item.podcast.imageUrl,
                    onClick = { onOpenEpisode(item.episode.id) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

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
