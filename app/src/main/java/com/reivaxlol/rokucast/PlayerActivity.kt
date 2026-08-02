package com.reivaxlol.rokucast

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "extra_video_url"
    }

    private var player: ExoPlayer? = null
    private lateinit var prefs: SharedPreferences
    private lateinit var videoUrl: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        prefs = getSharedPreferences("roku_cast_prefs", Context.MODE_PRIVATE)
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: ""

        val playerView: PlayerView = findViewById(R.id.playerView)
        val status: TextView = findViewById(R.id.playerStatus)
        val backButton: Button = findViewById(R.id.backButton)
        val sendButton: Button = findViewById(R.id.sendToRokuButton)

        status.text = videoUrl

        backButton.setOnClickListener { finish() }
        sendButton.setOnClickListener {
            val rokuIp = prefs.getString("roku_ip", "192.168.3.46") ?: "192.168.3.46"
            RokuClient.send(rokuIp, videoUrl) { _, message ->
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            }
        }

        if (videoUrl.isEmpty()) {
            status.text = "No se recibió ninguna URL de video."
            return
        }

        val exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        playerView.player = exoPlayer
        player = exoPlayer
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
    }
}
