import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

class AudioState {
  final bool isRunning;
  final bool isUsingOboe;
  final bool isMuted;
  final double boostGain; // 1.0x to 5.0x (+0dB to +14dB)
  final double echoWet;
  final int echoDelayMs;
  final double echoFeedback;
  final double reverbWet;
  final double reverbRoomSize;
  final bool antiHowling;
  final bool safetyAutoMute;
  final String deviceName;
  final bool isBluetooth;
  final double rms;
  final double peak;
  final bool isHowling;
  final bool isClipping;

  const AudioState({
    this.isRunning = false,
    this.isUsingOboe = false,
    this.isMuted = false,
    this.boostGain = 1.5,
    this.echoWet = 0.25,
    this.echoDelayMs = 220,
    this.echoFeedback = 0.35,
    this.reverbWet = 0.30,
    this.reverbRoomSize = 0.75,
    this.antiHowling = true,
    this.safetyAutoMute = true,
    this.deviceName = 'Scanning audio...',
    this.isBluetooth = false,
    this.rms = 0.0,
    this.peak = 0.0,
    this.isHowling = false,
    this.isClipping = false,
  });

  AudioState copyWith({
    bool? isRunning,
    bool? isUsingOboe,
    bool? isMuted,
    double? boostGain,
    double? echoWet,
    int? echoDelayMs,
    double? echoFeedback,
    double? reverbWet,
    double? reverbRoomSize,
    bool? antiHowling,
    bool? safetyAutoMute,
    String? deviceName,
    bool? isBluetooth,
    double? rms,
    double? peak,
    bool? isHowling,
    bool? isClipping,
  }) {
    return AudioState(
      isRunning: isRunning ?? this.isRunning,
      isUsingOboe: isUsingOboe ?? this.isUsingOboe,
      isMuted: isMuted ?? this.isMuted,
      boostGain: boostGain ?? this.boostGain,
      echoWet: echoWet ?? this.echoWet,
      echoDelayMs: echoDelayMs ?? this.echoDelayMs,
      echoFeedback: echoFeedback ?? this.echoFeedback,
      reverbWet: reverbWet ?? this.reverbWet,
      reverbRoomSize: reverbRoomSize ?? this.reverbRoomSize,
      antiHowling: antiHowling ?? this.antiHowling,
      safetyAutoMute: safetyAutoMute ?? this.safetyAutoMute,
      deviceName: deviceName ?? this.deviceName,
      isBluetooth: isBluetooth ?? this.isBluetooth,
      rms: rms ?? this.rms,
      peak: peak ?? this.peak,
      isHowling: isHowling ?? this.isHowling,
      isClipping: isClipping ?? this.isClipping,
    );
  }
}

class AudioController extends ValueNotifier<AudioState> {
  static const MethodChannel _methodChannel = MethodChannel('com.mic.app/audio_control');
  static const EventChannel _eventChannel = EventChannel('com.mic.app/audio_events');

  StreamSubscription? _eventSubscription;

  AudioController() : super(const AudioState()) {
    _init();
  }

  Future<void> _init() async {
    _eventSubscription = _eventChannel.receiveBroadcastStream().listen(
      (dynamic event) {
        if (event is Map) {
          final rms = (event['rms'] as num?)?.toDouble() ?? 0.0;
          final peak = (event['peak'] as num?)?.toDouble() ?? 0.0;
          final isHowling = (event['isHowling'] as bool?) ?? false;
          final isClipping = (event['isClipping'] as bool?) ?? false;
          final isMuted = (event['isMuted'] as bool?) ?? false;
          final isRunning = (event['isRunning'] as bool?) ?? false;
          final isUsingOboe = (event['isUsingOboe'] as bool?) ?? false;
          final deviceName = (event['deviceName'] as String?) ?? 'Phone Speaker';
          final isBluetooth = (event['isBluetooth'] as bool?) ?? false;

          value = value.copyWith(
            rms: rms,
            peak: peak,
            isHowling: isHowling,
            isClipping: isClipping,
            isMuted: isMuted,
            isRunning: isRunning,
            isUsingOboe: isUsingOboe,
            deviceName: deviceName,
            isBluetooth: isBluetooth,
          );
        }
      },
      onError: (dynamic error) {
        debugPrint('Audio event stream error: $error');
      },
    );

    await refreshDeviceInfo();
  }

  Future<bool> requestPermissions() async {
    try {
      await _methodChannel.invokeMethod('requestPermissions');
      final bool? isGranted = await _methodChannel.invokeMethod<bool>('checkPermissions');
      return isGranted ?? true;
    } catch (e) {
      debugPrint('Error requesting permissions: $e');
      return true;
    }
  }

