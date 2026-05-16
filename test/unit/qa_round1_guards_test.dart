import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// QA 라운드 1 회귀 가드:
/// - Bug #2: "잠금?" 다이얼로그 제거 — 카드/썸네일에 자물쇠 토글 버튼으로 대체
/// - Bug #3: "원본 삭제?" 다이얼로그 제거 — capture sheet에 "원본 삭제" toggle (default OFF)
void main() {
  late String workScreenSource;
  late String personalScreenSource;
  late String mediaTimelineSource;
  late String captureSheetSource;

  setUpAll(() {
    workScreenSource = File(
      'lib/features/work/screens/work_screen.dart',
    ).readAsStringSync();
    personalScreenSource = File(
      'lib/features/personal/screens/personal_screen.dart',
    ).readAsStringSync();
    mediaTimelineSource = File(
      'lib/shared/widgets/media_timeline.dart',
    ).readAsStringSync();
    captureSheetSource = File(
      'lib/shared/widgets/capture_bottom_sheet.dart',
    ).readAsStringSync();
  });

  group('Bug #2 — 잠금 다이얼로그 제거', () {
    test('work_screen.dart에 offerLockAfterAdd 호출이 없다', () {
      final pattern = RegExp(r'\bofferLockAfterAdd\s*\(');
      expect(
        pattern.hasMatch(workScreenSource),
        isFalse,
        reason:
            'work_screen이 미디어 추가 후 offerLockAfterAdd 다이얼로그를 띄운다. '
            'QA 보고: "잠금?" 다이얼로그 제거 — 카드 자물쇠 버튼으로 대체.',
      );
    });

    test('personal_screen.dart에 offerLockAfterAdd 호출이 없다', () {
      final pattern = RegExp(r'\bofferLockAfterAdd\s*\(');
      expect(
        pattern.hasMatch(personalScreenSource),
        isFalse,
        reason:
            'personal_screen이 미디어 추가 후 offerLockAfterAdd 다이얼로그를 띄운다. '
            'QA 보고: "잠금?" 다이얼로그 제거.',
      );
    });

    test('MediaTimeline에 자물쇠 토글 IconButton 토큰이 있다', () {
      // 자물쇠 토글 버튼은 lock_rounded / lock_open_rounded 두 아이콘을 모두 사용.
      expect(
        mediaTimelineSource.contains('Icons.lock_rounded'),
        isTrue,
        reason:
            'MediaTimeline에 Icons.lock_rounded가 없다 — 자물쇠 버튼 누락.',
      );
      expect(
        mediaTimelineSource.contains('Icons.lock_open_rounded'),
        isTrue,
        reason:
            'MediaTimeline에 Icons.lock_open_rounded가 없다 — 잠금 해제 상태 '
            '아이콘 누락.',
      );
    });

    test('MediaTimeline에 onLockToggle 콜백 매개변수가 있다', () {
      expect(
        mediaTimelineSource.contains('onLockToggle'),
        isTrue,
        reason:
            'MediaTimeline.onLockToggle 매개변수가 없다 — 카드에서 호출처로 '
            '잠금 토글을 위임할 방법 없음.',
      );
    });

    test('work_screen이 MediaTimeline에 onLockToggle을 전달한다', () {
      expect(
        workScreenSource.contains('onLockToggle'),
        isTrue,
        reason:
            'work_screen이 MediaTimeline에 onLockToggle 콜백을 전달하지 않음. '
            '카드 자물쇠 버튼이 동작하지 않는다.',
      );
    });

    test('personal_screen이 MediaTimeline에 onLockToggle을 전달한다', () {
      expect(
        personalScreenSource.contains('onLockToggle'),
        isTrue,
        reason:
            'personal_screen이 MediaTimeline에 onLockToggle 콜백을 전달하지 '
            '않음. 카드 자물쇠 버튼이 동작하지 않는다.',
      );
    });
  });

  group('Bug #3 — 원본 삭제 다이얼로그 제거 + capture sheet toggle', () {
    test('work_screen.dart에 _offerDeleteOriginals 호출이 없다', () {
      final pattern = RegExp(r'\b_offerDeleteOriginals\s*\(');
      expect(
        pattern.hasMatch(workScreenSource),
        isFalse,
        reason:
            'work_screen이 _offerDeleteOriginals 다이얼로그를 호출한다. '
            'QA 보고: "원본 삭제?" 다이얼로그 제거 — capture sheet toggle로 대체.',
      );
    });

    test('personal_screen.dart에 _offerDeleteOriginals 호출이 없다', () {
      final pattern = RegExp(r'\b_offerDeleteOriginals\s*\(');
      expect(
        pattern.hasMatch(personalScreenSource),
        isFalse,
        reason:
            'personal_screen이 _offerDeleteOriginals 다이얼로그를 호출한다. '
            'QA 보고: "원본 삭제?" 다이얼로그 제거.',
      );
    });

    test('CaptureBottomSheet에 Switch 위젯이 있다', () {
      expect(
        captureSheetSource.contains('Switch('),
        isTrue,
        reason:
            'CaptureBottomSheet에 Switch가 없다 — "원본 삭제" toggle 누락.',
      );
    });

    test('CaptureBottomSheet에 deleteOriginal 상태가 있다', () {
      expect(
        captureSheetSource.contains('deleteOriginal'),
        isTrue,
        reason:
            'CaptureBottomSheet에 deleteOriginal 토큰이 없다 — toggle 상태 누락.',
      );
    });

    test('CaptureBottomSheet에 "원본 삭제" 라벨이 있다', () {
      expect(
        captureSheetSource.contains('원본 삭제'),
        isTrue,
        reason:
            'CaptureBottomSheet에 "원본 삭제" 라벨이 없다 — toggle UI 누락.',
      );
    });

    test('CaptureBottomSheet의 deleteOriginal default가 false다', () {
      // bool _deleteOriginal = false; 패턴
      final pattern = RegExp(r'_deleteOriginal\s*=\s*false');
      expect(
        pattern.hasMatch(captureSheetSource),
        isTrue,
        reason:
            '_deleteOriginal 기본값이 false가 아니다. '
            'QA 명세: "default OFF — 사용자가 명시 ON 했을 때만 실행".',
      );
    });

    test('CaptureBottomSheet.show가 CaptureSheetResult를 반환한다', () {
      // 시그니처에서 record 형태 반환 검증
      expect(
        captureSheetSource.contains('CaptureSheetResult'),
        isTrue,
        reason:
            'CaptureBottomSheet.show가 CaptureSheetResult를 반환하지 않는다. '
            'capturedList + deleteOriginal을 함께 호출처로 넘길 방법 없음.',
      );
    });

    test('work_screen이 captureResult.deleteOriginal을 분해한다', () {
      expect(
        workScreenSource.contains('deleteOriginal'),
        isTrue,
        reason:
            'work_screen이 deleteOriginal 플래그를 사용하지 않는다. '
            'capture sheet toggle 결과를 무시하고 있음.',
      );
    });

    test('personal_screen이 captureResult.deleteOriginal을 분해한다', () {
      expect(
        personalScreenSource.contains('deleteOriginal'),
        isTrue,
        reason:
            'personal_screen이 deleteOriginal 플래그를 사용하지 않는다. '
            'capture sheet toggle 결과를 무시하고 있음.',
      );
    });
  });
}
