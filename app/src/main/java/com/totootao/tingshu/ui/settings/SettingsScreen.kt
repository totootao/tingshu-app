package com.totootao.tingshu.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.totootao.tingshu.di.ServiceLocator
import com.totootao.tingshu.playback.ChapterCacheManager

/**
 * 设置页:聚合搜索 / 播放 / 缓存 / UA / 关于
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val settings = ServiceLocator.settings

    var aggregate by remember { mutableStateOf(settings.aggregateSearch) }
    var skipSilence by remember { mutableStateOf(settings.skipSilence) }
    var cacheEnabled by remember { mutableStateOf(settings.cacheEnabled) }
    var cacheLimit by remember { mutableStateOf(settings.cacheLimitMb.toString()) }
    var mobileUa by remember { mutableStateOf(settings.mobileUa) }
    var desktopUa by remember { mutableStateOf(settings.desktopUa) }
    var savedHint by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        TopAppBar(title = { Text("设置") })

        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("搜索", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("聚合搜索(所有源合并结果)", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = aggregate,
                        onCheckedChange = {
                            aggregate = it
                            settings.aggregateSearch = it
                        }
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("播放", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("跳过无声部分", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = skipSilence,
                        onCheckedChange = {
                            skipSilence = it
                            settings.skipSilence = it
                        }
                    )
                }
                Text(
                    "今日已收听: ${settings.todayPlaybackTime / 60000} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("缓存", style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("自动缓存后续章节", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = cacheEnabled,
                        onCheckedChange = {
                            cacheEnabled = it
                            settings.cacheEnabled = it
                        }
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("缓存上限(MB)", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = cacheLimit,
                        onValueChange = { cacheLimit = it.filter { c -> c.isDigit() } },
                        singleLine = true,
                        modifier = Modifier.size(width = 120.dp, height = 52.dp)
                    )
                    OutlinedButton(
                        onClick = { settings.cacheLimitMb = cacheLimit.toIntOrNull() ?: 500 },
                        modifier = Modifier.padding(start = 8.dp)
                    ) { Text("保存") }
                }
                OutlinedButton(
                    onClick = { ChapterCacheManager.clearAll() },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("清除音频缓存") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("User-Agent", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = mobileUa,
                    onValueChange = { mobileUa = it },
                    label = { Text("手机版 UA") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(8.dp))
                OutlinedTextField(
                    value = desktopUa,
                    onValueChange = { desktopUa = it },
                    label = { Text("PC 版 UA") },
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        settings.mobileUa = mobileUa
                        settings.desktopUa = desktopUa
                        savedHint = "已保存"
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) { Text("保存 UA") }
                if (savedHint.isNotEmpty()) {
                    Text(savedHint, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("关于", style = MaterialTheme.typography.titleSmall)
                Text(
                    "听书 v1.0.0\n复刻自\"我的听书\"(eprendre/tingshu) 的开源插件架构。\n" +
                        "内置源: 酷我畅听 / 云图有声 / 哔哩哔哩 / 恋听网 / LibriVox。\n" +
                        "外置源 jar 与原版 app 完全兼容。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
