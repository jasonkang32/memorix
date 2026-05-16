import 'dart:ui' show PlatformDispatcher;

import '../db/media_dao.dart';

/// Work 미디어의 default country code 결정.
///
/// Fallback chain (사용자 요청):
/// 1. 폰 locale의 country code (예: `ko_KR` → `'KR'`). 권한 불필요.
/// 2. (1)이 null/빈 값이면 → MediaDao의 가장 최근 등록 work country.
/// 3. 그것도 없으면 → `''` (사용자가 picker에서 직접 선택해야 함).
///
/// `localeCountryCodeProvider`를 주입 가능하게 두어 단위 테스트에서
/// PlatformDispatcher를 mock하지 않아도 검증 가능.
class CountryDefaultService {
  final MediaDao _dao;
  final String? Function() _localeCountryCodeProvider;

  CountryDefaultService({
    MediaDao? dao,
    String? Function()? localeCountryCodeProvider,
  })  : _dao = dao ?? MediaDao(),
        _localeCountryCodeProvider = localeCountryCodeProvider ??
            _defaultLocaleCountryCodeProvider;

  static String? _defaultLocaleCountryCodeProvider() {
    try {
      return PlatformDispatcher.instance.locale.countryCode;
    } catch (_) {
      // 매우 드문 경우(테스트 환경 등) — 무시하고 fallback.
      return null;
    }
  }

  /// 새 미디어 등록 시 country 입력의 default 값을 반환.
  ///
  /// 빈 문자열이면 picker 진입 후 사용자가 직접 선택해야 한다.
  /// [space]는 fallback (2)에서 어느 공간의 최근 country를 볼지 결정한다.
  /// Work picker에서 호출하면 `'work'`를 넘긴다.
  Future<String> resolveDefault({String space = 'work'}) async {
    // 1. Phone locale country
    final localeCode = _localeCountryCodeProvider();
    if (localeCode != null && localeCode.isNotEmpty) {
      return localeCode.toUpperCase();
    }

    // 2. 최근 등록된 country (fallback)
    try {
      final recent = await _dao.findMostRecentCountryCode(space: space);
      if (recent != null && recent.isNotEmpty) return recent;
    } catch (_) {
      // DAO 실패는 무시 — 빈 값으로 fallback.
    }

    // 3. 빈 값 — 사용자가 picker에서 선택.
    return '';
  }
}
