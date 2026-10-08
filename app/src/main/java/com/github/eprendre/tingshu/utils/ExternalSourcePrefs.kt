package com.github.eprendre.tingshu.utils

import android.content.Context

/**
 * 外置源键值存储 —— 运行时由 App 提供 SharedPreferences 实现。
 */
object ExternalSourcePrefs {

    private val prefs
        get() = SourceRuntime.appContext.getSharedPreferences("external_source_prefs", Context.MODE_PRIVATE)

    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getString(key: String, defValue: String?): String? {
        return prefs.getString(key, defValue)
    }
}
