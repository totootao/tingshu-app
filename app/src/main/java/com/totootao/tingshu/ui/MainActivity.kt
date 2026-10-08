package com.totootao.tingshu.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import coil.compose.AsyncImage
import com.totootao.tingshu.playback.PlaybackManager
import com.totootao.tingshu.ui.detail.BookDetailScreen
import com.totootao.tingshu.ui.discover.DiscoverScreen
import com.totootao.tingshu.ui.discover.DiscoverListScreen
import com.totootao.tingshu.ui.player.PlayerScreen
import com.totootao.tingshu.ui.search.SearchScreen
import com.totootao.tingshu.ui.settings.SettingsScreen
import com.totootao.tingshu.ui.shelf.ShelfScreen
import com.totootao.tingshu.ui.source.SourceDebugScreen
import com.totootao.tingshu.ui.source.SourceManageScreen
import com.totootao.tingshu.ui.theme.TingShuTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TingShuTheme {
                AppRoot()
            }
        }
    }
}

object Routes {
    const val SHELF = "shelf"
    const val DISCOVER = "discover"
    const val SOURCES = "sources"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val BOOK_DETAIL = "bookDetail/{sourceId}/{bookUrl}"
    const val DISCOVER_LIST = "discoverList/{sourceId}/{url}"
    const val SOURCE_DEBUG = "sourceDebug/{sourceId}"

    fun bookDetail(sourceId: String, bookUrl: String) =
        "bookDetail/${Uri.encode(sourceId)}/${Uri.encode(bookUrl)}"

    fun discoverList(sourceId: String, url: String) =
        "discoverList/${Uri.encode(sourceId)}/${Uri.encode(url)}"

    fun sourceDebug(sourceId: String) =
        "sourceDebug/${Uri.encode(sourceId)}"
}

private data class TabItem(val route: String, val label: String, val icon: @Composable () -> Unit)

@Composable
fun AppRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val tabs = listOf(
        TabItem(Routes.SHELF, "书架") { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
        TabItem(Routes.DISCOVER, "发现") { Icon(Icons.Filled.Explore, contentDescription = null) },
        TabItem(Routes.SOURCES, "源管理") { Icon(Icons.Filled.Tune, contentDescription = null) },
        TabItem(Routes.SETTINGS, "设置") { Icon(Icons.Filled.Settings, contentDescription = null) }
    )
    val showBottomBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Column {
                    MiniPlayerBar(navController)
                    NavigationBar {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = currentRoute == tab.route,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(Routes.SHELF) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = tab.icon,
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.SHELF,
            modifier = Modifier
                .fillMaxSize()
                .padding(if (showBottomBar) padding else androidx.compose.foundation.layout.PaddingValues(0.dp))
        ) {
            composable(Routes.SHELF) { ShelfScreen(navController) }
            composable(Routes.DISCOVER) { DiscoverScreen(navController) }
            composable(Routes.SOURCES) { SourceManageScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen() }
            composable(Routes.SEARCH) { SearchScreen(navController) }
            composable(Routes.PLAYER) { PlayerScreen(navController) }
            composable(
                Routes.BOOK_DETAIL,
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("bookUrl") { type = NavType.StringType }
                )
            ) {
                BookDetailScreen(
                    navController,
                    sourceId = it.arguments?.getString("sourceId") ?: "",
                    bookUrl = it.arguments?.getString("bookUrl") ?: ""
                )
            }
            composable(
                Routes.DISCOVER_LIST,
                arguments = listOf(
                    navArgument("sourceId") { type = NavType.StringType },
                    navArgument("url") { type = NavType.StringType }
                )
            ) {
                DiscoverListScreen(
                    navController,
                    sourceId = it.arguments?.getString("sourceId") ?: "",
                    url = it.arguments?.getString("url") ?: ""
                )
            }
            composable(
                Routes.SOURCE_DEBUG,
                arguments = listOf(navArgument("sourceId") { type = NavType.StringType })
            ) {
                SourceDebugScreen(
                    sourceId = it.arguments?.getString("sourceId") ?: ""
                )
            }
        }
    }
}

/**
 * 迷你播放条:显示当前播放书籍,点击进入播放页
 */
@Composable
private fun MiniPlayerBar(navController: NavHostController) {
    val currentBook by PlaybackManager.currentBookFlow.collectAsStateWithLifecycle()
    val isPlaying by PlaybackManager.isPlayingFlow.collectAsStateWithLifecycle()
    val currentIndex by PlaybackManager.currentIndexFlow.collectAsStateWithLifecycle()
    val episodes by PlaybackManager.episodesFlow.collectAsStateWithLifecycle()

    val book = currentBook ?: return
    LaunchedEffect(book.sourceId) { }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { navController.navigate(Routes.PLAYER) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = book.coverUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(book.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                episodes.getOrNull(currentIndex)?.title ?: book.currentEpisodeName ?: "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { PlaybackManager.togglePlayPause() }) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放"
            )
        }
    }
}
