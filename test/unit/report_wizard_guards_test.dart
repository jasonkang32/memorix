import 'dart:io';
import 'package:flutter_test/flutter_test.dart';

/// Source-grep guards: report_screen.dart이 4-step wizard 구조로 작성되었는지 검증.
///
/// 사용자 요구: "1.사진검색, 2.사진추가, 3. 보고서 작성, 4.생성" — 4-step Stepper.
/// 코드 회귀 시 (예: 옛 단일 화면 폼으로 되돌아갈 때) 즉시 실패.
void main() {
  late final String src;

  setUpAll(() {
    final f = File('lib/features/work/screens/report_screen.dart');
    src = f.readAsStringSync();
  });

  group('Report wizard structure guards', () {
    test('uses Stepper widget (4-step horizontal wizard)', () {
      expect(
        src.contains('Stepper('),
        isTrue,
        reason: 'report_screen.dart은 Stepper 위젯을 사용해야 한다 (4-step wizard).',
      );
      expect(
        src.contains('StepperType.horizontal'),
        isTrue,
        reason: '진행 표시는 horizontal stepper로 한다.',
      );
    });

    test('declares all 4 step labels in Korean', () {
      expect(src.contains("'사진 검색'"), isTrue, reason: 'Step 1 라벨 누락');
      expect(src.contains("'사진 추가'"), isTrue, reason: 'Step 2 라벨 누락');
      expect(src.contains("'보고서 작성'"), isTrue, reason: 'Step 3 라벨 누락');
      expect(src.contains("'생성'"), isTrue, reason: 'Step 4 라벨 누락');
    });

    test('tracks current step state', () {
      expect(
        src.contains('_currentStep'),
        isTrue,
        reason: '_currentStep state를 통해 현재 step을 추적해야 한다.',
      );
      expect(
        src.contains('onStepContinue') && src.contains('onStepCancel'),
        isTrue,
        reason: 'Stepper는 다음/이전 콜백을 wire 해야 한다.',
      );
    });

    test('uses multi-select state for photos', () {
      expect(
        src.contains('_selectedIds'),
        isTrue,
        reason: 'Step 2의 multi-select는 _selectedIds (Set) 로 관리한다.',
      );
      expect(
        src.contains('Set<int>'),
        isTrue,
        reason: '_selectedIds는 정수 ID Set이어야 한다.',
      );
    });

    test('integrates with MediaDao.findWork excluding locked', () {
      expect(
        src.contains('findWork('),
        isTrue,
        reason: '기존 MediaDao.findWork를 재사용해야 한다.',
      );
      expect(
        src.contains('includeLocked: false'),
        isTrue,
        reason: '보고서는 외부 공유 가능성 → 잠긴 항목 제외 필수 (spec 8b).',
      );
    });

    test('preserves ReportService.generate call signature', () {
      // 보고서 생성 흐름은 그대로 유지: 같은 서비스 호출, 같은 파라미터.
      expect(
        src.contains('ReportService.generate('),
        isTrue,
        reason: 'PDF 생성은 ReportService.generate를 통해 수행한다.',
      );
      expect(
        src.contains('type: widget.reportType'),
        isTrue,
        reason: 'reportType 매개변수 그대로 전달.',
      );
      expect(
        src.contains('items:'),
        isTrue,
        reason: '선택된 items 리스트 전달.',
      );
    });

    test('blocks advancing from step 2 without selection', () {
      // 가드 함수 또는 동등한 패턴이 있어야 한다.
      expect(
        src.contains('_canAdvanceFromSelect') ||
            src.contains('_selectedIds.isNotEmpty'),
        isTrue,
        reason: '사진 0장이면 step 2 → 3 진행 불가 가드 필요.',
      );
    });

    test('blocks advancing from step 3 without title', () {
      expect(
        src.contains('_canAdvanceFromForm') ||
            src.contains('_titleCtrl.text.trim().isNotEmpty'),
        isTrue,
        reason: '제목 없이 step 3 → 4 진행 불가 가드 필요.',
      );
    });
  });
}
