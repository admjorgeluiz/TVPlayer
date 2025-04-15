package com.example.tvplayer.ui.player

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.tvplayer.R
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class PlayerActivity : AppCompatActivity() {

    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var videoLayout: VLCVideoLayout

    companion object {
        private const val TAG = "PlayerActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Utiliza o layout que contém o VLCVideoLayout com id "videoLayout"
        setContentView(R.layout.activity_player)

        videoLayout = findViewById(R.id.videoLayout)

        // Recupera a URL do canal enviada via Intent
        val channelUrl = intent.getStringExtra("channel_url")
        if (channelUrl.isNullOrEmpty()) {
            Toast.makeText(this, "URL do canal não encontrada", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d(TAG, "Reproduzindo canal: $channelUrl")

        // Inicializa o LibVLC com as opções desejadas (por exemplo, network caching de 300ms)
        val options = arrayListOf("--network-caching=300")
        libVLC = LibVLC(this, options)

        // Cria o MediaPlayer e anexa as views (o VLCVideoLayout)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.attachViews(videoLayout, null, false, false)

        // Define o listener para tratamento de eventos, incluindo erros (utilize EncounteredError conforme sua versão do LibVLC)
        mediaPlayer.setEventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Erro de reprodução: evento ${event.type}")
                    runOnUiThread {
                        Toast.makeText(
                            this@PlayerActivity,
                            "Erro durante a reprodução",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                else -> {
                    Log.d(TAG, "Evento de MediaPlayer: ${event.type}")
                }
            }
        }

        // Inicia a reprodução
        playMedia(channelUrl)
    }

    /**
     * Inicia a reprodução do stream a partir da URL informada.
     */
    private fun playMedia(url: String) {
        try {
            // Cria um objeto Media a partir da URL
            val uri = Uri.parse(url)
            val media = Media(libVLC, uri)
            // Habilita o decodificador de hardware (se suportado)
            media.setHWDecoderEnabled(true, false)
            // Adiciona opções extras se necessário (exemplo: network caching)
            media.addOption(":network-caching=300")
            mediaPlayer.media = media

            // Libera o objeto Media (o MediaPlayer já o clonou internamente)
            media.release()

            // Inicia a reprodução
            mediaPlayer.play()
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao reproduzir o conteúdo", e)
            Toast.makeText(this, "Erro ao iniciar a reprodução", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.pause()
        }
    }

    override fun onResume() {
        super.onResume()
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
