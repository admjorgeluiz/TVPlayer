package com.jorgenascimento.tvplayer.ui.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
// SR_CORRECTION: import de GestureDetectorCompat removido
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.databinding.ActivityPlayerBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.util.Formatter
import java.util.Locale
import kotlin.math.abs

class PlayerActivity : AppCompatActivity(), MediaPlayer.EventListener, SeekBar.OnSeekBarChangeListener, GestureDetector.OnGestureListener {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    private val handler = Handler(Looper.getMainLooper())
    private var isControlsVisible = true
    private val hideControlsRunnable = Runnable { hideControls() }
    private val controlsTimeoutMs = 3000L

    // SR_CORRECTION: Usar GestureDetector diretamente
    private lateinit var gestureDetector: GestureDetector
    private lateinit var audioManager: AudioManager
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    private var volumeAtGestureStart: Int = 0
    private var brightnessAtGestureStart: Float = 0.5f
    private var maxVolume: Int = 0

    private enum class GestureControlMode { NONE, BRIGHTNESS, VOLUME }
    private var gestureControlMode = GestureControlMode.NONE

    companion object {
        private const val TAG = "PlayerActivity"
        private const val GESTURE_TAG = "PlayerGesture"
        private const val DEFAULT_NETWORK_CACHING = 1500
        private const val POSITION_UPDATE_INTERVAL_MS = 500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.d(TAG, "onCreate: Iniciando PlayerActivity")

        supportActionBar?.hide()

        val channelUrlToPlay: String? = intent.getStringExtra("channel_url")

        if (channelUrlToPlay.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.toast_url_canal_nao_encontrada), Toast.LENGTH_SHORT).show()
            Log.e(TAG, "URL do canal está nula ou vazia. Finalizando PlayerActivity.")
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
        binding.playerRootLayout.post {
            updateScreenDimensions()
        }
        hideSystemUI()
    }

    private fun setupPlayer(url: String) {
        Log.d(TAG, "setupPlayer: Configurando player para URL: $url")
        try {
            val options = arrayListOf("--network-caching=$DEFAULT_NETWORK_CACHING", "--no-sub-autodetect-file")
            libVLC = LibVLC(this, options)
            mediaPlayer = MediaPlayer(libVLC)
            mediaPlayer.attachViews(binding.videoLayout, null, false, false)
            mediaPlayer.setEventListener(this)
            Log.d(TAG, "LibVLC e MediaPlayer inicializados.")

            val uri = Uri.parse(url)
            val media = Media(libVLC, uri)

            media.setHWDecoderEnabled(false, false)
            Log.i(TAG, "Decodificação por Hardware DESATIVADA para maior compatibilidade.")

            mediaPlayer.media = media
            media.release()
            Log.d(TAG, "Mídia definida no MediaPlayer. A iniciar play().")
            mediaPlayer.play()
        } catch (e: Exception) {
            Log.e(TAG, "Exceção em setupPlayer ao tentar reproduzir: $url", e)
            Toast.makeText(this, getString(R.string.toast_erro_iniciar_reproducao), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlsAndGestures() {
        Log.d(TAG, "setupControlsAndGestures: Configurando controlos e gestos.")
        binding.buttonBack.setOnClickListener {
            Log.d(TAG, "Botão Voltar clicado.")
            onBackPressedDispatcher.onBackPressed()
            resetControlsTimeout()
        }
        binding.buttonPlayPause.setOnClickListener {
            Log.d(TAG, "Botão Play/Pause clicado. IsPlaying: ${mediaPlayer.isPlaying}")
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
            } else {
                mediaPlayer.play()
            }
            resetControlsTimeout()
        }
        binding.seekBarProgress.setOnSeekBarChangeListener(this)

        binding.playerRootLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        if(isControlsVisible) scheduleHideControls()

        // SR_CORRECTION: Usar GestureDetector diretamente
        gestureDetector = GestureDetector(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        binding.playerRootLayout.post {
            updateScreenDimensions()
        }
    }

    private fun updateScreenDimensions() {
        screenWidth = binding.playerRootLayout.width
        screenHeight = binding.playerRootLayout.height
        Log.d(GESTURE_TAG, "Screen dimensions atualizadas: width=$screenWidth, height=$screenHeight")
        if (screenWidth == 0 || screenHeight == 0) {
            Log.w(GESTURE_TAG, "AVISO: Dimensões da tela são zero após a atualização!")
        }
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
        if (!isControlsVisible) showControls()
        scheduleHideControls()
    }

    private val updateProgressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying && !isFinishing && !isDestroyed) {
                val pos = mediaPlayer.position
                val time = mediaPlayer.time
                if (binding.seekBarProgress.max > 0) {
                    binding.seekBarProgress.progress = (pos * binding.seekBarProgress.max).toInt()
                }
                binding.textCurrentTime.text = formatTime(time)
            }
            if (!isFinishing && !isDestroyed) {
                handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun formatTime(timeMs: Long): String {
        if (timeMs < 0) return "--:--"
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
                        Log.d(TAG, "MediaPlayer Event: Playing")
                        binding.playerBuffering.visibility = View.GONE
                        binding.buttonPlayPause.setImageResource(R.drawable.ic_player_pause)
                        handler.removeCallbacks(updateProgressRunnable)
                        handler.post(updateProgressRunnable)
                        val length = mediaPlayer.length
                        binding.textTotalTime.text = formatTime(length)
                        binding.seekBarProgress.max = if (length > 0) length.toInt() else 1000
                    }
                    MediaPlayer.Event.Paused -> {
                        Log.d(TAG, "MediaPlayer Event: Paused")
                        binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
                        handler.removeCallbacks(updateProgressRunnable)
                    }
                    MediaPlayer.Event.Stopped -> {
                        Log.d(TAG, "MediaPlayer Event: Stopped")
                        binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
                        handler.removeCallbacks(updateProgressRunnable)
                        binding.seekBarProgress.progress = 0
                        binding.textCurrentTime.text = formatTime(0)
                    }
                    MediaPlayer.Event.EndReached -> {
                        Log.d(TAG, "MediaPlayer Event: EndReached")
                        binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
                        handler.removeCallbacks(updateProgressRunnable)
                        binding.seekBarProgress.progress = binding.seekBarProgress.max
                        binding.textCurrentTime.text = binding.textTotalTime.text
                    }
                    MediaPlayer.Event.EncounteredError -> {
                        Log.e(TAG, "MediaPlayer Event: EncounteredError")
                        Toast.makeText(this, getString(R.string.toast_erro_reproducao), Toast.LENGTH_LONG).show()
                        binding.playerBuffering.visibility = View.GONE
                    }
                    MediaPlayer.Event.Buffering -> {
                        Log.d(TAG, "MediaPlayer Event: Buffering ${event.buffering}%")
                        if (event.buffering >= 100f || !mediaPlayer.isPlaying) {
                            binding.playerBuffering.visibility = View.GONE
                        } else {
                            binding.playerBuffering.visibility = View.VISIBLE
                        }
                    }
                    MediaPlayer.Event.LengthChanged -> {
                        val newLength = event.lengthChanged
                        Log.d(TAG, "MediaPlayer Event: LengthChanged $newLength")
                        binding.textTotalTime.text = formatTime(newLength)
                        binding.seekBarProgress.max = if (newLength > 0) newLength.toInt() else 1000
                    }
                    MediaPlayer.Event.TimeChanged -> { /* O updateProgressRunnable já lida com isso */ }
                }
            }
        }
    }

    // Implementação de GestureDetector.OnGestureListener
    override fun onDown(e: MotionEvent): Boolean {
        if (screenHeight == 0 || screenWidth == 0) {
            updateScreenDimensions()
            if (screenHeight == 0 || screenWidth == 0) {
                Log.e(GESTURE_TAG, "onDown: FALHA ao obter dimensões da tela. Gesto não será processado.")
                return false
            }
        }
        volumeAtGestureStart = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        brightnessAtGestureStart = window.attributes.screenBrightness
        if (brightnessAtGestureStart < 0) {
            try {
                brightnessAtGestureStart = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (ex: Exception) {
                Log.w(GESTURE_TAG, "onDown: Não foi possível obter o brilho do sistema.", ex)
                brightnessAtGestureStart = 0.5f
            }
        }
        Log.d(GESTURE_TAG, "onDown: x=${e.x.toInt()}, y=${e.y.toInt()}. Vol inicial: $volumeAtGestureStart, Brilho inicial: $brightnessAtGestureStart")

        val oneQuarterScreenWidth = screenWidth / 4
        val threeQuartersScreenWidth = screenWidth * 3 / 4

        if (e.x < oneQuarterScreenWidth) {
            gestureControlMode = GestureControlMode.BRIGHTNESS
            Log.d(GESTURE_TAG, "Modo Gesto: BRILHO (x=${e.x.toInt()} < $oneQuarterScreenWidth)")
        } else if (e.x > threeQuartersScreenWidth) {
            gestureControlMode = GestureControlMode.VOLUME
            Log.d(GESTURE_TAG, "Modo Gesto: VOLUME (x=${e.x.toInt()} > $threeQuartersScreenWidth)")
        } else {
            gestureControlMode = GestureControlMode.NONE
            Log.d(GESTURE_TAG, "Modo Gesto: NENHUM (zona morta central, x=${e.x.toInt()})")
        }
        return true
    }

    override fun onShowPress(e: MotionEvent) { Log.d(GESTURE_TAG, "onShowPress") }

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        Log.d(GESTURE_TAG, "onSingleTapUp: Alternando visibilidade dos controlos.")
        toggleControlsVisibility()
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        if (e1 == null || gestureControlMode == GestureControlMode.NONE || screenHeight == 0) {
            return false
        }

        val deltaYTotal = e1.y - e2.y
        val scrollPercentageOfScreen = deltaYTotal / screenHeight.toFloat()

        when (gestureControlMode) {
            GestureControlMode.BRIGHTNESS -> {
                val brightnessChangeFactor = 1.2f
                var newBrightness = brightnessAtGestureStart + (scrollPercentageOfScreen * brightnessChangeFactor)
                newBrightness = newBrightness.coerceIn(0.05f, 1.0f)

                val layoutParams = window.attributes
                if (abs(layoutParams.screenBrightness - newBrightness) > 0.01f) {
                    layoutParams.screenBrightness = newBrightness
                    window.attributes = layoutParams
                    Log.i(GESTURE_TAG, "Brilho ajustado para: $newBrightness")
                }
            }
            GestureControlMode.VOLUME -> {
                val volumeChangeFactor = 1.5f
                val deltaVolume = (scrollPercentageOfScreen * maxVolume * volumeChangeFactor).toInt()
                var newVolume = volumeAtGestureStart + deltaVolume
                newVolume = newVolume.coerceIn(0, maxVolume)

                if (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) != newVolume) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                    Log.i(GESTURE_TAG, "Volume ajustado para: $newVolume")
                }
            }
            GestureControlMode.NONE -> return false
        }
        resetControlsTimeout()
        return true
    }

    override fun onLongPress(e: MotionEvent) { Log.d(GESTURE_TAG, "onLongPress") }
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        Log.d(GESTURE_TAG, "onFling: vX=$velocityX, vY=$velocityY")
        return false
    }

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
            handler.removeCallbacks(hideControlsRunnable)
        }
    }

    override fun onStopTrackingTouch(seekBar: SeekBar?) {
        if (::mediaPlayer.isInitialized) {
            if (mediaPlayer.isSeekable) { }
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
