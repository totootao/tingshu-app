package com.github.eprendre.tingshu.extensions

import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import com.github.eprendre.tingshu.utils.Book
import com.github.eprendre.tingshu.utils.SourceRuntime
import io.reactivex.disposables.Disposable
import org.jsoup.Connection
import java.io.File
import java.net.URL
import java.net.URLDecoder

private val mainHandler = Handler(Looper.getMainLooper())

fun splitQuery(url: URL): LinkedHashMap<String, String> {
    val queryPairs = LinkedHashMap<String, String>()
    val query = url.query
    val pairs = query.split("&")
    for (pair in pairs) {
        val idx = pair.indexOf("=")
        queryPairs[URLDecoder.decode(pair.substring(0, idx), "UTF-8")] =
            URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
    }
    return queryPairs
}

private val defaultDesktopUA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
private val defaultMobileUA =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

/**
 * 是否使用 PC 版的 UA，UA 字段会自动从 app 里的配置读取。
 */
fun Connection.config(isDesktop: Boolean = false): Connection {
    return this
        .userAgent(if (isDesktop) getDesktopUA() else getMobileUA())
        .timeout(15000)
        .followRedirects(true)
}

/**
 * 获取 PC 版 UA
 */
fun getDesktopUA(): String = SourceRuntime.uaProvider?.invoke(true) ?: defaultDesktopUA

/**
 * 获取手机版 UA
 */
fun getMobileUA(): String = SourceRuntime.uaProvider?.invoke(false) ?: defaultMobileUA

/**
 * WebView 登录之后，可以用此方法得到 Cookie
 */
fun getCookie(url: String): String? {
    return try {
        CookieManager.getInstance().getCookie(url)
    } catch (e: Exception) {
        null
    }
}

/**
 * 提示消息
 */
fun showToast(msg: String) {
    mainHandler.post { SourceRuntime.toastShower?.invoke(msg) }
}

/**
 * 获取当前正在播放的书籍。2.1.1 开始加入
 */
fun getCurrentBook(): Book? = SourceRuntime.currentBookProvider?.invoke()

/**
 * 加载多页章节列表时用到这个。pageInfo 传空，代表加载完毕
 */
fun notifyLoadingEpisodes(pageInfo: String?) {
    SourceRuntime.episodeLoadingNotifier?.invoke(pageInfo)
}

private val extractorDisposable = object : Disposable {
    @Volatile private var disposed = false
    override fun dispose() { disposed = true }
    override fun isDisposed(): Boolean = disposed
}

/**
 * 适用于继承 AudioUrlExtractor，若需要嵌套调用多个 AudioUrlXXXExtractor 时务必调用此方法。
 * backgroundTask: 网络请求或者耗时代码需要在后台线程处理, 返回一个 String
 * mainThreadCallback: 处理前者返回的String，并根据情况选择具体的 AudioUrlXXXExtractor
 */
fun extractorAsyncExecute(
    url: String,
    autoPlay: Boolean,
    isCache: Boolean,
    isDebug: Boolean,
    backgroundTask: () -> String,
    mainThreadCallback: (String) -> Unit
): Disposable {
    return io.reactivex.Observable
        .fromCallable(backgroundTask)
        .subscribeOn(io.reactivex.schedulers.Schedulers.io())
        .observeOn(io.reactivex.android.schedulers.AndroidSchedulers.mainThread())
        .subscribe(
            { result -> mainThreadCallback(result) },
            { error ->
                // 出错则视为提取失败
                mainHandler.post { SourceRuntime.audioResolver?.invoke(url, null, autoPlay, isCache, isDebug) }
            }
        )
}

/**
 * 获取今天播放时长, 单位毫秒。2.3.5 开始加入
 */
fun todayPlaybackTime(): Long = SourceRuntime.todayPlaybackTimeProvider?.invoke() ?: 0L

/**
 * 控制播放器: play, pause, skipToNext, skipToPrevious, rewind, fastForward, stop。
 * 2.3.5 开始加入
 */
fun playerControl(action: String) {
    SourceRuntime.playbackController?.invoke(action)
}

/**
 * 获取每个源自己的缓存目录。2.5.1 开始加入
 */
fun getSourceCacheDir(sourceId: String, subDir: String): File {
    return SourceRuntime.cacheDirProvider?.invoke(sourceId, subDir)
        ?: throw IllegalStateException("SourceRuntime.cacheDirProvider 未注册")
}
