package com.github.eprendre.tingshu.sources

import com.github.eprendre.tingshu.extensions.getDesktopUA
import com.github.eprendre.tingshu.extensions.getMobileUA
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.json.FuelJson
import com.github.kittinunf.fuel.json.responseJson

/**
 * 提取音频的地址返回json时，使用此类
 */
object AudioUrlJsonExtractor : AudioUrlExtractor {
    /**
     * @param isDesktop true 请求加上 PC UA, false 请求加上手机 UA
     * @param parse 处理json，参数类型为 FuelJson，通过 FuelJson.obj() 得到 JSONObject, FuelJson.array() 得到 JSONArray
     */
    @JvmOverloads
    fun setUp(isDesktop: Boolean = false, parse: (FuelJson) -> String) {
        this.isDesktop = isDesktop
        this.parse = parse
    }

    private var isDesktop: Boolean = false
    private var parse: ((FuelJson) -> String)? = null

    override fun extract(url: String, autoPlay: Boolean, isCache: Boolean, isDebug: Boolean) {
        val p = parse ?: throw IllegalStateException("AudioUrlJsonExtractor 未 setUp")
        val ua = if (isDesktop) getDesktopUA() else getMobileUA()
        val json = Fuel.get(url).header("User-Agent" to ua).responseJson().third.get()
        resolveAudio(url, p(json), autoPlay, isCache, isDebug)
    }
}
