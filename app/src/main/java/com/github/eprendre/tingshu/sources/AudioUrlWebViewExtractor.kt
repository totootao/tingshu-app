package com.github.eprendre.tingshu.sources

import com.github.eprendre.tingshu.utils.SourceRuntime
import com.github.eprendre.tingshu.utils.WebViewRequest

/**
 * 某些站点需要渲染一遍网页才能在页面元素提取音频地址的，请用此类。
 */
object AudioUrlWebViewExtractor : AudioUrlExtractor {
    /**
     * @param isDeskTop true 加载 PC 页面, false 加载手机页面
     * @param script 当 WebView 加载完毕后运行的 js 脚本， 默认是提取 html。
     * @param parse 处理脚本运行结果的回调，如果脚本返回的是 html 页面，可通过 jsoup 后续解析。
     * 然后返回获取到的音频地址，若获取失败请返回 null。因为当程序检测到为 null 时会进行若干次尝试直到超时。
     */
    @JvmOverloads
    fun setUp(
        isDeskTop: Boolean = false,
        script: String = WebViewRequest.DEFAULT_SCRIPT,
        parse: (String) -> String?
    ) {
        this.isDeskTop = isDeskTop
        this.script = script
        this.parse = parse
    }

    private var isDeskTop: Boolean = false
    private var script: String = WebViewRequest.DEFAULT_SCRIPT
    private var parse: ((String) -> String?)? = null

    override fun extract(url: String, autoPlay: Boolean, isCache: Boolean, isDebug: Boolean) {
        val p = parse ?: throw IllegalStateException("AudioUrlWebViewExtractor 未 setUp")
        val audioUrl = SourceRuntime.webviewExecutor?.invoke(
            WebViewRequest(WebViewRequest.MODE_EXTRACT, url, isDeskTop, script, p)
        )
        resolveAudio(url, audioUrl, autoPlay, isCache, isDebug)
    }
}
