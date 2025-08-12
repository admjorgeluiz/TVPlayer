package com.jorgenascimento.tvplayer.ui.player

import android.annotation.SuppressLint
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.mediarouter.media.MediaControlIntent
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.jorgenascimento.tvplayer.R
import com.jorgenascimento.tvplayer.databinding.ActivityPlayerBinding
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererDiscoverer
import org.videolan.libvlc.RendererItem
import java.util.Formatter
import java.util.Locale

class PlayerActivity : AppCompatActivity(), MediaPlayer.EventListener, SeekBar.OnSeekBarChangeListener, GestureDetector.OnGestureListener {

    private lateinit var binding: ActivityPlayerBinding
    private lateinit var libVLC: LibVLC
    private lateinit var mediaPlayer: MediaPlayer

    // Variáveis para Casting
    private var rendererDiscoverer: RendererDiscoverer? = null
    private val renderers = mutableListOf<RendererItem>()
    private lateinit var mediaRouter: MediaRouter
    private lateinit var mediaRouterCallback: MediaRouter.Callback

    private val handler = Handler(Looper.getMainLooper())
    private var isControlsVisible = true
    private val hideControlsRunnable = Runnable { hideControls() }
    private val controlsTimeoutMs = 3000L

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

        setupCasting()
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

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && mediaPlayer.isPlaying) {
            enterPiPMode()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        binding.playerControlsContainer.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
    }

    private fun enterPiPMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pipParamsBuilder = PictureInPictureParams.Builder()
            val videoTrack = mediaPlayer.getCurrentVideoTrack()
            if (videoTrack != null && videoTrack.width > 0 && videoTrack.height > 0) {
                val aspectRatio = android.util.Rational(videoTrack.width, videoTrack.height)
                pipParamsBuilder.setAspectRatio(aspectRatio)
            }
            enterPictureInPictureMode(pipParamsBuilder.build())
        }
    }

    override fun onPause() {
        super.onPause()
        mediaRouter.removeCallback(mediaRouterCallback)
        stopRendererDiscovery()

        if (!isInPictureInPictureMode) {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                Log.d(TAG, "Player pausado em onPause() porque não está em modo PiP")
            }
        }
        handler.removeCallbacks(updateProgressRunnable)
        handler.removeCallbacks(hideControlsRunnable)
    }

    override fun onResume() {
        super.onResume()
        mediaRouter.addCallback(binding.mediaRouteButton.routeSelector, mediaRouterCallback, MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN)
        startRendererDiscovery()

        hideSystemUI()
        if (::mediaPlayer.isInitialized && !mediaPlayer.isPlaying) {
            if (!isInPictureInPictureMode) {
                mediaPlayer.play()
                Log.d(TAG, "Player retomado em onResume()")
            }
        }
        if (isControlsVisible) {
            scheduleHideControls()
        }
    }

    private fun setupCasting() {
        val mediaRouteSelector = MediaRouteSelector.Builder()
            .addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)
            .build()
        mediaRouter = MediaRouter.getInstance(this)
        binding.mediaRouteButton.setRouteSelector(mediaRouteSelector)

        mediaRouterCallback = object : MediaRouter.Callback() {
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) {
                val renderer = renderers.find { it.name == route.name }
                if (renderer != null) {
                    mediaPlayer.setRenderer(renderer)
                    Log.d(TAG, "Iniciando casting para: ${renderer.name}")
                }
            }

            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) {
                mediaPlayer.setRenderer(null)
                Log.d(TAG, "Casting parado.")
            }
        }
    }

    private fun startRendererDiscovery() {
        if (rendererDiscoverer != null) return
        rendererDiscoverer = RendererDiscoverer(libVLC, "microdns").apply {
            setEventListener(object : RendererDiscoverer.EventListener {
                override fun onEvent(event: RendererDiscoverer.Event) {
                    runOnUiThread {
                        val item = event.item ?: return@runOnUiThread
                        when (event.type) {
                            RendererDiscoverer.Event.ItemAdded -> {
                                if (!renderers.any { it.name == item.name }) {
                                    renderers.add(item)
                                }
                            }
                            RendererDiscoverer.Event.ItemDeleted -> {
                                renderers.removeAll { it.name == item.name }
                            }
                        }
                    }
                }
            })
            start()
        }
        Log.d(TAG, "Iniciada a descoberta de renderizadores.")
    }

    private fun stopRendererDiscovery() {
        rendererDiscoverer?.stop()
        rendererDiscoverer = null
        renderers.clear()
        Log.d(TAG, "Parada a descoberta de renderizadores.")
    }

    private fun setupPlayer(url: String) {
        try {
            val options = arrayListOf("--network-caching=$DEFAULT_NETWORK_CACHING", "--no-sub-autodetect-file", "--vout=gles2", "-vvv")
            libVLC = LibVLC(this, options)
            mediaPlayer = MediaPlayer(libVLC)
            mediaPlayer.attachViews(binding.videoLayout, null, false, false)
            mediaPlayer.setEventListener(this)

            val media = Media(libVLC, url.toUri())
            media.setHWDecoderEnabled(false, false)
            media.addOption(":http-user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36")
            mediaPlayer.media = media
            media.release()
            mediaPlayer.play()
        } catch (e: Exception) {
            Log.e(TAG, "Exceção em setupPlayer", e)
            Toast.makeText(this, getString(R.string.toast_erro_iniciar_reproducao), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupControlsAndGestures() {
        binding.buttonBack.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
            resetControlsTimeout()
        }
        binding.buttonPlayPause.setOnClickListener {
            if (mediaPlayer.isPlaying) mediaPlayer.pause() else mediaPlayer.play()
            resetControlsTimeout()
        }
        binding.buttonPip.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) enterPiPMode()
        }
        binding.buttonRotate.setOnClickListener {
            requestedOrientation = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            resetControlsTimeout()
        }
        binding.seekBarProgress.setOnSeekBarChangeListener(this)
        binding.playerRootLayout.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        if(isControlsVisible) scheduleHideControls()

        gestureDetector = GestureDetector(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        binding.playerRootLayout.post { updateScreenDimensions() }
    }

    private fun updateScreenDimensions() {
        screenWidth = binding.playerRootLayout.width
        screenHeight = binding.playerRootLayout.height
    }

    private fun toggleControlsVisibility() {
        if (isControlsVisible) hideControls() else showControls()
    }

    private fun showControls() {
        binding.playerControlsContainer.animate().alpha(1f).setDuration(200).withStartAction {
            binding.playerControlsContainer.visibility = View.VISIBLE
        }.start()
        isControlsVisible = true
        scheduleHideControls()
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
        if (!isControlsVisible) showControls() else scheduleHideControls()
    }

    private val updateProgressRunnable: Runnable = object : Runnable {
        override fun run() {
            if (::mediaPlayer.isInitialized && mediaPlayer.isPlaying && !isFinishing && !isDestroyed) {
                binding.seekBarProgress.progress = (mediaPlayer.position * binding.seekBarProgress.max).toInt()
                binding.textCurrentTime.text = formatTime(mediaPlayer.time)
            }
            if (!isFinishing && !isDestroyed) {
                handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun formatTime(timeMs: Long): String {
        if (timeMs < 0) return "--:--"
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
        if (isFinishing || isDestroyed) return
        runOnUiThread {
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    binding.playerBuffering.visibility = View.GONE
                    binding.buttonPlayPause.setImageResource(R.drawable.ic_player_pause)
                    handler.post(updateProgressRunnable)
                    val length = mediaPlayer.length
                    binding.textTotalTime.text = formatTime(length)
                    binding.seekBarProgress.max = if (length > 0) length.toInt() else 1000
                }
                MediaPlayer.Event.Paused -> {
                    binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
                    handler.removeCallbacks(updateProgressRunnable)
                }
                MediaPlayer.Event.Stopped, MediaPlayer.Event.EndReached -> {
                    binding.buttonPlayPause.setImageResource(R.drawable.ic_player_play)
                    handler.removeCallbacks(updateProgressRunnable)
                    binding.seekBarProgress.progress = if (event.type == MediaPlayer.Event.EndReached) binding.seekBarProgress.max else 0
                    binding.textCurrentTime.text = formatTime(if (event.type == MediaPlayer.Event.EndReached) mediaPlayer.length else 0)
                }
                MediaPlayer.Event.EncounteredError -> {
                    Toast.makeText(this, getString(R.string.toast_erro_reproducao), Toast.LENGTH_LONG).show()
                    binding.playerBuffering.visibility = View.GONE
                }
                MediaPlayer.Event.Buffering -> {
                    binding.playerBuffering.visibility = if (event.buffering < 100f) View.VISIBLE else View.GONE
                }
                MediaPlayer.Event.LengthChanged -> {
                    val newLength = event.lengthChanged
                    binding.textTotalTime.text = formatTime(newLength)
                    binding.seekBarProgress.max = if (newLength > 0) newLength.toInt() else 1000
                }
            }
        }
    }

    override fun onDown(e: MotionEvent): Boolean {
        if (screenHeight == 0 || screenWidth == 0) updateScreenDimensions()
        volumeAtGestureStart = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        brightnessAtGestureStart = window.attributes.screenBrightness.takeIf { it >= 0 } ?: (Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f)
        gestureControlMode = when {
            e.x < screenWidth / 4 -> GestureControlMode.BRIGHTNESS
            e.x > screenWidth * 3 / 4 -> GestureControlMode.VOLUME
            else -> GestureControlMode.NONE
        }
        return true
    }

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean {
        toggleControlsVisibility()
        return true
    }

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
        if (e1 == null || gestureControlMode == GestureControlMode.NONE || screenHeight == 0) return false
        val deltaY = e1.y - e2.y
        val scrollPercent = deltaY / screenHeight
        when (gestureControlMode) {
            GestureControlMode.BRIGHTNESS -> {
                val newBrightness = (brightnessAtGestureStart + scrollPercent * 1.2f).coerceIn(0.05f, 1.0f)
                window.attributes = window.attributes.apply { screenBrightness = newBrightness }
            }
            GestureControlMode.VOLUME -> {
                val deltaVolume = (scrollPercent * maxVolume * 1.5f).toInt()
                val newVolume = (volumeAtGestureStart + deltaVolume).coerceIn(0, maxVolume)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            }
            GestureControlMode.NONE -> {}
        }
        resetControlsTimeout()
        return true
    }

    override fun onLongPress(e: MotionEvent) {}
    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean = false

    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
        if (fromUser && ::mediaPlayer.isInitialized && mediaPlayer.isSeekable) {
            val newPosition = progress.toFloat() / (seekBar?.max?.takeIf { it > 0 }?.toFloat() ?: 1000f)
            mediaPlayer.position = newPosition
            binding.textCurrentTime.text = formatTime((newPosition * mediaPlayer.length).toLong())
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
        if (hasFocus) hideSystemUI()
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        if (::mediaPlayer.isInitialized) {
            mediaPlayer.stop()
            mediaPlayer.detachViews()
            mediaPlayer.setEventListener(null)
            mediaPlayer.release()
        }
        if (::libVLC.isInitialized) {
            libVLC.release()
        }
    }
}
