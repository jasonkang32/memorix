import 'dart:async';
import 'dart:developer' as developer;

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

class OcrService {
  static TextRecognizer? _recognizer;

  static TextRecognizer? _getRecognizer() {
    if (_recognizer != null) return _recognizer;
    try {
      _recognizer = TextRecognizer(script: TextRecognitionScript.korean);
      return _recognizer;
    } catch (e, st) {
      developer.log(
        'OCR Korean 초기화 실패, Latin 폴백 시도: $e',
        error: e,
        stackTrace: st,
        name: 'memorix.ocr',
      );
    }
    try {
      _recognizer = TextRecognizer(script: TextRecognitionScript.latin);
      return _recognizer;
    } catch (e, st) {
      developer.log(
        'OCR Latin 초기화 실패: $e',
        error: e,
        stackTrace: st,
        name: 'memorix.ocr',
      );
      // 영구 비활성화 금지 — 일시 실패가 세션 전체를 죽여서는 안 된다.
      // 다음 호출 시 재시도 가능. 실패 자체는 빈 문자열 반환으로 방어됨.
      return null;
    }
  }

  /// 이미지 파일에서 텍스트 추출.
  ///
  /// 30초 타임아웃 — 첫 호출은 ML Kit 한국어 모델 초기화 + recognizer 워밍업으로
  /// 8초가 빠듯했다. 30초로 늘려 콜드 스타트 + 큰 사진 처리 여유 확보.
  /// 실패 시 빈 문자열 반환.
  static Future<String> extractText(String imagePath) async {
    try {
      return await _extract(imagePath).timeout(const Duration(seconds: 30));
    } catch (e) {
      // 타임아웃 또는 오류 시 recognizer 리셋 (다음 호출 시 재생성)
      try {
        _recognizer?.close();
      } catch (_) {}
      _recognizer = null;
      developer.log('OCR 실패 (타임아웃 포함): $e', name: 'memorix.ocr');
      return '';
    }
  }

  static Future<String> _extract(String imagePath) async {
    // 동기 작업(ML Kit 초기화, 파일 로딩) 전에 이벤트 루프에 양보
    await Future<void>.delayed(Duration.zero);

    final recognizer = _getRecognizer();
    if (recognizer == null) return '';

    final inputImage = InputImage.fromFilePath(imagePath);
    final recognized = await recognizer.processImage(inputImage);
    final text = recognized.text.trim();
    return text.length >= 2 ? text : '';
  }

  static void close() {
    try {
      _recognizer?.close();
    } catch (_) {}
    _recognizer = null;
  }
}
