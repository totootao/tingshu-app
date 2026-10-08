package com.totootao.tingshu.ui.discover

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.github.eprendre.tingshu.utils.Category
import com.github.eprendre.tingshu.utils.CategoryMenu
import com.totootao.tingshu.ui.Routes
import com.totootao.tingshu.data.SourceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 发现页:源选择 + 分类菜单
 */
class DiscoverViewModel : ViewModel() {
    val sources = MutableStateFlow<List<TingShu>>(emptyList())
    val selectedSource = MutableStateFlow<TingShu?>(null)
    val menus = MutableStateFlow<List<CategoryMenu>>(emptyList())
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    init {
        refreshSources()
    }

    fun refreshSources() {
        val list = SourceRepository.discoverableSources()
        sources.value = list
        if (selectedSource.value == null && list.isNotEmpty()) {
            selectSource(list.first())
        }
    }

    fun selectSource(source: TingShu) {
        selectedSource.value = source
        loading.value = true
        error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { source.getCategoryMenus() }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        menus.value = it
                        loading.value = false
                    }
                }
                .onFailure { t ->
                    withContext(Dispatchers.Main) {
                        error.value = t.message ?: "加载分类失败"
                        loading.value = false
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DiscoverScreen(navController: NavHostController, vm: DiscoverViewModel = viewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val selected by vm.selectedSource.collectAsStateWithLifecycle()
    val menus by vm.menus.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("发现") })
        // 源选择 chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.lazy.LazyRow {
                items(sources, key = { it.getSourceId() }) { source ->
                    FilterChip(
                        selected = selected?.getSourceId() == source.getSourceId(),
                        onClick = { vm.selectSource(source) },
                        label = { Text(source.getName()) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(
                "加载失败: $it",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            menus.forEach { menu ->
                item(key = "menu_${menu.title}") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(menu.title, style = MaterialTheme.typography.titleSmall)
                            Row(modifier = Modifier.fillMaxWidth()) {
                                androidx.compose.foundation.layout.FlowRow(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    menu.tabs.forEach { tab ->
                                        FilterChip(
                                            selected = false,
                                            onClick = {
                                                navController.navigate(
                                                    Routes.discoverList(
                                                        selected?.getSourceId() ?: "",
                                                        tab.url
                                                    )
                                                )
                                            },
                                            label = { Text(tab.title) },
                                            modifier = Modifier.padding(end = 6.dp, top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分类列表页
 */
class DiscoverListViewModel : ViewModel() {
    val category = MutableStateFlow<Category?>(null)
    val loading = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    private var source: TingShu? = null

    fun load(sourceId: String, url: String) {
        if (source == null) source = SourceRepository.findById(sourceId)
        val src = source ?: return
        loading.value = true
        error.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { src.getCategoryList(url) }
                .onSuccess {
                    withContext(Dispatchers.Main) {
                        val old = category.value
                        category.value = if (old != null && old.currentUrl == it.currentUrl) it else it
                        loading.value = false
                    }
                }
                .onFailure { t ->
                    withContext(Dispatchers.Main) {
                        error.value = t.message ?: "加载失败"
                        loading.value = false
                    }
                }
        }
    }

    fun loadMore() {
        val cat = category.value ?: return
        if (cat.nextUrl.isBlank() || loading.value) return
        val src = source ?: return
        loading.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { src.getCategoryList(cat.nextUrl) }
                .onSuccess { next ->
                    withContext(Dispatchers.Main) {
                        category.value = Category(
                            cat.list + next.list,
                            next.currentPage,
                            next.totalPage,
                            cat.currentUrl,
                            next.nextUrl
                        )
                        loading.value = false
                    }
                }
                .onFailure { t ->
                    withContext(Dispatchers.Main) {
                        error.value = t.message ?: "加载失败"
                        loading.value = false
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverListScreen(
    navController: NavHostController,
    sourceId: String,
    url: String,
    vm: DiscoverListViewModel = viewModel()
) {
    val category by vm.category.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    LaunchedEffect(sourceId, url) { vm.load(sourceId, url) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("分类") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text("加载失败: $it", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }
        val cat = category
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            cat?.let { c ->
                items(c.list) { book ->
                    DiscoverBookItem(book) {
                        navController.navigate(Routes.bookDetail(sourceId, book.bookUrl))
                    }
                }
                if (c.nextUrl.isNotBlank() && c.currentPage < c.totalPage) {
                    item {
                        OutlinedButton(
                            onClick = { vm.loadMore() },
                            modifier = Modifier.fillMaxWidth().padding(16.dp)
                        ) {
                            Text("加载更多 (${c.currentPage}/${c.totalPage})")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverBookItem(book: Book, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            AsyncImage(
                model = book.coverUrl,
                contentDescription = book.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 64.dp, height = 88.dp)
                    .clip(MaterialTheme.shapes.small)
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)
            ) {
                Text(book.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text("作者: ${book.author}", style = MaterialTheme.typography.bodySmall)
                if (book.artist.isNotBlank()) Text(book.artist, style = MaterialTheme.typography.bodySmall)
                if (book.status.isNotBlank()) Text(
                    book.status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
