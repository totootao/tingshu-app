package com.totootao.tingshu.webview

import android.content.Context
import android.content.Intent
import com.github.eprendre.tingshu.utils.WebViewRequest
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 阻塞式 WebView 执行器:把请求排队并启动 WebViewActivity,等待结果。
 * (调用线程为后台线程)
 */
object WebViewExecutor {

    data class PendingTask(val request: WebViewRequest, val callback: (String?) -> Unit)

    private val queue = ConcurrentLinkedQueue<PendingTask>()

    fun push(request: WebViewRequest, callback: (String?) -> Unit) {
        queue.add(PendingTask(request, callback))
    }

    fun poll(): PendingTask? = queue.poll()

    fun executeBlocking(appContext: Context, request: WebViewRequest): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        push(request) { r ->
            result = r
            latch.countDown()
        }
        val intent = Intent(appContext, WebViewActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { appContext.startActivity(intent) }
            .onFailure {
                // 启动失败也要释放等待者
                latch.countDown()
            }
        latch.await(120, TimeUnit.SECONDS)
        return result
    }
}
