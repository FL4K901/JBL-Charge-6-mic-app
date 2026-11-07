package com.mic.app.mic

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Real-time Digital Signal Processing (DSP) engine.
 *
 * Implements:
 * 1. Independent Mic Boost DSP Multiplier (1.0x to 5.0x / +14dB)
 * 2. Hard-clipper / brickwall limiter strictly preventing 16-bit integer distortion
 * 3. Circular buffer Echo / Delay with feedback decay
 * 4. Schroeder / Freeverb-inspired multi-comb & all-pass reverberation
 * 5. Anti-Howling / Feedback Loop auto-detector & fast protective ducking
 */
class AudioDspProcessor(
    private val sampleRate: Int = 48000
) {
    // ----------------------------------------------------
    // User Controllable DSP Parameters
    // ----------------------------------------------------
    @Volatile var boostGain: Float = 1.5f        // 1.0x to 5.0x independent Mic Boost
    @Volatile var isMuted: Boolean = false       // Mute switch
    @Volatile var echoWet: Float = 0.25f         // 0.0 to 1.0
    @Volatile var echoDelayMs: Int = 220         // 50ms to 600ms
    @Volatile var echoFeedback: Float = 0.35f    // 0.0 to 0.85
    @Volatile var reverbWet: Float = 0.30f       // 0.0 to 1.0
    @Volatile var reverbRoomSize: Float = 0.75f   // 0.2 to 0.95
    @Volatile var antiHowlingEnabled: Boolean = true

    // Real-time metrics
    @Volatile var currentRms: Float = 0.0f
    @Volatile var currentPeak: Float = 0.0f
    @Volatile var isHowlingDetected: Boolean = false
    @Volatile var isClippingDetected: Boolean = false

    // Delay / Echo Buffers
    private val maxDelaySamples = sampleRate
    private val echoBuffer = FloatArray(maxDelaySamples)
    private var echoWriteIndex = 0

    // Schroeder Reverb Filters
    private class CombFilter(size: Int) {
        val buffer = FloatArray(size)
        var bufferIndex = 0
        var filterStore = 0f

        fun process(input: Float, feedback: Float, damp: Float): Float {
            val output = buffer[bufferIndex]
            filterStore = (output * (1f - damp)) + (filterStore * damp)
            buffer[bufferIndex] = input + (filterStore * feedback)
            if (++bufferIndex >= buffer.size) bufferIndex = 0
            return output
        }
    }

    private class AllPassFilter(size: Int) {
        val buffer = FloatArray(size)
        var bufferIndex = 0

        fun process(input: Float, feedback: Float = 0.5f): Float {
            val bufOut = buffer[bufferIndex]
            val output = -input + bufOut
            buffer[bufferIndex] = input + (bufOut * feedback)
            if (++bufferIndex >= buffer.size) bufferIndex = 0
            return output
        }
    }

    private val combFilters = arrayOf(
        CombFilter(1116 * sampleRate / 44100),
        CombFilter(1188 * sampleRate / 44100),
        CombFilter(1277 * sampleRate / 44100),
        CombFilter(1356 * sampleRate / 44100)
    )

    private val allPassFilters = arrayOf(
        AllPassFilter(556 * sampleRate / 44100),
        AllPassFilter(441 * sampleRate / 44100)
    )

    private var highEnergyFrameCount = 0
    private var duckingGain = 1.0f
    private val duckingRecoverySpeed = 0.0005f

    /**
     * Process 16-bit PCM buffer in-place with zero heap allocations.
     */
    fun process(buffer: ShortArray, readSize: Int) {
        if (readSize <= 0) return

        if (isMuted) {
            for (i in 0 until readSize) {
                buffer[i] = 0
            }
            currentRms = 0f
            currentPeak = 0f
            isHowlingDetected = false
            return
        }

        var sumSquares = 0.0
        var maxAbsSample = 0f

        val delaySamples = min(maxDelaySamples - 1, max(1, (echoDelayMs * sampleRate) / 1000))
        val currentEchoWet = echoWet
        val currentEchoFeedback = echoFeedback
        val currentReverbWet = reverbWet
        val roomFeedback = reverbRoomSize * 0.28f + 0.7f
        val dampFactor = 0.35f
        val gainMultiplier = boostGain // 1.0x to 5.0x

        for (i in 0 until readSize) {
            val inputSample16 = buffer[i]
            val rawSample = inputSample16 / 32768.0f // -1.0 to 1.0

            val absSample = abs(rawSample)
            if (absSample > maxAbsSample) {
                maxAbsSample = absSample
            }
            sumSquares += (rawSample * rawSample)

            // 1. Independent Mic Boost DSP Gain Multiplier
            var sample = rawSample * gainMultiplier

            // 2. Anti-Howling protective ducking
            if (duckingGain < 1.0f) {
                sample *= duckingGain
                duckingGain = min(1.0f, duckingGain + duckingRecoverySpeed)
            }

            // 3. Reverb Processing
            var reverbOutput = 0.0f
            if (currentReverbWet > 0.01f) {
                var combSum = 0.0f
                for (comb in combFilters) {
                    combSum += comb.process(sample, roomFeedback, dampFactor)
                }
                var diffused = combSum * 0.25f
                for (allPass in allPassFilters) {
                    diffused = allPass.process(diffused, 0.5f)
                }
                reverbOutput = diffused
            }

            // 4. Echo / Delay Processing
            var echoOutput = 0.0f
            if (currentEchoWet > 0.01f) {
                val echoReadIndex = (echoWriteIndex - delaySamples + maxDelaySamples) % maxDelaySamples
                val delayedSample = echoBuffer[echoReadIndex]
                
                echoBuffer[echoWriteIndex] = sample + (delayedSample * currentEchoFeedback)
                echoOutput = delayedSample

                echoWriteIndex = (echoWriteIndex + 1) % maxDelaySamples
            }

            // 5. Wet / Dry Mix
            val dryMix = 1.0f - max(currentEchoWet, currentReverbWet) * 0.4f
            var mixedSample = (sample * dryMix) + (echoOutput * currentEchoWet) + (reverbOutput * currentReverbWet)

            // 6. Analog Soft-Saturating Limiter
            mixedSample = softSaturate(mixedSample)

            // 7. Brickwall Hard-Clipper strictly clamping to [-32768, 32767]
            val outputInt = (mixedSample * 32767.0f).toInt()
            val clamped = when {
                outputInt > 32767 -> {
                    isClippingDetected = true
                    32767
                }
                outputInt < -32768 -> {
                    isClippingDetected = true
                    -32768
                }
                else -> outputInt
            }.toShort()

            buffer[i] = clamped
        }

        // Metrics & Howling detection
        val rms = sqrt(sumSquares / readSize).toFloat()
        currentRms = rms
        currentPeak = maxAbsSample

        if (antiHowlingEnabled) {
            if (maxAbsSample > 0.92f && rms > 0.65f) {
                highEnergyFrameCount++
                if (highEnergyFrameCount > 4) {
                    isHowlingDetected = true
                    duckingGain = 0.12f // Clamp -18dB
                }
            } else {
                if (highEnergyFrameCount > 0) highEnergyFrameCount--
                if (duckingGain >= 0.95f) {
                    isHowlingDetected = false
                }
            }
        }
    }

    private fun softSaturate(x: Float): Float {
        if (x.isNaN() || x.isInfinite()) return 0.0f
        return when {
            x > 1.25f -> 1.0f
            x < -1.25f -> -1.0f
            x > 0.6f -> 0.6f + (x - 0.6f) / (1.0f + ((x - 0.6f) * (x - 0.6f)))
            x < -0.6f -> -0.6f + (x + 0.6f) / (1.0f + ((x + 0.6f) * (x + 0.6f)))
            else -> x
        }
    }

    fun reset() {
        echoBuffer.fill(0f)
        echoWriteIndex = 0
        duckingGain = 1.0f
        highEnergyFrameCount = 0
        currentRms = 0f
        currentPeak = 0f
        isHowlingDetected = false
        isClippingDetected = false
    }
}
