package com.totootao.tingshu.sources.builtin

import com.github.eprendre.tingshu.sources.TingShu

/**
 * 内置源入口(结构与外置 jar 的 SourceEntry 一致)
 */
object BuiltinSourceEntry {

    @JvmStatic
    fun getDesc(): String = "内置听书源(已验证可用)"

    @JvmStatic
    fun getCategory(): String = "听书"

    @JvmStatic
    fun getSources(): List<TingShu> {
        return listOf(
            KuWo,
            YunTuYouSheng,
            BiliBili,
            LianTingWang,
            LibriVox
        )
    }
}
