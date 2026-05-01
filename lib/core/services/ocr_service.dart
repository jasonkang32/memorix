import 'dart:async';
import 'dart:developer' as developer;

import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

class OcrService {
  static TextRecognizer? _recognizer;
  static bool _disabled = false;

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

  /// 이미지 파일에서 텍스트 추출.
  /// 초기화 포함 전체 8초 타임아웃. 실패 시 빈 문자열 반환.
  static Future<String> extractText(String imagePath) async {
    if (_disabled) return '';
    try {
      return await _extract(imagePath).timeout(const Duration(seconds: 8));
    } catch (e) {
      // 타임아웃 또는 오류 시 recognizer 리셋
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
