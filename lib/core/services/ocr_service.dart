import 'dart:developer' as developer;

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

class OcrService {
  static TextRecognizer? _recognizer;
  static bool _disabled = false;

  /// 지연 초기화 — Korean 스크립트 로드 실패 시 Latin 으로 폴백,
  /// 그마저도 실패하면 비활성화하여 이후 호출은 즉시 빈 문자열 반환
  static TextRecognizer? _getRecognizer() {
    if (_disabled) return null;
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
        'OCR Latin 초기화 실패 — OCR 비활성화: $e',
        error: e,
        stackTrace: st,
        name: 'memorix.ocr',
      );
      _disabled = true;
      return null;
    }
  }

  /// 이미지 파일에서 텍스트를 추출하여 반환
  /// 실패·미지원 시 빈 문자열 반환 (앱 크래시 없음)
  static Future<String> extractText(String imagePath) async {
    final recognizer = _getRecognizer();
    if (recognizer == null) return '';
    try {
      final inputImage = InputImage.fromFilePath(imagePath);
      final recognized = await recognizer.processImage(inputImage);
      final text = recognized.text.trim();
      return text.length >= 2 ? text : '';
    } catch (e, st) {
      developer.log(
        'OCR processImage 실패: $e',
        error: e,
        stackTrace: st,
        name: 'memorix.ocr',
      );
      return '';
    }
  }

  static void close() {
    try {
      _recognizer?.close();
    } catch (_) {}
    _recognizer = null;
  }
}
