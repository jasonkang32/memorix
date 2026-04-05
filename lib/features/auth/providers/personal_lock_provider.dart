import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/services/auth_service.dart';

/// Personal Space 별도 잠금 상태
/// true = 잠금 활성화 + 현재 잠긴 상태 → 인증 필요
/// false = 잠금 비활성화이거나 이미 인증됨
enum PersonalLockState { checking, locked, unlocked }

class PersonalLockNotifier extends StateNotifier<PersonalLockState> {
  PersonalLockNotifier() : super(PersonalLockState.checking) {
    _init();
  }

  Future<void> _init() async {
    final enabled = await AuthService.isPersonalLockEnabled();
    state = enabled ? PersonalLockState.locked : PersonalLockState.unlocked;
  }

  void lock() => state = PersonalLockState.locked;
  void unlock() => state = PersonalLockState.unlocked;

  Future<bool> tryUnlock() async {
    final ok = await AuthService.authenticateForPersonal();
    if (ok) state = PersonalLockState.unlocked;
    return ok;
  }
}

final personalLockProvider =
    StateNotifierProvider<PersonalLockNotifier, PersonalLockState>(
  (ref) => PersonalLockNotifier(),
);
