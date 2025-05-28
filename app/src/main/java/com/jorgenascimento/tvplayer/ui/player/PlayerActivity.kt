package com.jorgenascimento.tvplayer.ui.player

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.databinding.ActivityPlayerBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.Formatter
import java.util.Locale

class PlayerActivity : AppCompatActivity(), MediaPlayer.EventListener, SeekBar.OnSeekBarChangeListener {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    private val handler = Handler(Looper.getMainLooper())
    private var isControlsVisible = true
    private val hideControlsRunnable = Runnable { hideControls() }
    private val controlsTimeoutMs = 3000L // 3 segundos

    companion object {
        private const val TAG = "PlayerActivity"
        private const val DEFAULT_NETWORK_CACHING = 1500 //ms
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        hideSystemUI() // Entra em modo imersivo

        val channelUrl = intent.getStringExtra("channel_url")
        if (channelUrl.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_url_canal_nao_encontrada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d(TAG, "A reproduzir canal: $channelUrl")

        setupPlayer(channelUrl)
        setupControls()
    }

    private fun setupPlayer(url: String) {
        val options = arrayListOf("--network-caching=$DEFAULT_NETWORK_CACHING")
        libVLC = LibVLC(this, options)
        mediaPlayer = MediaPlayer(libVLC)
        mediaPlayer.attachViews(binding.videoLayout, null, false, false)
        mediaPlayer.setEventListener(this)

        try {
            val uri = Uri.parse(url)
            val media = Media(libVLC, uri)
            media.setHWDecoderEnabled(true, false)
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
            binding.buttonPlayPause.setImageResource(R.drawable.ic_player_pause)
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao tentar reproduzir: $url", e)
            Toast.makeText(this, getString(R.string.toast_erro_iniciar_reproducao), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupControls() {
        binding.buttonPlayPause.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
            } else {
                mediaPlayer.play()
                binding.buttonPlayPause.setImageResource(R.drawable.ic_player_pause)
            }
            resetControlsTimeout() // Mostra os controlos ao interagir
        }

        binding.seekBarProgress.setOnSeekBarChangeListener(this)

        binding.playerRootLayout.setOnClickListener {
            toggleControlsVisibility()
        }
        scheduleHideControls() // Esconde os controlos após um tempo
    }

    private fun toggleControlsVisibility() {
        if (isControlsVisible) {
            hideControls()
        } else {
            showControls()
            scheduleHideControls()
        }
    }

    private fun showControls() {
        binding.playerControlsContainer.visibility = View.VISIBLE
        isControlsVisible = true
    }

    private fun hideControls() {
        binding.playerControlsContainer.visibility = View.GONE
        isControlsVisible = false
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, controlsTimeoutMs)
    }

    private fun resetControlsTimeout() {
        showControls() // Garante que os controlos estão visíveis
        scheduleHideControls()
    }


    private val updateProgressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (mediaPlayer.isPlaying) {
                val pos = mediaPlayer.position
                binding.seekBarProgress.progress = (pos * binding.seekBarProgress.max).toInt()
                binding.textCurrentTime.text = formatTime(mediaPlayer.time)
            }
            if (mediaPlayer.length > 0 && binding.seekBarProgress.max.toLong() != mediaPlayer.length) {
                // Atualiza o max da seekbar se o length mudar (para streams ao vivo pode não ser fixo)
                binding.seekBarProgress.max = mediaPlayer.length.toInt() // Ou um valor fixo como 1000 e calcular percentagem
                binding.textTotalTime.text = formatTime(mediaPlayer.length)
            }
            handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    private fun formatTime(timeMs: Long): String {
        if (timeMs <= 0) return "00:00"
        val totalSeconds = timeMs / 1000
        val seconds = totalSeconds % 60
        val minutes = totalSeconds / 60 % 60
        val hours = totalSeconds / 3600
        val formatter = Formatter(Locale.getDefault())
        return if (hours > 0) {
            formatter.format("%d:%02d:%02d", hours, minutes, seconds).toString()
        } else {
            formatter.format("%02d:%02d", minutes, seconds).toString()
        }
    }


    override fun onEvent(event: MediaPlayer.Event) {
        runOnUiThread { // Garante que as atualizações da UI ocorram na thread principal
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    Log.d(TAG, "MediaPlayer Playing")
                    binding.playerBuffering.visibility = View.GONE
                    binding.buttonPlayPause.setImageResource(R.drawable.ic_player_pause)
                    handler.post(updateProgressRunnable)
                    binding.textTotalTime.text = formatTime(mediaPlayer.length)
                    binding.seekBarProgress.max = mediaPlayer.length.toInt().takeIf { it > 0 } ?: 1000 // Evita max 0
                }
                MediaPlayer.Event.Paused -> {
                    Log.d(TAG, "MediaPlayer Paused")
                    binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
                    handler.removeCallbacks(updateProgressRunnable)
                }
                MediaPlayer.Event.Stopped -> {
                    Log.d(TAG, "MediaPlayer Stopped")
                    binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
                    handler.removeCallbacks(updateProgressRunnable)
                    binding.seekBarProgress.progress = 0
                    binding.textCurrentTime.text = formatTime(0)
                }
                MediaPlayer.Event.EndReached -> {
                    Log.d(TAG, "MediaPlayer EndReached")
                    binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play) // Ou um ícone de replay
                    handler.removeCallbacks(updateProgressRunnable)
                    binding.seekBarProgress.progress = binding.seekBarProgress.max
                    binding.textCurrentTime.text = binding.textTotalTime.text
                    // finish() // Opcional: fechar o player
                }
                MediaPlayer.Event.EncounteredError -> {
                    Log.e(TAG, "Erro de reprodução LibVLC")
                    Toast.makeText(this, getString(R.string.toast_erro_reproducao), Toast.LENGTH_LONG).show()
                    binding.playerBuffering.visibility = View.GONE
                    // finish()
                }
                MediaPlayer.Event.Buffering -> {
                    Log.d(TAG, "MediaPlayer Buffering: ${event.buffering}%")
                    if (event.buffering >= 100f || !mediaPlayer.isPlaying) { // Se buffer completo ou não está a tocar
                        binding.playerBuffering.visibility = View.GONE
                    } else {
                        binding.playerBuffering.visibility = View.VISIBLE
                    }
                }
                MediaPlayer.Event.TimeChanged -> {
                    // O runnable updateProgressRunnable já lida com isso de forma mais controlada
                    // binding.textCurrentTime.text = formatTime(event.timeChanged)
                    // binding.seekBarProgress.progress = (mediaPlayer.position * binding.seekBarProgress.max).toInt()
                }
                MediaPlayer.Event.LengthChanged -> {
                    Log.d(TAG, "MediaPlayer LengthChanged: ${event.lengthChanged}")
                    binding.textTotalTime.text = formatTime(event.lengthChanged)
                    binding.seekBarProgress.max = event.lengthChanged.toInt().takeIf { it > 0 } ?: 1000
                }
                else -> {
                    // Log.v(TAG, "Evento MediaPlayer não tratado: ${event.type}")
                }
            }
        }
    }

    // Implementação de SeekBar.OnSeekBarChangeListener
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser && ::mediaPlayer.isInitialized) {
            // Converte o progresso da SeekBar (0-max) para a posição do media player (0.0f-1.0f)
            val newPosition = progress.toFloat() / seekBar!!.max.toFloat()
            mediaPlayer.position = newPosition
            binding.textCurrentTime.text = formatTime((newPosition * mediaPlayer.length).toLong())
            resetControlsTimeout()
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        if (::mediaPlayer.isInitialized) {
            handler.removeCallbacks(updateProgressRunnable) // Pausa a atualização automática
            handler.removeCallbacks(hideControlsRunnable) // Mantém os controlos visíveis
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        if (::mediaPlayer.isInitialized) {
            handler.post(updateProgressRunnable) // Retoma a atualização automática
            scheduleHideControls() // Agenda o esconder dos controlos
        }
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

    override fun onPause() {
        super.onPause()
        if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
            mediaPlayer.pause()
        }
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
    }

    override fun onResume() {
        super.onResume()
        if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
            // Apenas retoma se não estiver a tocar (pode ter sido pausado ou parado)
            // Se o media player foi parado ou chegou ao fim, play() pode recomeçar.
            mediaPlayer.play()
        }
        if (isControlsVisible) { // Se os controlos estavam visíveis, reagenda o esconder
            scheduleHideControls()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.setEventListener(null) // Remove o listener
            mediaPlayer.release()
        }
        if (::libVLC.isInitialized) {
            libVLC.release()
        }
    }
}
