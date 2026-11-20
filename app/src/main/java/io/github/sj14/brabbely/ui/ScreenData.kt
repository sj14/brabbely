package io.github.sj14.brabbely.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Holds the latest value of a database query for as long as its screen is in the back stack. */
class ScreenData<T>(flow: Flow<T>) : ViewModel() {
    val value: StateFlow<T?> = flow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * The data of a screen, kept while the user is on another tab. Coming back shows it at once
 * instead of an empty list, so the list does not flicker and keeps its scroll position.
 * Screens that load more than one query give each a different [key].
 */
@Composable
fun <T> rememberScreenData(key: String = "data", query: () -> Flow<T>): State<T?> {
    val model = viewModel(key = key) { ScreenData(query()) }
    return model.value.collectAsStateWithLifecycle()
}
