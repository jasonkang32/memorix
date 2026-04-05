import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/services/auth_service.dart';

enum AppLockState { checking, locked, unlocked }

class LockNotifier extends StateNotifier<AppLockState> {
  LockNotifier() : super(AppLockState.checking) {
    _init();
  }

  Future<void> _init() async {
    final locked = await _shouldLock();
    state = locked ? AppLockState.locked : AppLockState.unlocked;
  }

  /// PIN 설정 또는 생체인증 활성화 여부로 잠금 결정
  static Future<bool> _shouldLock() async {
    final hasPin = await AuthService.hasPin();
    if (hasPin) return true;
    // PIN 없이 생체인증만 활성화된 경우
    final biometricEnabled = await AuthService.isBiometricEnabled();
    if (biometricEnabled) {
      return await AuthService.canUseBiometric();
    }
    return false;
  }

  Future<bool> unlockWithBiometric() async {
    final ok = await AuthService.authenticate();
    if (ok) state = AppLockState.unlocked;
    return ok;
  }

  Future<bool> unlockWithPin(String pin) async {
    final ok = await AuthService.verifyPin(pin);
    if (ok) state = AppLockState.unlocked;
    return ok;
  }

  void unlock() => state = AppLockState.unlocked;
  void lock() => state = AppLockState.locked;
}

final lockProvider = StateNotifierProvider<LockNotifier, AppLockState>(
  (ref) => LockNotifier(),
);

// Personal Space 별도 잠금 여부 (하위 호환)
final personalLockedProvider = StateProvider<bool>((ref) => false);
