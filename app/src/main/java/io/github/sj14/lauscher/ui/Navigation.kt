package io.github.sj14.lauscher.ui

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
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
    const val SETTINGS = "settings"
    const val DOWNLOADS = "downloads"

    fun podcast(id: Long) = "podcast/$id"
    fun episode(id: Long) = "episode/$id"
}

private const val SCREEN_FADE_MS = 150

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.LIBRARY, R.string.library, Icons.Rounded.GridView),
    Tab(Routes.NEW, R.string.new_episodes, Icons.Rounded.NewReleases),
    Tab(Routes.QUEUE, R.string.tab_queue, Icons.AutoMirrored.Rounded.PlaylistPlay),
    Tab(Routes.SEARCH, R.string.discover, Icons.Rounded.Explore),
    Tab(Routes.SETTINGS, R.string.tab_settings, Icons.Rounded.Settings),
)

@Composable
fun LauscherNavigation(openPlayer: Boolean, onPlayerOpened: () -> Unit) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    // The tab the current screen was opened from, so it stays highlighted on detail screens.
    var selectedTab by rememberSaveable { mutableStateOf(Routes.LIBRARY) }
    // Going back can return to another tab's start screen.
    LaunchedEffect(route) {
        if (tabs.any { it.route == route }) selectedTab = route!!
    }

    // Set right away: switching back to a tab can restore one of its detail screens, such as a podcast.
    fun selectTab(tabRoute: String) {
        selectedTab = tabRoute
        switchTab(navController, tabRoute)
    }

    // The player lies on top of the screens instead of being one, so the screen below stays
    // unchanged while it slides up and down.
    var playerOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(openPlayer) {
        if (openPlayer) {
            playerOpen = true
            onPlayerOpened()
        }
    }

    fun onTabClick(tabRoute: String) {
        if (playerOpen) {
            playerOpen = false
            if (tabRoute == selectedTab) return
        }
        if (tabRoute == selectedTab) {
            // Tapping the current tab again goes back to its start, e.g. from a podcast to the library.
            navController.popBackStack(tabRoute, inclusive = false)
        } else {
            selectTab(tabRoute)
        }
    }

    // The player covers everything above the navigation bar, which stays visible below it.
    val density = LocalDensity.current
    var navigationBarHeight by remember { mutableStateOf(0.dp) }

    Box {
        // Each screen handles the status bar itself.
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = {
                Column {
                    MiniPlayer(onOpen = { playerOpen = true })
                    BottomBar(
                        selectedTab = selectedTab,
                        onTabClick = ::onTabClick,
                        modifier = Modifier.onSizeChanged { navigationBarHeight = with(density) { it.height.toDp() } },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.LIBRARY,
                // Much shorter than the default of 700 ms, during which both screens are drawn.
                enterTransition = { fadeIn(tween(SCREEN_FADE_MS)) },
                exitTransition = { fadeOut(tween(SCREEN_FADE_MS)) },
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            ) {
                val openPodcast = { id: Long -> navController.navigate(Routes.podcast(id)) }
                val openEpisode = { id: Long -> navController.navigate(Routes.episode(id)) }
                val back: () -> Unit = { navController.popBackStack() }
                val idArgument = listOf(navArgument("id") { type = NavType.LongType })

                composable(Routes.LIBRARY) { LibraryScreen(onOpenPodcast = openPodcast) }
                composable(Routes.NEW) { NewEpisodesScreen(onOpenEpisode = openEpisode) }
                composable(Routes.QUEUE) { QueueScreen(onOpenEpisode = openEpisode) }
                composable(Routes.SEARCH) { SearchScreen(onOpenPodcast = openPodcast) }
                composable(Routes.PODCAST, idArgument) { entry ->
                    PodcastScreen(entry.arguments!!.getLong("id"), onBack = back, onOpenEpisode = openEpisode)
                }
                composable(Routes.EPISODE, idArgument) { entry ->
                    EpisodeScreen(entry.arguments!!.getLong("id"), onBack = back, onOpenPodcast = openPodcast)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) })
                }
                composable(Routes.DOWNLOADS) { DownloadsScreen(onBack = back, onOpenEpisode = openEpisode) }
            }
        }

        AnimatedVisibility(
            visible = playerOpen,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier
                .padding(bottom = navigationBarHeight)
                // The navigation bar already keeps clear of the system bar.
                .consumeWindowInsets(WindowInsets.navigationBars)
                // Slides in from behind the navigation bar instead of over it.
                .clipToBounds(),
        ) {
            // Registered after the NavHost, so back closes the player instead of leaving the screen below.
            BackHandler { playerOpen = false }
            // The surface makes the player opaque and sets the theme's text and icon colors,
            // which the screens below get from the Scaffold.
            Surface(Modifier.fillMaxSize()) {
                PlayerScreen(
                    onClose = { playerOpen = false },
                    onShowEpisode = { id ->
                        playerOpen = false
                        navController.navigate(Routes.episode(id))
                    },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(selectedTab: String, onTabClick: (String) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(modifier) {
        for (tab in tabs) {
            NavigationBarItem(
                selected = selectedTab == tab.route,
                onClick = { onTabClick(tab.route) },
                icon = { Icon(tab.icon, null) },
                label = { Text(stringResource(tab.label), maxLines = 1, softWrap = false) },
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
