#include "OboeAudioEngine.h"
#include <algorithm>
#include <cmath>
#include <cstring>

OboeAudioEngine::OboeAudioEngine()
    : mEchoBuffer(MAX_DELAY_SAMPLES, 0.0f) {
    // Standard Schroeder prime-spaced delay lines for 48kHz
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
    mRunning.store(true);
    return true;
}

void OboeAudioEngine::stop() {
    mRunning.store(false);
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

void OboeAudioEngine::processAudio(int16_t* inBuffer, int16_t* outBuffer, int32_t numFrames) {
    if (numFrames <= 0) return;

    if (mMuted.load()) {
        std::memset(outBuffer, 0, numFrames * sizeof(int16_t));
        mRms.store(0.0f);
        mPeak.store(0.0f);
        mHowlingDetected.store(false);
        return;
    }

    const float gain = mGain.load();
    const float echoWet = mEchoWet.load();
    const int delaySamples = mEchoDelaySamples.load();
    const float echoFeedback = mEchoFeedback.load();
    const float reverbWet = mReverbWet.load();
    const float roomFeedback = mReverbRoomSize.load() * 0.28f + 0.70f;
    const bool antiHowl = mAntiHowling.load();

    double sumSq = 0.0;
    float maxPeak = 0.0f;

    for (int32_t i = 0; i < numFrames; ++i) {
        float raw = inBuffer[i] / 32768.0f;
        float absRaw = std::abs(raw);
        if (absRaw > maxPeak) maxPeak = absRaw;
        sumSq += (raw * raw);

        float s = raw * gain;

        // Anti-howling protective ducking
        if (mDuckingGain < 1.0f) {
            s *= mDuckingGain;
            mDuckingGain = std::min(1.0f, mDuckingGain + 0.0005f);
        }

        // Reverb comb & allpass diffusion
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

        // Circular Echo
        float echoOut = 0.0f;
        if (echoWet > 0.01f) {
            int rIdx = (mEchoWriteIndex - delaySamples + MAX_DELAY_SAMPLES) % MAX_DELAY_SAMPLES;
            float delayed = mEchoBuffer[rIdx];
            mEchoBuffer[mEchoWriteIndex] = s + (delayed * echoFeedback);
            echoOut = delayed;
            mEchoWriteIndex = (mEchoWriteIndex + 1) % MAX_DELAY_SAMPLES;
        }

        float dry = 1.0f - std::max(echoWet, reverbWet) * 0.4f;
        float mixed = (s * dry) + (echoOut * echoWet) + (reverbOut * reverbWet);
        mixed = softSaturate(mixed);

        int32_t pcm = static_cast<int32_t>(mixed * 32767.0f);
        outBuffer[i] = static_cast<int16_t>(std::clamp(pcm, -32768, 32767));
    }

    float rms = static_cast<float>(std::sqrt(sumSq / numFrames));
    mRms.store(rms);
    mPeak.store(maxPeak);

    if (antiHowl) {
        if (maxPeak > 0.92f && rms > 0.65f) {
            mHighEnergyCount++;
            if (mHighEnergyCount > 4) {
                mHowlingDetected.store(true);
                mDuckingGain = 0.12f; // Clamp -18dB
            }
        } else {
            if (mHighEnergyCount > 0) mHighEnergyCount--;
            if (mDuckingGain >= 0.95f) {
                mHowlingDetected.store(false);
            }
        }
    }
}
