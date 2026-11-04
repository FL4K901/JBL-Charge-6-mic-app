package com.mic.app.mic

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
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
 * Low-Latency Audio Engine for Real-Time Vocal Routing.
 *
 * Key Architectural Decisions:
 * 1. Zero Audio Focus Requests: We deliberately DO NOT request audio focus.
 *    This allows concurrent audio mixing with Spotify, YouTube, and Apple Music
 *    without ducking or pausing background media.
 * 2. MODE_NORMAL Persistence: Avoids Bluetooth SCO telephone profile downgrade,
 *    ensuring pristine stereo 44.1/48kHz A2DP Bluetooth transmission to the JBL speaker.
 * 3. Low-Latency Pipeline: Uses PERFORMANCE_MODE_LOW_LATENCY with minimal buffer sizes
 *    synchronized to the Android Audio HAL burst size.
 * 4. Hardware AEC/NS: Enables hardware Acoustic Echo Cancellation and Noise Suppression
 *    on Samsung Galaxy S21 Ultra's HAL if available.
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

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var audioThread: Thread? = null

    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null

    @Volatile var isRunning: Boolean = false
        private set

    @Volatile var safetyAutoMuteEnabled: Boolean = true

    // Listener for state updates to Flutter
    var onStateChanged: ((Boolean) -> Unit)? = null
    var onDeviceChanged: ((String, Boolean) -> Unit)? = null

    /**
     * Start the real-time audio pipeline.
     */
    @Synchronized
    fun start(): Boolean {
        if (isRunning) return true

        try {
            // Ensure audio mode remains NORMAL so Bluetooth stays in high-fidelity A2DP
            audioManager.mode = AudioManager.MODE_NORMAL

            // Calculate optimal buffer sizes
            val minRecordBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_IN, ENCODING)
            val minTrackBufferSize = AudioTrack.getMinBufferSize(SAMPLE_RATE, CHANNEL_OUT, ENCODING)

            // Get hardware burst size if available (e.g., 192 or 240 frames on Galaxy S21 Ultra)
            val framesPerBufferStr = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            val burstFrames = framesPerBufferStr?.toIntOrNull() ?: 240
            val burstBufferSizeInBytes = burstFrames * 2 // 16-bit = 2 bytes per sample

            // Use small buffer for minimal roundtrip latency (2x burst or minBufferSize)
            val recordBufferSize = maxOf(minRecordBufferSize, burstBufferSizeInBytes * 2)
            val trackBufferSize = maxOf(minTrackBufferSize, burstBufferSizeInBytes * 2)

            // 1. Initialize AudioRecord
            val recordBuilder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION) // Enables hardware AEC & NS tuning
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

            // 2. Attach Hardware AEC (Acoustic Echo Canceler) & NS (Noise Suppressor)
            val audioSessionId = audioRecord!!.audioSessionId
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply {
                        enabled = true
                    }
                    Log.d(TAG, "Hardware AcousticEchoCanceler enabled on session $audioSessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not attach AEC: ${e.message}")
                }
            }

            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply {
                        enabled = true
                    }
                    Log.d(TAG, "Hardware NoiseSuppressor enabled on session $audioSessionId")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not attach NS: ${e.message}")
                }
            }

            // 3. Initialize AudioTrack
            // NOTE: Using USAGE_MEDIA with NO audio focus request enables seamless background mixing!
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

            // Check if active output is a safe device (e.g. Bluetooth speaker)
            checkOutputSafety()

            // 4. Start Capture & Playback
            audioRecord?.startRecording()
            audioTrack?.play()

            isRunning = true
            dsp.reset()

            // 5. Start Real-Time Audio Processing Thread with URGENT_AUDIO priority
            val processFrameSize = burstFrames.coerceIn(128, 480)
            val pcmBuffer = ShortArray(processFrameSize)

            audioThread = Thread {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)

                while (isRunning) {
                    val record = audioRecord ?: break
                    val track = audioTrack ?: break

                    val read = record.read(pcmBuffer, 0, processFrameSize)
                    if (read > 0) {
                        // Apply DSP: Gain + Reverb + Echo + Anti-Howling + Saturation
                        dsp.process(pcmBuffer, read)

                        // Stream directly to low-latency output
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
     * Stop real-time audio pipeline and release resources.
     */
    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false

        try {
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
        try {
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
     * Auto-mute when speaker is disconnected or phone built-in speaker is active.
     */
    fun checkOutputSafety() {
        val (deviceName, isBluetooth) = getConnectedOutputDeviceInfo()
        if (safetyAutoMuteEnabled && !isBluetooth) {
            dsp.isMuted = true
            Log.w(TAG, "Safe Auto-Mute engaged: Active output is $deviceName (Non-Bluetooth)")
        }
        onDeviceChanged?.invoke(deviceName, isBluetooth)
    }

    /**
     * Query currently active audio output devices.
     */
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
