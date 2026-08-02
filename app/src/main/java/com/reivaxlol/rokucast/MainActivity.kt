package com.reivaxlol.rokucast

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlBar: EditText
    private lateinit var sendButton: Button
    private lateinit var prefs: SharedPreferences

    private val candidates = LinkedHashSet<String>()

    private val mediaExtensionRegex = Regex(
        "\\.(m3u8|ts|mp4|mpd|key|webm)(\\?|$)",
        RegexOption.IGNORE_CASE
    )
    private val mediaKeywordRegex = Regex(
        "segment|chunk|playlist|manifest|upload|source|stream|embed|get_video",
        RegexOption.IGNORE_CASE
    )

    private val adBlockDomains = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adexchangerapid.com", "usrpubtrk.com", "youradexchange.com",
        "shameful-farm.com", "difficultblock.com", "motionless-bus.com",
        "acscdn.com", "propellerads.com", "popads.net", "adsterra.com",
        "exoclick.com", "hilltopads.net"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("roku_cast_prefs", Context.MODE_PRIVATE)

        webView = findViewById(R.id.webView)
        urlBar = findViewById(R.id.urlBar)
        val goButton: Button = findViewById(R.id.goButton)
        sendButton = findViewById(R.id.sendButton)
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

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                val host = request.url.host ?: ""

                if (adBlockDomains.any { blocked -> host == blocked || host.endsWith(".$blocked") }) {
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
        return mediaExtensionRegex.containsMatchIn(url) || mediaKeywordRegex.containsMatchIn(url)
    }

    private fun updateSendButtonLabel() {
        sendButton.text = "📺 Enviar (${candidates.size})"
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
            .setTitle("Elige el video a enviar")
            .setItems(list.toTypedArray()) { _, which ->
                sendToRoku(list[which])
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun sendToRoku(videoUrl: String) {
        val rokuIp = prefs.getString("roku_ip", "192.168.3.46") ?: "192.168.3.46"
        Thread {
            try {
                val encoded = URLEncoder.encode(videoUrl, "UTF-8")
                val connection = URL("http://$rokuIp:8060/input?contentId=$encoded")
                    .openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.doOutput = false
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val code = connection.responseCode
                connection.disconnect()
                runOnUiThread {
                    Toast.makeText(this, "Enviado a Roku ($code)", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
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
