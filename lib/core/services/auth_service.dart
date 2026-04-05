import 'package:local_auth/local_auth.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

class AuthService {
  static final _auth = LocalAuthentication();
  static const _storage = FlutterSecureStorage();

  static const _pinKey = 'memorix_pin';
  static const _personalLockKey = 'memorix_personal_lock_enabled';
  static const _biometricEnabledKey = 'memorix_biometric_enabled';

  // ── 생체인증 기기 지원 ──────────────────────────────────────

  /// 기기가 생체인증을 지원하고 실제로 등록된 생체정보가 있는지
  static Future<bool> canUseBiometric() async {
    try {
      final supported = await _auth.isDeviceSupported();
      if (!supported) return false;
      final canCheck = await _auth.canCheckBiometrics;
      return canCheck;
    } catch (_) {
      return false;
    }
  }

  /// 등록된 생체인증 종류 목록 (fingerprint, face, iris …)
  static Future<List<BiometricType>> getAvailableBiometrics() async {
    try {
      return await _auth.getAvailableBiometrics();
    } catch (_) {
      return [];
    }
  }

  // ── 앱 내 생체인증 활성화 설정 ───────────────────────────────

  static Future<bool> isBiometricEnabled() async {
    final v = await _storage.read(key: _biometricEnabledKey);
    return v == '1';
  }

  static Future<void> setBiometricEnabled(bool enabled) =>
      _storage.write(key: _biometricEnabledKey, value: enabled ? '1' : '0');

  // ── 인증 실행 ────────────────────────────────────────────────

  static Future<bool> authenticate({String reason = '앱 잠금 해제'}) async {
    try {
      return await _auth.authenticate(
        localizedReason: reason,
        options: const AuthenticationOptions(
          stickyAuth: true,
          biometricOnly: false, // 생체인증 실패 시 기기 PIN/패턴 폴백 허용
        ),
      );
    } catch (_) {
      return false;
    }
  }

  static Future<bool> authenticateForPersonal() =>
      authenticate(reason: 'Personal Space 접근');

  // ── PIN ─────────────────────────────────────────────────────

  static Future<void> setPin(String pin) =>
      _storage.write(key: _pinKey, value: pin);

  static Future<String?> getPin() => _storage.read(key: _pinKey);

  static Future<bool> hasPin() async {
    final pin = await _storage.read(key: _pinKey);
    return pin != null && pin.isNotEmpty;
  }

  static Future<bool> verifyPin(String input) async {
    final stored = await getPin();
    return stored != null && stored == input;
  }

  // ── Personal Space 별도 잠금 ─────────────────────────────────

  static Future<void> setPersonalLockEnabled(bool enabled) =>
      _storage.write(key: _personalLockKey, value: enabled ? '1' : '0');

  static Future<bool> isPersonalLockEnabled() async {
    final v = await _storage.read(key: _personalLockKey);
    return v == '1';
  }
}
