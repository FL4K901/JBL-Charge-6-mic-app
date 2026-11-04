import 'dart:math' as math;
import 'package:flutter/material.dart';

class VuMeterVisualizer extends StatefulWidget {
  final double rms;
  final double peak;
  final bool isLive;
  final bool isHowling;
  final bool isClipping;

  const VuMeterVisualizer({
    super.key,
    required this.rms,
    required this.peak,
    required this.isLive,
    required this.isHowling,
    this.isClipping = false,
  });

  @override
  State<VuMeterVisualizer> createState() => _VuMeterVisualizerState();
}

class _VuMeterVisualizerState extends State<VuMeterVisualizer> with SingleTickerProviderStateMixin {
  double _smoothLevel = 0.0;
  double _peakHold = 0.0;
  int _peakHoldTimer = 0;
  int _clipDisplayTimer = 0;

  @override
  void didUpdateWidget(covariant VuMeterVisualizer oldWidget) {
    super.didUpdateWidget(oldWidget);

    if (!widget.isLive) {
      _smoothLevel = 0.0;
      _peakHold = 0.0;
      _clipDisplayTimer = 0;
      return;
    }

    if (widget.isClipping) {
      _clipDisplayTimer = 15;
    } else if (_clipDisplayTimer > 0) {
      _clipDisplayTimer--;
    }

    final targetLevel = (widget.peak * 1.25).clamp(0.0, 1.0);
    if (targetLevel > _smoothLevel) {
      _smoothLevel = targetLevel;
    } else {
      _smoothLevel = (_smoothLevel * 0.82) + (targetLevel * 0.18);
    }

    if (_smoothLevel >= _peakHold) {
      _peakHold = _smoothLevel;
      _peakHoldTimer = 25;
    } else {
      if (_peakHoldTimer > 0) {
        _peakHoldTimer--;
      } else {
        _peakHold = math.max(0.0, _peakHold - 0.025);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Icon(
                  Icons.graphic_eq_rounded,
                  size: 16,
                  color: widget.isLive
                      ? const Color(0xFF00E5FF)
                      : Colors.white.withValues(alpha: 0.4),
                ),
                const SizedBox(width: 6),
                Text(
                  'INPUT LEVEL',
                  style: TextStyle(
                    fontSize: 12,
                    fontWeight: FontWeight.w700,
                    letterSpacing: 1.2,
                    color: Colors.white.withValues(alpha: 0.6),
                  ),
                ),
              ],
            ),
            if (widget.isHowling)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFFFF3B30).withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFFFF3B30), width: 0.8),
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.shield_rounded, size: 12, color: Color(0xFFFF3B30)),
                    SizedBox(width: 4),
                    Text(
                      'HOWLING DUCKED',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFFFF3B30),
                        letterSpacing: 0.8,
                      ),
                    ),
                  ],
                ),
              )
            else if (_clipDisplayTimer > 0)
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: const Color(0xFFFF9F0A).withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: const Color(0xFFFF9F0A), width: 0.8),
                ),
                child: const Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.bolt_rounded, size: 12, color: Color(0xFFFF9F0A)),
                    SizedBox(width: 4),
                    Text(
                      'LIMITER ACTIVE',
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w800,
                        color: Color(0xFFFF9F0A),
                        letterSpacing: 0.8,
                      ),
                    ),
                  ],
                ),
              )
            else
              Text(
                widget.isLive ? '${(_smoothLevel * 100).toInt()}%' : '0%',
                style: TextStyle(
                  fontSize: 12,
                  fontFeatures: const [FontFeature.tabularFigures()],
                  fontWeight: FontWeight.w600,
                  color: Colors.white.withValues(alpha: 0.5),
                ),
              ),
          ],
        ),
        const SizedBox(height: 10),

        // Custom Meter Bar
        SizedBox(
          height: 16,
          child: CustomPaint(
            painter: _VuSegmentPainter(
              level: _smoothLevel,
              peakHold: _peakHold,
              isLive: widget.isLive,
            ),
          ),
        ),
        const SizedBox(height: 6),

        // Decibel Scale Labels
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            _buildDbLabel('-∞'),
            _buildDbLabel('-24dB'),
            _buildDbLabel('-12dB'),
            _buildDbLabel('-6dB'),
            _buildDbLabel('0dB'),
            _buildDbLabel('LIMIT', isWarning: true),
          ],
        ),
      ],
    );
  }

  Widget _buildDbLabel(String text, {bool isWarning = false}) {
    return Text(
      text,
      style: TextStyle(
        fontSize: 10,
        fontWeight: FontWeight.w500,
        color: isWarning
            ? const Color(0xFFFF453A).withValues(alpha: 0.8)
            : Colors.white.withValues(alpha: 0.35),
      ),
    );
  }
}

class _VuSegmentPainter extends CustomPainter {
  final double level;
  final double peakHold;
  final bool isLive;

  _VuSegmentPainter({
    required this.level,
    required this.peakHold,
    required this.isLive,
  });

  @override
  void paint(Canvas canvas, Size size) {
    const int segmentCount = 34;
    final double segmentWidth = (size.width - (segmentCount - 1) * 3) / segmentCount;
    final double segmentHeight = size.height;

    final paint = Paint()..style = PaintingStyle.fill;

    for (int i = 0; i < segmentCount; i++) {
      final double segThreshold = (i + 1) / segmentCount;
      final double x = i * (segmentWidth + 3);
      final rrect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, 0, segmentWidth, segmentHeight),
        const Radius.circular(3),
      );

      Color activeColor;
      if (i < segmentCount * 0.65) {
        activeColor = const Color(0xFF30D158);
      } else if (i < segmentCount * 0.85) {
        activeColor = const Color(0xFFFFD60A);
      } else {
        activeColor = const Color(0xFFFF453A);
      }

      final bool isSegmentActive = isLive && (level >= segThreshold);
      final bool isPeakSegment = isLive && ((peakHold * segmentCount).round() == i);

      if (isSegmentActive) {
        paint.color = activeColor;
        canvas.drawRRect(rrect, paint);
      } else if (isPeakSegment) {
        paint.color = Colors.white;
        canvas.drawRRect(rrect, paint);
      } else {
        paint.color = const Color(0xFF1E222D);
        canvas.drawRRect(rrect, paint);
      }
    }
  }

  @override
  bool shouldRepaint(covariant _VuSegmentPainter oldDelegate) {
    return oldDelegate.level != level ||
        oldDelegate.peakHold != peakHold ||
        oldDelegate.isLive != isLive;
  }
}
