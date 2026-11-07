// Copyright (c) 2026 FL4K901. All rights reserved.
// Licensed under the MIT License. See LICENSE in the project root.

#include "OboeAudioEngine.h"
#include <android/log.h>
#include <algorithm>
#include <cmath>
#include <cstring>

#define LOG_TAG "OboeAudioEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

OboeAudioEngine::OboeAudioEngine()
    : mEchoBuffer(MAX_DELAY_SAMPLES, 0.0f),
      mInputBuffer(2048, 0) {
    // Schroeder reverberator delay prime intervals scaled for 48kHz
    mCombFilters.emplace_back(1116 * SAMPLE_RATE / 44100);
    mCombFilters.emplace_back(1188 * SAMPLE_RATE / 44100);
    mCombFilters.emplace_back(1277 * SAMPLE_RATE / 44100);
    mCombFilters.emplace_back(1356 * SAMPLE_RATE / 44100);

    mAllPassFilters.emplace_back(556 * SAMPLE_RATE / 44100);
    mAllPassFilters.emplace_back(441 * SAMPLE_RATE / 44100);
}

OboeAudioEngine::~OboeAudioEngine() {
    stop();
}

bool OboeAudioEngine::start() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (mRunning.load()) return true;

    LOGD("Starting Oboe duplex low-latency audio streams...");

    // 1. Build and open the Low-Latency Input Stream (Microphone)
    oboe::AudioStreamBuilder inBuilder;
    inBuilder.setDirection(oboe::Direction::Input)
             ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
             ->setSharingMode(oboe::SharingMode::Shared)
             ->setFormat(oboe::AudioFormat::I16)
             ->setChannelCount(oboe::ChannelCount::Mono)
             ->setSampleRate(SAMPLE_RATE)
             ->setInputPreset(oboe::InputPreset::VoiceCommunication) // Hardware AEC/NS on Samsung Galaxy
             ->setAudioApi(oboe::AudioApi::AAudio);

    oboe::Result result = inBuilder.openStream(mInputStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Oboe input stream: %s", oboe::convertToText(result));
        return false;
    }

    // 2. Build and open the Low-Latency Output Stream (Media / Bluetooth)
    oboe::AudioStreamBuilder outBuilder;
    outBuilder.setDirection(oboe::Direction::Output)
              ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
              ->setSharingMode(oboe::SharingMode::Shared)
              ->setFormat(oboe::AudioFormat::I16)
              ->setChannelCount(oboe::ChannelCount::Mono)
              ->setSampleRate(SAMPLE_RATE)
              ->setUsage(oboe::Usage::Media)
              ->setContentType(oboe::ContentType::Speech)
              ->setAudioApi(oboe::AudioApi::AAudio)
              ->setCallback(this)
              ->setErrorCallback(this);

    result = outBuilder.openStream(mOutputStream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open Oboe output stream: %s", oboe::convertToText(result));
        mInputStream->close();
        mInputStream.reset();
        return false;
    }

    // Configure optimal buffer size for Galaxy S21 Ultra (double-burst = 8-10ms)
    int32_t burstSize = mOutputStream->getFramesPerBurst();
    int32_t targetBufferFrames = std::max(burstSize * 2, 384);
    mOutputStream->setBufferSizeInFrames(targetBufferFrames);

    LOGD("Oboe Output Stream configured: Burst = %d frames, Buffer = %d frames, SampleRate = %d Hz",
         burstSize, mOutputStream->getBufferSizeInFrames(), mOutputStream->getSampleRate());

    if (mInputBuffer.size() < static_cast<size_t>(targetBufferFrames * 2)) {
        mInputBuffer.resize(targetBufferFrames * 2, 0);
    }

    // 3. Start audio streams
    result = mInputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe input stream: %s", oboe::convertToText(result));
        mOutputStream->close();
        mInputStream->close();
        return false;
    }

    result = mOutputStream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start Oboe output stream: %s", oboe::convertToText(result));
        mInputStream->stop();
        mInputStream->close();
        mOutputStream->close();
        return false;
    }

    mRunning.store(true);
    LOGD("Oboe real-time audio pipeline successfully started!");
    return true;
}

void OboeAudioEngine::stop() {
    std::lock_guard<std::mutex> lock(mStreamMutex);
    if (!mRunning.load()) return;
    mRunning.store(false);

    LOGD("Stopping Oboe streams...");
    if (mOutputStream) {
        mOutputStream->stop();
        mOutputStream->close();
        mOutputStream.reset();
    }
    if (mInputStream) {
        mInputStream->stop();
        mInputStream->close();
        mInputStream.reset();
    }
    mRms.store(0.0f);
    mPeak.store(0.0f);
    mHowlingDetected.store(false);
}

void OboeAudioEngine::setEcho(float wet, int delayMs, float feedback) {
    mEchoWet.store(std::clamp(wet, 0.0f, 1.0f));
    int delaySamples = (delayMs * SAMPLE_RATE) / 1000;
    mEchoDelaySamples.store(std::clamp(delaySamples, 100, MAX_DELAY_SAMPLES - 1));
    mEchoFeedback.store(std::clamp(feedback, 0.0f, 0.85f));
}