  Future<void> refreshDeviceInfo() async {
    try {
      final dynamic info = await _methodChannel.invokeMethod('getAudioDeviceInfo');
      if (info is Map) {
        value = value.copyWith(
          deviceName: (info['deviceName'] as String?) ?? value.deviceName,
          isBluetooth: (info['isBluetooth'] as bool?) ?? value.isBluetooth,
          isRunning: (info['isRunning'] as bool?) ?? value.isRunning,
          isUsingOboe: (info['isUsingOboe'] as bool?) ?? value.isUsingOboe,
          isMuted: (info['isMuted'] as bool?) ?? value.isMuted,
          boostGain: (info['boostGain'] as num?)?.toDouble() ?? value.boostGain,
          echoWet: (info['echoWet'] as num?)?.toDouble() ?? value.echoWet,
          reverbWet: (info['reverbWet'] as num?)?.toDouble() ?? value.reverbWet,
          antiHowling: (info['antiHowling'] as bool?) ?? value.antiHowling,
        );
      }
    } catch (e) {
      debugPrint('Error refreshing audio device info: $e');
    }
  }

  Future<void> toggleMic() async {
    if (value.isRunning) {
      await stopMic();
    } else {
      await startMic();
    }
  }

  Future<void> startMic() async {
    await requestPermissions();
    try {
      await _methodChannel.invokeMethod('startMic');
      value = value.copyWith(isRunning: true);
      // Synchronize initial DSP settings
      await setBoostGain(value.boostGain);
      await setEcho(value.echoWet, value.echoDelayMs, value.echoFeedback);
      await setReverb(value.reverbWet, value.reverbRoomSize);
      await setAntiHowling(value.antiHowling);
      await setSafetyAutoMute(value.safetyAutoMute);
    } catch (e) {
      debugPrint('Error starting mic: $e');
    }
  }

  Future<void> stopMic() async {
    try {
      await _methodChannel.invokeMethod('stopMic');
      value = value.copyWith(isRunning: false, rms: 0.0, peak: 0.0, isHowling: false, isClipping: false);
    } catch (e) {
      debugPrint('Error stopping mic: $e');
    }
  }

  Future<void> toggleMute() async {
    final newMute = !value.isMuted;
    try {
      await _methodChannel.invokeMethod('setMute', {'isMuted': newMute});
      value = value.copyWith(isMuted: newMute);
    } catch (e) {
      debugPrint('Error toggling mute: $e');
    }
  }

  /// Independent Mic Boost multiplier (1.0x to 5.0x)
  Future<void> setBoostGain(double gain) async {
    final clamped = gain.clamp(1.0, 5.0);
    value = value.copyWith(boostGain: clamped);
    try {
      await _methodChannel.invokeMethod('setBoostGain', {'gain': clamped});
    } catch (e) {
      debugPrint('Error setting boost gain: $e');
    }
  }

  Future<void> setEcho(double wet, int delayMs, double feedback) async {
    final clampedWet = wet.clamp(0.0, 1.0);
    final clampedDelay = delayMs.clamp(50, 600);
    final clampedFeedback = feedback.clamp(0.0, 0.85);

    value = value.copyWith(
      echoWet: clampedWet,
      echoDelayMs: clampedDelay,
      echoFeedback: clampedFeedback,
    );

    try {
      await _methodChannel.invokeMethod('setEcho', {
        'wet': clampedWet,
        'delayMs': clampedDelay,
        'feedback': clampedFeedback,
      });
    } catch (e) {
      debugPrint('Error setting echo: $e');
    }
  }

  Future<void> setReverb(double wet, double roomSize) async {
    final clampedWet = wet.clamp(0.0, 1.0);
    final clampedRoom = roomSize.clamp(0.1, 0.95);

    value = value.copyWith(
      reverbWet: clampedWet,
      reverbRoomSize: clampedRoom,
    );

    try {
      await _methodChannel.invokeMethod('setReverb', {
        'wet': clampedWet,
        'roomSize': clampedRoom,
      });
    } catch (e) {
      debugPrint('Error setting reverb: $e');
    }
  }

  Future<void> setAntiHowling(bool enabled) async {
    value = value.copyWith(antiHowling: enabled);
    try {
      await _methodChannel.invokeMethod('setAntiHowling', {'enabled': enabled});
    } catch (e) {
      debugPrint('Error setting anti-howling: $e');
    }
  }

  Future<void> setSafetyAutoMute(bool enabled) async {
    value = value.copyWith(safetyAutoMute: enabled);
    try {
      await _methodChannel.invokeMethod('setSafetyAutoMute', {'enabled': enabled});
    } catch (e) {
      debugPrint('Error setting safety auto mute: $e');
    }
  }

  @override
  void dispose() {
    _eventSubscription?.cancel();
    super.dispose();
  }
}
