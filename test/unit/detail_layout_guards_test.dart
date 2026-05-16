import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// UX 보강 회귀 가드 — `MediaDetailScreen` 5개 변경 invariant.
///
/// 사용자 의도:
///  1) AppBar 우상단 저장 아이콘 제거 → 하단 sticky bar로 이동
///  2) 신규 추가(`isNewlyAdded == true`) 시 [미디어 삭제] 버튼 숨김
///  3) OCR 텍스트는 기본 2줄만 표시, "더보기"로 펼침
///  4) 태그 입력은 이벤트 날짜 바로 아래에 위치 (검색 동선 단축)
///  5) 메모 입력은 1줄로 시작해 입력에 따라 height 자동 확장
void main() {
  late String detailScreenSource;
  late String workScreenSource;
  late String personalScreenSource;

  setUpAll(() {
    detailScreenSource = File('lib/shared/screens/media_detail_screen.dart')
        .readAsStringSync();
    workScreenSource = File('lib/features/work/screens/work_screen.dart')
        .readAsStringSync();
    personalScreenSource =
        File('lib/features/personal/screens/personal_screen.dart')
            .readAsStringSync();
  });

  group('MediaDetailScreen — UX 보강 invariants', () {
    test('생성자에 isNewlyAdded 매개변수가 있다', () {
      // `final bool isNewlyAdded;` 선언 + 생성자 초기화
      expect(
        RegExp(r'final\s+bool\s+isNewlyAdded\s*;').hasMatch(detailScreenSource),
        isTrue,
        reason:
            'MediaDetailScreen에 isNewlyAdded 필드가 없다. 신규/수정 모드 '
            '구분 불가 → [미디어 삭제] 버튼이 추가 직후에도 노출됨.',
      );
      expect(
        detailScreenSource.contains('this.isNewlyAdded'),
        isTrue,
        reason: '생성자에 this.isNewlyAdded 매개변수가 없다.',
      );
    });

    test('AppBar actions에서 저장 IconButton(Icons.check / Icons.save)이 제거됐다', () {
      // 본 AppBar는 lock 게이트 화면이 아닌 _isLocked 통과 후의 AppBar.
      // `_toggleLock`을 onPressed로 가지는 AppBar를 정확히 식별하려고
      // `_toggleLock` 토큰이 들어가는 AppBar 블록 전체를 추출.
      final appBarMatches = RegExp(
        r'appBar:\s*AppBar\(([\s\S]*?)\n\s{8}\),',
      ).allMatches(detailScreenSource).toList();
      // _toggleLock을 포함하는 AppBar 블록 — 본 AppBar.
      final mainAppBar = appBarMatches.firstWhere(
        (m) => m.group(1)!.contains('_toggleLock'),
        orElse: () => throw StateError('본 AppBar 블록을 찾지 못함'),
      );
      final appBarBlock = mainAppBar.group(1)!;

      expect(
        appBarBlock.contains('Icons.check'),
        isFalse,
        reason:
            'AppBar actions에 Icons.check(저장)가 여전히 있다. 하단 sticky '
            'bar로 이동해야 함 (A1).',
      );
      expect(
        appBarBlock.contains('Icons.save'),
        isFalse,
        reason: 'AppBar actions에 Icons.save 아이콘이 여전히 있다 (A1).',
      );
      // 자물쇠 토글은 유지돼야 함
      expect(
        appBarBlock.contains('Icons.lock'),
        isTrue,
        reason: '잠금 토글(Icons.lock_*)은 AppBar에서 유지돼야 함.',
      );
    });

    test('하단 sticky 액션 바(_BottomActionBar) 위젯이 존재하고 Scaffold가 사용한다', () {
      expect(
        detailScreenSource.contains('class _BottomActionBar'),
        isTrue,
        reason: '_BottomActionBar 위젯이 정의돼야 함 (A2).',
      );
      expect(
        RegExp(r'bottomNavigationBar:\s*_BottomActionBar\(')
            .hasMatch(detailScreenSource),
        isTrue,
        reason:
            'Scaffold.bottomNavigationBar로 _BottomActionBar를 sticky하게 '
            '사용해야 함 (A2).',
      );
      // isNewlyAdded → 삭제 버튼 분기
      expect(
        detailScreenSource.contains('isNewlyAdded'),
        isTrue,
        reason: '액션 바 분기에 isNewlyAdded가 사용돼야 함 (A3).',
      );
    });

    test('메모 TextField는 maxLines: null + minLines: 1 (auto-grow)', () {
      // 메모 컨트롤러를 사용하는 TextField 블록 추출.
      final notePattern = RegExp(
        r'TextField\(\s*controller:\s*_noteCtrl,([\s\S]{0,400}?)\)',
      );
      final match = notePattern.firstMatch(detailScreenSource);
      expect(
        match,
        isNotNull,
        reason: '_noteCtrl을 사용하는 TextField를 찾지 못함',
      );
      final block = match!.group(1)!;
      expect(
        RegExp(r'maxLines:\s*null').hasMatch(block),
        isTrue,
        reason:
            '메모 TextField에 maxLines: null이 없다. 입력 줄 수에 따른 '
            'height auto-grow가 동작하지 않음 (A6).',
      );
      expect(
        RegExp(r'minLines:\s*1').hasMatch(block),
        isTrue,
        reason: '메모 TextField에 minLines: 1이 없다 (A6).',
      );
      // 기존의 maxLines: 4 패턴이 남아있으면 안 됨
      expect(
        RegExp(r'maxLines:\s*4').hasMatch(block),
        isFalse,
        reason: '기존 maxLines: 4가 남아있다. auto-grow로 교체돼야 함 (A6).',
      );
    });

    test('OCR 섹션은 maxLines 2 + 더보기/접기 토글을 가진다', () {
      // _OcrSection은 StatefulWidget으로 변환되고 _expanded 상태를 가져야 함
      expect(
        detailScreenSource.contains('class _OcrSection extends StatefulWidget'),
        isTrue,
        reason: '_OcrSection이 StatefulWidget이어야 더보기 토글이 가능함 (A4).',
      );
      expect(
        detailScreenSource.contains('_expanded'),
        isTrue,
        reason: '_OcrSection에 _expanded 상태가 없다 (A4 더보기 토글).',
      );
      // 기본 2줄 maxLines 토큰
      expect(
        detailScreenSource.contains('_ocrCollapsedMaxLines'),
        isTrue,
        reason:
            'OCR 본문에 collapse 시 maxLines 2 상수가 보이지 않는다 (A4).',
      );
      // 더보기/접기 라벨
      final hasToggleLabel = detailScreenSource.contains("'더보기'") &&
          detailScreenSource.contains("'접기'");
      expect(
        hasToggleLabel,
        isTrue,
        reason: 'OCR 섹션에 "더보기"/"접기" 라벨이 없다 (A4).',
      );
    });

    test('태그 섹션이 이벤트 날짜(_EventDateRow) 바로 아래에 위치한다', () {
      // _EventDateRow 호출 위치 → 그 다음에 _TagSection이 처음으로 등장해야 함.
      final eventIdx = detailScreenSource.indexOf('_EventDateRow(');
      expect(
        eventIdx,
        greaterThan(0),
        reason: '_EventDateRow 호출을 찾지 못함',
      );
      final tagIdx = detailScreenSource.indexOf('_TagSection(', eventIdx);
      expect(
        tagIdx,
        greaterThan(eventIdx),
        reason:
            '_EventDateRow 이후 _TagSection 호출이 없다. 태그가 이벤트 '
            '날짜 아래로 이동되지 않음 (A5).',
      );

      // 위치 인접성 검사 — 두 호출 사이에 위치(_countryCtrl)나 메모(_noteCtrl)가
      // 끼어들면 안 됨 (태그가 이벤트 날짜 직후 섹션이어야 함).
      final between = detailScreenSource.substring(eventIdx, tagIdx);
      expect(
        between.contains('_noteCtrl'),
        isFalse,
        reason:
            '이벤트 날짜와 태그 섹션 사이에 메모 입력이 끼어들었다. 태그는 '
            '이벤트 날짜 직후여야 함 (A5).',
      );
      expect(
        between.contains('_countryCtrl'),
        isFalse,
        reason:
            '이벤트 날짜와 태그 섹션 사이에 위치 입력이 끼어들었다. 태그는 '
            '이벤트 날짜 직후여야 함 (A5).',
      );
    });
  });

  group('isNewlyAdded 호출처 invariants', () {
    test('Work _addMedia 흐름은 isNewlyAdded: true로 detail에 진입', () {
      // `MediaDetailScreen(items: savedItems, ..., isNewlyAdded: true,)`
      final pattern = RegExp(
        r'MediaDetailScreen\(\s*items:\s*savedItems[\s\S]{0,200}?isNewlyAdded:\s*true',
      );
      expect(
        pattern.hasMatch(workScreenSource),
        isTrue,
        reason:
            'work_screen.dart의 신규 추가 직후 detail 진입에 '
            'isNewlyAdded: true 전달이 빠졌다 (A3).',
      );
    });

    test('Personal _addMedia 흐름은 isNewlyAdded: true로 detail에 진입', () {
      final pattern = RegExp(
        r'MediaDetailScreen\(\s*items:\s*savedItems[\s\S]{0,200}?isNewlyAdded:\s*true',
      );
      expect(
        pattern.hasMatch(personalScreenSource),
        isTrue,
        reason:
            'personal_screen.dart의 신규 추가 직후 detail 진입에 '
            'isNewlyAdded: true 전달이 빠졌다 (A3).',
      );
    });
  });
}
