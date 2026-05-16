import 'package:fake_async/fake_async.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/features/auth/services/lock_session_manager.dart';

void main() {
  group('LockSessionManager', () {
    test('초기 상태는 잠금(미인증)이며 remainingSeconds는 0', () {
      // Arrange
      final session = LockSessionManager();

      // Assert
      expect(session.isUnlocked, isFalse);
      expect(session.remainingSeconds, 0);
    });

    test('markUnlocked 호출 후 isUnlocked는 true', () {
      // Arrange
      final session = LockSessionManager();

      // Act
      session.markUnlocked();

      // Assert
      expect(session.isUnlocked, isTrue);
    });

    test('markUnlocked 직후 remainingSeconds는 sessionDuration에 근접', () {
      // Arrange
      final session = LockSessionManager();

      // Act
      session.markUnlocked();

      // Assert — 약간의 처리 지연을 고려해 inSeconds-1 이상
      final expected = LockSessionManager.sessionDuration.inSeconds;
      expect(session.remainingSeconds, inInclusiveRange(expected - 1, expected));
    });

    test('sessionDuration 경과 시 isUnlocked는 false (fakeAsync)', () {
      fakeAsync((async) {
        // Arrange
        final session = LockSessionManager();
        session.markUnlocked();

        // Act — 경계 직전: 4분 59초 — 여전히 unlocked
        async.elapse(const Duration(minutes: 4, seconds: 59));
        expect(session.isUnlocked, isTrue);

        // Act — 5분 정확히 경과
        async.elapse(const Duration(seconds: 1));

        // Assert — 만료
        expect(session.isUnlocked, isFalse);
        expect(session.remainingSeconds, 0);
      });
    });

    test('invalidate 호출 후 isUnlocked는 false이고 remainingSeconds는 0', () {
      // Arrange
      final session = LockSessionManager();
      session.markUnlocked();
      expect(session.isUnlocked, isTrue);

      // Act
      session.invalidate();

      // Assert
      expect(session.isUnlocked, isFalse);
      expect(session.remainingSeconds, 0);
    });

    test('remainingSeconds는 시간 경과에 따라 감소 (fakeAsync)', () {
      fakeAsync((async) {
        // Arrange
        final session = LockSessionManager();
        session.markUnlocked();
        final initial = session.remainingSeconds;

        // Act
        async.elapse(const Duration(minutes: 2));

        // Assert — 약 2분(120초) 감소
        final after = session.remainingSeconds;
        expect(initial - after, inInclusiveRange(119, 121));
      });
    });

    test('만료 후 markUnlocked로 세션 갱신 가능 (fakeAsync)', () {
      fakeAsync((async) {
        // Arrange
        final session = LockSessionManager();
        session.markUnlocked();
        async.elapse(const Duration(minutes: 6));
        expect(session.isUnlocked, isFalse);

        // Act
        session.markUnlocked();

        // Assert
        expect(session.isUnlocked, isTrue);
        expect(session.remainingSeconds, greaterThan(0));
      });
    });
  });
}
