package com.totootao.tingshu.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.eprendre.tingshu.sources.AudioUrlDirectExtractor
import com.github.eprendre.tingshu.sources.AudioUrlExtraHeaders
import com.github.eprendre.tingshu.sources.TingShu
import com.github.eprendre.tingshu.utils.Book
import com.github.eprendre.tingshu.utils.Episode
import com.totootao.tingshu.data.LibraryRepository
import com.totootao.tingshu.data.SettingsStore
import com.totootao.tingshu.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 提取结果
 */
data class ResolvedAudio(val audioUrl: String, val headers: Map<String, String>)

/**
 * 播放队列
 */
class PlaybackQueue(
    val book: Book,
    val source: TingShu,
    val episodes: List<Episode>,
    @Volatile var currentIndex: Int
) {
    @Volatile var lastHistoryEpisodeUrl: String? = null
}

/**
 * 播放管理单例:
 * - 组装队列与 MediaItem
 * - 通过 ResolvingDataSource 在播放时进行音频提取(支持 WebView 提取/嗅探)
 * - 章节缓存、预缓存、倍速、睡眠定时、进度保存、今日时长统计
 */
object PlaybackManager {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ---------- 状态流 ----------
    private val _currentBookFlow = MutableStateFlow<Book?>(null)
    val currentBookFlow: StateFlow<Book?> = _currentBookFlow

    private val _episodesFlow = MutableStateFlow<List<Episode>>(emptyList())
    val episodesFlow: StateFlow<List<Episode>> = _episodesFlow

    private val _currentIndexFlow = MutableStateFlow(-1)
    val currentIndexFlow: StateFlow<Int> = _currentIndexFlow

    private val _isPlayingFlow = MutableStateFlow(false)
    val isPlayingFlow: StateFlow<Boolean> = _isPlayingFlow

    private val _positionFlow = MutableStateFlow(0L)
    val positionFlow: StateFlow<Long> = _positionFlow

    private val _durationFlow = MutableStateFlow(0L)
    val durationFlow: StateFlow<Long> = _durationFlow

    private val _speedFlow = MutableStateFlow(1f)
    val speedFlow: StateFlow<Float> = _speedFlow

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow

    private val _loadingEpisodesMsgFlow = MutableStateFlow<String?>(null)
    val loadingEpisodesMsgFlow: StateFlow<String?> = _loadingEpisodesMsgFlow

    private val _sleepTimerRemainFlow = MutableStateFlow(0L)
    val sleepTimerRemainFlow: StateFlow<Long> = _sleepTimerRemainFlow

    // ---------- 内部状态 ----------
    @Volatile var currentBook: Book? = null
        private set

    private var queue: PlaybackQueue? = null
    private var controller: MediaController? = null
    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var lastSavedPosition = 0L
    private var lastSaveTime = 0L

    /** 章节地址 -> 提取结果缓存(本会话内) */
    private val resolvedMap = ConcurrentHashMap<String, ResolvedAudio>()
    /** 直链音频地址 -> 请求头 */
    private val headerMap = ConcurrentHashMap<String, Map<String, String>>()

    /** 阻塞式提取的收集器 */
    private val sinkTL = ThreadLocal<MutableMap<String, String?>>()

    private val settings: SettingsStore get() = ServiceLocator.settings

    // ---------- 初始化 ----------
    fun init(context: Context) {
        val token = SessionToken(context, ComponentName(context, PlayerService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            runCatching {
                controller = future.get().apply { addListener(playerListener) }
                progressJob = scope.launch { positionLoop() }
            }.onFailure { it.printStackTrace() }
        }, Runnable::run)
    }

    fun buildPlayer(context: Context): ExoPlayer {
        val dataSourceFactory = createDataSourceFactory(context)
        val player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
        player.skipSilenceEnabled = settings.skipSilence
        return player
    }

