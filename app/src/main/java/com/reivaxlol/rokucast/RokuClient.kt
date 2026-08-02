package com.reivaxlol.rokucast

import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object RokuClient {
    fun send(rokuIp: String, videoUrl: String, onResult: (success: Boolean, message: String) -> Unit) {
        val mainHandler = Handler(Looper.getMainLooper())
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
                mainHandler.post { onResult(code in 200..299, "Enviado a Roku ($code)") }
            } catch (e: Exception) {
                mainHandler.post { onResult(false, "Error: ${e.message}") }
            }
        }.start()
    }
}
