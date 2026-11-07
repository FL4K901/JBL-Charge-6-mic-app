// Copyright (c) 2026 FL4K901. All rights reserved.
// Licensed under the MIT License. See LICENSE in the project root.

package com.mic.app.mic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * Ultra-Low Latency Audio Engine with Google Oboe and Android AAudio.
 *
 * Implements:
 * 1. Google Oboe C++ Engine with sub-10ms software latency.
 * 2. Background Audio Ducking: Requests AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK to duck Spotify/YouTube
 *    by ~30% while the mic is active, and abandons focus on stop to restore 100% volume.
 * 3. Independent Mic Boost DSP Multiplier (1.0x to 5.0x) with hard-clipper/limiter.
 * 4. Fallback to high-priority Kotlin AAudio/AudioTrack if native Oboe is unavailable.
 */
class AudioEngine(private val context: Context) {

    companion object {
        private const val TAG = "AudioEngine"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_IN = AudioFormat.CHANNEL_IN_MONO
        const val CHANNEL_OUT = AudioFormat.CHANNEL_OUT_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }

    val dsp = AudioDspProcessor(SAMPLE_RATE)
    val oboeBridge = OboeAudioEngineBridge()

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Volatile var isRunning: Boolean = false
        private set

    @Volatile var isUsingOboe: Boolean = false
        private set

    @Volatile var safetyAutoMuteEnabled: Boolean = true

    var onStateChanged: ((Boolean) -> Unit)? = null
    var onDeviceChanged: ((String, Boolean) -> Unit)? = null
    var onFocusInterrupted: ((Boolean) -> Unit)? = null
    @Volatile private var isInterruptedByFocus: Boolean = false

