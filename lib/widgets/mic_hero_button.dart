import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class MicHeroButton extends StatefulWidget {
  final bool isRunning;
  final bool isMuted;
  final VoidCallback onTap;

  const MicHeroButton({
    super.key,
    required this.isRunning,
    required this.isMuted,
    required this.onTap,
  });

  @override
  State<MicHeroButton> createState() => _MicHeroButtonState();
}

class _MicHeroButtonState extends State<MicHeroButton> with SingleTickerProviderStateMixin {
  late AnimationController _pulseController;
  late Animation<double> _pulseAnimation;
  bool _isPressed = false;

  @override
  void initState() {
    super.initState();
    _pulseController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
    );

    _pulseAnimation = Tween<double>(begin: 1.0, end: 1.15).animate(
      CurvedAnimation(parent: _pulseController, curve: Curves.easeInOut),
    );

    if (widget.isRunning && !widget.isMuted) {
      _pulseController.repeat(reverse: true);
    }
  }

  @override
  void didUpdateWidget(covariant MicHeroButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isRunning && !widget.isMuted) {
      if (!_pulseController.isAnimating) {
        _pulseController.repeat(reverse: true);
      }
    } else {
      _pulseController.stop();
      _pulseController.reset();
    }
  }

  @override
  void dispose() {
    _pulseController.dispose();
    super.dispose();
  }

  void _handleTap() {
    HapticFeedback.heavyImpact();
    widget.onTap();
  }

  @override
  Widget build(BuildContext context) {
    final isLive = widget.isRunning && !widget.isMuted;
    final isMutedState = widget.isRunning && widget.isMuted;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        GestureDetector(
          onTapDown: (_) => setState(() => _isPressed = true),
          onTapUp: (_) => setState(() => _isPressed = false),
          onTapCancel: () => setState(() => _isPressed = false),
          onTap: _handleTap,
          child: AnimatedBuilder(
            animation: _pulseAnimation,
            builder: (context, child) {
              final scale = _isPressed ? 0.94 : (isLive ? _pulseAnimation.value : 1.0);

              return Stack(
                alignment: Alignment.center,
                children: [
                  // Outer Glow Halo Ring
                  if (isLive)
                    Container(
                      width: 210 * scale,
                      height: 210 * scale,
                      decoration: BoxDecoration(
                        shape: BoxShape.circle,
                        boxShadow: [
                          BoxShadow(
                            color: const Color(0xFF00E5FF).withValues(alpha: 0.35),
                            blurRadius: 40,
                            spreadRadius: 8,
                          ),
                          BoxShadow(
                            color: const Color(0xFF7000FF).withValues(alpha: 0.25),
                            blurRadius: 60,
                            spreadRadius: 12,
                          ),
                        ],
                      ),
                    ),

                  // Main Interactive Button Disc
                  AnimatedContainer(
                    duration: const Duration(milliseconds: 300),
                    curve: Curves.easeOutCubic,
                    width: 175,
                    height: 175,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      gradient: isLive
                          ? const RadialGradient(
                              center: Alignment(-0.25, -0.35),
                              radius: 0.95,
                              colors: [
                                Color(0xFF4EEBFF),
                                Color(0xFF00B4D8),
                                Color(0xFF0077B6),
                              ],
                            )
                          : isMutedState
                              ? const RadialGradient(
                                  center: Alignment(-0.25, -0.35),
                                  radius: 0.95,
                                  colors: [
                                    Color(0xFFFF8577),
                                    Color(0xFFE63946),
                                    Color(0xFF9D0208),
                                  ],
                                )
                              : const RadialGradient(
                                  center: Alignment(-0.2, -0.3),
                                  radius: 0.9,
                                  colors: [
                                    Color(0xFF2C2F3E),
                                    Color(0xFF1E202B),
                                    Color(0xFF13151D),
                                  ],
                                ),
                      boxShadow: [
                        BoxShadow(
                          color: Colors.black.withValues(alpha: 0.6),
                          blurRadius: 28,
                          offset: const Offset(0, 14),
                        ),
                        if (isLive)
                          BoxShadow(
                            color: const Color(0xFF00E5FF).withValues(alpha: 0.45),
                            blurRadius: 20,
                            offset: const Offset(0, 0),
                          ),
                      ],
                      border: Border.all(
                        color: isLive
                            ? Colors.white.withValues(alpha: 0.8)
                            : Colors.white.withValues(alpha: 0.12),
                        width: isLive ? 2.2 : 1.2,
                      ),
                    ),
                    child: Stack(
                      alignment: Alignment.center,
                      children: [
                        // Subtle specular glass highlight
                        Positioned(
                          top: 14,
                          child: Container(
                            width: 100,
                            height: 35,
                            decoration: BoxDecoration(
                              borderRadius: BorderRadius.circular(20),
                              gradient: LinearGradient(
                                begin: Alignment.topCenter,
                                end: Alignment.bottomCenter,
                                colors: [
                                  Colors.white.withValues(alpha: isLive ? 0.45 : 0.15),
                                  Colors.white.withValues(alpha: 0.0),
                                ],
                              ),
                            ),
                          ),
                        ),

                        // Center Icon
                        Icon(
                          isMutedState
                              ? Icons.mic_off_rounded
                              : (isLive ? Icons.mic_rounded : Icons.mic_none_rounded),
                          size: 72,
                          color: isLive || isMutedState
                              ? Colors.white
                              : Colors.white.withValues(alpha: 0.65),
                        ),
                      ],
                    ),
                  ),
                ],
              );
            },
          ),
        ),
        const SizedBox(height: 20),

        // Status Typography Badge
        AnimatedSwitcher(
          duration: const Duration(milliseconds: 250),
          child: Column(
            key: ValueKey('$isLive-$isMutedState'),
            children: [
              Text(
                isLive
                    ? 'MIC LIVE'
                    : isMutedState
                        ? 'MIC MUTED'
                        : 'MIC OFF',
                style: TextStyle(
                  fontSize: 20,
                  fontWeight: FontWeight.w700,
                  letterSpacing: 2.0,
                  color: isLive
                      ? const Color(0xFF00E5FF)
                      : isMutedState
                          ? const Color(0xFFFF5252)
                          : Colors.white.withValues(alpha: 0.5),
                ),
              ),
              const SizedBox(height: 4),
              Text(
                isLive
                    ? 'Transmitting real-time audio'
                    : isMutedState
                        ? 'Auto-muted for feedback protection'
                        : 'Tap button to start karaoke routing',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.w400,
                  color: Colors.white.withValues(alpha: 0.45),
                  letterSpacing: -0.2,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
