package com.totootao.tingshu.ui.shelf

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.totootao.tingshu.ui.Routes
import com.totootao.tingshu.data.LibraryRepository
import com.totootao.tingshu.data.SourceRepository
import com.totootao.tingshu.data.db.FavoriteEntity
import com.totootao.tingshu.data.db.HistoryEntity
import com.totootao.tingshu.playback.PlaybackManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShelfViewModel : ViewModel() {
    val favorites = LibraryRepository.favoritesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val history = LibraryRepository.historyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * 从书架打开:优先恢复上次收听的章节;章节未缓存时先联网加载。
     */
    fun resume(favorite: FavoriteEntity) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val book = LibraryRepository.toBook(favorite)
            val sourceId = favorite.sourceId
            var episodes = book.episodeList
            if (episodes.isNullOrEmpty()) {
                val source = SourceRepository.findById(sourceId) ?: return@launch
                episodes = runCatching {
                    source.getBookDetailInfo(book.bookUrl, loadEpisodes = true, loadFullPages = true)
                }.getOrNull()?.playList ?: return@launch
                LibraryRepository.updateEpisodes(book, episodes, true)
            }
            val index = episodes.indexOfFirst { it.url == book.currentEpisodeUrl }.coerceAtLeast(0)
            PlaybackManager.play(book, episodes, index, startPositionMs = book.currentEpisodePosition)
        }
    }

    fun remove(favorite: FavoriteEntity) {
        viewModelScope.launch {
            LibraryRepository.removeFavorite(favorite.sourceId, favorite.bookUrl)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShelfScreen(navController: NavHostController, vm: ShelfViewModel = viewModel()) {
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("书架") },
            actions = {
                IconButton(onClick = { navController.navigate(Routes.SEARCH) }) {
                    Icon(Icons.Filled.Search, contentDescription = "搜索")
                }
            }
        )
        if (favorites.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("书架空空如也,去搜索添加吧", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
            items(favorites, key = { it.bookUrl + it.sourceId }) { fav ->
                Column(
                    modifier = Modifier
                        .clickable { vm.resume(fav) }
                        .padding(8.dp)
                ) {
                    Box {
                        AsyncImage(
                            model = fav.coverUrl,
                            contentDescription = fav.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.72f)
                                .clip(MaterialTheme.shapes.medium)
                        )
                        IconButton(
                            onClick = { vm.remove(fav) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "移除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        fav.title,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        fav.currentEpisodeName?.let { "听到: $it" } ?: "未开始",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
