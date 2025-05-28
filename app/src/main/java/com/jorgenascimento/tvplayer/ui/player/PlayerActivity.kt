package com.jorgenascimento.tvplayer.ui.player

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.databinding.ActivityPlayerBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
// SR_CORRECTION: import org.videolan.libvlc.interfaces.IMedia removido conforme sugestão.

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    companion object {
        private const val TAG = "PlayerActivity"
        private const val DEFAULT_NETWORK_CACHING = 1500 //ms
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()

        val channelUrl = intent.getStringExtra("channel_url")
        if (channelUrl.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_url_canal_nao_encontrada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d(TAG, "Reproduzindo canal: $channelUrl")

        val options = arrayListOf("--network-caching=$DEFAULT_NETWORK_CACHING")
        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.attachViews(binding.videoLayout, null, false, false)

        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Erro de reprodução LibVLC: ${event.type}")
                    runOnUiThread {
                        Toast.makeText(
                            this@PlayerActivity,
                            getString(R.string.toast_erro_reproducao),
                            Toast.LENGTH_LONG
                        ).show()
                        // finish() // Considere se quer fechar em erro
                    }
                }
                MediaPlayer.Event.Buffering -> {
                    val viz = if (event.buffering == 100f) View.GONE else View.VISIBLE
                    Log.d(TAG, "MediaPlayer Buffering: ${event.buffering}%")
                    // binding.playerBufferingProgressBar.visibility = viz
                }
                MediaPlayer.Event.Playing -> Log.d(TAG, "MediaPlayer Playing")
                MediaPlayer.Event.Paused -> Log.d(TAG, "MediaPlayer Paused")
                MediaPlayer.Event.Stopped -> Log.d(TAG, "MediaPlayer Stopped")
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "MediaPlayer EndReached")
                    finish() // Fecha o player quando o stream termina
                }
                // else -> { Log.v(TAG, "Evento de MediaPlayer: ${event.type}") }
            }
        }
        playMedia(channelUrl)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            hideSystemUI()
        }
    }

    private fun hideSystemUI() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(android.view.WindowInsets.Type.statusBars() or android.view.WindowInsets.Type.navigationBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    )
        }
    }

    private fun playMedia(url: String) {
        try {
            val uri = Uri.parse(url)
            val media = Media(libVLC, uri)
            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao tentar reproduzir: $url", e)
            Toast.makeText(this, getString(R.string.toast_erro_iniciar_reproducao), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
    }

    // SR_CORRECTION: Lógica do onResume simplificada conforme sua sugestão
    override fun onResume() {
        super.onResume()
        // se inicializou e não estiver tocando (pausado, parado, etc.), tenta resumir/iniciar
        if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
            mediaPlayer.play()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.release()
        }
        if (::libVLC.isInitialized) {
            libVLC.release()
        }
    }
}
