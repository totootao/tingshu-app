package com.totootao.tingshu.data

import com.github.eprendre.tingshu.sources.TingShu
import com.totootao.tingshu.data.db.ExternalSourceEntity
import com.totootao.tingshu.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 外置源订阅管理。
 * 订阅接口 JSON 格式与原版一致:
 * { "version": 27, "entry_package": "sources_by_eprendre", "download_url": "...",
 *   "update_msg": "...", "support_url": "" }
 */
object SubscriptionManager {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dao get() = ServiceLocator.database.externalSourceDao()

    data class SubscriptionMeta(
        val version: Int,
        val entryPackage: String,
        val downloadUrl: String,
        val updateMsg: String,
        val supportUrl: String
    )

    /**
     * 添加/更新订阅:拉取 JSON -> 下载 jar -> 入库 -> 刷新源
     */
    suspend fun addOrUpdateSubscription(url: String, name: String): Result<ExternalSourceEntity> =
        withContext(Dispatchers.IO) {
            runCatching {
                val meta = fetchMeta(url)
                require(meta.entryPackage.isNotBlank()) { "entry_package 为空" }
                require(meta.downloadUrl.isNotBlank()) { "download_url 为空" }
                downloadJar(meta)

                val exist = dao.getByUrl(url)
                val entity = (exist ?: ExternalSourceEntity(name = name, url = url, entryPackage = meta.entryPackage)).let {
                    it.copy(
                        name = name,
                        entryPackage = meta.entryPackage,
                        version = meta.version,
                        downloadUrl = meta.downloadUrl,
                        updateMsg = meta.updateMsg,
                        supportUrl = meta.supportUrl,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
                if (exist != null) dao.update(entity) else dao.upsert(entity)
                SourceRepository.refresh()
                entity
            }
        }

    /**
     * 启动时检查所有订阅更新
     */
    suspend fun checkAllUpdates(): List<String> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<String>()
        dao.getAll().forEach { entity ->
            runCatching {
                val meta = fetchMeta(entity.url)
                if (meta.version > entity.version) {
                    downloadJar(meta)
                    dao.update(
                        entity.copy(
                            version = meta.version,
                            downloadUrl = meta.downloadUrl,
                            updateMsg = meta.updateMsg,
                            supportUrl = meta.supportUrl,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                    messages.add("${entity.name} 已更新到 v${meta.version}: ${meta.updateMsg}")
                }
            }.onFailure { it.printStackTrace() }
        }
        if (messages.isNotEmpty()) SourceRepository.refresh()
        messages
    }

    suspend fun removeSubscription(entity: ExternalSourceEntity) = withContext(Dispatchers.IO) {
        dao.delete(entity)
        File(SourceRepository.jarsDir(), "${entity.entryPackage}.jar").delete()
        SourceRepository.refresh()
    }

    private fun fetchMeta(url: String): SubscriptionMeta {
        val body = client.newCall(Request.Builder().url(url).build())
            .execute().body?.string() ?: throw IllegalStateException("订阅接口返回为空")
        val json = JSONObject(body)
        return SubscriptionMeta(
            version = json.optInt("version", 0),
            entryPackage = json.optString("entry_package"),
            downloadUrl = json.optString("download_url"),
            updateMsg = json.optString("update_msg"),
            supportUrl = json.optString("support_url")
        )
    }

    private fun downloadJar(meta: SubscriptionMeta) {
        val response = client.newCall(Request.Builder().url(meta.downloadUrl).build())
            .execute()
        if (!response.isSuccessful) throw IllegalStateException("jar 下载失败: HTTP ${response.code}")
        val bytes = response.body?.bytes() ?: throw IllegalStateException("jar 下载内容为空")
        val target = File(SourceRepository.jarsDir(), "${meta.entryPackage}.jar")
        target.writeBytes(bytes)
    }
}
