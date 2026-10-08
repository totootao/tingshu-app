package com.totootao.tingshu.webview

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.github.eprendre.tingshu.utils.WebViewRequest

/**
 * 通用 WebView 容器,四种模式:
 * 1. EXTRACT  渲染页面后执行脚本提取音频地址
 * 2. SNIFF    嗅探媒体流地址
 * 3. LOGIN    源登录页
 * 4. VERIFY   搜索验证页
 */
class WebViewActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var currentTask: WebViewExecutor.PendingTask? = null
    private var completed = false
    private var extractAttempts = 0

    private val statusView by lazy { TextView(this) }
    private val actionButton by lazy { Button(this) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT
            )
        }
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(24, 16, 24, 16)
        }
        statusView.textSize = 14f
        actionButton.text = "完成"
        actionButton.visibility = View.GONE
        topBar.addView(statusView, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        topBar.addView(actionButton)
        root.addView(topBar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        root.addView(webView)
        setContentView(root)

        val task = WebViewExecutor.poll()
        if (task == null) {
            finish()
            return
        }
        currentTask = task
        val request = task.request

        setupWebView(request)

        actionButton.setOnClickListener {
            when (request.mode) {
                WebViewRequest.MODE_LOGIN -> {
                    Toast.makeText(this, "登录信息已保存", Toast.LENGTH_SHORT).show()
                    complete("")
                }
                WebViewRequest.MODE_VERIFY -> {
                    statusView.text = "验证完成,即将继续搜索…"
                    actionButton.isEnabled = false
                    webView?.visibility = View.GONE
                    window.decorView.postDelayed({ complete("") }, request.delayMs.coerceAtLeast(300))
                }
                else -> complete(null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(request: WebViewRequest) {
        val wv = webView ?: return
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = com.github.eprendre.tingshu.extensions.getMobileUA()
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        statusView.text = when (request.mode) {
            WebViewRequest.MODE_EXTRACT -> "正在提取音频地址…"
            WebViewRequest.MODE_SNIFF -> "正在嗅探音频地址…"
            WebViewRequest.MODE_LOGIN -> request.loginHint.ifBlank { "请登录,完成后点击右上角\"完成\"" }
            WebViewRequest.MODE_VERIFY -> "请完成人机验证,完成后点击\"完成\""
            else -> ""
        }
        if (request.mode == WebViewRequest.MODE_LOGIN || request.mode == WebViewRequest.MODE_VERIFY) {
            actionButton.visibility = View.VISIBLE
        }
        if (request.isDesktop) {
            wv.settings.userAgentString = com.github.eprendre.tingshu.extensions.getDesktopUA()
        }

        extractAttempts = 0

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, urlRequest: WebResourceRequest): Boolean {
                return false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                when (request.mode) {
                    WebViewRequest.MODE_EXTRACT -> runExtract(view, request)
                    WebViewRequest.MODE_SNIFF -> runSniffPlay(view)
                }
            }

            override fun shouldInterceptRequest(view: WebView, resourceRequest: WebResourceRequest): WebResourceResponse? {
                if (request.mode == WebViewRequest.MODE_SNIFF) {
                    val url = resourceRequest.url.toString()
                    if (isMediaUrl(url) || request.validateUrl?.invoke(url) == true) {
                        mainHandler.post { complete(url) }
                    }
                }
                return null
            }
        }
        wv.webChromeClient = WebChromeClient()
        wv.loadUrl(request.url)
    }

    private fun runExtract(view: WebView, request: WebViewRequest) {
        if (completed) return
        view.evaluateJavascript(request.script) { raw ->
            if (completed) return@evaluateJavascript
            val result = raw?.removePrefix("\"")?.removeSuffix("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\\\", "\\")
                ?.replace("\\n", "\n")
            val audioUrl = runCatching { request.parse?.invoke(result ?: "") }.getOrNull()
            if (audioUrl != null) {
                complete(audioUrl)
            } else {
                extractAttempts++
                if (extractAttempts <= 20) {
                    statusView.text = "正在提取音频地址…(第 $extractAttempts 次尝试)"
                    mainHandler.postDelayed({ if (!completed) runExtract(view, request) }, 1500)
                } else {
                    complete(null)
                }
            }
        }
    }

    private fun runSniffPlay(view: WebView) {
        if (completed) return
        // 尝试自动点击页面上的媒体元素播放
        val js = """
            (function() {
                var medias = document.querySelectorAll('audio,video');
                for (var i = 0; i < medias.length; i++) { try { medias[i].play(); } catch(e) {} }
                return medias.length;
            })();
        """.trimIndent()
        view.evaluateJavascript(js, null)
        mainHandler.postDelayed({ if (!completed) runSniffPlay(view) }, 2000)
    }

    private fun isMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        val extHit = MEDIA_EXT.any { lower.contains(it) }
        return extHit
    }

    private val mainHandler = android.os.Handler(mainLooper)

    private fun complete(result: String?) {
        if (completed) return
        completed = true
        currentTask?.let { task ->
            // 结果通过回调回传,同时延迟 finish 保证界面过渡
            task.callback(result)
        }
        mainHandler.postDelayed({ finish() }, 300)
    }

    override fun onDestroy() {
        // 防止等待方永久阻塞
        complete(completedAnswerOrNull())
        webView?.apply {
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun completedAnswerOrNull(): String? = if (completed) "" else null

    companion object {
        private val MEDIA_EXT = listOf(
            ".mp3", ".m4a", ".aac", ".wav", ".flac", ".ogg", ".m3u8",
            "audio/", "/audio", "type=m3u8", ".mp4"
        )
    }
}