void OboeAudioEngine::setReverb(float wet, float roomSize) {
    mReverbWet.store(std::clamp(wet, 0.0f, 1.0f));
    mReverbRoomSize.store(std::clamp(roomSize, 0.1f, 0.95f));
}

oboe::DataCallbackResult OboeAudioEngine::onAudioReady(
    oboe::AudioStream *audioStream,
    void *audioData,
    int32_t numFrames
) {
    if (numFrames <= 0) return oboe::DataCallbackResult::Continue;

    int16_t* outputBuffer = static_cast<int16_t*>(audioData);

    if (mMuted.load()) {
        std::memset(outputBuffer, 0, numFrames * sizeof(int16_t));
        mRms.store(0.0f);
        mPeak.store(0.0f);
        mHowlingDetected.store(false);
        return oboe::DataCallbackResult::Continue;
    }

    // Pull microphone samples from the input stream with 0 timeout (non-blocking)
    int32_t framesRead = 0;
    if (mInputStream) {
        int32_t maxSafeFrames = static_cast<int32_t>(mInputBuffer.size());
        int32_t framesToRead = std::min(numFrames, maxSafeFrames);
        auto result = mInputStream->read(mInputBuffer.data(), framesToRead, 0);
        if (result) {
            framesRead = result.value();
        }
    }

    // Cached DSP parameters
    const float boostGain = mBoostGain.load(); // 1.0x to 5.0x
    const float echoWet = mEchoWet.load();
    const int delaySamples = mEchoDelaySamples.load();
    const float echoFeedback = mEchoFeedback.load();
    const float reverbWet = mReverbWet.load();
    const float roomFeedback = mReverbRoomSize.load() * 0.28f + 0.70f;
    const bool antiHowl = mAntiHowling.load();

    double sumSq = 0.0;
    float maxPeak = 0.0f;

    for (int32_t i = 0; i < numFrames; ++i) {
        float raw = 0.0f;
        if (i < framesRead && i < static_cast<int32_t>(mInputBuffer.size())) {
            raw = mInputBuffer[i] / 32768.0f;
        }
        if (!std::isfinite(raw)) raw = 0.0f;

        float absRaw = std::abs(raw);
        if (absRaw > maxPeak) maxPeak = absRaw;
        sumSq += (raw * raw);

        // 1. Apply independent Mic Boost Gain multiplier (1.0x to 5.0x)
        float s = raw * boostGain;

        // 2. Anti-Howling protective ducking
        if (mDuckingGain < 1.0f) {
            s *= mDuckingGain;
            mDuckingGain = std::min(1.0f, mDuckingGain + 0.0005f);
        }

        // 3. Reverb DSP (Schroeder multi-comb & all-pass diffusion)
        float reverbOut = 0.0f;
        if (reverbWet > 0.01f) {
            float cSum = 0.0f;
            for (auto& c : mCombFilters) {
                cSum += c.process(s, roomFeedback, 0.35f);
            }
            float diff = cSum * 0.25f;
            for (auto& ap : mAllPassFilters) {
                diff = ap.process(diff, 0.5f);
            }
            reverbOut = diff;
        }

        // 4. Circular Echo DSP
        float echoOut = 0.0f;
        if (echoWet > 0.01f) {
            int rIdx = (mEchoWriteIndex - delaySamples + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;
            float delayed = mEchoBuffer[rIdx];
            mEchoBuffer[mEchoWriteIndex] = s + (delayed * echoFeedback);
            echoOut = delayed;
            mEchoWriteIndex = (mEchoWriteIndex + 1) % MAX_DELAY_SAMPLES;
        }

        // 5. Wet/Dry mix
        float dry = 1.0f - std::max(echoWet, reverbWet) * 0.4f;
        float mixed = (s * dry) + (echoOut * echoWet) + (reverbOut * reverbWet);

        // 6. Analog-Style Soft Saturation limiter
        mixed = softSaturate(mixed);
        if (!std::isfinite(mixed)) mixed = 0.0f;

        // 7. Brickwall Hard-Clipper strictly clamping to [-32768, 32767]
        int32_t pcm = static_cast<int32_t>(mixed * 32767.0f);
        if (pcm > 32767) {
            pcm = 32767;
            mClipDetected.store(true);
        } else if (pcm < -32768) {
            pcm = -32768;
            mClipDetected.store(true);
        }

        outputBuffer[i] = static_cast<int16_t>(pcm);
    }

    float rms = static_cast<float>(std::sqrt(sumSq / numFrames));
    mRms.store(rms);
    mPeak.store(maxPeak);

    // Howling detection algorithm
    if (antiHowl) {
        if (maxPeak > 0.92f && rms > 0.65f) {
            mHighEnergyCount++;
            if (mHighEnergyCount > 4) {
                mHowlingDetected.store(true);
                mDuckingGain = 0.12f; // Fast ducking to -18dB
            }
        } else {
            if (mHighEnergyCount > 0) mHighEnergyCount--;
            if (mDuckingGain >= 0.95f) {
                mHowlingDetected.store(false);
            }
        }
    }

    return oboe::DataCallbackResult::Continue;
}

void OboeAudioEngine::onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) {
    LOGW("Oboe stream error occurred: %s. Attempting graceful reconnect...", oboe::convertToText(error));
    if (mRunning.load()) {
        stop();
        start();
    }
}
