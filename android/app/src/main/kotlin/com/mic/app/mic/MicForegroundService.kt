package com.mic.app.mic

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/**
 * Foreground Service for persistent background microphone processing and edge-case management.
 *
 * Edge cases handled:
 * 1. Bluetooth Disconnect (ACTION_AUDIO_BECOMING_NOISY & AudioDeviceCallback):
 *    Auto-mutes mic to prevent phone speaker feedback blast.
 * 2. Incoming/Ongoing Phone Calls & Audio Interruptions (AudioManager Focus Listener):
 *    Auto-pauses mic capture during calls to free hardware mic, then auto-resumes.
 * 3. Background Persistence:
 *    Maintains FOREGROUND_SERVICE_TYPE_MICROPHONE and WakeLock so user can minimize
 *    app to use Spotify, YouTube, or lyrics.
 */
class MicForegroundService : Service() {

    companion object {
        private const val TAG = "MicForegroundService"
        const val CHANNEL_ID = "mic_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.mic.app.ACTION_START"
        const val ACTION_STOP = "com.mic.app.ACTION_STOP"
        const val ACTION_TOGGLE_MUTE = "com.mic.app.ACTION_TOGGLE_MUTE"
    }

    inner class LocalBinder : Binder() {
        fun getService(): MicForegroundService = this@MicForegroundService
    }

    private val binder = LocalBinder()
    lateinit var audioEngine: AudioEngine
        private set

    private var wakeLock: PowerManager.WakeLock? = null

    // Receivers and Callbacks
    private var noisyReceiver: BroadcastReceiver? = null
    private var audioDeviceCallback: AudioDeviceCallback? = null

    override fun onCreate() {
        super.onCreate()
        audioEngine = AudioEngine(this)

        createNotificationChannel()
        acquireWakeLock()
        registerBecomingNoisyReceiver()
        registerAudioDeviceCallback()

        audioEngine.onDeviceChanged = { deviceName, isBt ->
            updateNotification("Active: $deviceName", isBt)
        }
        audioEngine.onFocusInterrupted = { interrupted ->
            if (interrupted) {
                updateNotification("Paused for Call / Audio Interrupt", false)
            } else {
                val (devName, isBt) = audioEngine.getConnectedOutputDeviceInfo()
                updateNotification("Routing to $devName", isBt)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val started = audioEngine.start()
                startForegroundWithNotification()
                if (started) {
                    val (devName, isBt) = audioEngine.getConnectedOutputDeviceInfo()
                    updateNotification("Routing to $devName", isBt)
                }
            }
            ACTION_STOP -> {
                audioEngine.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_TOGGLE_MUTE -> {
                audioEngine.dsp.isMuted = !audioEngine.dsp.isMuted
                val (devName, isBt) = audioEngine.getConnectedOutputDeviceInfo()
                val status = if (audioEngine.dsp.isMuted) "Muted" else "Live • $devName"
                updateNotification(status, isBt)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        audioEngine.stop()
        unregisterReceivers()
        releaseWakeLock()
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification("Microphone Active • Mixing with Media", true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(statusText: String, isBtConnected: Boolean): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteIntent = Intent(this, MicForegroundService::class.java).apply {
            action = ACTION_TOGGLE_MUTE
        }
        val mutePendingIntent = PendingIntent.getService(
            this, 1, muteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, MicForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val muteActionTitle = if (audioEngine.dsp.isMuted) "Unmute" else "Mute"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Mic • Live Audio Routing")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_pause, muteActionTitle, mutePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn Off", stopPendingIntent)
            .build()
    }

    private fun updateNotification(statusText: String, isBtConnected: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(statusText, isBtConnected))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Karaoke Mic Foreground Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps microphone routing alive while background mixing"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Mic::ForegroundAudioWakeLock"
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // 6 hours safety timeout
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    /**
     * BroadcastReceiver for AUDIO_BECOMING_NOISY (e.g. JBL Bluetooth Speaker disconnects).
     * Instantly auto-mutes the microphone to prevent extreme howling blast through phone speakers.
     */
    private fun registerBecomingNoisyReceiver() {
        noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    Log.w(TAG, "Audio became noisy (Bluetooth disconnected). Auto-muting microphone!")
                    audioEngine.dsp.isMuted = true
                    updateNotification("Speaker Disconnected (Auto-Muted)", false)
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        ContextCompat.registerReceiver(this, noisyReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /**
     * Monitor dynamic AudioDevice changes (Bluetooth connect/disconnect in real-time).
     */
    private fun registerAudioDeviceCallback() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioDeviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                audioEngine.checkOutputSafety()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                audioEngine.checkOutputSafety()
            }
        }
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
    }

    private fun unregisterReceivers() {
        try {
            noisyReceiver?.let { unregisterReceiver(it) }
        } catch (_: Exception) {}
        noisyReceiver = null

        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioDeviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        } catch (_: Exception) {}
        audioDeviceCallback = null
    }
}
