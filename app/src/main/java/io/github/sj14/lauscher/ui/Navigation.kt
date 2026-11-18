package io.github.sj14.lauscher.ui

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.ui.components.MiniPlayer
import io.github.sj14.lauscher.ui.screens.DownloadsScreen
import io.github.sj14.lauscher.ui.screens.EpisodeScreen
import io.github.sj14.lauscher.ui.screens.LibraryScreen
import io.github.sj14.lauscher.ui.screens.NewEpisodesScreen
import io.github.sj14.lauscher.ui.screens.PlayerScreen
import io.github.sj14.lauscher.ui.screens.PodcastScreen
import io.github.sj14.lauscher.ui.screens.QueueScreen
import io.github.sj14.lauscher.ui.screens.SearchScreen
import io.github.sj14.lauscher.ui.screens.SettingsScreen

private object Routes {
    const val LIBRARY = "library"
    const val NEW = "new"
    const val QUEUE = "queue"
    const val SEARCH = "search"
    const val PODCAST = "podcast/{id}"
    const val EPISODE = "episode/{id}"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"

    fun podcast(id: Long) = "podcast/$id"
    fun episode(id: Long) = "episode/$id"
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIBRARY, R.string.library, Icons.Rounded.GridView),
    Tab(Routes.NEW, R.string.new_episodes, Icons.Rounded.NewReleases),
    Tab(Routes.QUEUE, R.string.queue, Icons.AutoMirrored.Rounded.PlaylistPlay),
    Tab(Routes.SEARCH, R.string.discover, Icons.Rounded.Explore),
)

@Composable
fun LauscherNavigation(openPlayer: Boolean, onPlayerOpened: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTopLevel = tabs.any { it.route == route }

    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            navController.navigate(Routes.PLAYER) { launchSingleTop = true }
            onPlayerOpened()
        }
    }

    // Each screen handles the status bar itself, so the player background can reach behind it.
    Scaffold(
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (route == Routes.PLAYER) return@Scaffold
            // The navigation bar handles the system bar inset itself; without it the mini player has to.
            val insetPadding = if (isTopLevel) Modifier else Modifier.navigationBarsPadding()
            Column(insetPadding) {
                MiniPlayer(onOpen = { navController.navigate(Routes.PLAYER) { launchSingleTop = true } })
                if (isTopLevel) BottomBar(navController, route)
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            val openPodcast = { id: Long -> navController.navigate(Routes.podcast(id)) }
            val openEpisode = { id: Long -> navController.navigate(Routes.episode(id)) }
            val back: () -> Unit = { navController.popBackStack() }
            val idArgument = listOf(navArgument("id") { type = NavType.LongType })

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenPodcast = openPodcast,
                    onOpenSearch = { switchTab(navController, Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.NEW) { NewEpisodesScreen(onOpenEpisode = openEpisode) }
            composable(Routes.QUEUE) { QueueScreen(onOpenEpisode = openEpisode) }
            composable(Routes.SEARCH) { SearchScreen(onOpenPodcast = openPodcast) }
            composable(Routes.PODCAST, idArgument) { entry ->
                PodcastScreen(entry.arguments!!.getLong("id"), onBack = back, onOpenEpisode = openEpisode)
            }
            composable(Routes.EPISODE, idArgument) { entry ->
                EpisodeScreen(entry.arguments!!.getLong("id"), onBack = back, onOpenPodcast = openPodcast)
            }
            composable(
                Routes.PLAYER,
                enterTransition = { slideInVertically { it } },
                exitTransition = { slideOutVertically { it } },
                popExitTransition = { slideOutVertically { it } },
            ) {
                PlayerScreen(onClose = back, onShowEpisode = openEpisode)
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onBack = back, onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) })
            }
            composable(Routes.DOWNLOADS) { DownloadsScreen(onBack = back, onOpenEpisode = openEpisode) }
        }
    }
}

@Composable
private fun BottomBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        for (tab in tabs) {
            NavigationBarItem(
                selected = currentRoute == tab.route,
                onClick = { switchTab(navController, tab.route) },
                icon = { Icon(tab.icon, null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

private fun switchTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
