package com.github.eprendre.tingshu.sources

/**
 * 自定义提取音频地址逻辑
 */
object AudioUrlCustomExtractor : AudioUrlExtractor {
    /**
     * @param parse 传入章节地址，解析后传出音频地址
     */
    fun setUp(parse: (String) -> String) {
        this.parse = parse
    }

    private var parse: ((String) -> String)? = null

    override fun extract(url: String, autoPlay: Boolean, isCache: Boolean, isDebug: Boolean) {
        val p = parse ?: throw IllegalStateException("AudioUrlCustomExtractor 未 setUp")
        resolveAudio(url, p(url), autoPlay, isCache, isDebug)
    }
}
