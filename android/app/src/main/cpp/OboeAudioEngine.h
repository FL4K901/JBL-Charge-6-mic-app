#pragma once

#include <vector>
#include <atomic>
#include <cmath>
#include <memory>

/**
 * C++ Real-Time Audio Engine utilizing Android AAudio / Google Oboe.
 *
 * Configured for:
 * - Direction: Input (Recording) and Output (Media playback)
 * - SharingMode: Shared (Allows external media players like Spotify to play concurrently)
 * - PerformanceMode: LowLatency
 * - AudioFormat: I16 / Float
 * - Usage: Media (No audio focus request -> mixes naturally in AudioFlinger)
 */
class OboeAudioEngine {
public:
    OboeAudioEngine();
    ~OboeAudioEngine();

    bool start();
    void stop();

    void setGain(float g) { mGain.store(g); }
    void setEcho(float wet, int delayMs, float feedback);
    void setReverb(float wet, float roomSize);
    void setMute(bool mute) { mMuted.store(mute); }
    void setAntiHowling(bool enabled) { mAntiHowling.store(enabled); }

    float getRms() const { return mRms.load(); }
    float getPeak() const { return mPeak.load(); }
    bool isHowling() const { return mHowlingDetected.load(); }

    // Direct callback processing routine (invoked by AAudio / Oboe audio thread)
    void processAudio(int16_t* inBuffer, int16_t* outBuffer, int32_t numFrames);

private:
    static constexpr int SAMPLE_RATE = 48000;
    static constexpr int MAX_DELAY_SAMPLES = 48000;

    std::atomic<bool> mRunning{false};
    std::atomic<bool> mMuted{false};
    std::atomic<float> mGain{1.0f};
    std::atomic<float> mEchoWet{0.25f};
    std::atomic<int> mEchoDelaySamples{12000};
    std::atomic<float> mEchoFeedback{0.35f};
    std::atomic<float> mReverbWet{0.30f};
    std::atomic<float> mReverbRoomSize{0.75f};
    std::atomic<bool> mAntiHowling{true};

    std::atomic<float> mRms{0.0f};
    std::atomic<float> mPeak{0.0f};
    std::atomic<bool> mHowlingDetected{false};

    // Circular delay buffer for echo
    std::vector<float> mEchoBuffer;
    int mEchoWriteIndex{0};

    // Comb & Allpass structures for reverb
    struct CombFilter {
        std::vector<float> buf;
        int idx = 0;
        float store = 0.0f;
        CombFilter(size_t sz) : buf(sz, 0.0f) {}
        inline float process(float in, float fb, float damp) {
            float out = buf[idx];
            store = (out * (1.0f - damp)) + (store * damp);
            buf[idx] = in + (store * fb);
            if (++idx >= (int)buf.size()) idx = 0;
            return out;
        }
    };

    struct AllPassFilter {
        std::vector<float> buf;
        int idx = 0;
        AllPassFilter(size_t sz) : buf(sz, 0.0f) {}
        inline float process(float in, float fb = 0.5f) {
            float bufOut = buf[idx];
            float out = -in + bufOut;
            buf[idx] = in + (bufOut * fb);
            if (++idx >= (int)buf.size()) idx = 0;
            return out;
        }
    };

    std::vector<CombFilter> mCombFilters;
    std::vector<AllPassFilter> mAllPassFilters;

    float mDuckingGain{1.0f};
    int mHighEnergyCount{0};

    inline float softSaturate(float x) {
        if (x > 1.25f) return 1.0f;
        if (x < -1.25f) return -1.0f;
        if (x > 0.6f) return 0.6f + (x - 0.6f) / (1.0f + ((x - 0.6f) * (x - 0.6f)));
        if (x < -0.6f) return -0.6f + (x + 0.6f) / (1.0f + ((x + 0.6f) * (x + 0.6f)));
        return x;
    }
};
