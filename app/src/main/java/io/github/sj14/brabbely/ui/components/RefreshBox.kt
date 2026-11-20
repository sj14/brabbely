package io.github.sj14.brabbely.ui.components

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import io.github.sj14.brabbely.LOG_TAG
import io.github.sj14.brabbely.R
import io.github.sj14.brabbely.data.db.Podcast
import io.github.sj14.brabbely.ui.LocalContainer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Pulling down runs [onRefresh]; the indicator shows until it is done. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(
    onRefresh: suspend () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    onRefresh()
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = modifier,
        content = content,
    )
}

/** Pulling down refreshes all subscriptions. Feeds that could not be loaded are reported. */
@Composable
fun RefreshAllBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val repository = LocalContainer.current.repository
    val context = LocalContext.current
    val resources = LocalResources.current
    RefreshBox(
        onRefresh = {
            val failures = repository.refreshAll()
            if (failures > 0) {
                val message = resources.getQuantityString(R.plurals.import_failed_feeds, failures, failures)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            }
        },
        modifier = modifier,
        content = content,
    )
}

/**
 * Refreshes one podcast, as pulling down on its screen and its menu in the library do.
 * If the feed cannot be loaded, the user is told.
 */
@Composable
fun rememberRefreshPodcast(): suspend (Podcast) -> Unit {
    val repository = LocalContainer.current.repository
    val context = LocalContext.current
    val failedMessage = stringResource(R.string.error_loading)
    return remember(repository, context, failedMessage) {
        { podcast ->
            try {
                repository.refreshAndDownload(podcast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Refreshing ${podcast.feedUrl} failed", e)
                Toast.makeText(context, failedMessage, Toast.LENGTH_LONG).show()
            }
        }
    }
}