    private fun createDataSourceFactory(context: Context): ResolvingDataSource.Factory {
        // OkHttp 自身已配置跟随重定向(含 http<->https 跨协议)
        val okHttpFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(emptyMap())
        val defaultFactory = DefaultDataSource.Factory(context, okHttpFactory)
        return ResolvingDataSource.Factory(defaultFactory) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "tingshu-pending") {
                val chapterUrl = uri.getQueryParameter("u")
                    ?: throw IOException("非法的待解析章节地址")
                val source = queue?.source
                    ?: throw IOException("播放队列已清空")
                val resolved = resolveForPlayback(source, chapterUrl)
                    ?: throw IOException("音频提取失败: $chapterUrl")
                ChapterCacheManager.cachedFile(chapterUrl)?.let { file ->
                    return@Factory dataSpec.buildUpon().setUri(Uri.fromFile(file)).build()
                }
                val builder = dataSpec.buildUpon().setUri(Uri.parse(resolved.audioUrl))
                if (resolved.headers.isNotEmpty()) builder.setHttpRequestHeaders(resolved.headers)
                builder.build()
            } else {
                val headers = headerMap[uri.toString()]
                if (headers.isNullOrEmpty()) dataSpec
                else dataSpec.buildUpon().setHttpRequestHeaders(headers).build()
            }
        }
    }

    /**
     * 解析章节音频地址:优先取本会话缓存,否则阻塞式提取。
     * (在 ExoPlayer 加载线程上调用)
     */
    private fun resolveForPlayback(source: TingShu, chapterUrl: String): ResolvedAudio? {
        resolvedMap[chapterUrl]?.let { return it }
        return try {
            val resolved = runBlocking { withContext(Dispatchers.IO) { extractBlocking(source, chapterUrl) } }
            resolved?.also { resolvedMap[chapterUrl] = it }
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
    }

    /**
     * 阻塞式调用源的提取器(可从任意线程调用,不能在主线程)。
     */
    suspend fun extractBlocking(source: TingShu, chapterUrl: String, isCache: Boolean = false): ResolvedAudio? =
        withContext(Dispatchers.IO) {
            val sink = HashMap<String, String?>()
            sinkTL.set(sink)
            try {
                source.getAudioUrlExtractor().extract(chapterUrl, autoPlay = false, isCache = isCache)
            } catch (t: Throwable) {
                t.printStackTrace()
            } finally {
                sinkTL.remove()
            }
            val audioUrl = sink[chapterUrl] ?: return@withContext null
            val headers = (source as? AudioUrlExtraHeaders)?.headers(audioUrl) ?: emptyMap()
            ResolvedAudio(audioUrl, headers)
        }

    /**
     * SourceRuntime.audioResolver 的实现
     */
    fun onAudioResolved(chapterUrl: String, audioUrl: String?, autoPlay: Boolean, isCache: Boolean, isDebug: Boolean) {
        val sink = sinkTL.get()
        if (sink != null) {
            sink[chapterUrl] = audioUrl
            return
        }
        // 非收集器路径:外部直接调用了 extract(autoPlay=true),记录并尝试播放
        if (audioUrl == null) {
            _errorFlow.value = "音频提取失败: $chapterUrl"
            return
        }
        val q = queue ?: return
        val source = q.source
        val headers = (source as? AudioUrlExtraHeaders)?.headers(audioUrl) ?: emptyMap()
        resolvedMap[chapterUrl] = ResolvedAudio(audioUrl, headers)
        val index = q.episodes.indexOfFirst { it.url == chapterUrl }
        if (autoPlay && index >= 0) {
            q.currentIndex = index
            _currentIndexFlow.value = index
            scope.launch { prepareAndPlay(startPositionMs = 0L) }
        }
    }

    // ---------- 播放入口 ----------
    fun play(book: Book, episodes: List<Episode>, startIndex: Int, startPositionMs: Long = 0L) {
        val source = SourceRepositoryHolder.findById(book.sourceId ?: return) ?: return
        queue?.source?.reset()
        val q = PlaybackQueue(book, source, episodes, startIndex)
        queue = q
        currentBook = book
        resolvedMap.clear()
        _currentBookFlow.value = book
        _episodesFlow.value = episodes
        _currentIndexFlow.value = startIndex
        _errorFlow.value = null

        // 直链源:提前登记请求头 + 标记缓存
        val direct = source.getAudioUrlExtractor() === AudioUrlDirectExtractor
        if (direct) {
            episodes.forEach { ep ->
                val headers = (source as? AudioUrlExtraHeaders)?.headers(ep.url) ?: emptyMap()
                if (headers.isNotEmpty()) headerMap[ep.url] = headers
                if (ChapterCacheManager.isCached(ep.url)) ep.isCached = true
            }
        }

        scope.launch {
            prepareAndPlay(startPositionMs)
            // 保存倍速到收藏(Book 非 copy 可用的主构造字段,直接赋值)
            book.playSpeed = _speedFlow.value
            LibraryRepository.updateBookFields(book)
            prefetchNext(settings.prefetchCount)
        }
    }

    private suspend fun prepareAndPlay(startPositionMs: Long) {
        val q = queue ?: return
        val ctl = controller ?: return
        val items = buildMediaItems(q)
        withContext(Dispatchers.Main) {
            ctl.setMediaItems(items, q.currentIndex, startPositionMs)
            ctl.playbackParameters = PlaybackParameters(q.book.playSpeed)
            ctl.prepare()
            ctl.playWhenReady = true
        }
    }

    private fun buildMediaItems(q: PlaybackQueue): List<MediaItem> {
        val direct = q.source.getAudioUrlExtractor() === AudioUrlDirectExtractor
        val coverUri = q.book.coverUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
        return q.episodes.mapIndexed { index, ep ->
            val uri = if (direct) {
                Uri.parse(ep.url)
            } else {
                val encoded = java.net.URLEncoder.encode(ep.url, "UTF-8")
                Uri.parse("tingshu-pending://chapter/$index?u=$encoded")
            }
            MediaItem.Builder()
                .setMediaId(index.toString())
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(ep.title)
                        .setArtist(q.book.title)
                        .setArtworkUri(coverUri)
                        .build()
                )
                .build()
        }
    }

    /**
     * 预缓存后续章节(仅直链且源 isCacheable)
     */
    private suspend fun prefetchNext(count: Int) {
        val q = queue ?: return
        if (!settings.cacheEnabled || !q.source.isCacheable()) return
        if (q.source.getAudioUrlExtractor() !== AudioUrlDirectExtractor) return
        val to = minOf(q.currentIndex + count, q.episodes.lastIndex)
        for (i in (q.currentIndex + 1)..to) {
            val ep = q.episodes[i]
            if (!ChapterCacheManager.isCached(ep.url)) {
                ChapterCacheManager.download(ep.url, ep.url, headerMap[ep.url] ?: emptyMap())
                ep.isCached = true
                _episodesFlow.value = q.episodes.toList()
            }
        }
    }

    // ---------- 播放控制 ----------
    fun togglePlayPause() = scope.launch(Dispatchers.Main) {
        val ctl = controller ?: return@launch
        if (ctl.isPlaying) ctl.pause() else ctl.play()
    }

    fun seekTo(positionMs: Long) = scope.launch(Dispatchers.Main) {
        controller?.seekTo(positionMs)
    }

    fun seekBy(offsetMs: Long) = scope.launch(Dispatchers.Main) {
        controller?.let { ctl ->
            ctl.seekTo((ctl.currentPosition + offsetMs).coerceAtLeast(0))
        }
    }

    fun skipToNext() = scope.launch(Dispatchers.Main) { controller?.seekToNextMediaItem() }
    fun skipToPrevious() = scope.launch(Dispatchers.Main) { controller?.seekToPreviousMediaItem() }

    fun setSpeed(speed: Float) = scope.launch(Dispatchers.Main) {
        controller?.playbackParameters = PlaybackParameters(speed)
        _speedFlow.value = speed
        queue?.let {
            it.book.playSpeed = speed
            LibraryRepository.updateBookFields(it.book)
        }
    }

    fun setSkipSilence(enabled: Boolean) = scope.launch(Dispatchers.Main) {
        // media3 中 setSkipSilenceEnabled 仅在 ExoPlayer 接口上,MediaController 未暴露
        PlayerService.activePlayer?.setSkipSilenceEnabled(enabled)
        settings.skipSilence = enabled
    }

    fun playEpisodeAt(index: Int) {
        val q = queue ?: return
        q.currentIndex = index
        _currentIndexFlow.value = index
        scope.launch { prepareAndPlay(0L) }
    }

    fun stopAndClear() = scope.launch(Dispatchers.Main) {
        saveProgressNow()
        controller?.stop()
        controller?.clearMediaItems()
        queue?.source?.reset()
        queue = null
        currentBook = null
        _currentBookFlow.value = null
        _episodesFlow.value = emptyList()
        _currentIndexFlow.value = -1
        _isPlayingFlow.value = false
        notifyPlaybackState("stopped")
    }

    fun control(action: String) {
        when (action) {
            "play" -> scope.launch(Dispatchers.Main) { controller?.play() }
            "pause" -> scope.launch(Dispatchers.Main) { controller?.pause() }
            "skipToNext" -> skipToNext()
            "skipToPrevious" -> skipToPrevious()
            "rewind" -> seekBy(-10_000)
            "fastForward" -> seekBy(30_000)
            "stop" -> stopAndClear()
        }
    }

    // ---------- 睡眠定时 ----------
    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _sleepTimerRemainFlow.value = 0
            return
        }
        sleepTimerJob = scope.launch {
            var remain = minutes * 60_000L
            _sleepTimerRemainFlow.value = remain
            while (remain > 0 && isActive) {
                delay(1000)
                remain -= 1000
                _sleepTimerRemainFlow.value = remain
            }
            if (isActive) {
                scope.launch(Dispatchers.Main) { controller?.pause() }
                _sleepTimerRemainFlow.value = 0
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _sleepTimerRemainFlow.value = 0
    }

    // ---------- 事件 ----------
    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlayingFlow.value = isPlaying
            notifyPlaybackState(if (isPlaying) "playing" else "paused")
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val q = queue ?: return
            val index = mediaItem?.mediaId?.toIntOrNull() ?: return
            q.currentIndex = index
            _currentIndexFlow.value = index
            _positionFlow.value = 0
            // 跳过片头
            val skipBegin = q.book.skipBeginning
            if (skipBegin > 0) {
                scope.launch(Dispatchers.Main) { controller?.seekTo(skipBegin) }
            }
            // 历史记录
            val ep = q.episodes.getOrNull(index)
            if (ep != null && q.lastHistoryEpisodeUrl != ep.url) {
                q.lastHistoryEpisodeUrl = ep.url
                scope.launch { LibraryRepository.addHistory(q.book, ep, 0) }
                scope.launch { prefetchNext(settings.prefetchCount) }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            _errorFlow.value = "播放出错: ${error.message ?: "未知错误"}"
        }
    }

    private suspend fun positionLoop() {
        while (scope.isActive) {
            val ctl = controller
            if (ctl != null) {
                val position = ctl.currentPosition.coerceAtLeast(0)
                val duration = ctl.duration.takeIf { it > 0 } ?: 0L
                _positionFlow.value = position
                _durationFlow.value = duration
                // 跳过片尾
                val q = queue
                if (q != null && q.book.skipEnd > 0 && duration > 0 &&
                    position >= duration - q.book.skipEnd && ctl.isPlaying
                ) {
                    withContext(Dispatchers.Main) { ctl.seekToNextMediaItem() }
                }
                // 进度保存(每15秒)
                if (q != null && ctl.isPlaying && position - lastSavedPosition >= 15_000) {
                    saveProgressNow()
                }
                // 今日时长
                if (ctl.isPlaying) {
                    settings.addPlaybackTime(500)
                }
            }
            delay(500)
        }
    }

    private suspend fun saveProgressNow() {
        val q = queue ?: return
        val ctl = controller ?: return
        val position = runCatching { withContext(Dispatchers.Main) { ctl.currentPosition } }.getOrDefault(0L)
        lastSavedPosition = position
        lastSaveTime = System.currentTimeMillis()
        val ep = q.episodes.getOrNull(q.currentIndex) ?: return
        LibraryRepository.saveProgress(q.book, ep.url, ep.title, position)
        // 同步到内存中的 book
        q.book.currentEpisodeUrl = ep.url
        q.book.currentEpisodeName = ep.title
        q.book.currentEpisodePosition = position
    }

    private fun notifyPlaybackState(state: String) {
        SourceRepositoryHolder.enabledSources().forEach { source ->
            if (source is com.github.eprendre.tingshu.sources.IPlaybackStateCallback) {
                runCatching { source.onPlaybackStateChanged(state) }
            }
        }
    }

    /** 章节分页加载提示桥(供 notifyLoadingEpisodes 使用) */
    fun notifyLoadingEpisodes(pageInfo: String?) {
        _loadingEpisodesMsgFlow.value = pageInfo
    }

    private val okHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

/**
 * 延迟引用,避免循环依赖
 */
object SourceRepositoryHolder {
    @Volatile var findById: (String) -> TingShu? = { null }
    @Volatile var enabledSources: () -> List<TingShu> = { emptyList() }
}
