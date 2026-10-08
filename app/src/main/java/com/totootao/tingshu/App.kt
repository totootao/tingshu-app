package com.totootao.tingshu

import android.app.Application
import android.widget.Toast
import com.github.eprendre.tingshu.utils.SourceRuntime
import com.totootao.tingshu.data.SourceRepository
import com.totootao.tingshu.data.SubscriptionManager
import com.totootao.tingshu.di.ServiceLocator
import com.totootao.tingshu.playback.PlaybackManager
import com.totootao.tingshu.playback.SourceRepositoryHolder
import com.totootao.tingshu.webview.WebViewExecutor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)

        // 注册插件 API 运行时
        SourceRuntime.appContext = this
        val settings = ServiceLocator.settings
        SourceRuntime.uaProvider = { isDesktop ->
            if (isDesktop) settings.desktopUa else settings.mobileUa
        }
        SourceRuntime.toastShower = { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        SourceRuntime.currentBookProvider = { PlaybackManager.currentBook }
        SourceRuntime.episodeLoadingNotifier = { msg -> PlaybackManager.notifyLoadingEpisodes(msg) }
        SourceRuntime.playbackController = { action -> PlaybackManager.control(action) }
        SourceRuntime.todayPlaybackTimeProvider = { settings.todayPlaybackTime }
        SourceRuntime.cacheDirProvider = { sourceId, subDir ->
            File(cacheDir, "sources/$sourceId/$subDir").apply { mkdirs() }
        }
        SourceRuntime.audioResolver = { chapterUrl, audioUrl, autoPlay, isCache, isDebug ->
            PlaybackManager.onAudioResolved(chapterUrl, audioUrl, autoPlay, isCache, isDebug)
        }
        SourceRuntime.webviewExecutor = { request ->
            WebViewExecutor.executeBlocking(this, request)
        }

        SourceRepositoryHolder.findById = { id -> SourceRepository.findById(id) }
        SourceRepositoryHolder.enabledSources = { SourceRepository.enabledSources() }

        appScope.launch {
            SourceRepository.init()
            runCatching { SubscriptionManager.checkAllUpdates() }
            PlaybackManager.init(this@App)
        }
    }
}