    /**
     * Start the real-time audio pipeline and request background audio ducking.
     */
    @Synchronized
    fun start(): Boolean {
        if (isRunning) return true

        try {
            // Keep normal mode so Bluetooth A2DP stereo is preserved
            audioManager.mode = AudioManager.MODE_NORMAL

            // 1. Request Audio Focus with Transient Ducking (Spotify/YouTube lowered by ~30%)
            requestBackgroundDucking()

            // 2. Attempt starting Native Google Oboe C++ Engine first (ultra-low latency)
            if (OboeAudioEngineBridge.isLibraryLoaded && oboeBridge.create()) {
                // Synchronize DSP parameters to Oboe
                oboeBridge.setBoostGain(dsp.boostGain)
                oboeBridge.setEcho(dsp.echoWet, dsp.echoDelayMs, dsp.echoFeedback)
                oboeBridge.setReverb(dsp.reverbWet, dsp.reverbRoomSize)
                oboeBridge.setMute(dsp.isMuted)
                oboeBridge.setAntiHowling(dsp.antiHowlingEnabled)

                if (oboeBridge.start()) {
                    isUsingOboe = true
                    isRunning = true
                    Log.d(TAG, "AudioEngine running via Google Oboe C++ AAudio native engine!")
                    checkOutputSafety()
                    onStateChanged?.invoke(true)
                    return true
                }
            }

            // 3. Fallback to Optimized Kotlin Low-Latency AudioRecord / AudioTrack Pipeline
            Log.d(TAG, "Using Kotlin high-priority AudioRecord/AudioTrack engine")
            isUsingOboe = false

            val minRecordBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
            val minTrackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)

            val framesPerBufferStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            val burstFrames = framesPerBufferStr?.toIntOrNull() ?: 240
            val burstBufferSizeInBytes = burstFrames * 2

            val recordBufferSize = maxOf(minRecordBufferSize, burstBufferSizeInBytes * 2)
            val trackBufferSize = maxOf(minTrackBufferSize, burstBufferSizeInBytes * 2)

            val recordBuilder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_IN)
                        .build()
                )
                .setBufferSizeInBytes(recordBufferSize)

            audioRecord = recordBuilder.build()
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioRecord")
                release()
                return false
            }

            val audioSessionId = audioRecord!!.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
                } catch (_: Exception) {}
            }

            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
                } catch (_: Exception) {}
            }

            val trackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setFlags(AudioAttributes.FLAG_LOW_LATENCY)
                .build()

            val trackFormat = AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_OUT)
                .build()

            val trackBuilder = AudioTrack.Builder()
                .setAudioAttributes(trackAttributes)
                .setAudioFormat(trackFormat)
                .setBufferSizeInBytes(trackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                trackBuilder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            }

            audioTrack = trackBuilder.build()
            if (audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
                Log.e(TAG, "Failed to initialize AudioTrack")
                release()
                return false
            }

            checkOutputSafety()

            audioRecord?.startRecording()
            audioTrack?.play()

            isRunning = true
            dsp.reset()

            val processFrameSize = burstFrames.coerceIn(128, 480)
            val pcmBuffer = ShortArray(processFrameSize)

            audioThread = Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                while (isRunning) {
                    val record = audioRecord ?: break
                    val track = audioTrack ?: break

                    val read = record.read(pcmBuffer, 0, processFrameSize)
                    if (read > 0) {
                        dsp.process(pcmBuffer, read)
                        track.write(pcmBuffer, 0, read)
                    }
                }
            }.apply {
                name = "MicAudioRealtimeThread"
                priority = Thread.MAX_PRIORITY
                start()
            }

            onStateChanged?.invoke(true)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioEngine: ${e.message}", e)
            release()
            return false
        }
    }

    /**
     * Stop real-time audio pipeline and abandon ducking focus.
     */
    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false

        // Abandon audio focus to restore Spotify/YouTube volume to 100%
        abandonBackgroundDucking()

        try {
            if (isUsingOboe) {
                oboeBridge.stop()
            }

            audioThread?.interrupt()
            audioThread = null

            audioRecord?.stop()
            audioTrack?.stop()

            echoCanceler?.release()
            echoCanceler = null

            noiseSuppressor?.release()
            noiseSuppressor = null

            audioRecord?.release()
            audioRecord = null

            audioTrack?.release()
            audioTrack = null

            dsp.reset()
            onStateChanged?.invoke(false)
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioEngine: ${e.message}", e)
        }
    }

    private fun release() {
        isRunning = false
        abandonBackgroundDucking()
        try {
            oboeBridge.stop()
            audioThread?.interrupt()
            audioThread = null
            echoCanceler?.release()
            noiseSuppressor?.release()
            audioRecord?.release()
            audioTrack?.release()
        } catch (_: Exception) {}
        audioRecord = null
        audioTrack = null
        echoCanceler = null
        noiseSuppressor = null
    }

    /**
     * Request AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK.
     * Tells Android Audio Policy to lower Spotify/YouTube volume by ~30% without pausing.
     */
    private fun requestBackgroundDucking() {
        try {
            val focusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
                when (focusChange) {
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                    AudioManager.AUDIOFOCUS_LOSS -> {
                        Log.d(TAG, "Audio focus interrupted (e.g. call or exclusive app). Auto-pausing mic.")
                        if (isRunning && !isInterruptedByFocus) {
                            isInterruptedByFocus = true
                            stop()
                            onFocusInterrupted?.invoke(true)
                        }
                    }
                    AudioManager.AUDIOFOCUS_GAIN -> {
                        if (isInterruptedByFocus) {
                            Log.d(TAG, "Audio focus restored. Resuming mic.")
                            isInterruptedByFocus = false
                            start()
                            onFocusInterrupted?.invoke(false)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()

                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .setWillPauseWhenDucked(false) // Lowers music by 30% instead of pausing
                    .setOnAudioFocusChangeListener(focusListener)
                    .build()

                val res = audioManager.requestAudioFocus(focusRequest!!)
                Log.d(TAG, "Requested AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK: result=$res")
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    focusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not request audio focus ducking: ${e.message}")
        }
    }

    /**
     * Abandon audio focus so background music returns smoothly to 100% volume.
     */
    private fun abandonBackgroundDucking() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let {
                    audioManager.abandonAudioFocusRequest(it)
                    focusRequest = null
                    Log.d(TAG, "Abandoned audio focus ducking — background music restored to 100%")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not abandon audio focus ducking: ${e.message}")
        }
    }

    fun setBoostGain(gain: Float) {
        val clamped = gain.coerceIn(1.0f, 5.0f)
        dsp.boostGain = clamped
        if (isUsingOboe) {
            oboeBridge.setBoostGain(clamped)
        }
    }

    fun setEcho(wet: Float, delayMs: Int, feedback: Float) {
        dsp.echoWet = wet
        dsp.echoDelayMs = delayMs
        dsp.echoFeedback = feedback
        if (isUsingOboe) {
            oboeBridge.setEcho(wet, delayMs, feedback)
        }
    }

    fun setReverb(wet: Float, roomSize: Float) {
        dsp.reverbWet = wet
        dsp.reverbRoomSize = roomSize
        if (isUsingOboe) {
            oboeBridge.setReverb(wet, roomSize)
        }
    }

    fun setMute(mute: Boolean) {
        dsp.isMuted = mute
        if (isUsingOboe) {
            oboeBridge.setMute(mute)
        }
    }

    fun setAntiHowling(enabled: Boolean) {
        dsp.antiHowlingEnabled = enabled
        if (isUsingOboe) {
            oboeBridge.setAntiHowling(enabled)
        }
    }

    fun getMetrics(): Triple<Float, Float, Boolean> {
        return if (isUsingOboe) {
            Triple(oboeBridge.getRms(), oboeBridge.getPeak(), oboeBridge.isHowling())
        } else {
            Triple(dsp.currentRms, dsp.currentPeak, dsp.isHowlingDetected)
        }
    }

    fun isClipping(): Boolean {
        return if (isUsingOboe) {
            oboeBridge.isClipping()
        } else {
            val clip = dsp.isClippingDetected
            dsp.isClippingDetected = false
            clip
        }
    }

    fun checkOutputSafety() {
        val (deviceName, isBluetooth) = getConnectedOutputDeviceInfo()
        if (safetyAutoMuteEnabled && !isBluetooth) {
            setMute(true)
            Log.w(TAG, "Safe Auto-Mute engaged: Active output is $deviceName")
        }
        onDeviceChanged?.invoke(deviceName, isBluetooth)
    }

    fun getConnectedOutputDeviceInfo(): Pair<String, Boolean> {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var bluetoothDeviceName: String? = null

        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                    bluetoothDeviceName = device.productName.toString().ifEmpty { "Bluetooth Speaker" }
                    return Pair(bluetoothDeviceName, true)
                }
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    bluetoothDeviceName = device.productName.toString().ifEmpty { "Bluetooth Headset" }
                    return Pair(bluetoothDeviceName, true)
                }
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> {
                    return Pair(device.productName.toString().ifEmpty { "Wired Headset" }, true)
                }
            }
        }

        return Pair("Phone Speaker", false)
    }
}
