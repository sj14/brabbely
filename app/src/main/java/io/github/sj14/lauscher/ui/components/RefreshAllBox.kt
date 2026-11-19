package io.github.sj14.lauscher.ui.components

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
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.ui.LocalContainer
import kotlinx.coroutines.launch

/** Pulling down refreshes all subscriptions. Feeds that could not be loaded are reported. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshAllBox(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val repository = LocalContainer.current.repository
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    val failures = repository.refreshAll()
                    if (failures > 0) {
                        val message = resources.getQuantityString(R.plurals.import_failed_feeds, failures, failures)
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                } finally {
                    refreshing = false
                }
            }
        },
        modifier = modifier,
        content = content,
    )
}
