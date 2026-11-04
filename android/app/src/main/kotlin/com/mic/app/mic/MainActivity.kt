package com.mic.app.mic

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        private const val METHOD_CHANNEL = "com.mic.app/audio_control"
        private const val EVENT_CHANNEL = "com.mic.app/audio_events"
        private const val PERMISSION_REQ_CODE = 1001
    }

    private var micService: MicForegroundService? = null
    private var isBound = false
    private var eventSink: EventChannel.EventSink? = null

    private val handler = Handler(Looper.getMainLooper())
    private var metricsRunnable: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MicForegroundService.LocalBinder
            micService = binder.getService()
            isBound = true

            micService?.audioEngine?.onStateChanged = { _ ->
                handler.post {
                    pushMetricsUpdate()
                }
            }
            micService?.audioEngine?.onDeviceChanged = { _, _ ->
                handler.post {
                    pushMetricsUpdate()
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            micService = null
            isBound = false
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Bind to background service early
        val intent = Intent(this, MicForegroundService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        // 1. Method Channel
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, METHOD_CHANNEL).setMethodCallHandler { call, result ->
            val service = micService
            val dsp = service?.audioEngine?.dsp

            when (call.method) {
                "requestPermissions" -> {
                    val permissions = mutableListOf(
                        Manifest.permission.RECORD_AUDIO,
                        Manifest.permission.MODIFY_AUDIO_SETTINGS
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    ActivityCompat.requestPermissions(this, permissions.toTypedArray(), PERMISSION_REQ_CODE)
                    result.success(true)
                }

                "checkPermissions" -> {
                    val granted = ContextCompat.checkSelfPermission(
                        this, Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                    result.success(granted)
                }

                "startMic" -> {
                    val serviceIntent = Intent(this, MicForegroundService::class.java).apply {
                        action = MicForegroundService.ACTION_START
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                    result.success(true)
                }

                "stopMic" -> {
                    val serviceIntent = Intent(this, MicForegroundService::class.java).apply {
                        action = MicForegroundService.ACTION_STOP
                    }
                    startService(serviceIntent)
                    result.success(true)
                }

                "isMicRunning" -> {
                    result.success(service?.audioEngine?.isRunning ?: false)
                }

                "setMute" -> {
                    val muted = call.argument<Boolean>("isMuted") ?: false
                    dsp?.isMuted = muted
                    result.success(true)
                }

                "setGain" -> {
                    val gain = call.argument<Double>("gain")?.toFloat() ?: 1.0f
                    dsp?.gain = gain.coerceIn(0.0f, 3.0f)
                    result.success(true)
                }

                "setEcho" -> {
                    val wet = call.argument<Double>("wet")?.toFloat() ?: 0.0f
                    val delayMs = call.argument<Int>("delayMs") ?: 220
                    val feedback = call.argument<Double>("feedback")?.toFloat() ?: 0.35f

                    dsp?.echoWet = wet.coerceIn(0.0f, 1.0f)
                    dsp?.echoDelayMs = delayMs.coerceIn(50, 600)
                    dsp?.echoFeedback = feedback.coerceIn(0.0f, 0.85f)
                    result.success(true)
                }

                "setReverb" -> {
                    val wet = call.argument<Double>("wet")?.toFloat() ?: 0.0f
                    val roomSize = call.argument<Double>("roomSize")?.toFloat() ?: 0.75f

                    dsp?.reverbWet = wet.coerceIn(0.0f, 1.0f)
                    dsp?.reverbRoomSize = roomSize.coerceIn(0.1f, 0.95f)
                    result.success(true)
                }

                "setAntiHowling" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    dsp?.antiHowlingEnabled = enabled
                    result.success(true)
                }

                "setSafetyAutoMute" -> {
                    val enabled = call.argument<Boolean>("enabled") ?: true
                    service?.audioEngine?.safetyAutoMuteEnabled = enabled
                    result.success(true)
                }

                "getAudioDeviceInfo" -> {
                    val engine = service?.audioEngine
                    val (deviceName, isBt) = engine?.getConnectedOutputDeviceInfo() ?: Pair("Phone Speaker", false)
                    val info = mapOf(
                        "deviceName" to deviceName,
                        "isBluetooth" to isBt,
                        "isRunning" to (engine?.isRunning ?: false),
                        "isMuted" to (dsp?.isMuted ?: false),
                        "gain" to (dsp?.gain ?: 1.0f),
                        "echoWet" to (dsp?.echoWet ?: 0.25f),
                        "reverbWet" to (dsp?.reverbWet ?: 0.30f),
                        "antiHowling" to (dsp?.antiHowlingEnabled ?: true)
                    )
                    result.success(info)
                }

                else -> result.notImplemented()
            }
        }

        // 2. Event Channel for real-time visualizer & metrics (~33 fps / 30ms intervals)
        EventChannel(flutterEngine.dartExecutor.binaryMessenger, EVENT_CHANNEL).setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    eventSink = events
                    startMetricsPolling()
                }

                override fun onCancel(arguments: Any?) {
                    eventSink = null
                    stopMetricsPolling()
                }
            }
        )
    }

    private fun startMetricsPolling() {
        stopMetricsPolling()
        metricsRunnable = object : Runnable {
            override fun run() {
                pushMetricsUpdate()
                handler.postDelayed(this, 30) // ~33 Hz refresh
            }
        }
        handler.post(metricsRunnable!!)
    }

    private fun stopMetricsPolling() {
        metricsRunnable?.let { handler.removeCallbacks(it) }
        metricsRunnable = null
    }

    private fun pushMetricsUpdate() {
        val sink = eventSink ?: return
        val service = micService
        val engine = service?.audioEngine
        val dsp = engine?.dsp

        val (deviceName, isBt) = engine?.getConnectedOutputDeviceInfo() ?: Pair("Phone Speaker", false)

        val data = mapOf(
            "rms" to (dsp?.currentRms?.toDouble() ?: 0.0),
            "peak" to (dsp?.currentPeak?.toDouble() ?: 0.0),
            "isHowling" to (dsp?.isHowlingDetected ?: false),
            "isMuted" to (dsp?.isMuted ?: false),
            "isRunning" to (engine?.isRunning ?: false),
            "deviceName" to deviceName,
            "isBluetooth" to isBt
        )
        sink.success(data)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopMetricsPolling()
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
