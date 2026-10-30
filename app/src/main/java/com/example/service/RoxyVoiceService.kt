package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.MainActivity
import com.example.R
import com.example.RoxyApplication
import com.example.audio.AudioRecorderManager
import com.example.audio.AudioStreamPlayer
import com.example.network.GeminiLiveListener
import com.example.network.GeminiLiveWebSocketClient
import com.example.security.AssistantConfig
import com.example.tools.ToolNotificationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class RoxySessionStatus {
    DISCONNECTED,
    CONNECTING,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

class RoxyVoiceService : Service() {

    companion object {
        private const val TAG = "RoxyVoiceService"
        private const val NOTIFICATION_ID = 4040
        const val CHANNEL_ID = "roxy_live_voice_channel"

        const val ACTION_START = "com.example.roxy.ACTION_START"
        const val ACTION_STOP = "com.example.roxy.ACTION_STOP"

        // State flows shared with ViewModel / UI
        private val _sessionStatus = MutableStateFlow(RoxySessionStatus.DISCONNECTED)
        val sessionStatus: StateFlow<RoxySessionStatus> = _sessionStatus.asStateFlow()

        private val _audioVisualizerLevel = MutableStateFlow(0f)
        val audioVisualizerLevel: StateFlow<Float> = _audioVisualizerLevel.asStateFlow()

        private val _waveformBars = MutableStateFlow<List<Float>>(List(32) { 0.04f })
        val waveformBars: StateFlow<List<Float>> = _waveformBars.asStateFlow()

        private val _errorMessage = MutableStateFlow<String?>(null)
        val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

        private val _activeToolNotification = MutableSharedFlow<ToolNotificationState>(extraBufferCapacity = 5)
        val activeToolNotification: SharedFlow<ToolNotificationState> = _activeToolNotification.asSharedFlow()

        var isServiceRunning = false
            private set
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val binder = LocalBinder()

    private var audioRecorder: AudioRecorderManager? = null
    private var audioPlayer: AudioStreamPlayer? = null
    private var liveWebSocketClient: GeminiLiveWebSocketClient? = null

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    inner class LocalBinder : Binder() {
        fun getService(): RoxyVoiceService = this@RoxyVoiceService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        createNotificationChannel()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop action triggered from notification or UI.")
                stopSession()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundSession()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundSession() {
        val notification = buildNotification("Connecting to Roxy...")
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            _errorMessage.value = "Microphone foreground permission restriction: ${e.message}"
            stopSelf()
            return
        }

        acquireWakeLock()
        requestAudioFocus()
        startVoicePipeline()
    }

    private fun startVoicePipeline() {
        _sessionStatus.value = RoxySessionStatus.CONNECTING
        updateNotification("Connecting to Roxy...")

        val app = application as? RoxyApplication ?: return
        val config = app.securityManager.getConfig()

        if (config.apiKey.isBlank()) {
            _sessionStatus.value = RoxySessionStatus.ERROR
            _errorMessage.value = "Gemini API key is missing. Please add it in Settings."
            updateNotification("API key missing. Tap to configure.")
            return
        }

        // Initialize Audio Player for 24kHz PCM downlink
        audioPlayer = AudioStreamPlayer(
            scope = serviceScope,
            onPlaybackStateChange = { isPlaying ->
                if (_sessionStatus.value != RoxySessionStatus.DISCONNECTED &&
                    _sessionStatus.value != RoxySessionStatus.CONNECTING &&
                    _sessionStatus.value != RoxySessionStatus.ERROR) {
                    val newStatus = if (isPlaying) RoxySessionStatus.SPEAKING else RoxySessionStatus.LISTENING
                    _sessionStatus.value = newStatus
                    updateNotification(if (isPlaying) "Roxy is speaking..." else "Listening to you...")
                }
            },
            onOutputLevel = { level ->
                if (_sessionStatus.value == RoxySessionStatus.SPEAKING) {
                    _audioVisualizerLevel.value = level
                }
            },
            onOutputWaveform = { bars ->
                if (_sessionStatus.value == RoxySessionStatus.SPEAKING) {
                    _waveformBars.value = bars
                }
            }
        )
        audioPlayer?.start()

        // Forward tool notifications to UI
        serviceScope.launch {
            app.toolManager.toolNotifications.collect { toolNotif ->
                _activeToolNotification.tryEmit(toolNotif)
                updateNotification("Action: ${toolNotif.message}")
            }
        }

        // Initialize Gemini Live WebSocket Client
        liveWebSocketClient = GeminiLiveWebSocketClient(
            toolManager = app.toolManager,
            scope = serviceScope,
            listener = object : GeminiLiveListener {
                override fun onConnected() {
                    Log.i(TAG, "Live WebSocket session established, waiting for setupComplete...")
                    _sessionStatus.value = RoxySessionStatus.CONNECTING
                    _errorMessage.value = null
                    updateNotification("Setting up Live session...")
                }

                override fun onSetupComplete() {
                    Log.i(TAG, "Setup complete confirmed by server. Starting microphone uplink.")
                    _sessionStatus.value = RoxySessionStatus.LISTENING
                    _errorMessage.value = null
                    updateNotification("Listening... speak to Roxy")
                    startMicrophone()
                }

                override fun onAudioDataReceived(pcmBytes: ByteArray, mimeType: String) {
                    audioPlayer?.enqueueAudio(pcmBytes)
                }

                override fun onTextReceived(text: String) {
                    Log.d(TAG, "Live transcript text received: ${text.take(60)}")
                }

                override fun onThinking() {
                    if (_sessionStatus.value != RoxySessionStatus.SPEAKING &&
                        _sessionStatus.value != RoxySessionStatus.DISCONNECTED &&
                        _sessionStatus.value != RoxySessionStatus.ERROR) {
                        _sessionStatus.value = RoxySessionStatus.THINKING
                        updateNotification("Roxy is thinking...")
                    }
                }

                override fun onInterrupted() {
                    Log.d(TAG, "Barge-in: Gemini detected user speech, halting audio output immediately.")
                    audioPlayer?.stopAndFlush()
                    _waveformBars.value = List(32) { 0.04f }
                    _sessionStatus.value = RoxySessionStatus.LISTENING
                    updateNotification("Listening to you...")
                }

                override fun onTurnComplete() {
                    Log.d(TAG, "Model turn complete.")
                    if (audioPlayer?.isPlaying() != true &&
                        _sessionStatus.value != RoxySessionStatus.DISCONNECTED &&
                        _sessionStatus.value != RoxySessionStatus.ERROR) {
                        _waveformBars.value = List(32) { 0.04f }
                        _sessionStatus.value = RoxySessionStatus.LISTENING
                        updateNotification("Listening to you...")
                    }
                }

                override fun onError(errorMessage: String) {
                    Log.e(TAG, "Live session error: $errorMessage")
                    _sessionStatus.value = RoxySessionStatus.ERROR
                    _waveformBars.value = List(32) { 0.04f }
                    _errorMessage.value = errorMessage
                    updateNotification("Error: $errorMessage")
                }

                override fun onClosed() {
                    Log.i(TAG, "Live session closed.")
                    _waveformBars.value = List(32) { 0.04f }
                    if (_sessionStatus.value != RoxySessionStatus.DISCONNECTED) {
                        _sessionStatus.value = RoxySessionStatus.DISCONNECTED
                    }
                }
            }
        )

        // Fetch context memories and connect
        serviceScope.launch(Dispatchers.IO) {
            val memoryContext = app.memoryRepository.getMemoryContextForPrompt()
            liveWebSocketClient?.connect(config, memoryContext)
        }
    }

    private fun startMicrophone() {
        audioRecorder = AudioRecorderManager(
            scope = serviceScope,
            onAudioChunk = { pcmChunk ->
                // Guard against speaker-to-mic feedback loop:
                // When Roxy is actively speaking, only forward chunks if user speaks with deliberate volume (> 0.20 amplitude),
                // preventing ambient speaker bleed from triggering false barge-in and cutting off Roxy's voice.
                val isRoxySpeaking = audioPlayer?.isPlaying() == true
                if (!isRoxySpeaking) {
                    liveWebSocketClient?.sendRealtimeAudio(pcmChunk)
                } else {
                    var maxAmp = 0
                    for (i in pcmChunk.indices step 2) {
                        if (i + 1 < pcmChunk.size) {
                            val sample = (pcmChunk[i].toInt() and 0xFF) or (pcmChunk[i + 1].toInt() shl 8)
                            val shortSample = sample.toShort()
                            val abs = kotlin.math.abs(shortSample.toInt())
                            if (abs > maxAmp) maxAmp = abs
                        }
                    }
                    val normalized = maxAmp / 32767f
                    if (normalized > 0.20f) {
                        liveWebSocketClient?.sendRealtimeAudio(pcmChunk)
                    }
                }
            },
            onAmplitudeChanged = { amp ->
                if (_sessionStatus.value == RoxySessionStatus.LISTENING) {
                    _audioVisualizerLevel.value = amp
                }
            },
            onWaveformChanged = { bars ->
                if (_sessionStatus.value == RoxySessionStatus.LISTENING) {
                    _waveformBars.value = bars
                }
            }
        )

        val started = audioRecorder?.start() == true
        if (!started) {
            _sessionStatus.value = RoxySessionStatus.ERROR
            _errorMessage.value = "Failed to access microphone. Please check permissions."
            updateNotification("Microphone permission required.")
        }
    }

    private fun stopSession() {
        Log.i(TAG, "Stopping Roxy voice session...")
        audioRecorder?.stop()
        audioRecorder = null

        audioPlayer?.release()
        audioPlayer = null

        liveWebSocketClient?.disconnect()
        liveWebSocketClient = null

        abandonAudioFocus()
        releaseWakeLock()

        _sessionStatus.value = RoxySessionStatus.DISCONNECTED
        _audioVisualizerLevel.value = 0f
        _waveformBars.value = List(32) { 0.04f }
    }

    private fun requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        AudioManager.AUDIOFOCUS_LOSS,
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                            Log.w(TAG, "Audio focus lost. Stopping playback.")
                            audioPlayer?.stopAndFlush()
                        }
                    }
                }
                .build()

            audioFocusRequest?.let { audioManager?.requestAudioFocus(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.requestAudioFocus(
                null,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
        }
    }

    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Roxy:VoiceWakeLock")
        wakeLock?.acquire(30 * 60 * 1000L /* 30 minutes max */)
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wakelock: ${e.message}")
        }
        wakeLock = null
    }

    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                Log.w(TAG, "Internet network lost during active session.")
                _errorMessage.value = "Network connection lost."
                updateNotification("Network disconnected. Waiting for connection...")
            }

            override fun onAvailable(network: Network) {
                Log.i(TAG, "Internet network restored.")
                if (_sessionStatus.value == RoxySessionStatus.ERROR) {
                    _errorMessage.value = null
                }
            }
        }
        try {
            connectivityManager?.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            Log.w(TAG, "Could not register network callback: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Roxy Voice Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing voice conversation status with Roxy"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RoxyVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ROXY Live Voice Session")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Session", stopPendingIntent)
            .addAction(android.R.drawable.ic_dialog_info, "Open Roxy", openPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stopSession()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Error unregistering network callback: ${e.message}")
        }
        serviceScope.cancel()
    }
}
