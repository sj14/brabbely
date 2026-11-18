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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.feed.SearchResult
import io.github.sj14.lauscher.ui.LocalContainer
import io.github.sj14.lauscher.ui.components.Artwork
import io.github.sj14.lauscher.ui.components.EmptyState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Finds podcasts by name, or opens a feed directly when a URL is entered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(onOpenPodcast: (Long) -> Unit) {
    val container = LocalContainer.current
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val errorText = stringResource(R.string.error_loading)

    fun open(feedUrl: String) {
        scope.launch {
            loading = true
            error = null
            try {
                onOpenPodcast(container.repository.preview(feedUrl))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Loading $feedUrl failed", e)
                error = errorText
            }
            loading = false
        }
    }

    fun submit() {
        val text = query.trim()
        if (text.isEmpty()) return
        if (text.startsWith("http://") || text.startsWith("https://")) {
            open(text)
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                results = withContext(Dispatchers.IO) { container.search.search(text) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Searching for $text failed", e)
                error = errorText
            }
            loading = false
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.discover)) }) }) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
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
                val list = results
                when {
                    loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    error != null -> EmptyState(error.orEmpty())
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
