package com.totootao.tingshu.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.github.eprendre.tingshu.sources.TingShu
import com.github.eprendre.tingshu.utils.Book
import com.github.eprendre.tingshu.utils.BookDetail
import com.github.eprendre.tingshu.utils.Episode
import com.totootao.tingshu.data.LibraryRepository
import com.totootao.tingshu.data.SourceRepository
import com.totootao.tingshu.playback.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookDetailViewModel : ViewModel() {
    val book = MutableStateFlow<Book?>(null)
    val episodes = MutableStateFlow<List<Episode>>(emptyList())
    val loading = MutableStateFlow(false)
    val loadingFull = MutableStateFlow(false)
    val isFavorite = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    var source: TingShu? = null
        private set
    private var hasFull = false
    private var loaded = false

    fun load(sourceId: String, bookUrl: String) {
        if (loaded) return
        loaded = true
        source = SourceRepository.findById(sourceId)
        val src = source ?: run {
            error.value = "找不到源: $sourceId"
            return
        }
        loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val favorite = LibraryRepository.getFavorite(sourceId, bookUrl)
                val detail = src.getBookDetailInfo(bookUrl, loadEpisodes = true, loadFullPages = false)
                favorite?.let {
                    it.episodeList = detail.playList
                    it.hasFullEpisodes = false
                }
                Triple(favorite, detail, src.isMultipleEpisodePages())
            }.onSuccess { (favorite, detail, multiple) ->
                withContext(Dispatchers.Main) {
                    val b = favorite ?: Book("", bookUrl, "", "", "").apply {
                        this.sourceId = sourceId
                        hasFullEpisodes = false
                    }
                    book.value = b
                    episodes.value = detail.playList
                    hasFull = !multiple
                    isFavorite.value = favorite != null
                    loading.value = false
                }
            }.onFailure { t ->
                withContext(Dispatchers.Main) {
                    error.value = t.message ?: "加载失败"
                    loading.value = false
                }
            }
        }
    }

    fun toggleFavorite() {
        val b = book.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val fav = isFavorite.value
            if (fav) {
                LibraryRepository.removeFavorite(b.sourceId ?: "", b.bookUrl)
                withContext(Dispatchers.Main) { isFavorite.value = false }
            } else {
                LibraryRepository.addFavorite(b)
                withContext(Dispatchers.Main) { isFavorite.value = true }
            }
        }
    }

    /**
     * 播放指定章节。若源章节分页且尚未加载完整,先拉取所有章节。
     */
    fun playAt(episode: Episode) {
        val src = source ?: return
        val b = book.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            var list = episodes.value
            if (src.isMultipleEpisodePages() && !hasFull) {
                withContext(Dispatchers.Main) { loadingFull.value = true }
                runCatching {
                    src.getBookDetailInfo(b.bookUrl, loadEpisodes = true, loadFullPages = true)
                }.onSuccess { detail ->
                    list = detail.playList
                    hasFull = true
                    LibraryRepository.updateEpisodes(b, list, true)
                }
                withContext(Dispatchers.Main) { loadingFull.value = false }
            }
            if (list.isEmpty()) return@launch
            val idx = list.indexOfFirst { it.url == episode.url }.coerceAtLeast(0)
            b.episodeList = list
            b.hasFullEpisodes = hasFull
            withContext(Dispatchers.Main) { episodes.value = list }
            PlaybackManager.play(b, list, idx)
        }
    }

    /**
     * 手动刷新章节列表(重新完整加载)
     */
    fun refreshEpisodes() {
        val src = source ?: return
        val b = book.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { loadingFull.value = true }
            runCatching {
                src.getBookDetailInfo(b.bookUrl, loadEpisodes = true, loadFullPages = true)
            }.onSuccess { detail: BookDetail ->
                hasFull = true
                episodes.value = detail.playList
                LibraryRepository.updateEpisodes(b, detail.playList, true)
            }
            withContext(Dispatchers.Main) { loadingFull.value = false }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    navController: NavHostController,
    sourceId: String,
    bookUrl: String,
    vm: BookDetailViewModel = viewModel()
) {
    LaunchedEffect(sourceId, bookUrl) { vm.load(sourceId, bookUrl) }

    val book by vm.book.collectAsStateWithLifecycle()
    val episodes by vm.episodes.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val loadingFull by vm.loadingFull.collectAsStateWithLifecycle()
    val isFavorite by vm.isFavorite.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val loadingMsg by PlaybackManager.loadingEpisodesMsgFlow.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(book?.title ?: "详情", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            actions = {
                IconButton(onClick = { vm.refreshEpisodes() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新章节")
                }
                IconButton(onClick = { vm.toggleFavorite() }) {
                    Icon(
                        if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "收藏",
                        tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
        if (loadingFull) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                loadingMsg?.let { "正在加载章节列表: $it" } ?: "正在加载全部章节…",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }
        error?.let {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("加载失败: $it", color = MaterialTheme.colorScheme.error)
            }
            return@Column
        }
        val b = book ?: return@Column
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "header") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        AsyncImage(
                            model = b.coverUrl,
                            contentDescription = b.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 96.dp, height = 132.dp)
                                .clip(MaterialTheme.shapes.small)
                        )
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(b.title, style = MaterialTheme.typography.titleMedium)
                            if (b.author.isNotBlank()) Text("作者: ${b.author}", style = MaterialTheme.typography.bodySmall)
                            if (b.artist.isNotBlank()) Text(b.artist, style = MaterialTheme.typography.bodySmall)
                            if (b.status.isNotBlank()) Text(
                                b.status,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                "共 ${episodes.size} 集",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (b.intro.isNotBlank()) {
                        Text(
                            b.intro,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
            items(episodes) { ep ->
                val isCurrent = b.currentEpisodeUrl == ep.url
                Text(
                    text = if (ep.isFree) ep.title else "[收费] ${ep.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else if (!ep.isFree) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.playAt(ep) }
                        .padding(horizontal = 20.dp, vertical = 10.dp)
                )
            }
        }
    }
}
