package com.mic.app.mic

import android.util.Log

/**
 * JNI Bridge to Google Oboe C++ Real-Time Audio Engine.
 */
class OboeAudioEngineBridge {

    companion object {
        private const val TAG = "OboeBridge"
        var isLibraryLoaded = false
            private set

        init {
            try {
                System.loadLibrary("mic-audio-engine")
                isLibraryLoaded = true
                Log.d(TAG, "Successfully loaded native library: mic-audio-engine (Oboe C++)")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Could not load mic-audio-engine native library: ${e.message}. Using low-latency Kotlin AudioRecord fallback.")
                isLibraryLoaded = false
            }
        }
    }

    private var engineHandle: Long = 0

    fun create(): Boolean {
        if (!isLibraryLoaded) return false
        if (engineHandle == 0L) {
            engineHandle = createEngine()
        }
        return engineHandle != 0L
    }

    fun start(): Boolean {
        if (engineHandle == 0L && !create()) return false
        return startEngine(engineHandle)
    }

    fun stop() {
        if (engineHandle != 0L) {
            stopEngine(engineHandle)
        }
    }

    fun setBoostGain(gain: Float) {
        if (engineHandle != 0L) {
            setBoostGain(engineHandle, gain)
        }
    }

    fun setEcho(wet: Float, delayMs: Int, feedback: Float) {
        if (engineHandle != 0L) {
            setEcho(engineHandle, wet, delayMs, feedback)
        }
    }

    fun setReverb(wet: Float, roomSize: Float) {
        if (engineHandle != 0L) {
            setReverb(engineHandle, wet, roomSize)
        }
    }

    fun setMute(mute: Boolean) {
        if (engineHandle != 0L) {
            setMute(engineHandle, mute)
        }
    }

    fun setAntiHowling(enabled: Boolean) {
        if (engineHandle != 0L) {
            setAntiHowling(engineHandle, enabled)
        }
    }

    fun getRms(): Float {
        return if (engineHandle != 0L) getRms(engineHandle) else 0.0f
    }

    fun getPeak(): Float {
        return if (engineHandle != 0L) getPeak(engineHandle) else 0.0f
    }

    fun isHowling(): Boolean {
        return if (engineHandle != 0L) isHowling(engineHandle) else false
    }

    fun isClipping(): Boolean {
        return if (engineHandle != 0L) isClipping(engineHandle) else false
    }

    fun destroy() {
        if (engineHandle != 0L) {
            deleteEngine(engineHandle)
            engineHandle = 0L
        }
    }

    // Native JNI Declarations
    private external fun createEngine(): Long
    private external fun startEngine(handle: Long): Boolean
    private external fun stopEngine(handle: Long)
    private external fun setBoostGain(handle: Long, gain: Float)
    private external fun setEcho(handle: Long, wet: Float, delayMs: Int, feedback: Float)
    private external fun setReverb(handle: Long, wet: Float, roomSize: Float)
    private external fun setMute(handle: Long, mute: Boolean)
    private external fun setAntiHowling(handle: Long, enabled: Boolean)
    private external fun getRms(handle: Long): Float
    private external fun getPeak(handle: Long): Float
    private external fun isHowling(handle: Long): Boolean
    private external fun isClipping(handle: Long): Boolean
    private external fun deleteEngine(handle: Long)
}
