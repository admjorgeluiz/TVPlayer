package com.jorgenascimento.tvplayer.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.databinding.ActivityPlayerBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.Formatter
import java.util.Locale
// import kotlin.math.abs // Não é estritamente necessário para a lógica atual de deltaY

class PlayerActivity : AppCompatActivity(), MediaPlayer.EventListener, SeekBar.OnSeekBarChangeListener, GestureDetector.OnGestureListener {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    private val handler = Handler(Looper.getMainLooper())
    private var isControlsVisible = true
    private val hideControlsRunnable = Runnable { hideControls() }
    private val controlsTimeoutMs = 3000L

    // Para controlo de gestos
    private lateinit var gestureDetector: GestureDetectorCompat
    private lateinit var audioManager: AudioManager
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var initialVolumeOnScroll: Int = 0 // Guarda o volume no início do gesto de scroll
    private var initialBrightnessOnScroll: Float = 0.5f // Guarda o brilho no início do gesto de scroll
    private var maxVolume: Int = 0


    private enum class GestureControlMode { NONE, BRIGHTNESS, VOLUME }
    private var gestureControlMode = GestureControlMode.NONE

    companion object {
        private const val TAG = "PlayerActivity"
        private const val DEFAULT_NETWORK_CACHING = 1500
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
        // private const val KEY_CURRENT_CHANNEL_URL = "current_channel_url" // Para salvar estado se não usar configChanges
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.hide()
        // hideSystemUI() // Será chamado em onWindowFocusChanged e onResume

        val channelUrlToPlay: String? = intent.getStringExtra("channel_url")

        if (channelUrlToPlay.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_url_canal_nao_encontrada), Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        Log.d(TAG, "A reproduzir canal: $channelUrlToPlay")

        setupPlayer(channelUrlToPlay)
        setupControlsAndGestures()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "onConfigurationChanged: Nova orientação: ${newConfig.orientation}")
        // Atualiza as dimensões da tela após a re-layout devido à mudança de orientação
        binding.playerRootLayout.post {
            updateScreenDimensions()
        }
        hideSystemUI()
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
            // O ícone será atualizado pelo evento Playing
        } catch (e: Exception) {
            Log.e(TAG, "Exceção ao tentar reproduzir: $url", e)
            Toast.makeText(this, getString(R.string.toast_erro_iniciar_reproducao), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupControlsAndGestures() {
        binding.buttonBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            resetControlsTimeout()
        }
        binding.buttonPlayPause.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                // O ícone será atualizado pelo evento Paused
            } else {
                mediaPlayer.play()
                // O ícone será atualizado pelo evento Playing
            }
            resetControlsTimeout()
        }
        binding.seekBarProgress.setOnSeekBarChangeListener(this)

        binding.playerRootLayout.setOnClickListener {
            toggleControlsVisibility()
        }
        // Inicialmente, os controlos estão visíveis, então agendamos para escondê-los
        if(isControlsVisible) scheduleHideControls()


        // Configuração de Gestos
        gestureDetector = GestureDetectorCompat(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        binding.playerRootLayout.post {
            updateScreenDimensions()
        }

        // Obter brilho atual da janela
        initialBrightnessOnScroll = window.attributes.screenBrightness
        if (initialBrightnessOnScroll < 0) { // -1 significa brilho padrão do sistema
            try {
                initialBrightnessOnScroll = android.provider.Settings.System.getInt(
                    contentResolver,
                    android.provider.Settings.System.SCREEN_BRIGHTNESS
                ) / 255f
            } catch (e: Exception) {
                Log.w(TAG, "Não foi possível obter o brilho do sistema.", e)
                initialBrightnessOnScroll = 0.5f // Fallback
            }
        }
    }

    private fun updateScreenDimensions() {
        screenWidth = binding.playerRootLayout.width
        screenHeight = binding.playerRootLayout.height
        Log.d(TAG, "Screen dimensions atualizadas: width=$screenWidth, height=$screenHeight")
    }

    private fun toggleControlsVisibility() {
        if (isControlsVisible) {
            hideControls()
        } else {
            showControls()
            scheduleHideControls() // Reagenda esconder se foram mostrados
        }
    }

    private fun showControls() {
        binding.playerControlsContainer.animate().alpha(1f).setDuration(200).withStartAction {
            binding.playerControlsContainer.visibility = View.VISIBLE
        }.start()
        isControlsVisible = true
    }

    private fun hideControls() {
        binding.playerControlsContainer.animate().alpha(0f).setDuration(200).withEndAction {
            binding.playerControlsContainer.visibility = View.GONE
        }.start()
        isControlsVisible = false
    }

    private fun scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, controlsTimeoutMs)
    }

    private fun resetControlsTimeout() {
        if (!isControlsVisible) showControls() // Mostra se estiverem escondidos
        scheduleHideControls()
    }

    private val updateProgressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying && !isFinishing && !isDestroyed) {
                val pos = mediaPlayer.position
                val time = mediaPlayer.time
                binding.seekBarProgress.progress = (pos * binding.seekBarProgress.max).toInt()
                binding.textCurrentTime.text = formatTime(time)
            }
            // Re-agenda apenas se a activity ainda estiver ativa
            if (!isFinishing && !isDestroyed) {
                handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun formatTime(timeMs: Long): String {
        if (timeMs < 0) return "--:--" // Para durações desconhecidas (streams ao vivo)
        if (timeMs == 0L) return "00:00"

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

    @SuppressLint("SwitchIntDef")
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
                        val length = mediaPlayer.length
                        binding.textTotalTime.text = formatTime(length)
                        binding.seekBarProgress.max = if (length > 0) length.toInt() else 1000 // Evita max 0
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
                        // finish() // Opcional: fechar o player
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
                        val newLength = event.lengthChanged
                        Log.d(TAG, "MediaPlayer LengthChanged: $newLength")
                        binding.textTotalTime.text = formatTime(newLength)
                        binding.seekBarProgress.max = if (newLength > 0) newLength.toInt() else 1000
                    }
                    MediaPlayer.Event.TimeChanged -> {
                        // O updateProgressRunnable já lida com isso, mas podemos atualizar aqui também se quisermos
                        // val newTime = event.timeChanged
                        // binding.textCurrentTime.text = formatTime(newTime)
                        // if (mediaPlayer.length > 0) {
                        //    binding.seekBarProgress.progress = (newTime * binding.seekBarProgress.max / mediaPlayer.length).toInt()
                        // }
                    }
                }
            }
        }
    }

    // --- Início da implementação de GestureDetector.OnGestureListener ---
    override fun onDown(e: MotionEvent): Boolean {
        if (screenWidth == 0 || screenHeight == 0) updateScreenDimensions() // Garante que temos as dimensões

        initialVolumeOnScroll = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        initialBrightnessOnScroll = window.attributes.screenBrightness
        if (initialBrightnessOnScroll < 0) initialBrightnessOnScroll = 0.5f // Fallback se for brilho do sistema

        if (e.x < screenWidth / 3) { // Terço esquerdo para brilho
            gestureControlMode = GestureControlMode.BRIGHTNESS
            Log.d(TAG, "Gesture: Modo Brilho ativado (x=${e.x}, screenWidth/3=${screenWidth/3})")
        } else if (e.x > screenWidth * 2 / 3) { // Terço direito para volume
            gestureControlMode = GestureControlMode.VOLUME
            Log.d(TAG, "Gesture: Modo Volume ativado (x=${e.x}, screenWidth*2/3=${screenWidth*2/3})")
        } else {
            gestureControlMode = GestureControlMode.NONE // Meio da tela não faz nada no scroll
            Log.d(TAG, "Gesture: Modo Nenhum (toque no meio)")
        }
        // Se o toque for na área dos controlos, não interceta o scroll para gestos
        // (Esta lógica pode ser refinada para permitir gestos mesmo sobre controlos transparentes)
        // if (isControlsVisible && e.y > binding.playerControlsContainer.top) {
        //    return false
        // }
        return true
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        // Já tratado pelo onClickListener do playerRootLayout
        // toggleControlsVisibility() // Poderia ser chamado aqui também
        return false // Deixa o evento de clique ser processado pelo listener do root
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float // distanceY > 0 significa deslizar para cima
    ): Boolean {
        if (e1 == null || gestureControlMode == GestureControlMode.NONE) return false
        if (screenHeight == 0) return false // Evita divisão por zero se dimensões não estiverem prontas

        // Inverte distanceY para que deslizar para cima seja positivo (aumentar)
        // e para baixo seja negativo (diminuir)
        val deltaY = -distanceY
        val scrollFactor = 2.0f // Ajuste a sensibilidade do scroll aqui (maior = mais sensível)

        when (gestureControlMode) {
            GestureControlMode.BRIGHTNESS -> {
                val change = (deltaY / screenHeight.toFloat()) * scrollFactor
                var newBrightness = initialBrightnessOnScroll + change
                newBrightness = newBrightness.coerceIn(0.05f, 1.0f) // Evita 0% e >100%

                val layoutParams = window.attributes
                layoutParams.screenBrightness = newBrightness
                window.attributes = layoutParams
                initialBrightnessOnScroll = newBrightness // Atualiza para o próximo onScroll
                // Log.d(TAG, "Brilho: $newBrightness (deltaY: $deltaY)")
                // TODO: Mostrar feedback visual para brilho (ex: um Toast ou um indicador na tela)
            }
            GestureControlMode.VOLUME -> {
                val change = (deltaY / screenHeight.toFloat()) * scrollFactor * maxVolume
                var newVolume = initialVolumeOnScroll + change.toInt()
                newVolume = newVolume.coerceIn(0, maxVolume)

                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0) // 0 para não mostrar UI padrão
                initialVolumeOnScroll = newVolume // Atualiza para o próximo onScroll
                // Log.d(TAG, "Volume: $newVolume (deltaY: $deltaY)")
                // TODO: Mostrar feedback visual para volume
            }
            GestureControlMode.NONE -> return false
        }
        return true
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        return false
    }
    // --- Fim da implementação de GestureDetector.OnGestureListener ---

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        // Se os controlos estiverem visíveis e o toque for sobre eles, não interceta para gestos de brilho/volume
        // Esta lógica pode ser mais refinada para verificar se o toque foi *fora* dos botões específicos
        if (isControlsVisible && event != null) {
            val x = event.x.toInt()
            val y = event.y.toInt()
            // Verifica se o toque está dentro da área dos controlos inferiores ou do botão de voltar
            // Esta é uma simplificação, pode ser melhorada
            if (y > binding.bottomControls.top || (y < binding.buttonBack.bottom && x < binding.buttonBack.right)) {
                if (gestureDetector.onTouchEvent(event)) return true // Deixa o detector de gestos tratar (ex: onSingleTapUp para os controlos)
                return super.onTouchEvent(event) // Permite que os controlos recebam o clique
            }
        }

        if (event != null && gestureDetector.onTouchEvent(event)) {
            return true
        }
        return super.onTouchEvent(event)
    }

    // Implementação de SeekBar.OnSeekBarChangeListener
    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser && ::mediaPlayer.isInitialized && mediaPlayer.isSeekable) {
            val newPosition = progress.toFloat() / (seekBar?.max?.takeIf { it > 0 }?.toFloat() ?: 1000f)
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
            handler.removeCallbacks(hideControlsRunnable) // Mantém os controlos visíveis
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        if (::mediaPlayer.isInitialized) {
            if (mediaPlayer.isSeekable) {
                // A posição já foi definida em onProgressChanged
            }
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
        hideSystemUI()
        if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
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
