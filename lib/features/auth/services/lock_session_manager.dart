import 'package:clock/clock.dart';

/// 5분 단위 잠금 세션 매니저.
///
/// 한 번 인증 후 [sessionDuration] 동안 모든 잠긴 항목 자유 접근.
/// 앱 백그라운드 진입 시 [invalidate] 호출로 즉시 만료.
///
/// 시간 추상화는 [clock] 패키지를 사용 — 테스트에서 `fakeAsync`로 가짜 시간 주입 가능.
class LockSessionManager {
  static const sessionDuration = Duration(minutes: 5);

  DateTime? _unlockedAt;

  bool get isUnlocked =>
      _unlockedAt != null &&
      clock.now().difference(_unlockedAt!) < sessionDuration;

  void markUnlocked() => _unlockedAt = clock.now();

  void invalidate() => _unlockedAt = null;

  /// 테스트/디버그용 — 남은 세션 시간(초). 만료 또는 미인증이면 0.
  int get remainingSeconds {
    final start = _unlockedAt;
    if (start == null) return 0;
    final elapsed = clock.now().difference(start);
    final remaining = sessionDuration - elapsed;
    return remaining.isNegative ? 0 : remaining.inSeconds;
  }
}
