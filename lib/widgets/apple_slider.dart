import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class AppleSlider extends StatefulWidget {
  final String title;
  final String subtitle;
  final IconData icon;
  final double value;
  final double min;
  final double max;
  final String Function(double)? formatValue;
  final ValueChanged<double> onChanged;
  final Color activeColor;

  const AppleSlider({
    super.key,
    required this.title,
    this.subtitle = '',
    required this.icon,
    required this.value,
    this.min = 0.0,
    this.max = 1.0,
    this.formatValue,
    required this.onChanged,
    this.activeColor = const Color(0xFF00E5FF),
  });

  @override
  State<AppleSlider> createState() => _AppleSliderState();
}

class _AppleSliderState extends State<AppleSlider> {
  bool _isDragging = false;
  final GlobalKey _trackKey = GlobalKey();

  void _updateValueFromPosition(Offset localPosition) {
    final renderBox = _trackKey.currentContext?.findRenderObject() as RenderBox?;
    if (renderBox == null || !renderBox.hasSize) return;

    final totalWidth = renderBox.size.width;
    if (totalWidth <= 0) return;

    final double clampedX = localPosition.dx.clamp(0.0, totalWidth);
    final double fraction = clampedX / totalWidth;
    final double newValue = widget.min + fraction * (widget.max - widget.min);

    // Haptic feedback at center/1.0
    if ((newValue - 1.0).abs() < 0.03 && (widget.value - 1.0).abs() >= 0.03) {
      HapticFeedback.selectionClick();
    }

    widget.onChanged(newValue);
  }

  @override
  Widget build(BuildContext context) {
    final normalized = ((widget.value - widget.min) / (widget.max - widget.min)).clamp(0.0, 1.0);
    final displayValue = widget.formatValue != null
        ? widget.formatValue!(widget.value)
        : '${(normalized * 100).toInt()}%';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                Icon(
                  widget.icon,
                  size: 16,
                  color: Colors.white.withValues(alpha: 0.7),
                ),
                const SizedBox(width: 8),
                Text(
                  widget.title,
                  style: const TextStyle(
                    fontSize: 14,
                    fontWeight: FontWeight.w600,
                    letterSpacing: -0.2,
                    color: Colors.white,
                  ),
                ),
                if (widget.subtitle.isNotEmpty) ...[
                  const SizedBox(width: 6),
                  Text(
                    '• ${widget.subtitle}',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.white.withValues(alpha: 0.4),
                    ),
                  ),
                ],
              ],
            ),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
              decoration: BoxDecoration(
                color: Colors.white.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                displayValue,
                style: const TextStyle(
                  fontSize: 12,
                  fontFeatures: [FontFeature.tabularFigures()],
                  fontWeight: FontWeight.w700,
                  color: Colors.white,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),

        // Apple Control Center Pill Slider
        GestureDetector(
          onHorizontalDragStart: (details) {
            setState(() => _isDragging = true);
            HapticFeedback.lightImpact();
            _updateValueFromPosition(details.localPosition);
          },
          onHorizontalDragUpdate: (details) {
            _updateValueFromPosition(details.localPosition);
          },
          onHorizontalDragEnd: (_) {
            setState(() => _isDragging = false);
          },
          onTapDown: (details) {
            HapticFeedback.lightImpact();
            _updateValueFromPosition(details.localPosition);
          },
          child: AnimatedScale(
            scale: _isDragging ? 1.015 : 1.0,
            duration: const Duration(milliseconds: 120),
            child: Container(
              key: _trackKey,
              height: 44.0,
              decoration: BoxDecoration(
                color: const Color(0xFF1E212D),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: Colors.white.withValues(alpha: 0.08),
                  width: 0.8,
                ),
              ),
              child: ClipRRect(
                borderRadius: BorderRadius.circular(13),
                child: Stack(
                  children: [
                    // Filled Portion
                    FractionallySizedBox(
                      alignment: Alignment.centerLeft,
                      widthFactor: normalized,
                      heightFactor: 1.0,
                      child: Container(
                        decoration: BoxDecoration(
                          gradient: LinearGradient(
                            begin: Alignment.centerLeft,
                            end: Alignment.centerRight,
                            colors: [
                              widget.activeColor.withValues(alpha: 0.85),
                              widget.activeColor,
                            ],
                          ),
                          boxShadow: [
                            BoxShadow(
                              color: widget.activeColor.withValues(alpha: 0.3),
                              blurRadius: 10,
                              offset: const Offset(0, 0),
                            ),
                          ],
                        ),
                      ),
                    ),

                    // Slider Glint Marker
                    Align(
                      alignment: Alignment(-1.0 + (normalized * 2.0), 0.0),
                      child: Container(
                        width: 4,
                        margin: const EdgeInsets.symmetric(horizontal: 4),
                        height: 32,
                        decoration: BoxDecoration(
                          color: Colors.white.withValues(alpha: 0.9),
                          borderRadius: BorderRadius.circular(2),
                          boxShadow: [
                            BoxShadow(
                              color: Colors.black.withValues(alpha: 0.35),
                              blurRadius: 4,
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
