import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class DeviceStatusPill extends StatelessWidget {
  final String deviceName;
  final bool isBluetooth;
  final bool isMuted;
  final VoidCallback onToggleMute;

  const DeviceStatusPill({
    super.key,
    required this.deviceName,
    required this.isBluetooth,
    required this.isMuted,
    required this.onToggleMute,
  });

  @override
  Widget build(BuildContext context) {
    final bool isSafe = isBluetooth;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
      decoration: BoxDecoration(
        color: const Color(0xFF14161E).withValues(alpha: 0.85),
        borderRadius: BorderRadius.circular(24),
        border: Border.all(
          color: isMuted
              ? const Color(0xFFFF453A).withValues(alpha: 0.5)
              : (isSafe
                  ? const Color(0xFF00E5FF).withValues(alpha: 0.25)
                  : const Color(0xFFFF9F0A).withValues(alpha: 0.35)),
          width: 1.0,
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.4),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Dynamic status dot / icon
          Container(
            width: 28,
            height: 28,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: isMuted
                  ? const Color(0xFFFF453A).withValues(alpha: 0.2)
                  : (isSafe
                      ? const Color(0xFF00E5FF).withValues(alpha: 0.2)
                      : const Color(0xFFFF9F0A).withValues(alpha: 0.2)),
            ),
            child: Icon(
              isBluetooth ? Icons.bluetooth_connected_rounded : Icons.speaker_rounded,
              size: 16,
              color: isMuted
                  ? const Color(0xFFFF453A)
                  : (isSafe ? const Color(0xFF00E5FF) : const Color(0xFFFF9F0A)),
            ),
          ),
          const SizedBox(width: 10),

          // Device status description
          Flexible(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  isMuted
                      ? 'MIC MUTED'
                      : (isSafe ? 'CONNECTED' : 'SPEAKER ACTIVE'),
                  style: TextStyle(
                    fontSize: 10,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.0,
                    color: isMuted
                        ? const Color(0xFFFF453A)
                        : (isSafe ? const Color(0xFF00E5FF) : const Color(0xFFFF9F0A)),
                  ),
                ),
                Text(
                  deviceName,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 13,
                    fontWeight: FontWeight.w600,
                    letterSpacing: -0.2,
                    color: Colors.white,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 12),

          // Mute Action Button
          GestureDetector(
            onTap: () {
              HapticFeedback.selectionClick();
              onToggleMute();
            },
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
              decoration: BoxDecoration(
                color: isMuted
                    ? const Color(0xFFFF453A).withValues(alpha: 0.25)
                    : Colors.white.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: isMuted
                      ? const Color(0xFFFF453A).withValues(alpha: 0.6)
                      : Colors.white.withValues(alpha: 0.15),
                  width: 0.8,
                ),
              ),
              child: Text(
                isMuted ? 'UNMUTE' : 'MUTE',
                style: TextStyle(
                  fontSize: 11,
                  fontWeight: FontWeight.w700,
                  color: isMuted ? const Color(0xFFFF453A) : Colors.white,
                  letterSpacing: 0.5,
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}
