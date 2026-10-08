package com.github.eprendre.tingshu.utils

import android.content.Context
import java.io.File

/**
 * 插件 API 与 App 本体之间的桥。
 * App 启动时注册各回调;内置源与外置源共用同一套运行时能力。
 */
object SourceRuntime {
    lateinit var appContext: Context

    /** UA 提供者: 参数 isDesktop */
    var uaProvider: ((Boolean) -> String)? = null

    var toastShower: ((String) -> Unit)? = null

    var currentBookProvider: (() -> Book?)? = null

    /** 章节分页加载进度提示, null 表示加载完毕 */
    var episodeLoadingNotifier: ((String?) -> Unit)? = null

    /** 播放控制: play, pause, skipToNext, skipToPrevious, rewind, fastForward, stop */
    var playbackController: ((String) -> Unit)? = null

    var todayPlaybackTimeProvider: (() -> Long)? = null

    var cacheDirProvider: ((String, String) -> File)? = null

    /**
     * 音频地址解析完成回调(由各 AudioUrlExtractor 调用)
     * chapterUrl: 章节页地址; audioUrl: 提取到的音频地址; 为 null 表示提取失败
     */
    var audioResolver: ((chapterUrl: String, audioUrl: String?, autoPlay: Boolean, isCache: Boolean, isDebug: Boolean) -> Unit)? = null

    /**
     * 需要 WebView 的阻塞式执行器(提取/嗅探/登录/验证)。
     * 返回提取到的音频地址;登录/验证模式返回空串即可。
     */
    var webviewExecutor: ((request: WebViewRequest) -> String?)? = null
}

/**
 * WebView 任务请求描述
 */
data class WebViewRequest(
    val mode: Int,
    val url: String,
    val isDesktop: Boolean = false,
    /** EXTRACT 模式: WebView 加载完成后执行的 js 脚本 */
    val script: String = DEFAULT_SCRIPT,
    /** EXTRACT 模式: 处理脚本运行结果, 返回音频地址 */
    val parse: ((String) -> String?)? = null,
    /** SNIFF 模式: 校验嗅探到的地址是否有效 */
    val validateUrl: ((String) -> Boolean)? = null,
    /** LOGIN 模式: 登录完成提示 */
    val loginHint: String = "",
    /** VERIFY 模式: 验证完成后的延迟毫秒 */
    val delayMs: Long = 0
) {
    companion object {
        const val MODE_EXTRACT = 0
        const val MODE_SNIFF = 1
        const val MODE_LOGIN = 2
        const val MODE_VERIFY = 3

        const val DEFAULT_SCRIPT =
            "(function() { return ('<html>'+document.getElementsByTagName('html')[0].innerHTML+'</html>'); })();"
    }
}
