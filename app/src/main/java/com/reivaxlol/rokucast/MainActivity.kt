package com.reivaxlol.rokucast

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Message
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var sendButton: Button
    private lateinit var blockCounter: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var prefs: SharedPreferences

    private val candidates = LinkedHashSet<String>()
    private var blockedCount = 0
    private var blockedDomains: Set<String> = emptySet()

    private val mediaExtensionRegex = Regex(
        "\\.(m3u8|ts|mp4|mpd|key|webm)(\\?|$)",
        RegexOption.IGNORE_CASE
    )
    private val mediaKeywordRegex = Regex(
        "segment|chunk|playlist|manifest|upload|source|stream|embed|get_video",
        RegexOption.IGNORE_CASE
    )
    private val nonMediaExtensionRegex = Regex(
        "\\.(js|css|png|jpe?g|gif|svg|webp|woff2?|ttf|json|ico|map)(\\?|$)",
        RegexOption.IGNORE_CASE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("roku_cast_prefs", Context.MODE_PRIVATE)
        blockedDomains = loadBlockedDomains()

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        val goButton: Button = findViewById(R.id.goButton)
        sendButton = findViewById(R.id.sendButton)
        blockCounter = findViewById(R.id.blockCounter)
        progressBar = findViewById(R.id.progressBar)
        val settingsButton: Button = findViewById(R.id.settingsButton)

        setupWebView()

        goButton.setOnClickListener { navigateToBarUrl() }
        urlBar.setOnEditorActionListener { _, actionId, event ->
            val pressedEnter = event != null &&
                event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || pressedEnter) {
                navigateToBarUrl()
                true
            } else {
                false
            }
        }

        settingsButton.setOnClickListener { showSettingsDialog() }
        sendButton.setOnClickListener { showCandidatesDialog() }

        val startUrl = prefs.getString("last_url", "https://www.google.com") ?: "https://www.google.com"
        urlBar.setText(startUrl)
        webView.loadUrl(startUrl)
    }

    private fun loadBlockedDomains(): Set<String> {
        return try {
            assets.open("adblock_domains.txt").bufferedReader().useLines { lines ->
                lines.map { it.trim() }.filter { it.isNotEmpty() }.toHashSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
    }

    private fun isBlockedHost(host: String): Boolean {
        if (host.isEmpty()) return false
        var current = host
        while (true) {
            if (blockedDomains.contains(current)) return true
            val dot = current.indexOf('.')
            if (dot < 0) return false
            current = current.substring(dot + 1)
        }
    }

    private fun registerBlock() {
        blockedCount++
        blockCounter.text = "$blockedCount bloqueados"
        blockCounter.setTextColor(android.graphics.Color.parseColor("#2fce6e"))
    }

    private fun navigateToBarUrl() {
        var url = urlBar.text.toString().trim()
        if (url.isEmpty()) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        candidates.clear()
        updateSendButtonLabel()
        webView.loadUrl(url)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.mediaPlaybackRequiresUserGesture = false
        // Never let the page open a second window/tab (the most common ad-popup mechanism).
        webView.settings.setSupportMultipleWindows(false)
        webView.settings.javaScriptCanOpenWindowsAutomatically = false

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) android.view.View.VISIBLE else android.view.View.GONE
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                // Refuse to open any popup window at all.
                return false
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val host = request.url.host ?: ""
                if (isBlockedHost(host)) {
                    runOnUiThread { registerBlock() }
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                val host = request.url.host ?: ""

                if (isBlockedHost(host)) {
                    runOnUiThread { registerBlock() }
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }

                if (looksLikeMedia(url)) {
                    runOnUiThread {
                        if (candidates.add(url)) {
                            updateSendButtonLabel()
                        }
                    }
                }
                return null
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                urlBar.setText(url)
                prefs.edit().putString("last_url", url).apply()
            }
        }
    }

    private fun looksLikeMedia(url: String): Boolean {
        if (url.startsWith("data:")) return false
        if (nonMediaExtensionRegex.containsMatchIn(url)) return false
        return mediaExtensionRegex.containsMatchIn(url) || mediaKeywordRegex.containsMatchIn(url)
    }

    private fun updateSendButtonLabel() {
        sendButton.text = "Enviar (${candidates.size})"
    }

    private fun showSettingsDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(prefs.getString("roku_ip", "192.168.3.46"))
        AlertDialog.Builder(this)
            .setTitle("IP del Roku")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                prefs.edit().putString("roku_ip", input.text.toString().trim()).apply()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun showCandidatesDialog() {
        if (candidates.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Sin videos detectados")
                .setMessage("Reproduce un video en la página y espera unos segundos.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val list = candidates.toList().reversed()
        AlertDialog.Builder(this)
            .setTitle("Elige el video a reproducir")
            .setItems(list.toTypedArray()) { _, which ->
                openInPlayer(list[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun openInPlayer(videoUrl: String) {
        val intent = Intent(this, PlayerActivity::class.java)
        intent.putExtra(PlayerActivity.EXTRA_VIDEO_URL, videoUrl)
        startActivity(intent)
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
