package com.totootao.tingshu.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.totootao.tingshu.playback.ChapterCacheManager
import com.totootao.tingshu.playback.PlaybackManager
import kotlinx.coroutines.launch

private val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f, 2.5f, 3.0f)
private val timerOptions = listOf(15, 30, 60, 90, 120)

fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavHostController) {
    val currentBook by PlaybackManager.currentBookFlow.collectAsStateWithLifecycle()
    val episodes by PlaybackManager.episodesFlow.collectAsStateWithLifecycle()
    val currentIndex by PlaybackManager.currentIndexFlow.collectAsStateWithLifecycle()
    val isPlaying by PlaybackManager.isPlayingFlow.collectAsStateWithLifecycle()
    val position by PlaybackManager.positionFlow.collectAsStateWithLifecycle()
    val duration by PlaybackManager.durationFlow.collectAsStateWithLifecycle()
    val speed by PlaybackManager.speedFlow.collectAsStateWithLifecycle()
    val error by PlaybackManager.errorFlow.collectAsStateWithLifecycle()
    val timerRemain by PlaybackManager.sleepTimerRemainFlow.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showTimer by remember { mutableStateOf(false) }
    var seeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }

    LaunchedEffect(error) {
        error?.let { snackbarHostState.showSnackbar(it) }
    }

    val book = currentBook
    val episode = episodes.getOrNull(currentIndex)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        book?.title ?: "播放器",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
            )

            // 封面
            AsyncImage(
                model = book?.coverUrl,
                contentDescription = book?.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .clip(MaterialTheme.shapes.large)
                    .padding(top = 8.dp)
            )
            Text(
                episode?.title ?: book?.currentEpisodeName ?: "未在播放",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            )

            // 进度
            val pos = if (seeking) seekValue else position.toFloat()
            val dur = duration.toFloat().coerceAtLeast(1f)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(formatTime(pos.toLong()), style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = pos.coerceAtMost(dur),
                    onValueChange = {
                        seeking = true
                        seekValue = it
                    },
                    onValueChangeFinished = {
                        PlaybackManager.seekTo(seekValue.toLong())
                        seeking = false
                    },
                    valueRange = 0f..dur,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(formatTime(duration), style = MaterialTheme.typography.labelSmall)
            }

            // 控制按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { PlaybackManager.skipToPrevious() }) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "上一集", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = { PlaybackManager.seekBy(-10_000) }) {
                    Icon(Icons.Filled.FastRewind, contentDescription = "快退10秒", modifier = Modifier.size(32.dp))
                }
                FloatingActionButton(
                    onClick = { PlaybackManager.togglePlayPause() },
                    shape = CircleShape
                ) {
                    Icon(
                        if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        modifier = Modifier.size(36.dp)
                    )
                }
                IconButton(onClick = { PlaybackManager.seekBy(30_000) }) {
                    Icon(Icons.Filled.FastForward, contentDescription = "快进30秒", modifier = Modifier.size(32.dp))
                }
                IconButton(onClick = { PlaybackManager.skipToNext() }) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "下一集", modifier = Modifier.size(36.dp))
                }
            }

            // 工具行
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton2(icon = { Icon(Icons.Filled.Speed, contentDescription = null, Modifier.size(18.dp)) },
                    label = "${speed}x") { showSpeed = true }
                TextButton2(icon = { Icon(Icons.Filled.Timer, contentDescription = null, Modifier.size(18.dp)) },
                    label = if (timerRemain > 0) formatTime(timerRemain) else "定时") { showTimer = true }
                TextButton2(icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, Modifier.size(18.dp)) },
                    label = "章节(${episodes.size})") { showChapters = true }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    // 章节列表
    if (showChapters) {
        ModalBottomSheet(
            onDismissRequest = { showChapters = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Text(
                "章节列表(${episodes.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(modifier = Modifier.fillMaxSize().padding(bottom = 32.dp)) {
                items(episodes) { ep ->
                    val idx = episodes.indexOf(ep)
                    val cached = ep.isCached || ChapterCacheManager.isCached(ep.url)
                    Text(
                        (if (idx == currentIndex) "▶ " else "") + ep.title + (if (cached) " ✔" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (idx == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                PlaybackManager.playEpisodeAt(idx)
                                showChapters = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    // 倍速
    if (showSpeed) {
        ModalBottomSheet(onDismissRequest = { showSpeed = false }) {
            Text("播放速度", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            speedOptions.forEach { s ->
                Text(
                    "${s}x",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (s == speed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            PlaybackManager.setSpeed(s)
                            showSpeed = false
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            Spacer(Modifier.size(32.dp))
        }
    }

    // 睡眠定时
    if (showTimer) {
        ModalBottomSheet(onDismissRequest = { showTimer = false }) {
            Text("睡眠定时", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            TextButton2(icon = {}, label = "取消定时") {
                PlaybackManager.cancelSleepTimer()
                showTimer = false
            }
            timerOptions.forEach { minutes ->
                Text(
                    "$minutes 分钟",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            PlaybackManager.startSleepTimer(minutes)
                            showTimer = false
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }
            Spacer(Modifier.size(32.dp))
        }
    }
}

@Composable
private fun TextButton2(icon: @Composable () -> Unit, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
