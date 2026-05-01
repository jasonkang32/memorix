import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:local_auth/local_auth.dart';
import '../providers/lock_provider.dart';
import '../../../core/services/auth_service.dart';

class LockScreen extends ConsumerStatefulWidget {
  final bool isPersonalLock;
  final VoidCallback onUnlocked;

  const LockScreen({
    super.key,
    this.isPersonalLock = false,
    required this.onUnlocked,
  });

  @override
  ConsumerState<LockScreen> createState() => _LockScreenState();
}

class _LockScreenState extends ConsumerState<LockScreen> {
  final List<String> _digits = [];
  String? _errorMsg;
  bool _biometricReady = false; // 기기 지원 + 등록 + 활성화
  List<BiometricType> _biometricTypes = [];
  bool _hasPin = false;

  static const _pinLength = 6;

  @override
  void initState() {
    super.initState();
    _init();
  }

  Future<void> _init() async {
    final hasPin = await AuthService.hasPin();
    final canUse = await AuthService.canUseBiometric();
    final enabled = await AuthService.isBiometricEnabled();
    final types = canUse
        ? await AuthService.getAvailableBiometrics()
        : <BiometricType>[];

    if (!mounted) return;
    setState(() {
      _hasPin = hasPin;
      _biometricReady = canUse && (enabled || !hasPin); // PIN 없으면 자동 활성화
      _biometricTypes = types;
    });

    // 생체인증 준비됐으면 자동 시도
    if (_biometricReady) _tryBiometric();
  }

  Future<void> _tryBiometric() async {
    final reason = widget.isPersonalLock ? 'Personal Space 접근' : '앱 잠금 해제';
    final ok = await AuthService.authenticate(reason: reason);
    if (ok && mounted) widget.onUnlocked();
  }

  void _onDigit(String d) {
    if (_digits.length >= _pinLength) return;
    setState(() {
      _digits.add(d);
      _errorMsg = null;
    });
    if (_digits.length == _pinLength) _verifyPin();
  }

  void _onDelete() {
    if (_digits.isEmpty) return;
    setState(() => _digits.removeLast());
  }

  Future<void> _verifyPin() async {
    final input = _digits.join();
    final ok = await AuthService.verifyPin(input);
    if (ok) {
      if (!widget.isPersonalLock) {
        ref.read(lockProvider.notifier).unlockWithPin(input);
      }
      if (mounted) widget.onUnlocked();
    } else {
      setState(() {
        _digits.clear();
        _errorMsg = 'PIN이 올바르지 않습니다';
      });
    }
  }

  IconData get _biometricIcon {
    if (_biometricTypes.contains(BiometricType.face)) {
      return Icons.face_outlined;
    }
    if (_biometricTypes.contains(BiometricType.iris)) {
      return Icons.remove_red_eye_outlined;
    }
    return Icons.fingerprint;
  }

  String get _biometricLabel {
    if (_biometricTypes.contains(BiometricType.face)) return 'Face ID';
    if (_biometricTypes.contains(BiometricType.iris)) return '홍채인식';
    return '지문인식';
  }

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;

    return Scaffold(
      backgroundColor: cs.surface,
      body: SafeArea(
        child: Column(
          children: [
            const Spacer(),

            // 아이콘
            Container(
              width: 72,
              height: 72,
              decoration: BoxDecoration(
                gradient: LinearGradient(
                  colors: widget.isPersonalLock
                      ? [const Color(0xFFFF6B9D), const Color(0xFF7B61FF)]
                      : [const Color(0xFF00C896), const Color(0xFF1A73E8)],
                ),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Icon(
                widget.isPersonalLock
                    ? Icons.home_outlined
                    : Icons.lock_outline,
                size: 36,
                color: Colors.white,
              ),
            ),
            const SizedBox(height: 20),

            // 타이틀
            Text(
              widget.isPersonalLock ? 'Personal Space' : 'Memorix',
              style: Theme.of(
                context,
              ).textTheme.headlineSmall?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              _hasPin ? 'PIN을 입력하세요' : '$_biometricLabel으로 잠금을 해제하세요',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.6),
              ),
            ),
            const SizedBox(height: 40),

            // PIN 도트 (PIN 설정된 경우만)
            if (_hasPin) ...[
              Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: List.generate(_pinLength, (i) {
                  final filled = i < _digits.length;
                  return AnimatedContainer(
                    duration: const Duration(milliseconds: 150),
                    margin: const EdgeInsets.symmetric(horizontal: 8),
                    width: 16,
                    height: 16,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: filled
                          ? cs.primary
                          : Theme.of(context).dividerColor,
                      border: Border.all(
                        color: filled
                            ? cs.primary
                            : Theme.of(
                                context,
                              ).colorScheme.onSurface.withValues(alpha: 0.4),
                      ),
                    ),
                  );
                }),
              ),
              const SizedBox(height: 12),
              SizedBox(
                height: 20,
                child: _errorMsg != null
                    ? Text(
                        _errorMsg!,
                        style: const TextStyle(color: Colors.red, fontSize: 13),
                      )
                    : null,
              ),
            ],

            const Spacer(),

            // 키패드 (PIN 설정된 경우만)
            if (_hasPin) _Keypad(onDigit: _onDigit, onDelete: _onDelete),

            // 생체인증 버튼
            if (_biometricReady) ...[
              const SizedBox(height: 16),
              _BiometricButton(
                icon: _biometricIcon,
                label: _biometricLabel,
                onTap: _tryBiometric,
              ),
            ],

            const SizedBox(height: 32),
          ],
        ),
      ),
    );
  }
}

// ── 키패드 ───────────────────────────────────────────────────

class _Keypad extends StatelessWidget {
  final ValueChanged<String> onDigit;
  final VoidCallback onDelete;

  const _Keypad({required this.onDigit, required this.onDelete});

  static const _layout = [
    ['1', '2', '3'],
    ['4', '5', '6'],
    ['7', '8', '9'],
    ['', '0', '⌫'],
  ];

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 48),
      child: Column(
        children: _layout.map((row) {
          return Row(
            children: row.map((key) {
              if (key.isEmpty) return const Expanded(child: SizedBox());
              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.all(6),
                  child: AspectRatio(
                    aspectRatio: 1.6,
                    child: TextButton(
                      style: TextButton.styleFrom(
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(12),
                        ),
                        backgroundColor: Theme.of(
                          context,
                        ).colorScheme.surfaceContainerHighest,
                      ),
                      onPressed: () => key == '⌫' ? onDelete() : onDigit(key),
                      child: Text(
                        key,
                        style: Theme.of(context).textTheme.titleLarge,
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          );
        }).toList(),
      ),
    );
  }
}

// ── 생체인증 버튼 ─────────────────────────────────────────────

class _BiometricButton extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _BiometricButton({
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final cs = Theme.of(context).colorScheme;
    return GestureDetector(
      onTap: onTap,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: BoxDecoration(
              color: cs.primary.withValues(alpha: 0.1),
              shape: BoxShape.circle,
              border: Border.all(
                color: cs.primary.withValues(alpha: 0.3),
                width: 1.5,
              ),
            ),
            child: Icon(icon, size: 32, color: cs.primary),
          ),
          const SizedBox(height: 8),
          Text(
            label,
            style: TextStyle(
              fontSize: 12,
              color: cs.primary,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}
