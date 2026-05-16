import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// QA 라운드: Work 탭은 MediaTimeline을 사용해야 한다.
///
/// 배경:
/// - 직전 라운드: MediaGrid (평탄 그리드, 월별 섹션) 채택 — 사용자 보고
///   "월별 갤러리뷰처럼 다 보임. 인스타 스타일 카드 리스트가 의도".
/// - 정답: MediaTimeline (같은 batchId 항목 = 카드 1개, 카드당 최대 3개 썸네일,
///   4+장은 "+N" 오버레이). work 등록 1건이 카드 1개로 묶임.
///
/// 가드 원칙:
/// - work_screen.dart에 MediaTimeline import/사용 있음.
/// - work_screen.dart에 MediaGrid 사용 없음.
/// - MediaTimeline의 `_dateKey`는 createdAt 기반 (Bug #3 회귀 가드).
void main() {
  late String workScreenSource;
  late String mediaTimelineSource;

  setUpAll(() {
    workScreenSource = File('lib/features/work/screens/work_screen.dart')
        .readAsStringSync();
    mediaTimelineSource = File('lib/shared/widgets/media_timeline.dart')
        .readAsStringSync();
  });

  group('Work 탭 — MediaTimeline 사용 invariant', () {
    test('work_screen.dart에 MediaTimeline import가 있다', () {
      expect(
        workScreenSource.contains('media_timeline.dart'),
        isTrue,
        reason:
            'work_screen이 MediaTimeline을 import하지 않는다. '
            'Work 탭은 인스타 스타일 카드 리스트(MediaTimeline)여야 한다.',
      );
    });

    test('work_screen.dart에 MediaTimeline(...) 호출이 있다', () {
      final pattern = RegExp(r'\bMediaTimeline\s*\(');
      expect(
        pattern.hasMatch(workScreenSource),
        isTrue,
        reason:
            'work_screen.dart에 MediaTimeline 호출이 없다. '
            'MediaGrid가 다시 살아났을 가능성 (회귀).',
      );
    });

    test('work_screen.dart에 MediaGrid 사용이 없다', () {
      final pattern = RegExp(r'\bMediaGrid\s*\(');
      expect(
        pattern.hasMatch(workScreenSource),
        isFalse,
        reason:
            'work_screen.dart에 MediaGrid 호출이 남아있다. '
            'Work 탭이 평탄 그리드로 회귀 (사용자 보고: "월별 갤러리뷰처럼 다 보임").',
      );
    });

    test('work_screen.dart에 media_grid.dart import가 없다', () {
      expect(
        workScreenSource.contains('media_grid.dart'),
        isFalse,
        reason: 'work_screen이 MediaGrid를 import한다 — 회귀.',
      );
    });

    test('MediaTimeline._dateKey가 createdAt 기반이다 (Bug #3 회귀 방지)', () {
      // _dateKey 메서드가 createdAt을 사용해야 함.
      // takenAt(EXIF) 사용 시 옛날 사진이 과거 섹션에 묻힘 — Bug #3 재발.
      expect(
        mediaTimelineSource.contains('item.createdAt'),
        isTrue,
        reason:
            'MediaTimeline이 createdAt을 안 쓴다. 옛날 사진 추가 시 과거 '
            '섹션에 묻혀 안 보인다 (Bug #3 재발).',
      );

      // _dateKey 함수 본문에 takenAt이 직접 쓰이면 안됨.
      // (헤더 시간 표시용 _dtFmt(DateTime.fromMillisecondsSinceEpoch(item.takenAt))는
      //  허용 — 그건 시간 라벨이지 섹션 키가 아님)
      final dateKeyMatch = RegExp(
        r'String\s+_dateKey\s*\([^)]*\)\s*=>[^;]+;',
      ).firstMatch(mediaTimelineSource);
      expect(
        dateKeyMatch,
        isNotNull,
        reason: '_dateKey 메서드를 찾지 못함 — 시그니처가 바뀌었나?',
      );
      expect(
        dateKeyMatch!.group(0)!.contains('takenAt'),
        isFalse,
        reason: '_dateKey 본문이 takenAt을 사용한다 — Bug #3 재발.',
      );
    });
  });
}
