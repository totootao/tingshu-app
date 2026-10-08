package com.totootao.tingshu.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 全局设置(SharedPreferences 持久化)
 */
class SettingsStore(context: Context) {

    private val sp: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var mobileUa: String
        get() = sp.getString(KEY_MOBILE_UA, DEFAULT_MOBILE_UA) ?: DEFAULT_MOBILE_UA
        set(value) = sp.edit().putString(KEY_MOBILE_UA, value).apply()

    var desktopUa: String
        get() = sp.getString(KEY_DESKTOP_UA, DEFAULT_DESKTOP_UA) ?: DEFAULT_DESKTOP_UA
        set(value) = sp.edit().putString(KEY_DESKTOP_UA, value).apply()

    /** 聚合搜索:开 = 所有源结果合并, 关 = 按源分开 */
    var aggregateSearch: Boolean
        get() = sp.getBoolean(KEY_AGGREGATE_SEARCH, true)
        set(value) = sp.edit().putBoolean(KEY_AGGREGATE_SEARCH, value).apply()

    var cacheEnabled: Boolean
        get() = sp.getBoolean(KEY_CACHE_ENABLED, true)
        set(value) = sp.edit().putBoolean(KEY_CACHE_ENABLED, value).apply()

    /** 缓存上限 MB */
    var cacheLimitMb: Int
        get() = sp.getInt(KEY_CACHE_LIMIT_MB, 500)
        set(value) = sp.edit().putInt(KEY_CACHE_LIMIT_MB, value).apply()

    /** 跳过无声部分 */
    var skipSilence: Boolean
        get() = sp.getBoolean(KEY_SKIP_SILENCE, false)
        set(value) = sp.edit().putBoolean(KEY_SKIP_SILENCE, value).apply()

    /** 播放页打开时自动刷新章节列表 */
    var autoRefreshEpisodes: Boolean
        get() = sp.getBoolean(KEY_AUTO_REFRESH, true)
        set(value) = sp.edit().putBoolean(KEY_AUTO_REFRESH, value).apply()

    /** 自动预缓存后续章节的个数 */
    var prefetchCount: Int
        get() = sp.getInt(KEY_PREFETCH_COUNT, 2)
        set(value) = sp.edit().putInt(KEY_PREFETCH_COUNT, value).apply()

    /** 启用的源(sourceId 集合), null 表示尚未初始化 -> 默认全开 */
    var enabledSourceIds: Set<String>?
        get() = sp.getStringSet(KEY_ENABLED_SOURCES, null)
        set(value) = sp.edit().putStringSet(KEY_ENABLED_SOURCES, value?.toSet()).apply()

    /** 今日播放时长(毫秒)与日期 */
    var todayPlaybackTime: Long
        get() = if (sp.getString(KEY_PLAYBACK_DATE, "") == todayString()) sp.getLong(KEY_PLAYBACK_TIME, 0) else 0
        set(value) = sp.edit()
            .putLong(KEY_PLAYBACK_TIME, value)
            .putString(KEY_PLAYBACK_DATE, todayString())
            .apply()

    fun addPlaybackTime(deltaMs: Long) {
        todayPlaybackTime = todayPlaybackTime + deltaMs
    }

    private fun todayString(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

    companion object {
        private const val KEY_MOBILE_UA = "mobile_ua"
        private const val KEY_DESKTOP_UA = "desktop_ua"
        private const val KEY_AGGREGATE_SEARCH = "aggregate_search"
        private const val KEY_CACHE_ENABLED = "cache_enabled"
        private const val KEY_CACHE_LIMIT_MB = "cache_limit_mb"
        private const val KEY_SKIP_SILENCE = "skip_silence"
        private const val KEY_AUTO_REFRESH = "auto_refresh_episodes"
        private const val KEY_PREFETCH_COUNT = "prefetch_count"
        private const val KEY_ENABLED_SOURCES = "enabled_source_ids"
        private const val KEY_PLAYBACK_TIME = "today_playback_time"
        private const val KEY_PLAYBACK_DATE = "today_playback_date"

        const val DEFAULT_MOBILE_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        const val DEFAULT_DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
}
