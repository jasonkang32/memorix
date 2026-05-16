import 'package:flutter/material.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';

import '../screens/pin_setup_screen.dart';
import 'lock_session_manager.dart';

/// per-item lock 인증 서비스.
///
/// 세션이 이미 유효하면 즉시 통과. 그 외에는 생체 우선 → PIN 폴백.
/// PIN 미설정 시 PinSetupScreen으로 안내.
///
/// PIN 저장 형식은 기존 `AuthService`와 통일: 키 `memorix_pin`, 값은 평문 PIN.
/// (해싱 도입 시 두 서비스 모두 마이그레이션 필요 — 현재는 기존 동작 유지.)
class LockAuthService {
  LockAuthService({
    required this.session,
    LocalAuthentication? auth,
    FlutterSecureStorage? storage,
  }) : _auth = auth ?? LocalAuthentication(),
       _storage = storage ?? const FlutterSecureStorage();

  final LockSessionManager session;
  final LocalAuthentication _auth;
  final FlutterSecureStorage _storage;

  /// 통합 PIN 키 — `AuthService._pinKey`와 동일.
  /// 기존 PinSetupScreen이 저장한 PIN을 그대로 인증에 사용한다.
  static const _pinKey = 'memorix_pin';

  /// 인증 시도. 세션 유효 시 true 즉시 반환.
  /// 1) PIN 미설정 → PinSetupScreen 안내
  /// 2) 생체 가능 → 생체 시도, 실패 시 PIN 폴백
  /// 3) PIN 다이얼로그
  Future<bool> authenticate(BuildContext context) async {
    if (session.isUnlocked) return true;

    if (!await _hasPinSet()) {
      if (!context.mounted) return false;
      return _navigateToPinSetup(context);
    }

    if (await _hasBiometric()) {
      if (await _authBiometric()) {
        session.markUnlocked();
        return true;
      }
      // 생체 실패 → PIN 폴백
    }

    if (!context.mounted) return false;
    final ok = await _promptPin(context);
    if (ok) session.markUnlocked();
    return ok;
  }

  /// 외부에서 직접 PIN 설정 (PinSetupScreen 우회용).
  /// 기존 `AuthService.setPin`과 동일한 평문 저장 형식.
  Future<void> setPin(String pin) async {
    await _storage.write(key: _pinKey, value: pin);
  }

  /// PIN 존재 여부.
  Future<bool> hasPinSet() => _hasPinSet();

  // ── 내부 ──────────────────────────────────────────────────────

  Future<bool> _hasPinSet() async {
    final v = await _storage.read(key: _pinKey);
    return v != null && v.isNotEmpty;
  }

  Future<bool> _hasBiometric() async {
    try {
      final supported = await _auth.isDeviceSupported();
      if (!supported) return false;
      final canCheck = await _auth.canCheckBiometrics;
      if (!canCheck) return false;
      final available = await _auth.getAvailableBiometrics();
      return available.isNotEmpty;
    } on Exception {
      return false;
    }
  }

  Future<bool> _authBiometric() async {
    try {
      return await _auth.authenticate(
        localizedReason: '잠긴 항목 잠금 해제',
        options: const AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: false,
        ),
      );
    } on Exception {
      return false;
    }
  }

  Future<bool> _promptPin(BuildContext context) async {
    final stored = await _storage.read(key: _pinKey);
    if (stored == null || stored.isEmpty) return false;

    if (!context.mounted) return false;

    final result = await showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => _PinPromptDialog(expectedPin: stored),
    );
    return result ?? false;
  }

  Future<bool> _navigateToPinSetup(BuildContext context) async {
    if (!context.mounted) return false;

    final ok = await Navigator.of(context).push<bool>(
      MaterialPageRoute(
        builder: (_) => const PinSetupScreen(),
        fullscreenDialog: true,
      ),
    );

    // PinSetupScreen이 AuthService._pinKey('memorix_pin')에 저장하고
    // LockAuthService도 동일 키를 읽으므로 별도 미러링 불필요.
    // PinSetupScreen 자체가 두 번 확인을 거치므로 세션을 즉시 마크한다.
    if (ok == true) {
      session.markUnlocked();
      return true;
    }
    return false;
  }
}

/// PIN 입력 다이얼로그 — 6자리 숫자, 평문 비교.
/// (저장 형식이 평문이므로 비교도 평문. 추후 해싱 도입 시 [AuthService]와
/// 함께 마이그레이션 필요.)
class _PinPromptDialog extends StatefulWidget {
  const _PinPromptDialog({required this.expectedPin});

  final String expectedPin;

  @override
  State<_PinPromptDialog> createState() => _PinPromptDialogState();
}

class _PinPromptDialogState extends State<_PinPromptDialog> {
  final _controller = TextEditingController();
  String? _error;

  static const _pinLength = 6;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _onSubmit() {
    final pin = _controller.text.trim();
    if (pin.length != _pinLength) {
      setState(() => _error = 'PIN은 $_pinLength자리입니다.');
      return;
    }
    if (pin == widget.expectedPin) {
      Navigator.of(context).pop(true);
    } else {
      setState(() {
        _error = 'PIN이 일치하지 않습니다.';
        _controller.clear();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: const Text('PIN 입력'),
      content: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          TextField(
            controller: _controller,
            keyboardType: TextInputType.number,
            obscureText: true,
            maxLength: _pinLength,
            autofocus: true,
            onSubmitted: (_) => _onSubmit(),
            decoration: InputDecoration(
              counterText: '',
              hintText: '6자리 숫자',
              errorText: _error,
            ),
          ),
        ],
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: const Text('취소'),
        ),
        FilledButton(onPressed: _onSubmit, child: const Text('확인')),
      ],
    );
  }
}
