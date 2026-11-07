// Copyright (c) 2026 FL4K901. All rights reserved.
// Licensed under the MIT License. See LICENSE in the project root.

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'services/audio_service.dart';
import 'widgets/apple_card.dart';
import 'widgets/apple_slider.dart';
import 'widgets/device_status_pill.dart';
import 'widgets/mic_hero_button.dart';
import 'widgets/vu_meter_visualizer.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);
  SystemChrome.setSystemUIOverlayStyle(
    const SystemUiOverlayStyle(
      statusBarColor: Colors.transparent,
      statusBarIconBrightness: Brightness.light,
      systemNavigationBarColor: Color(0xFF0A0A0C),
      systemNavigationBarIconBrightness: Brightness.light,
    ),
  );
  runApp(const MicApp());
}

class MicApp extends StatelessWidget {
  const MicApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Mic',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: const Color(0xFF0A0A0C),
        colorScheme: const ColorScheme.dark(
          primary: Color(0xFF00E5FF),
          secondary: Color(0xFF7000FF),
          surface: Color(0xFF14161E),
        ),
        fontFamily: '.SF Pro Text',
        useMaterial3: true,
      ),
      home: const MicHomeScreen(),
    );
  }
}

class MicHomeScreen extends StatefulWidget {
  const MicHomeScreen({super.key});

  @override
  State<MicHomeScreen> createState() => _MicHomeScreenState();
}

class _MicHomeScreenState extends State<MicHomeScreen> {
  late final AudioController _audioController;

  @override
  void initState() {
    super.initState();
    _audioController = AudioController();
  }

  @override
  void dispose() {
    _audioController.dispose();
    super.dispose();
  }

