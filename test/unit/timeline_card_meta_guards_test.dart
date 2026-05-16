import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// 타임라인 카드 메타 표시 가드 (Phase B — Work 카드 메타 정리).
///
/// 사용자 요청:
/// - 푸터의 "사진 N장 / 동기화 대기 N개" 같은 메타 텍스트 제거
/// - 동기화 상태는 카드 헤더에 아이콘으로 표시 (자물쇠 토글 옆)
/// - 태그 wrap은 그대로 유지
///
/// 회귀 위험:
/// - 누군가가 푸터의 _TypeBadge 또는 "동기화 대기" 텍스트를 다시 살릴 수 있다.
/// - 누군가가 헤더의 cloud 아이콘 분기를 제거할 수 있다.
/// - 태그 표시가 사라질 수 있다.
void main() {
  late String source;
  late String headerBody;
  late String footerBody;

  setUpAll(() {
    // CRLF 정규화 — Windows 체크아웃에서도 시그니처 매칭이 안정적이도록.
    source = File('lib/shared/widgets/media_timeline.dart')
        .readAsStringSync()
        .replaceAll('\r\n', '\n');
    headerBody = _extractMethodBody(
      source,
      '_buildHeader(\n    BuildContext context,\n    MediaItem item,\n    bool isWork,\n    int count,\n  )',
    );
    footerBody = _extractMethodBody(
      source,
      '_buildFooter(BuildContext context, MediaItem item, bool isDark)',
    );
  });

  group('_TimelineCard 헤더 — 동기화 상태 아이콘 invariant', () {
    test('헤더 본문이 _buildSyncStatusIcon 헬퍼를 호출한다', () {
      expect(
        headerBody.contains('_buildSyncStatusIcon'),
        isTrue,
        reason:
            '헤더에서 동기화 상태 아이콘을 그리는 헬퍼 호출이 사라졌다. '
            '사용자 요청: 동기화 유무는 카드 상단 아이콘으로 표시.',
      );
    });

    test('헬퍼 정의가 cloud_upload_outlined와 cloud_done_outlined 모두 다룬다', () {
      // 헬퍼 자체는 _buildHeader 바깥에 있어 source 전체에서 검사한다.
      expect(
        source.contains('cloud_upload_outlined'),
        isTrue,
        reason: '미동기화(driveSynced==0) 표시용 cloud_upload_outlined 아이콘이 사라졌다.',
      );
      expect(
        source.contains('cloud_done_outlined'),
        isTrue,
        reason: '동기화 완료 표시용 cloud_done_outlined 아이콘이 사라졌다.',
      );
    });

    test('헤더에 자물쇠 토글 IconButton이 보존되어 있다', () {
      // Phase 4의 onLockToggle 코드 보호 — 동기화 아이콘 추가가 자물쇠를 깨뜨리면 안 된다.
      expect(
        headerBody.contains('onLockToggle'),
        isTrue,
        reason: '자물쇠 토글 호출이 헤더에서 사라졌다 (Phase 3C 회귀).',
      );
      expect(
        headerBody.contains('lock_rounded') &&
            headerBody.contains('lock_open_rounded'),
        isTrue,
        reason: '잠금 상태별 아이콘이 헤더에서 사라졌다.',
      );
    });
  });

  group('_TimelineCard 푸터 — 메타 텍스트 부재 invariant', () {
    test('푸터 본문이 "동기화 대기" 텍스트를 직접 표시하지 않는다', () {
      expect(
        footerBody.contains("'동기화 대기'") || footerBody.contains('"동기화 대기"'),
        isFalse,
        reason: '푸터에 "동기화 대기" 텍스트가 남아있다. 동기화 상태는 헤더 아이콘으로 이동했다.',
      );
    });

    test('푸터 본문이 _TypeBadge 같은 "사진/영상/문서 N장" 메타 표시를 더 이상 그리지 않는다', () {
      expect(
        footerBody.contains('_TypeBadge'),
        isFalse,
        reason: '푸터에 _TypeBadge가 남아있다. 사용자 요청: 메타 텍스트 제거.',
      );
      expect(
        footerBody.contains('cloud_upload_outlined'),
        isFalse,
        reason: '푸터에 cloud_upload_outlined가 남아있다. 동기화 표시는 헤더로 이동했다.',
      );
    });

    test('푸터 본문이 태그 wrap (_TagChipDisplay)을 여전히 표시한다', () {
      expect(
        footerBody.contains('_TagChipDisplay'),
        isTrue,
        reason: '푸터에서 태그 칩 (_TagChipDisplay) 호출이 사라졌다. 사용자 요청: 태그를 표시하라.',
      );
      expect(
        footerBody.contains('Wrap('),
        isTrue,
        reason: '푸터의 태그 Wrap 위젯이 사라졌다. 태그 표시 유지 필요.',
      );
    });

    test('푸터 본문이 메모(item.note) 표시를 보존한다', () {
      // 메모는 변경 대상 아님 — 회귀 방지.
      expect(
        footerBody.contains('item.note') || footerBody.contains('note.trim'),
        isTrue,
        reason: '푸터의 메모 표시 로직이 사라졌다.',
      );
    });
  });
}

/// 짧은 시그니처로 메서드 본문 추출. expression-bodied (`=>`)와 block (`{}`) 둘 다 지원.
/// (media_timeline_section_key_guard_test.dart의 동일 헬퍼와 일치.)
String _extractMethodBody(String source, String signaturePrefix) {
  final start = source.indexOf(signaturePrefix);
  if (start < 0) {
    throw StateError('Signature not found: $signaturePrefix');
  }

  final after = source.substring(start);
  final exprMatch = RegExp(r'\)\s*=>').firstMatch(after);
  final blockMatch = RegExp(
    r'\)\s*(?:async\*?\s*|sync\*?\s*)?\{',
  ).firstMatch(after);

  if (exprMatch != null &&
      (blockMatch == null || exprMatch.start < blockMatch.start)) {
    final bodyStart = start + exprMatch.end;
    final endIdx = source.indexOf(';', bodyStart);
    if (endIdx < 0) throw StateError('No expression terminator');
    return source.substring(bodyStart, endIdx);
  }

  if (blockMatch == null) {
    throw StateError('Body opening not found for $signaturePrefix');
  }
  final braceStart = start + blockMatch.end - 1;

  var depth = 0;
  for (var i = braceStart; i < source.length; i++) {
    final c = source[i];
    if (c == '{') depth++;
    if (c == '}') {
      depth--;
      if (depth == 0) {
        return source.substring(braceStart, i + 1);
      }
    }
  }
  throw StateError('Unbalanced braces for $signaturePrefix');
}
