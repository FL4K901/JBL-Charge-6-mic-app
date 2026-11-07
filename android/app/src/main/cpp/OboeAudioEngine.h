#pragma once

#include <oboe/Oboe.h>
#include <vector>
#include <atomic>
#include <cmath>
#include <memory>
#include <mutex>

/**
 * Ultra-low latency Real-Time Audio Engine utilizing Google Oboe and Android AAudio.
 *
 * Configured specifically for Samsung Galaxy S21 Ultra and Bluetooth output:
 * - Output Stream: Media usage with LowLatency AAudio, Native 48kHz, optimal burst size
 * - Input Stream: VoiceCommunication preset for hardware AEC & NS, LowLatency AAudio
 * - Full Duplex: Non-blocking pull callback on output stream (target latency: 8-16ms)
 * - DSP: Real-time Mic Boost Gain (1.0x - 5.0x), Echo delay, Schroeder Reverb, Anti-Howl
 * - Protective Limiter: Soft-knee saturation + brickwall hard-clipper strictly clamping to [-32768, 32767]
 */
class OboeAudioEngine : public oboe::AudioStreamCallback {
public:
    OboeAudioEngine();
    virtual ~OboeAudioEngine();

    bool start();
    void stop();
    bool isRunning() const { return mRunning.load(); }

    // DSP Parameter Controls
    void setBoostGain(float gain) { mBoostGain.store(std::clamp(gain, 1.0f, 5.0f)); }
    void setEcho(float wet, int delayMs, float feedback);
    void setReverb(float wet, float roomSize);
    void setMute(bool mute) { mMuted.store(mute); }
    void setAntiHowling(bool enabled) { mAntiHowling.store(enabled); }

    // Real-time Metrics
    float getRms() const { return mRms.load(); }
    float getPeak() const { return mPeak.load(); }
    bool isHowling() const { return mHowlingDetected.load(); }
    bool isClipping() { return mClipDetected.exchange(false); }

    // Oboe AudioStreamCallback interface
    oboe::DataCallbackResult onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames
    ) override;

    // Error callback from AudioStreamCallback base
    void onErrorAfterClose(oboe::AudioStream *audioStream, oboe::Result error) override;

private:
    static constexpr int SAMPLE_RATE = 48000;
    static constexpr int MAX_DELAY_SAMPLES = 48000; // 1 second buffer @ 48kHz
    static constexpr int CHANNEL_COUNT = 1;        // Mono processing

    std::shared_ptr<oboe::AudioStream> mInputStream;
    std::shared_ptr<oboe::AudioStream> mOutputStream;

    std::atomic<bool> mRunning{false};
    std::atomic<bool> mMuted{false};
    std::atomic<float> mBoostGain{1.5f}; // 1.0x to 5.0x Mic Boost
    std::atomic<float> mEchoWet{0.25f};
    std::atomic<int> mEchoDelaySamples{10560}; // ~220ms
    std::atomic<float> mEchoFeedback{0.35f};
    std::atomic<float> mReverbWet{0.30f};
    std::atomic<float> mReverbRoomSize{0.75f};
    std::atomic<bool> mAntiHowling{true};

    std::atomic<float> mRms{0.0f};
    std::atomic<float> mPeak{0.0f};
    std::atomic<bool> mHowlingDetected{false};
    std::atomic<bool> mClipDetected{false};

    // Pre-allocated intermediate buffer for input stream reads
    std::vector<int16_t> mInputBuffer;

    // Delay line buffer
    std::vector<float> mEchoBuffer;
    int mEchoWriteIndex{0};

    // Schroeder Reverberator Components
    struct CombFilter {
        std::vector<float> buf;
        int idx = 0;
        float store = 0.0f;
        CombFilter(size_t sz) : buf(sz, 0.0f) {}
        inline float process(float in, float fb, float damp) {
            float out = buf[idx];
            store = (out * (1.0f - damp)) + (store * damp);
            buf[idx] = in + (store * fb);
            if (++idx >= static_cast<int>(buf.size())) idx = 0;
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
            if (++idx >= static_cast<int>(buf.size())) idx = 0;
            return out;
        }
    };

    std::vector<CombFilter> mCombFilters;
    std::vector<AllPassFilter> mAllPassFilters;

    float mDuckingGain{1.0f};
    int mHighEnergyCount{0};

    // Thread synchronization for lifecycle operations
    std::mutex mStreamMutex;

    // DSP limiting helpers
    inline float softSaturate(float x) {
        if (!std::isfinite(x)) return 0.0f;
        if (x > 1.25f) return 1.0f;
        if (x < -1.25f) return -1.0f;
        if (x > 0.6f) return 0.6f + (x - 0.6f) / (1.0f + ((x - 0.6f) * (x - 0.6f)));
        if (x < -0.6f) return -0.6f + (x + 0.6f) / (1.0f + ((x + 0.6f) * (x + 0.6f)));
        return x;
    }
};