  void _showSettingsModal(BuildContext context, AudioState state) {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 32),
        decoration: const BoxDecoration(
          color: Color(0xFF161922),
          borderRadius: BorderRadius.vertical(top: Radius.circular(28)),
          border: Border(
            top: BorderSide(color: Color(0x33FFFFFF), width: 0.8),
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 36,
                height: 4,
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 18),
            const Text(
              'Acoustic Safety & Routing',
              style: TextStyle(
                fontSize: 18,
                fontWeight: FontWeight.w700,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 16),
            SwitchListTile.adaptive(
              contentPadding: EdgeInsets.zero,
              title: const Text('Anti-Howling Protection'),
              subtitle: const Text(
                'Auto-ducks audio when microphone feedback resonance is detected',
                style: TextStyle(fontSize: 12, color: Colors.white54),
              ),
              value: state.antiHowling,
              activeTrackColor: const Color(0xFF00E5FF),
              onChanged: (val) {
                _audioController.setAntiHowling(val);
                Navigator.pop(ctx);
              },
            ),
            SwitchListTile.adaptive(
              contentPadding: EdgeInsets.zero,
              title: const Text('Auto-Mute on Speaker Disconnect'),
              subtitle: const Text(
                'Prevents loud feedback blasts through phone speaker if JBL speaker disconnects',
                style: TextStyle(fontSize: 12, color: Colors.white54),
              ),
              value: state.safetyAutoMute,
              activeTrackColor: const Color(0xFF00E5FF),
              onChanged: (val) {
                _audioController.setSafetyAutoMute(val);
                Navigator.pop(ctx);
              },
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ValueListenableBuilder<AudioState>(
          valueListenable: _audioController,
          builder: (context, state, _) {
            final isLive = state.isRunning && !state.isMuted;

            return SingleChildScrollView(
              physics: const BouncingScrollPhysics(),
              padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  // 1. Header with App Name and Device Status Pill
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          const Text(
                            'Mic',
                            style: TextStyle(
                              fontSize: 32,
                              fontWeight: FontWeight.w800,
                              letterSpacing: -0.5,
                              color: Colors.white,
                            ),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: const Color(0xFF00E5FF).withValues(alpha: 0.15),
                              borderRadius: BorderRadius.circular(6),
                              border: Border.all(
                                color: const Color(0xFF00E5FF).withValues(alpha: 0.3),
                                width: 0.8,
                              ),
                            ),
                            child: const Text(
                              'OBOE DSP',
                              style: TextStyle(
                                fontSize: 9,
                                fontWeight: FontWeight.w800,
                                color: Color(0xFF00E5FF),
                                letterSpacing: 0.8,
                              ),
                            ),
                          ),
                        ],
                      ),
                      IconButton(
                        icon: const Icon(Icons.tune_rounded, color: Colors.white70),
                        onPressed: () => _showSettingsModal(context, state),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),

                  // Connected Device Capsule Pill with Ducking indicator
                  DeviceStatusPill(
                    deviceName: state.deviceName,
                    isBluetooth: state.isBluetooth,
                    isMuted: state.isMuted,
                    isLive: isLive,
                    isUsingOboe: state.isUsingOboe,
                    onToggleMute: () => _audioController.toggleMute(),
                  ),

                  const SizedBox(height: 24),

                  // 2. Hero Mic Button
                  MicHeroButton(
                    isRunning: state.isRunning,
                    isMuted: state.isMuted,
                    onTap: () => _audioController.toggleMic(),
                  ),

                  const SizedBox(height: 24),

                  // 3. VU Meter Visualizer Card with Limiter feedback
                  AppleCard(
                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
                    child: VuMeterVisualizer(
                      rms: state.rms,
                      peak: state.peak,
                      isLive: isLive,
                      isHowling: state.isHowling,
                      isClipping: state.isClipping,
                    ),
                  ),
                  const SizedBox(height: 14),

                  // 4. Karaoke DSP Effects Card
                  AppleCard(
                    padding: const EdgeInsets.all(18),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // Independent Mic Boost Slider (1.0x to 5.0x / +14dB)
                        AppleSlider(
                          title: 'Mic Boost',
                          subtitle: 'DSP Preamp & Limiter',
                          icon: Icons.electric_bolt_rounded,
                          value: state.boostGain,
                          min: 1.0,
                          max: 5.0,
                          activeColor: const Color(0xFF00E5FF),
                          formatValue: (val) {
                            final dB = ((val - 1.0) * 3.5).toStringAsFixed(0);
                            return '${val.toStringAsFixed(1)}x (+$dB dB)';
                          },
                          onChanged: (val) => _audioController.setBoostGain(val),
                        ),
                        const SizedBox(height: 18),

                        // Echo Slider (0% to 100%)
                        AppleSlider(
                          title: 'Echo',
                          subtitle: '${state.echoDelayMs}ms',
                          icon: Icons.repeat_rounded,
                          value: state.echoWet,
                          min: 0.0,
                          max: 1.0,
                          activeColor: const Color(0xFF7000FF),
                          formatValue: (val) => '${(val * 100).toInt()}%',
                          onChanged: (val) => _audioController.setEcho(
                            val,
                            state.echoDelayMs,
                            state.echoFeedback,
                          ),
                        ),
                        const SizedBox(height: 18),

                        // Reverb Slider (0% to 100%)
                        AppleSlider(
                          title: 'Reverb',
                          subtitle: 'Concert Hall',
                          icon: Icons.waves_rounded,
                          value: state.reverbWet,
                          min: 0.0,
                          max: 1.0,
                          activeColor: const Color(0xFFFF2A85),
                          formatValue: (val) => '${(val * 100).toInt()}%',
                          onChanged: (val) => _audioController.setReverb(
                            val,
                            state.reverbRoomSize,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 14),

                  // 5. Background Mixing & Ducking Status Banner
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.04),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.volume_down_rounded,
                          size: 14,
                          color: Colors.white.withValues(alpha: 0.5),
                        ),
                        const SizedBox(width: 8),
                        Text(
                          isLive
                              ? 'Spotify & YouTube ducked by ~30% for crystal clear vocals'
                              : 'Background audio ducks automatically when Mic is turned on',
                          style: TextStyle(
                            fontSize: 11,
                            color: isLive ? const Color(0xFF00E5FF) : Colors.white.withValues(alpha: 0.5),
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                ],
              ),
            );
          },
        ),
      ),
    );
  }
}
