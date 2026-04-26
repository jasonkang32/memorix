import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/services/auth_service.dart';
import '../../../core/services/secret_vault_service.dart';

/// Secret 영역은 매 진입마다 인증을 요구한다.
///
/// - 백그라운드 30초 이상 머물면 자동 잠금
/// - 다른 탭으로 이동만 해도 잠금 (보수적)
/// - 잠금 시 임시 복호화 디렉터리 제거
enum SecretLockState { locked, unlocked }

class SecretLockNotifier extends StateNotifier<SecretLockState> {
  SecretLockNotifier() : super(SecretLockState.locked);

  /// 잠금 해제 시도. 생체인증 또는 기기 PIN 폴백.
  Future<bool> tryUnlock() async {
    final ok = await AuthService.authenticate(reason: 'Secret 보관함 접근');
    if (ok) state = SecretLockState.unlocked;
    return ok;
  }

  /// 명시적 잠금 + 평문 임시 파일 정리.
  Future<void> lock() async {
    state = SecretLockState.locked;
    await SecretVaultService.purgeTempDecrypted();
  }
}

final secretLockProvider =
    StateNotifierProvider<SecretLockNotifier, SecretLockState>(
      (ref) => SecretLockNotifier(),
    );
