# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 1.0.x   | :white_check_mark: |
| < 1.0.0 | :x:                |

## Reporting a Vulnerability

We take the security and integrity of **Mic** seriously. If you believe you have discovered a security vulnerability or vulnerability in the audio DSP processing pipeline, please follow these steps:

1. **Do not open a public issue.**
2. Report the vulnerability privately via GitHub Security Advisories at [https://github.com/FL4K901/JBL-Charge-6-mic-app/security/advisories](https://github.com/FL4K901/JBL-Charge-6-mic-app/security/advisories).
3. Provide detailed steps to reproduce the vulnerability, including your device model, Android OS version, and any relevant logs or call stacks.

### Security Architecture Highlights
- **Zero Remote Exfiltration:** The app requests zero internet permissions (`android.permission.INTERNET` is omitted). Audio signals are processed strictly in RAM and never written to persistent disk or transmitted across network interfaces.
- **Bounds-Safe DSP Execution:** Raw PCM sample reads in native C++ (Google Oboe AAudio) are strictly clamped to buffer capacities, with mutex locks guarding stream lifecycles.
- **Privacy First:** Incoming call pauses are handled permission-free via Android's `AudioManager.OnAudioFocusChangeListener`, eliminating telephony permissions (`READ_PHONE_STATE`).
