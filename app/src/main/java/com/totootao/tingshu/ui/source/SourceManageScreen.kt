package com.totootao.tingshu.ui.source

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.totootao.tingshu.ui.Routes
import com.totootao.tingshu.data.SourceRepository
import com.totootao.tingshu.data.SubscriptionManager
import com.totootao.tingshu.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SourceManageViewModel : ViewModel() {
    val sources = MutableStateFlow<List<com.github.eprendre.tingshu.sources.TingShu>>(emptyList())
    val subscriptions = ServiceLocator.database.externalSourceDao().observeAll()
    val enabledIds = MutableStateFlow<Set<String>>(emptySet())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    init {
        refresh()
    }

    fun refresh() {
        val all = SourceRepository.allSources()
        sources.value = all
        val stored = ServiceLocator.settings.enabledSourceIds
        enabledIds.value = stored ?: all.map { it.getSourceId() }.toSet()
    }

    fun toggle(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            SourceRepository.setSourceEnabled(sourceId, enabled)
            refresh()
        }
    }

    fun addSubscription(url: String, name: String, onDone: (Boolean, String) -> Unit) {
        busy.value = true
        viewModelScope.launch {
            val result = SubscriptionManager.addOrUpdateSubscription(url, name)
            busy.value = false
            result.fold(
                onSuccess = {
                    refresh()
                    onDone(true, "订阅成功: ${it.name}")
                },
                onFailure = { onDone(false, "订阅失败: ${it.message}") }
            )
        }
    }

    fun removeSubscription(entity: com.totootao.tingshu.data.db.ExternalSourceEntity) {
        viewModelScope.launch {
            SubscriptionManager.removeSubscription(entity)
            refresh()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManageScreen(navController: NavHostController, vm: SourceManageViewModel = viewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    val subscriptions by vm.subscriptions.collectAsStateWithLifecycle(initialValue = emptyList())
    val enabledIds by vm.enabledIds.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var toast by remember { mutableStateOf<String?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加订阅")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TopAppBar(title = { Text("源管理") })
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            toast?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item(key = "builtin_header") {
                    Text(
                        "内置源",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                items(sources.filter { it.getSourceId() in SourceRepository.builtInSourceIds() }) { source ->
                    val enabled = enabledIds.contains(source.getSourceId())
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = enabled, onCheckedChange = { vm.toggle(source.getSourceId(), it) })
                        Column(modifier = Modifier.weight(1f)) {
                            Text(source.getName(), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                source.getDesc(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (source.isWebViewNotRequired()) {
                            Text("免WebView", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { navController.navigate(Routes.sourceDebug(source.getSourceId())) }) {
                            Icon(Icons.Filled.BugReport, contentDescription = "调试")
                        }
                    }
                }
                item(key = "sub_header") {
                    Text(
                        "外置源订阅(与原版\"我的听书\"jar 格式兼容)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                if (subscriptions.isEmpty()) {
                    item(key = "sub_empty") {
                        Text(
                            "暂无订阅。点击右下角 + 添加订阅接口,或把 jar 放到\nfiles/jars 目录(文件名即包名)。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                items(subscriptions, key = { "sub_${it.id}" }) { sub ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            Checkbox(checked = sub.enabled, onCheckedChange = { vm.toggle(sub.entryPackage, it) })
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${sub.name} v${sub.version}", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${sub.entryPackage} | ${sub.updateMsg}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { vm.removeSubscription(sub) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除订阅")
                            }
                        }
                    }
                }
                item(key = "tip") {
                    Text(
                        "提示: jar 包与原版 app 的外置源格式完全一致,可直接沿用社区源的订阅接口。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        var url by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加订阅") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("订阅接口地址(JSON)") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    vm.addSubscription(url, name.ifBlank { "订阅源" }) { ok, msg ->
                        toast = msg
                    }
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("取消") }
            }
        )
    }
}
