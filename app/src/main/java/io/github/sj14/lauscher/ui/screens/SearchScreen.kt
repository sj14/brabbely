package io.github.sj14.lauscher.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.PodcastRepository
import io.github.sj14.lauscher.data.feed.PodcastSearch
import io.github.sj14.lauscher.data.feed.SearchResult
import io.github.sj14.lauscher.ui.LocalContainer
import io.github.sj14.lauscher.ui.components.Artwork
import io.github.sj14.lauscher.ui.components.EmptyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holds the search across navigation, so the results are still there when the user comes back
 * from a podcast they opened.
 */
class SearchViewModel(
    private val search: PodcastSearch,
    private val repository: PodcastRepository,
) : ViewModel() {
    var query by mutableStateOf("")
    var results by mutableStateOf<List<SearchResult>?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var failed by mutableStateOf(false)
        private set

    fun search() {
        val text = query.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            loading = true
            failed = false
            try {
                results = withContext(Dispatchers.IO) { search.search(text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Searching for $text failed", e)
                failed = true
            }
            loading = false
        }
    }

    /** Loads the feed for a preview and returns the podcast id, or null if it could not be loaded. */
    suspend fun open(feedUrl: String): Long? {
        loading = true
        failed = false
        try {
            return repository.preview(feedUrl)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Loading $feedUrl failed", e)
            failed = true
            return null
        } finally {
            loading = false
        }
    }
}

/** Finds podcasts by name, or opens a feed directly when a URL is entered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onBack: () -> Unit, onOpenPodcast: (Long) -> Unit) {
    val container = LocalContainer.current
    val model = viewModel { SearchViewModel(container.search, container.repository) }
    val scope = rememberCoroutineScope()

    fun open(feedUrl: String) {
        scope.launch { model.open(feedUrl)?.let(onOpenPodcast) }
    }

    fun submit() {
        val text = model.query.trim()
        if (text.startsWith("http://") || text.startsWith("https://")) {
            open(text)
        } else {
            model.search()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.discover)) }, navigationIcon = { BackButton(onBack) })
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = model.query,
                onValueChange = { model.query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { submit() }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Box(Modifier.fillMaxSize()) {
                val list = model.results
                when {
                    model.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    model.failed -> EmptyState(stringResource(R.string.error_loading))
                    list == null -> EmptyState(stringResource(R.string.search_intro), Icons.Rounded.Search)
                    list.isEmpty() -> EmptyState(stringResource(R.string.search_no_results), Icons.Rounded.Search)
                    else -> LazyColumn {
                        items(list, key = { it.feedUrl }) { result ->
                            ListItem(
                                headlineContent = {
                                    Text(result.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(result.author, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = { Artwork(result.imageUrl, Modifier.size(56.dp), cornerRadius = 10.dp) },
                                modifier = Modifier.clickable { open(result.feedUrl) },
                            )
                        }
                    }
                }
            }
        }
    }
}
