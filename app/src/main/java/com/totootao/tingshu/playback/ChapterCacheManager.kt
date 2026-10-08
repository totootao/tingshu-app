package com.totootao.tingshu.playback

import com.totootao.tingshu.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 章节音频缓存:文件名 = md5(章节地址)。
 * 播放/提取后即可离线播放。
 */
object ChapterCacheManager {

    private val memoryMap = ConcurrentHashMap<String, String>() // chapterUrl -> fileName

    fun cacheDir(): File {
        val settings = ServiceLocator.settings
        val dir = File(ServiceLocator.appContext.cacheDir, "audio_cache")
        if (!dir.exists()) dir.mkdirs()
        enforceLimit(dir, settings.cacheLimitMb * 1024L * 1024L)
        return dir
    }

    fun cachedFile(chapterUrl: String): File? {
        val name = memoryMap[chapterUrl] ?: fileName(chapterUrl)
        val f = File(cacheDir(), name)
        return if (f.exists() && f.length() > 0) f else null
    }

    fun markCached(chapterUrl: String) {
        memoryMap[chapterUrl] = fileName(chapterUrl)
    }

    fun isCached(chapterUrl: String): Boolean = cachedFile(chapterUrl) != null

    /**
     * 下载章节音频到缓存
     */
    suspend fun download(chapterUrl: String, audioUrl: String, headers: Map<String, String> = emptyMap()): File? =
        withContext(Dispatchers.IO) {
            if (!ServiceLocator.settings.cacheEnabled) return@withContext null
            cachedFile(chapterUrl)?.let { return@withContext it }
            runCatching {
                val requestBuilder = okhttp3.Request.Builder().url(audioUrl)
                headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                val response = client.newCall(requestBuilder.build()).execute()
                if (!response.isSuccessful) return@withContext null
                val name = fileName(chapterUrl)
                val tmp = File(cacheDir(), "$name.tmp")
                response.body?.byteStream()?.use { input ->
                    tmp.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                val target = File(cacheDir(), name)
                if (!tmp.renameTo(target)) {
                    tmp.copyTo(target, overwrite = true)
                    tmp.delete()
                }
                markCached(chapterUrl)
                target
            }.getOrNull()
        }

    fun clearAll() {
        cacheDir().listFiles()?.forEach { it.delete() }
        memoryMap.clear()
    }

    private fun enforceLimit(dir: File, limitBytes: Long) {
        val files = dir.listFiles() ?: return
        val total = files.sumOf { it.length() }
        if (total <= limitBytes) return
        files.sortedBy { it.lastModified() }.forEach {
            if (files.sumOf { f -> f.length() } > limitBytes) it.delete()
        }
    }

    private fun fileName(chapterUrl: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(chapterUrl.toByteArray())
        return digest.joinToString("") { "%02x".format(it) } + ".audio"
    }

    private val client by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }
}
