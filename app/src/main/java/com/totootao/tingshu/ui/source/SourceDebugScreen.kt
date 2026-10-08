package com.totootao.tingshu.ui.source

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.eprendre.tingshu.sources.TingShu
import com.github.eprendre.tingshu.utils.Book
import com.totootao.tingshu.data.SourceRepository
import com.totootao.tingshu.playback.PlaybackManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DebugLogLine(val tag: String, val text: String)

/**
 * 源调试页:输入 cat 调试分类,其他关键词调试搜索与详情。
 */
class SourceDebugViewModel : ViewModel() {
    val logs = MutableStateFlow<List<DebugLogLine>>(emptyList())
    val running = MutableStateFlow(false)

    var source: TingShu? = null

    private fun log(tag: String, text: String) {
        logs.value = logs.value + DebugLogLine(tag, text)
    }

    fun run(sourceId: String, keyword: String) {
        if (running.value) return
        val src = source ?: SourceRepository.findById(sourceId) ?: return
        source = src
        running.value = true
        logs.value = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (keyword == "cat") {
                    log("INFO", "== 调试分类: ${src.getName()} ==")
                    val menus = src.getCategoryMenus()
                    log("INFO", "大分类数量: ${menus.size}")
                    menus.forEach { menu ->
                        log("MENU", "${menu.title} (${menu.tabs.size} 个子分类)")
                        menu.tabs.forEach { tab ->
                            log("TAB", "  ${tab.title} -> ${tab.url}")
                        }
                    }
                    val firstTab = menus.firstOrNull()?.tabs?.firstOrNull()
                    if (firstTab != null) {
                        log("INFO", "== 请求第一个分类列表: ${firstTab.title} ==")
                        val cat = src.getCategoryList(firstTab.url)
                        log("INFO", "page ${cat.currentPage}/${cat.totalPage}, 共 ${cat.list.size} 本, next=${cat.nextUrl}")
                        cat.list.take(5).forEach { b ->
                            log("BOOK", "${b.title} | ${b.author} | ${b.artist} | ${b.status}")
                        }
                        if (cat.nextUrl.isNotBlank()) {
                            val next = src.getCategoryList(cat.nextUrl)
                            log("INFO", "第二页: 共 ${next.list.size} 本")
                        }
                    }
                } else {
                    log("INFO", "== 调试搜索: ${src.getName()} 关键词=$keyword ==")
                    val (books, totalPage) = src.search(keyword, 1)
                    log("INFO", "共 ${books.size} 条, 总页数 $totalPage")
                    books.take(5).forEach { b ->
                        log("BOOK", "${b.title} | ${b.author} | ${b.artist}")
                        log("URL", "  ${b.bookUrl}")
                    }
                    books.firstOrNull()?.let { b ->
                        log("INFO", "== 请求第一本书详情: ${b.title} ==")
                        val detail = src.getBookDetailInfo(b.bookUrl, loadEpisodes = true, loadFullPages = false)
                        log("INFO", "章节数: ${detail.playList.size}")
                        detail.playList.take(3).forEach { ep ->
                            log("EPISODE", "${ep.title} -> ${ep.url}")
                        }
                        if (detail.playList.isNotEmpty()) {
                            log("INFO", "== 提取第一个章节音频 ==")
                            val resolved = PlaybackManager.extractBlocking(src, detail.playList[0].url)
                            log("RESULT", resolved?.audioUrl ?: "提取失败")
                        }
                    }
                }
                log("INFO", "== 调试完成 ==")
            } catch (t: Throwable) {
                log("ERROR", t.message ?: t.toString())
                t.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { running.value = false }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceDebugScreen(sourceId: String, vm: SourceDebugViewModel = viewModel()) {
    val logs by vm.logs.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    var keyword by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("调试: ${vm.source?.getName() ?: ""}") },
            navigationIcon = {
                IconButton(onClick = { }) { }
            }
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = keyword,
                onValueChange = { keyword = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入关键词;cat = 调试分类") },
                singleLine = true
            )
            Button(
                onClick = { vm.run(sourceId, keyword) },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("运行")
            }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(logs) { line ->
                Text(
                    "[${line.tag}] ${line.text}",
                    style = MaterialTheme.typography.bodySmall,
                    color = when (line.tag) {
                        "ERROR" -> MaterialTheme.colorScheme.error
                        "INFO", "RESULT" -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp)
                )
            }
        }
    }
}
