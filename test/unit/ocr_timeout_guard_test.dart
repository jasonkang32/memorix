import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// OCR fix 회귀 가드.
///
/// 배경:
/// - OcrService 내부 timeout이 8초였음 → 첫 호출 콜드 스타트(ML Kit 한국어 모델
///   초기화)로 거의 항상 빈 문자열 반환 → 사용자 보고 "OCR 안 됨".
/// - `_disabled = true` 영구 차단 — 한 번 실패하면 앱 재시작까지 모든 OCR 죽음.
/// - media_save_service의 호출부 timeout이 OCR 내부보다 짧으면 OCR 자체가 무의미.
///
/// 가드 원칙:
/// - OcrService 내부 timeout >= 25초.
/// - `_disabled` 영구 setter 호출이 본문에 없어야 함 (선언은 있어도 OK, 사용 X).
/// - 호출부 _enhance의 OCR timeout >= OCR 내부 timeout (마진 포함).
void main() {
  late String ocrSource;
  late String saveSource;

  setUpAll(() {
    ocrSource = File('lib/core/services/ocr_service.dart').readAsStringSync();
    saveSource = File('lib/core/services/media_save_service.dart')
        .readAsStringSync();
  });

  group('OCR timeout invariant', () {
    test('OcrService 내부 timeout이 25초 이상', () {
      // `Duration(seconds: N)` 패턴 모두 추출
      final matches = RegExp(r'Duration\(seconds:\s*(\d+)\)')
          .allMatches(ocrSource)
          .map((m) => int.parse(m.group(1)!))
          .toList();
      expect(
        matches,
        isNotEmpty,
        reason: 'OcrService에 Duration(seconds:) 사용처가 없다 — 구조 변경됨?',
      );
      final maxSec = matches.reduce((a, b) => a > b ? a : b);
      expect(
        maxSec,
        greaterThanOrEqualTo(25),
        reason:
            'OcrService 내부 timeout이 ${maxSec}초로 너무 짧다. ML Kit 콜드 스타트 + '
            '큰 사진 처리에 25초 이상 필요. 자동 OCR이 침묵 실패한다.',
      );
    });

    test('OcrService에 _disabled 영구 setter (= true) 사용이 없다', () {
      // `_disabled = true` 같은 영구 차단 setter 패턴 검출
      final antipattern = RegExp(r'_disabled\s*=\s*true');
      expect(
        antipattern.hasMatch(ocrSource),
        isFalse,
        reason:
            '_disabled = true 영구 차단이 살아있다. 한 번 실패하면 앱 재시작까지 '
            '모든 OCR이 죽는다. flag 자체를 제거하거나 카운터 기반 쿨다운으로.',
      );
    });

    test('media_save_service의 _enhance OCR 호출 timeout이 30초 이상', () {
      // _enhance 본문에서 OcrService.extractText 다음 timeout 패턴
      final pattern = RegExp(
        r'OcrService\.extractText\([^)]*\)\s*\.timeout\(\s*const\s+Duration\(seconds:\s*(\d+)\)',
      );
      final match = pattern.firstMatch(saveSource);
      expect(
        match,
        isNotNull,
        reason: '_enhance에서 OcrService.extractText().timeout() 패턴을 찾지 못함',
      );
      final sec = int.parse(match!.group(1)!);
      expect(
        sec,
        greaterThanOrEqualTo(30),
        reason:
            '_enhance의 OCR 호출 timeout이 ${sec}초로 OCR 내부 timeout(>=25초)보다 '
            '짧으면 OCR 자체 timeout이 무의미해진다. OCR 내부 + 마진(5초)으로.',
      );
    });

    test('media_save_service의 _enhance 자체 timeout이 45초 이상', () {
      // `_enhance(...).timeout(const Duration(seconds: N))` 패턴
      final pattern = RegExp(
        r'_enhance\([^)]*\)[\s\n]*\.timeout\(\s*const\s+Duration\(seconds:\s*(\d+)\)',
        multiLine: true,
        dotAll: true,
      );
      final match = pattern.firstMatch(saveSource);
      expect(
        match,
        isNotNull,
        reason: '_enhance(...).timeout() 패턴을 찾지 못함',
      );
      final sec = int.parse(match!.group(1)!);
      expect(
        sec,
        greaterThanOrEqualTo(45),
        reason:
            '_enhance 전체 timeout이 ${sec}초로 OCR(30~35) + 태그(5)를 못 끝낸다. '
            '45초 이상 권장.',
      );
    });
  });
}
