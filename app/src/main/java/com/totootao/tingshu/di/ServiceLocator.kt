package com.totootao.tingshu.di

import android.content.Context
import com.totootao.tingshu.data.SettingsStore
import com.totootao.tingshu.data.db.AppDatabase

/**
 * 轻量 ServiceLocator
 */
object ServiceLocator {
    lateinit var appContext: Context
        private set

    lateinit var settings: SettingsStore
        private set

    val database: AppDatabase by lazy { AppDatabase.get(appContext) }

    fun init(context: Context) {
        appContext = context.applicationContext
        settings = SettingsStore(appContext)
    }
}
