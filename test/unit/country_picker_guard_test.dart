import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

/// Country picker 통합 가드 — 사용자 요청 ("국가는 picker로, 지역은 텍스트 유지").
///
/// 회귀 방지 invariant:
/// - country_picker 의존성이 pubspec.yaml에 존재.
/// - CountryDefaultService 파일이 존재 + fallback chain 핵심 토큰 보유.
/// - MediaDao에 findMostRecentCountryCode 시그니처 존재.
/// - media_detail_screen에 country_picker import + showCountryPicker 호출 존재.
/// - 지역(region) 입력은 일반 TextField로 그대로 유지 (picker 적용 X).
void main() {
  group('country_picker 통합 가드', () {
    test('pubspec.yaml에 country_picker 의존성 존재', () {
      final pubspec = File('pubspec.yaml').readAsStringSync();
      expect(
        RegExp(r'^\s*country_picker:\s*\^?\d', multiLine: true)
            .hasMatch(pubspec),
        isTrue,
        reason:
            'pubspec.yaml에서 country_picker 의존성이 빠졌다. '
            '국가 입력이 자유 텍스트로 회귀할 위험 (사용자 의도 위배).',
      );
    });

    test('country_default_service.dart 파일 존재', () {
      final f = File('lib/core/services/country_default_service.dart');
      expect(
        f.existsSync(),
        isTrue,
        reason:
            'CountryDefaultService 파일이 사라졌다 — '
            'fallback chain (locale → 최근 등록 country)이 깨진다.',
      );
    });

    test('CountryDefaultService 본문에 fallback chain 핵심 토큰 존재', () {
      final source =
          File('lib/core/services/country_default_service.dart')
              .readAsStringSync();
      expect(
        source.contains('PlatformDispatcher'),
        isTrue,
        reason: 'PlatformDispatcher.locale 미사용 — fallback (1) 누락',
      );
      expect(
        source.contains('findMostRecentCountryCode'),
        isTrue,
        reason: 'DAO findMostRecentCountryCode 호출 누락 — fallback (2) 누락',
      );
      expect(
        source.contains('resolveDefault'),
        isTrue,
        reason: 'resolveDefault API 사라짐',
      );
    });

    test('MediaDao에 findMostRecentCountryCode 시그니처 존재', () {
      final dao = File('lib/core/db/media_dao.dart').readAsStringSync();
      final hasSignature = RegExp(
        r'Future<String\?>\s+findMostRecentCountryCode\s*\(\s*\{[^}]*required\s+String\s+space',
      ).hasMatch(dao);
      expect(
        hasSignature,
        isTrue,
        reason:
            'MediaDao.findMostRecentCountryCode({required String space}) '
            '시그니처가 없다 — CountryDefaultService fallback (2) 깨짐.',
      );
    });

    test('MediaDao.findMostRecentCountryCode 본문에 빈 country_code 제외 절', () {
      final dao = File('lib/core/db/media_dao.dart').readAsStringSync();
      // 본문에 country_code != '' 조건이 있어야 — 빈 값은 제외
      expect(
        dao.contains("country_code != ''"),
        isTrue,
        reason:
            "findMostRecentCountryCode가 빈 country_code를 제외하지 않으면 "
            "default가 빈 값으로 회귀 (사용자가 picker로 매번 선택해야 함).",
      );
    });

    test('media_detail_screen에 country_picker import 존재', () {
      final src = File('lib/shared/screens/media_detail_screen.dart')
          .readAsStringSync();
      expect(
        src.contains("package:country_picker/country_picker.dart"),
        isTrue,
        reason:
            'media_detail_screen이 country_picker를 import하지 않음 — '
            'picker 진입 불가, 사용자 요구사항 위배.',
      );
    });

    test('media_detail_screen에 showCountryPicker 호출 존재', () {
      final src = File('lib/shared/screens/media_detail_screen.dart')
          .readAsStringSync();
      expect(
        src.contains('showCountryPicker('),
        isTrue,
        reason:
            'showCountryPicker 호출이 사라졌다 — 국가 입력이 자유 텍스트로 회귀.',
      );
    });

    test('media_detail_screen이 CountryDefaultService를 사용', () {
      final src = File('lib/shared/screens/media_detail_screen.dart')
          .readAsStringSync();
      expect(
        src.contains('CountryDefaultService'),
        isTrue,
        reason:
            'CountryDefaultService 미사용 — default 적용 로직(폰 locale → 최근 country) 누락.',
      );
    });

    test('region(지역) 필드는 일반 TextField로 유지 (picker 적용 안 됨)', () {
      final src = File('lib/shared/screens/media_detail_screen.dart')
          .readAsStringSync();
      // _regionCtrl가 TextField에 직접 controller로 사용되는지
      // (AbsorbPointer로 감싼 picker 대상이 아닌지) 확인.
      // 사용자 요청: "지역은 직접 입력해야 한다."
      final regionControllerCount =
          'controller: _regionCtrl'.allMatches(src).length;
      expect(
        regionControllerCount,
        greaterThanOrEqualTo(1),
        reason:
            '_regionCtrl이 TextField controller로 더 이상 사용되지 않음 — '
            '지역 직접 입력 기능 회귀.',
      );

      // 추가: country만 AbsorbPointer로 감싸야 함 (region은 그대로).
      // 단순 검증: 'AbsorbPointer' 등장 수가 country 1회만 있도록.
      // (지역에 AbsorbPointer가 추가되었다면 fail.)
      // — 문자열로 region 근처에 AbsorbPointer가 직접 인접한지는 grep으로 체크.
      final regionAbsorbBlocked = RegExp(
        r'AbsorbPointer[\s\S]{0,200}?controller:\s*_regionCtrl',
      ).hasMatch(src);
      expect(
        regionAbsorbBlocked,
        isFalse,
        reason:
            '_regionCtrl이 AbsorbPointer로 감싸졌다 — 지역 직접 입력이 막힘 '
            '(사용자 요구사항 위배: "지역은 직접 입력해야 한다").',
      );
    });
  });
}
