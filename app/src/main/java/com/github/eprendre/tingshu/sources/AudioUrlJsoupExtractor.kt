package com.github.eprendre.tingshu.sources

import com.github.eprendre.tingshu.extensions.config
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Jsoup 就可搞定的音频提取用此类
 */
object AudioUrlJsoupExtractor : AudioUrlExtractor {
    /**
     * @param isDesktop true 请求加上 PC UA, false 请求加上手机 UA
     * @param parse 处理返回的 org.jsoup.nodes.Document
     */
    @JvmOverloads
    fun setUp(isDesktop: Boolean = false, parse: (Document) -> String) {
        this.isDesktop = isDesktop
        this.parse = parse
    }

    private var isDesktop: Boolean = false
    private var parse: ((Document) -> String)? = null

    override fun extract(url: String, autoPlay: Boolean, isCache: Boolean, isDebug: Boolean) {
        val p = parse ?: throw IllegalStateException("AudioUrlJsoupExtractor 未 setUp")
        val doc = Jsoup.connect(url).config(isDesktop).get()
        resolveAudio(url, p(doc), autoPlay, isCache, isDebug)
    }
}
