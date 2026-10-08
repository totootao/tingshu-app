package com.github.eprendre.tingshu.sources

import com.github.eprendre.tingshu.utils.SourceRuntime
import com.github.eprendre.tingshu.utils.WebViewRequest

/**
 * 自动嗅探提取音频
 */
object AudioUrlWebViewSniffExtractor : AudioUrlExtractor {
    /**
     * @param isDeskTop true 加载 PC 页面, false 加载手机页面
     * @param validateUrl 一般默认的音频提取足够用了，当有额外的需求时，提供此回调以自定义验证嗅探的地址是否符合要求，
     * 不需要验证条件时需要重新setUp，并回传 null
     */
    @JvmOverloads
    fun setUp(isDeskTop: Boolean = false, validateUrl: ((String) -> Boolean)? = null) {
        this.isDeskTop = isDeskTop
        this.validateUrl = validateUrl
    }

    private var isDeskTop: Boolean = false
    private var validateUrl: ((String) -> Boolean)? = null

    override fun extract(url: String, autoPlay: Boolean, isCache: Boolean, isDebug: Boolean) {
        val audioUrl = SourceRuntime.webviewExecutor?.invoke(
            WebViewRequest(WebViewRequest.MODE_SNIFF, url, isDeskTop, validateUrl = validateUrl)
        )
        resolveAudio(url, audioUrl, autoPlay, isCache, isDebug)
    }
}
