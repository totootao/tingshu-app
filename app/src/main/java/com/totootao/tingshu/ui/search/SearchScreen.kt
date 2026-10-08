package com.totootao.tingshu.ui.search

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.github.eprendre.tingshu.sources.ISearchVerification
import com.github.eprendre.tingshu.sources.TingShu
import com.github.eprendre.tingshu.utils.Book
import com.github.eprendre.tingshu.utils.WebViewRequest
import com.totootao.tingshu.ui.Routes
import com.totootao.tingshu.data.SourceRepository
import com.totootao.tingshu.di.ServiceLocator
import com.totootao.tingshu.playback.PlaybackManager
import com.totootao.tingshu.webview.WebViewExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 搜索结果:每个启用源一组
 */
data class SourceSearchResult(
    val source: TingShu,
    val books: List<Book> = emptyList(),
    val currentPage: Int = 1,
    val totalPage: Int = 1,
    val loading: Boolean = false,
    val error: String? = null,
    val verified: Boolean = true
)

class SearchViewModel : ViewModel() {
    val results = MutableStateFlow<List<SourceSearchResult>>(emptyList())
    val keyword = MutableStateFlow("")
    val isSearching = MutableStateFlow(false)
    /** 需要搜索验证的源 */
    val verificationRequest = MutableStateFlow<TingShu?>(null)

    private val aggregate: Boolean get() = ServiceLocator.settings.aggregateSearch

    fun search(text: String) {
        keyword.value = text
        if (text.isBlank()) return
        val sources = if (aggregate) SourceRepository.searchableSources() else emptyList()
        if (aggregate) {
            results.value = sources.map { SourceSearchResult(it) }
        } else {
            results.value = emptyList()
        }
        // 聚合搜索:并发各源
        isSearching.value = true
        sources.forEach { source -> searchSource(source, text, 1, replace = true) }
        // 非聚合:仅搜索第一个可搜索源(界面切换源时再搜)
        if (!aggregate) {
            SourceRepository.searchableSources().firstOrNull()?.let {
                searchSource(it, text, 1, replace = true)
            }
        }
    }

    fun searchSingle(source: TingShu) {
        val text = keyword.value
        if (text.isBlank()) return
        // 替换为单源结果
        results.value = listOf(SourceSearchResult(source))
        searchSource(source, text, 1, replace = true)
    }

    fun loadMore(result: SourceSearchResult) {
        searchSource(result.source, keyword.value, result.currentPage + 1, replace = false)
    }

    private fun searchSource(source: TingShu, keywords: String, page: Int, replace: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            updateResult(source) {
                if (replace) it.copy(loading = true, error = null) else it.copy(loading = true)
            }
            // 搜索验证检查
            if (source is ISearchVerification && !source.isSearchValidated()) {
                updateResult(source) { it.copy(loading = false, verified = false) }
                verificationRequest.value = source
                return@launch
            }
            runCatching { source.search(keywords, page) }
                .onSuccess { (books, totalPage) ->
                    updateResult(source) {
                        if (replace) {
                            it.copy(books = books, currentPage = page, totalPage = totalPage, loading = false, error = null)
                        } else {
                            it.copy(
                                books = it.books + books,
                                currentPage = page,
                                totalPage = totalPage,
                                loading = false,
                                error = null
                            )
                        }
                    }
                }
                .onFailure { t ->
                    updateResult(source) { it.copy(loading = false, error = t.message ?: "搜索失败") }
                }
            maybeFinish()
        }
    }

    /** 用户完成验证后继续 */
    fun onVerificationDone() {
        val source = verificationRequest.value ?: return
        verificationRequest.value = null
        searchSource(source, keyword.value, 1, replace = true)
    }

    fun dismissVerification() {
        verificationRequest.value = null
    }

    private suspend fun maybeFinish() {
        if (results.value.none { it.loading }) {
            withContext(Dispatchers.Main) { isSearching.value = false }
        }
    }

    private fun updateResult(source: TingShu, transform: (SourceSearchResult) -> SourceSearchResult) {
        val list = results.value.toMutableList()
        val idx = list.indexOfFirst { it.source.getSourceId() == source.getSourceId() }
        if (idx >= 0) list[idx] = transform(list[idx])
        results.value = list
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navController: NavHostController, vm: SearchViewModel = viewModel()) {
    var input by remember { mutableStateOf("") }
    val results by vm.results.collectAsStateWithLifecycle()
    val isSearching by vm.isSearching.collectAsStateWithLifecycle()
    val verifySource by vm.verificationRequest.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("搜索") },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            }
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入书名或作者") },
                singleLine = true
            )
            Button(
                onClick = { vm.search(input) },
                modifier = Modifier.padding(start = 8.dp)
            ) { Text("搜索") }
        }
        if (isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            results.forEach { result ->
                item(key = "header_${result.source.getSourceId()}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            result.source.getName(),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.weight(1f))
                        if (!result.verified) {
                            TextButton(onClick = { vm.verificationRequest.value = result.source }) {
                                Text("需要验证")
                            }
                        }
                        if (result.loading) {
                            LinearProgressIndicator(Modifier.size(20.dp))
                        } else if (result.error != null) {
                            Text(
                                result.error ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (result.books.isEmpty()) {
                            Text("无结果", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                items(result.books) { book ->
                    BookResultItem(book) {
                        navController.navigate(
                            Routes.bookDetail(result.source.getSourceId(), book.bookUrl)
                        )
                    }
                }
                if (result.books.isNotEmpty() && result.currentPage < result.totalPage) {
                    item(key = "more_${result.source.getSourceId()}") {
                        OutlinedButton(
                            onClick = { vm.loadMore(result) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text("加载更多 (${result.currentPage}/${result.totalPage})")
                        }
                    }
                }
            }
        }
    }

    // 搜索验证对话框
    verifySource?.let { source ->
        AlertDialog(
            onDismissRequest = { vm.dismissVerification() },
            title = { Text("搜索验证") },
            text = { Text("${source.getName()} 需要完成人机验证后才能搜索,点击\"去验证\"打开验证页面。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.dismissVerification()
                    val src = source
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        val verification = src as ISearchVerification
                        WebViewExecutor.executeBlocking(
                            ServiceLocator.appContext,
                            WebViewRequest(
                                mode = WebViewRequest.MODE_VERIFY,
                                url = verification.getSearchVerificationUrl(vm.keyword.value),
                                delayMs = verification.getSearchDelayMs()
                            )
                        )
                        vm.onVerificationDone()
                    }
                }) { Text("去验证") }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissVerification() }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun BookResultItem(book: Book, onClick: () -> Unit) {
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
                if (book.status.isNotBlank()) Text(book.status, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (book.intro.isNotBlank()) {
                    Text(
                        book.intro,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
