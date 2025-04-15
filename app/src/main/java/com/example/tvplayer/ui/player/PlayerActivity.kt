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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // Obtenha o componente de vídeo do layout
        videoLayout = findViewById(R.id.vlc_video_layout)

        // Obtenha a URL do canal enviada via Intent
        val channelUrl = intent.getStringExtra("channel_url") ?: ""
        if (channelUrl.isBlank()) {
            Toast.makeText(this, "URL do canal inválida!", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Configuração do LibVLC com opções (você pode adicionar opções específicas se necessário)
        val options = ArrayList<String>()
        // Exemplo de opção para cache de rede: options.add(":network-caching=300")
        libVLC = LibVLC(this, options)

        // Cria o MediaPlayer e anexa a view de vídeo
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.attachViews(videoLayout, null, false, false)

        // Cria a mídia a partir da URL
        val media = Media(libVLC, Uri.parse(channelUrl))
        // Ativa a aceleração de hardware (opcional)
        media.setHWDecoderEnabled(true, false)
        // Adiciona uma opção de cache, se desejado
        media.addOption(":network-caching=300")

        // Atribui a mídia ao MediaPlayer e inicia a reprodução
        mediaPlayer.media = media
        // Libera a referência à mídia, pois o MediaPlayer já a possui
        media.release()

        // Configura o listener de eventos para monitorar erros de reprodução
        mediaPlayer.setEventListener(object : MediaPlayer.EventListener {
            override fun onEvent(event: MediaPlayer.Event) {
                if (event.type == MediaPlayer.Event.EncounteredError) {
                    Log.e("PlayerActivity", "Erro na reprodução!")
                    Toast.makeText(this@PlayerActivity, "Erro na reprodução.", Toast.LENGTH_LONG).show()
                    // Opcional: finalize a atividade
                    // finish()
                }
            }
        })

        // Prepara e inicia a reprodução
        mediaPlayer.play()
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer.stop()
        mediaPlayer.detachViews()
        mediaPlayer.release()
        libVLC.release()
    }
}
