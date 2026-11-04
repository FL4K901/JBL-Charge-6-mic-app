# 🎙️ Mic — Real-Time Karaoke Microphone for JBL Charge 6

[![Build & Release APK](https://github.com/FL4K901/JBL-Charge-6-mic-app/actions/workflows/build_and_release.yml/badge.svg)](https://github.com/FL4K901/JBL-Charge-6-mic-app/actions/workflows/build_and_release.yml)
[![Download APK](https://img.shields.io/badge/Download-Release%20APK-00E5FF?style=for-the-badge&logo=android)](https://github.com/FL4K901/JBL-Charge-6-mic-app/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%20%7C%20Samsung%20Galaxy%20S21%20Ultra-blue?style=flat-square)](https://github.com/FL4K901/JBL-Charge-6-mic-app)
[![Design](https://img.shields.io/badge/Design-Apple%20Cupertino%20Dark%20Glass-7000FF?style=flat-square)](https://github.com/FL4K901/JBL-Charge-6-mic-app)

A high-performance, real-time karaoke microphone application tailored for the **Samsung Galaxy S21 Ultra** and connected **JBL Charge 6** (and any Bluetooth speaker or wired headset).

The app captures live vocals through the smartphone's microphone and routes them directly to the Bluetooth speaker with sub-5ms internal DSP latency, while **simultaneously mixing with background music players (Spotify, YouTube, Apple Music) without ducking, muting, or requesting exclusive audio focus.**

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

## 🌟 Key Features

### 1. Zero-Focus Concurrent Background Mixing
- Plays vocal audio through Android's `AudioFlinger` mixer on `USAGE_MEDIA` with **no exclusive audio focus request**.
- Background music from Spotify, YouTube, or Apple Music continues playing at full volume without pauses or ducking.
- Maintains `AudioManager.MODE_NORMAL` to prevent Android from downgrading Bluetooth to the telephone mono SCO profile, preserving full stereo 44.1/48kHz A2DP fidelity on the JBL Charge 6.

### 2. Studio Karaoke DSP Effects
- **Mic Volume (Preamp Booster):** Software volume booster (up to 250%) equipped with an analog-style soft-saturation curve to prevent harsh digital clipping.
- **Echo (Delay Line):** Circular buffer delay (50ms to 600ms) with smooth feedback decay.
- **Reverb (Concert Space):** Schroeder/Freeverb reverberator (4 parallel comb filters + 2 series all-pass diffusers) providing rich, natural vocal ambience.
- **Hardware AEC & Noise Suppression:** Integrates Android hardware `AcousticEchoCanceler` and `NoiseSuppressor` on the recording session.

### 3. Edge-Case & Acoustic Safety Protection
- **Anti-Howling (Feedback Loop) Protector:** Analyzes real-time crest factor (Peak / RMS). If continuous acoustic feedback resonance is detected, output is automatically ducked by -18dB immediately to save your ears and the speaker drivers.
- **Bluetooth Disconnect Auto-Mute:** Listens to `ACTION_AUDIO_BECOMING_NOISY`. If the JBL speaker disconnects, the mic instantly auto-mutes to prevent howling through the phone's built-in speaker.
- **Call Interruption Handling:** Pauses mic capture during incoming or active phone calls (`TelephonyCallback`), releasing hardware resources, and resumes automatically when the call finishes.
- **Persistent Background Execution:** Operates as a Foreground Service (`android:foregroundServiceType="microphone"`) with persistent notification controls (Quick Mute & Turn Off), allowing continuous use while browsing lyrics.

### 4. Apple-Style Clean Aesthetic
- Minimalist iOS Cupertino dark glassmorphism design.
- **Dynamic Island Status Pill:** Real-time connection badge (`"Connected: JBL Charge 6"`).
- **Hero Mic Button:** Massive central toggle disc with an animated breathing neon halo when active.
- **Ballistic VU Meter:** Reactive LED-style audio level visualizer with decibel markings and feedback warning banner.
- **Capsule Sliders:** iOS Control Center-style capsule sliders for Preamp Volume, Echo, and Reverb.

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
│       ├── kotlin/com/mic/app/mic/
│       │   ├── AudioDspProcessor.kt   # Real-time DSP (Gain boost, Echo, Reverb, Anti-Howl)
│       │   ├── AudioEngine.kt         # Low-latency AudioRecord/AudioTrack engine (Zero Focus)
│       │   ├── MicForegroundService.kt# Persistent background service & Bluetooth receiver
│       │   └── MainActivity.kt        # Flutter MethodChannel & EventChannel bridge
│       └── cpp/
│           ├── OboeAudioEngine.h      # C++ AAudio / Google Oboe engine definition
│           └── OboeAudioEngine.cpp    # C++ system-level audio stream implementation
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

The output APK will be generated at:
`build/app/outputs/flutter-apk/app-release.apk`

---

## 📄 License

MIT License © 2026 FL4K901
