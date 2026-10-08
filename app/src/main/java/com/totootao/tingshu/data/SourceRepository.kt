package com.totootao.tingshu.data

import com.totootao.tingshu.di.ServiceLocator
import com.totootao.tingshu.sources.builtin.BuiltinSourceEntry
import com.github.eprendre.tingshu.sources.TingShu
import dalvik.system.DexClassLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 源仓库:内置源 + 外置 jar 源(与原版"我的听书"的 jar 格式完全兼容)
 */
object SourceRepository {

    private val mutex = Mutex()
    private val sourceCache = LinkedHashMap<String, TingShu>() // sourceId -> source
    private val jarSources = mutableListOf<TingShu>()

    private val _sourcesFlow = MutableStateFlow<List<TingShu>>(emptyList())
    val sourcesFlow: StateFlow<List<TingShu>> = _sourcesFlow

    /** 外置 jar 目录: /sdcard/Android/data/<pkg>/files/jars */
    fun jarsDir(): File {
        val external = ServiceLocator.appContext.getExternalFilesDir("jars")
        if (external != null && (external.exists() || external.mkdirs())) return external
        val fallback = File(ServiceLocator.appContext.filesDir, "jars")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    fun init() {
        refresh()
    }

    fun refresh() {
        sourceCache.clear()
        jarSources.clear()
        // 内置源
        BuiltinSourceEntry.getSources().forEach {
            sourceCache[it.getSourceId()] = it
        }
        // 外置 jar 源
        runCatching { loadExternalJars() }
            .onFailure { it.printStackTrace() }
        sourceCache.putAll(jarSources.associateBy { it.getSourceId() })
        _sourcesFlow.value = allSources()
    }

    private fun loadExternalJars() {
        val dir = jarsDir()
        dir.listFiles { f -> f.isFile && f.name.endsWith(".jar") }
            ?.sortedBy { it.name }
            ?.forEach { jar ->
                loadJar(jar).forEach { source ->
                    jarSources.add(source)
                    sourceCache[source.getSourceId()] = source
                }
            }
    }

    /**
     * 加载单个 dex jar。jar 文件名(去扩展名)即 entry_package。
     */
    private fun loadJar(jar: File): List<TingShu> {
        return try {
            val entryPackage = jar.nameWithoutExtension
            val loader = DexClassLoader(
                jar.absolutePath,
                ServiceLocator.appContext.cacheDir.absolutePath,
                null,
                javaClass.classLoader
            )
            val entryClass = loader.loadClass("$entryPackage.SourceEntry")
            val sources = entryClass.getMethod("getSources").invoke(null) as List<TingShu>
            sources
        } catch (t: Throwable) {
            t.printStackTrace()
            emptyList()
        }
    }

    fun allSources(): List<TingShu> =
        builtInSources() + jarSources

    fun builtInSources(): List<TingShu> =
        BuiltinSourceEntry.getSources()

    fun builtInSourceIds(): Set<String> =
        builtInSources().map { it.getSourceId() }.toSet()

    fun jarSourceCount(): Int = jarSources.size

    /**
     * 用户勾选启用的源。settings.enabledSourceIds 为 null 时默认全部启用。
     */
    fun enabledSources(): List<TingShu> {
        val enabled = ServiceLocator.settings.enabledSourceIds
            ?: return allSources()
        return allSources().filter { it.getSourceId() in enabled }
    }

    fun searchableSources(): List<TingShu> = enabledSources().filter { it.isSearchable() }

    fun discoverableSources(): List<TingShu> = enabledSources().filter { it.isDiscoverable() }

    fun findById(sourceId: String): TingShu? = sourceCache[sourceId]

    /**
     * 更新某个源的启用状态
     */
    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) = mutex.withLock {
        val settings = ServiceLocator.settings
        val current = settings.enabledSourceIds ?: allSources().map { it.getSourceId() }.toSet()
        val next = if (enabled) current + sourceId else current - sourceId
        settings.enabledSourceIds = next
    }

    /**
     * 外置源 jar 是否来自订阅(决定源管理页能否编辑/删除)
     */
    suspend fun deleteJar(sourceEntryId: Long, jarFile: File) {
        ServiceLocator.database.externalSourceDao().let { dao ->
            val entity = ServiceLocator.database.externalSourceDao().getAll().find { it.id == sourceEntryId }
            if (entity != null) dao.delete(entity)
        }
        jarFile.delete()
        refresh()
    }
}
