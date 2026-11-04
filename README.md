# 🎙️ Mic — Real-Time Karaoke Microphone for JBL Charge 6

[![Build & Release APK](https://github.com/FL4K901/JBL-Charge-6-mic-app/actions/workflows/build_and_release.yml/badge.svg)](https://github.com/FL4K901/JBL-Charge-6-mic-app/actions/workflows/build_and_release.yml)
[![Download APK](https://img.shields.io/badge/Download-Release%20APK-00E5FF?style=for-the-badge&logo=android)](https://github.com/FL4K901/JBL-Charge-6-mic-app/releases/latest)
[![Audio Engine](https://img.shields.io/badge/Audio-Google%20Oboe%20C%2B%2B%20AAudio-00E5FF?style=flat-square)](https://github.com/FL4K901/JBL-Charge-6-mic-app)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Samsung%20Galaxy%20S21%20Ultra-blue?style=flat-square)](https://github.com/FL4K901/JBL-Charge-6-mic-app)
[![Design](https://img.shields.io/badge/Design-Apple%20Cupertino%20Dark%20Glass-7000FF?style=flat-square)](https://github.com/FL4K901/JBL-Charge-6-mic-app)

A high-performance, real-time karaoke microphone application engineered with **Google's Oboe C++ Library (AAudio)** for the **Samsung Galaxy S21 Ultra** and connected **JBL Charge 6** (as well as any Bluetooth speaker or wired headset).

The app captures live vocals through the phone's microphone, processes them in native C++ with sub-10ms software latency, and outputs them to the Bluetooth speaker while **automatically ducking background music (Spotify, YouTube, Apple Music) by ~30%** so your voice cuts through clearly.

---

## 📲 Download & Install APK

You can download the APK file directly to your smartphone:

- 📥 **[Download Latest APK from GitHub Releases](https://github.com/FL4K901/JBL-Charge-6-mic-app/releases/latest)**
- 📦 **[Direct Download: Mic-Karaoke-v1.0.0.apk](https://github.com/FL4K901/JBL-Charge-6-mic-app/releases/download/v1.0.0/Mic-Karaoke-v1.0.0.apk)**
- ⚙️ **[Automated GitHub Actions Build Runs](https://github.com/FL4K901/JBL-Charge-6-mic-app/actions)**

### Installation Instructions for Android:
1. Tap the download link on your phone.
2. When the download completes, tap the file (`Mic-Karaoke-v1.0.0.apk`) to install.
3. If prompted by Android, allow *"Install unknown apps"* for your browser.
4. Open **Mic**, grant Microphone and Bluetooth permissions, and start singing!

---

## 🚀 Key Architectural Updates

### 1. Ultra-Low Latency Native C++ Pipeline (Google Oboe)
- Replaces standard high-level audio routing with Google's **Oboe library** using direct JNI bindings.
- Operates in `oboe::PerformanceMode::LowLatency` with `oboe::AudioApi::AAudio` and `oboe::SharingMode::Shared`.
- Uses double-burst buffering tuned for the **Samsung Galaxy S21 Ultra** (typically 192 or 240 frames @ 48kHz), holding software latency to **8–16ms**.
- Runs in a real-time thread with zero Dart VM garbage collection pauses.

### 2. Independent Mic Boost (DSP Multiplier & Limiter)
- **Problem solved:** Physical device volume buttons adjust both the microphone and background music simultaneously.
- **Solution:** A dedicated **Mic Boost** slider in the UI that multiplies raw 16-bit PCM samples on the microphone stream from **1.0x up to 5.0x (+14 dB)** before mixing.
- **Brickwall Hard-Clipper & Soft Limiter:** Implements a soft-knee saturation curve followed by a strict hard-clipper clamping samples between `[-32768, 32767]` to prevent harsh digital distortion.

### 3. Background Audio Ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`)
- When the microphone is toggled **ON**, the app requests `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` with `willPauseWhenDucked(false)`.
- Android Audio Policy automatically ducks (lowers) background music from Spotify, YouTube, or Apple Music by **~30%**, allowing your boosted vocal signal to sit clearly on top of the mix.
- When the mic is toggled **OFF**, audio focus is cleanly abandoned, restoring background music to **100% volume**.

### 4. Karaoke DSP Effects & Safety
- **Echo (Delay Line):** Circular buffer delay (50ms to 600ms) with smooth feedback decay.
- **Reverb (Concert Space):** Schroeder/Freeverb reverberator (4 parallel comb filters + 2 series all-pass diffusers) for natural vocal space.
- **Anti-Howling (Feedback Protection):** Detects continuous acoustic feedback resonance and instantly ducks gain by -18dB to protect ears and speakers.
- **Bluetooth Disconnect Auto-Mute:** Auto-mutes microphone if the JBL speaker disconnects, preventing loud blasts through the phone speaker.
- **Call Interruption Handling:** Automatically pauses during phone calls and resumes afterward.

### 5. Apple-Style Clean Aesthetic
- Minimalist iOS Cupertino dark glassmorphism design.
- **Dynamic Island Status Pill:** Real-time badge showing connection status and ducking state (`"DUCKING -30%"`).
- **Hero Mic Button:** Massive central toggle disc with an animated breathing neon halo when active.
- **Ballistic VU Meter:** Reactive LED-style audio level visualizer with decibel markings and limiter engagement indicator.
- **Capsule Sliders:** iOS Control Center-style capsule sliders for Mic Boost (1.0x–5.0x), Echo, and Reverb.

---

## 🏗️ Repository Structure

```
├── lib/
│   ├── main.dart                      # Apple-style Cupertino UI and screen layout
│   ├── services/audio_service.dart    # Reactive audio state & native channel controller
│   └── widgets/
│       ├── apple_card.dart            # Frosted glass card container
│       ├── apple_slider.dart          # iOS Control Center-style capsule slider
│       ├── device_status_pill.dart    # Dynamic Island audio route status pill
│       ├── mic_hero_button.dart       # Central hero mic toggle button with neon halo
│       └── vu_meter_visualizer.dart   # Real-time ballistic VU meter & dB indicator
├── android/
│   └── app/src/main/
│       ├── AndroidManifest.xml        # Permissions and Foreground Service declaration
│       ├── cpp/
│       │   ├── CMakeLists.txt         # CMake build configuration linking Google Oboe
│       │   ├── OboeAudioEngine.h      # Google Oboe full-duplex low-latency engine
│       │   ├── OboeAudioEngine.cpp    # Real-time onAudioReady callback, DSP Boost & Limiter
│       │   └── native-lib.cpp         # JNI bridge between C++ Oboe and Kotlin
│       └── kotlin/com/mic/app/mic/
│           ├── OboeAudioEngineBridge.kt# Kotlin wrapper for JNI C++ engine
│           ├── AudioEngine.kt         # Audio routing, Oboe lifecycle & focus ducking
│           ├── AudioDspProcessor.kt   # High-performance DSP processor fallback
│           ├── MicForegroundService.kt# Persistent background service & Bluetooth receiver
│           └── MainActivity.kt        # Flutter MethodChannel & EventChannel bridge
└── .github/workflows/
    └── build_and_release.yml          # Automated CI/CD pipeline building release APKs
```

---

## ⚙️ Build from Source

```bash
# Clone the repository
git clone https://github.com/FL4K901/JBL-Charge-6-mic-app.git
cd JBL-Charge-6-mic-app

# Get dependencies
flutter pub get

# Run test suite
flutter test

# Build release APK
flutter build apk --release
```

---

## 📄 License

MIT License © 2026 FL4K901
