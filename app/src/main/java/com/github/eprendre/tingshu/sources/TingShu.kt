package com.github.eprendre.tingshu.sources

import com.github.eprendre.tingshu.extensions.getMobileUA
import com.github.eprendre.tingshu.utils.Book
import com.github.eprendre.tingshu.utils.BookDetail
import com.github.eprendre.tingshu.utils.Category
import com.github.eprendre.tingshu.utils.CategoryMenu
import com.github.eprendre.tingshu.utils.ConfigItem
import com.github.eprendre.tingshu.utils.SourceRuntime

abstract class TingShu {
    /**
     * 用来判断一本书属于哪个源，不能和其它源有重复的情况。
     */
    abstract fun getSourceId(): String

    /**
     * 需要提供合法的网址，有时候用来判断链接是否属于某个源。
     */
    abstract fun getUrl(): String

    /**
     * 站点名称
     */
    abstract fun getName(): String

    /**
     * 站点简介
     */
    open fun getDesc() = ""

    /**
     * 搜索。返回 Pair 的第二个 Int 参数为最大页数，用来判断是否有下一页。
     * 若部分网站无法获取总页数，但确定有下一页的，可以返回当前页数+1。
     * 异步方法，里面的网络请求同步调用即可。
     */
    abstract fun search(keywords: String, page: Int): Pair<List<Book>, Int>

    /**
     * 选择合适的音频提取逻辑
     */
    abstract fun getAudioUrlExtractor(): AudioUrlExtractor

    /**
     * 分类菜单。异步方法，里面的网络请求同步调用即可。
     */
    abstract fun getCategoryMenus(): List<CategoryMenu>

    /**
     * 分类页面的列表。异步方法，里面的网络请求同步调用即可。
     */
    abstract fun getCategoryList(url: String): Category

    /**
     * 当前站点的章节列表是否需要分页加载。app端会优化分页加载的逻辑。
     */
    open fun isMultipleEpisodePages() = false

    /**
     * 两个地方会调用这个方法，一是列表页弹窗，二是播放页获取所有章节。
     * loadEpisodes 为 true 时才请求网络加载章节信息。
     * loadFullPages 为 true 时遍历所有页的章节。
     */
    abstract fun getBookDetailInfo(bookUrl: String, loadEpisodes: Boolean = true, loadFullPages: Boolean = true): BookDetail

    /**
     * 翻页加载是耗时操作，如果此方法触发，需要取消掉翻页加载的操作。
     */
    open fun reset() = run { }

    /**
     * 当前源是否可搜索
     */
    open fun isSearchable() = true

    /**
     * 当前源是否有发现分类的相关内容
     */
    open fun isDiscoverable() = true

    /**
     * 当前源的音频地址是否能被缓存
     */
    open fun isCacheable() = true

    /**
     * 当前源的解析是否没有用到 WebView。
     * 如果返回 true 代表当前源的解析不需要 WebView 的介入。
     */
    open fun isWebViewNotRequired() = false

    /**
     * WebView 使用兼容模式运行
     */
    open fun isWebViewCompatible() = false

    open fun method238() = false

    open fun c24e329b36b542f4adde9694d4b28a4r() = false
}

interface AudioUrlExtractor {
    fun extract(url: String, autoPlay: Boolean, isCache: Boolean, isDebug: Boolean = false)
}

/**
 * 如果源需要展示广告就实现这个接口。1.7.3 开始加入
 */
interface IAd {
    /**
     * 广告页的网址，如果返回空，app 自动展示当前章节页面。
     */
    fun adUrl(): String = ""

    /**
     * 是否默认展示广告 @Deprecated 已弃用
     */
    fun showAdByDefault(): Boolean = false
}

/**
 * 请求音频地址时是否需要额外的 header。1.7.3 开始加入
 */
interface AudioUrlExtraHeaders {
    /**
     * 返回音频地址需要添加的 headers
     */
    fun headers(audioUrl: String): Map<String, String>
}

/**
 * 加载封面时是否需要额外的 headers。在 2.0.1 之后加入
 */
interface CoverUrlExtraHeaders {
    /**
     * 一定要严格判断 coverUrl，符合条件才能给 headers 添加合适的值，并且返回 true。
     */
    fun coverHeaders(coverUrl: String, headers: MutableMap<String, String>): Boolean
}

/**
 * 当源需要登录才可以获取数据时实现此接口。在 2.0.1 之后加入
 */
interface ILogin {
    /**
     * 获取登录网址
     */
    fun getLoginUrl(): String

    /**
     * 登录页是否需要使用 PC 版 UA，默认使用手机版
     */
    fun isLoginDesktop(): Boolean = false
}

/**
 * 播放状态有变化时的回调。在 2.3.5 开始加入
 */
interface IPlaybackStateCallback {
    /**
     * state 参数有：playing, paused, error, stopped
     */
    fun onPlaybackStateChanged(state: String)
}

/**
 * 歌词接口。2.5.1 开始加入
 */
interface ILrc {
    /**
     * 歌词配置缓存文件。hashMap: key:章节名 value:对应章节的歌词下载地址。
     */
    fun saveLrcConfig(hashMap: HashMap<String, String>, sourceId: String, bookUrl: String) {
        val json = com.google.gson.Gson().toJson(hashMap)
        val dir = com.github.eprendre.tingshu.extensions.getSourceCacheDir(sourceId, "lrc")
        java.io.File(dir, "config.json").writeText(json)
    }

    /**
     * 下载歌词文件。@return 返回歌词内容String
     */
    fun downloadLrc(url: String): String {
        val body = okhttp3.OkHttpClient.Builder().build()
            .newCall(okhttp3.Request.Builder().url(url).build())
            .execute().body?.string()
        return body ?: ""
    }
}

/**
 * 搜索验证接口。2.5.7加入
 */
interface ISearchVerification {
    /**
     * @return 返回搜索验证地址
     */
    fun getSearchVerificationUrl(keywords: String): String

    /**
     * 如果有办法根据cookies判断搜索是否已验证可以提高效率，没办法判断就返回false
     */
    fun isSearchValidated(): Boolean = false

    /**
     * 返回搜索验证页需要加载的UA
     */
    fun getSearchVerificationUA(): String = getMobileUA()

    /**
     * 有的网站有搜索时间限制，防止验证返回后和立即搜索的冲突，需要加一个时间延迟
     */
    fun getSearchDelayMs(): Long = 0
}

/**
 * 自定义源配置。2.5.9 加入
 * App写入的key为 ${getSourceId()}.${configItem.key}
 */
interface ConfigurableSource {
    fun getCustomConfigItems(): List<ConfigItem>
}

/**
 * 内部辅助:提取器把结果统一交给 App 的音频解析回调
 */
internal fun resolveAudio(
    chapterUrl: String,
    audioUrl: String?,
    autoPlay: Boolean,
    isCache: Boolean,
    isDebug: Boolean
) {
    SourceRuntime.audioResolver?.invoke(chapterUrl, audioUrl, autoPlay, isCache, isDebug)
        ?: throw IllegalStateException("SourceRuntime.audioResolver 未注册")
}
