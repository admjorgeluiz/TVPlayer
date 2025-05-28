package com.jorgenascimento.tvplayer.ui.player

import android.content.res.Configuration // Import para onConfigurationChanged
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
    private val controlsTimeoutMs = 3000L

    companion object {
        private const val TAG = "PlayerActivity"
        private const val DEFAULT_NETWORK_CACHING = 1500
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        private const val KEY_CURRENT_CHANNEL_URL = "current_channel_url" // Para salvar estado se não usar configChanges
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        // hideSystemUI() // Chamado em onWindowFocusChanged e onResume para garantir

        val channelUrlToPlay: String?
        if (savedInstanceState != null) {
            // Se estivéssemos a restaurar estado sem configChanges:
            // channelUrlToPlay = savedInstanceState.getString(KEY_CURRENT_CHANNEL_URL)
            // Log.d(TAG, "Restaurando URL do savedInstanceState: $channelUrlToPlay")
            channelUrlToPlay = intent.getStringExtra("channel_url") // Com configChanges, o intent original ainda é válido
        } else {
            channelUrlToPlay = intent.getStringExtra("channel_url")
            Log.d(TAG, "Obtendo URL do Intent: $channelUrlToPlay")
        }


        if (channelUrlToPlay.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_url_canal_nao_encontrada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d(TAG, "A reproduzir canal: $channelUrlToPlay")

        setupPlayer(channelUrlToPlay)
        setupControls()
    }

    // Se você usar android:configChanges, este método será chamado em vez de recriar a Activity.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: Nova orientação: ${newConfig.orientation}")
        // O LibVLC geralmente lida bem com a mudança de tamanho da surface.
        // Você pode adicionar lógica aqui se precisar ajustar a UI dos seus controlos personalizados.
        // Por exemplo, se os controlos tivessem um layout diferente para paisagem/retrato.
        // Como o VLCVideoLayout ocupa match_parent, ele deve se ajustar.
        // É uma boa prática chamar hideSystemUI() novamente para garantir o modo imersivo.
        hideSystemUI()
    }


    // Se você NÃO usar configChanges e quiser salvar o estado manualmente:
    // override fun onSaveInstanceState(outState: Bundle) {
    //     super.onSaveInstanceState(outState)
    //     if (::mediaPlayer.isInitialized) {
    //         val currentUrl = intent.getStringExtra("channel_url") // Ou se você guardar a URL numa variável de membro
    //         if (currentUrl != null) {
    //             outState.putString(KEY_CURRENT_CHANNEL_URL, currentUrl)
    //             Log.d(TAG, "Salvando URL no onSaveInstanceState: $currentUrl")
    //         }
    //         // Salvar a posição atual também seria ideal aqui
    //     }
    // }


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
        binding.buttonBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            resetControlsTimeout()
        }

        binding.buttonPlayPause.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
            } else {
                mediaPlayer.play()
                binding.buttonPlayPause.setImageResource(R.drawable.ic_player_pause)
            }
            resetControlsTimeout()
        }

        binding.seekBarProgress.setOnSeekBarChangeListener(this)

        binding.playerRootLayout.setOnClickListener {
            toggleControlsVisibility()
        }
        scheduleHideControls()
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
        showControls()
        scheduleHideControls()
    }


    private val updateProgressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                val pos = mediaPlayer.position
                binding.seekBarProgress.progress = (pos * binding.seekBarProgress.max).toInt()
                binding.textCurrentTime.text = formatTime(mediaPlayer.time)
            }
            if (::mediaPlayer.isInitialized && mediaPlayer.length > 0 && binding.seekBarProgress.max.toLong() != mediaPlayer.length) {
                binding.seekBarProgress.max = mediaPlayer.length.toInt().takeIf { it > 0 } ?: 1000
                binding.textTotalTime.text = formatTime(mediaPlayer.length)
            }
            if (::mediaPlayer.isInitialized && !isFinishing && !isDestroyed) { // Continua a agendar apenas se o player e activity estiverem válidos
                handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
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
        if (!isFinishing && !isDestroyed) {
            runOnUiThread {
                when (event.type) {
                    MediaPlayer.Event.Playing -> {
                        Log.d(TAG, "MediaPlayer Playing")
                        binding.playerBuffering.visibility = View.GONE
                        binding.buttonPlayPause.setImageResource(R.drawable.ic_player_pause)
                        handler.removeCallbacks(updateProgressRunnable)
                        handler.post(updateProgressRunnable)
                        binding.textTotalTime.text = formatTime(mediaPlayer.length)
                        binding.seekBarProgress.max = mediaPlayer.length.toInt().takeIf { it > 0 } ?: 1000
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
                        binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
                        handler.removeCallbacks(updateProgressRunnable)
                        binding.seekBarProgress.progress = binding.seekBarProgress.max
                        binding.textCurrentTime.text = binding.textTotalTime.text
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        Log.e(TAG, "Erro de reprodução LibVLC")
                        Toast.makeText(this, getString(R.string.toast_erro_reproducao), Toast.LENGTH_LONG).show()
                        binding.playerBuffering.visibility = View.GONE
                    }
                    MediaPlayer.Event.Buffering -> {
                        Log.d(TAG, "MediaPlayer Buffering: ${event.buffering}%")
                        if (event.buffering >= 100f || !mediaPlayer.isPlaying) {
                            binding.playerBuffering.visibility = View.GONE
                        } else {
                            binding.playerBuffering.visibility = View.VISIBLE
                        }
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        Log.d(TAG, "MediaPlayer LengthChanged: ${event.lengthChanged}")
                        binding.textTotalTime.text = formatTime(event.lengthChanged)
                        binding.seekBarProgress.max = event.lengthChanged.toInt().takeIf { it > 0 } ?: 1000
                    }
                }
            }
        }
    }

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser && ::mediaPlayer.isInitialized) {
            val newPosition = progress.toFloat() / seekBar!!.max.toFloat()
            if (newPosition in 0f..1f) {
                mediaPlayer.position = newPosition
                binding.textCurrentTime.text = formatTime((newPosition * mediaPlayer.length).toLong())
            }
            resetControlsTimeout()
        }
    }

    override fun onStartTrackingTouch(seekBar: SeekBar?) {
        if (::mediaPlayer.isInitialized) {
            handler.removeCallbacks(updateProgressRunnable)
            handler.removeCallbacks(hideControlsRunnable)
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        if (::mediaPlayer.isInitialized) {
            handler.post(updateProgressRunnable)
            scheduleHideControls()
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
            Log.d(TAG, "Player pausado em onPause()")
        }
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI() // Garante modo imersivo ao resumir
        if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
            // Apenas retoma se não estiver a tocar. Se foi parado ou erro, play() pode recomeçar.
            // Verifique o estado se precisar de lógica mais fina (ex: não dar play se foi 'EndReached' e você não quer replay automático)
            mediaPlayer.play()
            Log.d(TAG, "Player retomado em onResume()")
        }
        if (isControlsVisible) {
            scheduleHideControls()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy chamado para PlayerActivity")
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
        if (::mediaPlayer.isInitialized) {
            Log.d(TAG, "Liberando MediaPlayer")
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.setEventListener(null)
            mediaPlayer.release()
        }
        if (::libVLC.isInitialized) {
            Log.d(TAG, "Liberando LibVLC")
            libVLC.release()
        }
    }
}
