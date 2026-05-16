import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/core/db/media_dao.dart';
import 'package:memorix/core/services/country_default_service.dart';

/// Fake MediaDao — DB를 띄우지 않고 findMostRecentCountryCode만 stub.
class _FakeMediaDao extends MediaDao {
  String? recentCountryCode;
  Object? throwOnQuery;
  String? lastSpaceQueried;

  @override
  Future<String?> findMostRecentCountryCode({required String space}) async {
    lastSpaceQueried = space;
    if (throwOnQuery != null) throw throwOnQuery!;
    return recentCountryCode;
  }
}

void main() {
  group('CountryDefaultService.resolveDefault — fallback chain', () {
    test('locale countryCode가 있으면 그것을 대문자로 반환 (fallback 안 탐)', () async {
      // Arrange
      final fakeDao = _FakeMediaDao()..recentCountryCode = 'JP';
      final svc = CountryDefaultService(
        dao: fakeDao,
        localeCountryCodeProvider: () => 'kr', // 소문자 들어와도 대문자로
      );

      // Act
      final result = await svc.resolveDefault();

      // Assert
      expect(result, 'KR');
      expect(
        fakeDao.lastSpaceQueried,
        isNull,
        reason: 'locale에서 결정되었으면 DAO를 건드릴 필요 없음',
      );
    });

    test('locale countryCode가 null이면 DAO의 최근 country를 반환', () async {
      // Arrange
      final fakeDao = _FakeMediaDao()..recentCountryCode = 'US';
      final svc = CountryDefaultService(
        dao: fakeDao,
        localeCountryCodeProvider: () => null,
      );

      // Act
      final result = await svc.resolveDefault();

      // Assert
      expect(result, 'US');
      expect(fakeDao.lastSpaceQueried, 'work');
    });

    test('locale countryCode가 빈 문자열이어도 DAO fallback', () async {
      final fakeDao = _FakeMediaDao()..recentCountryCode = 'FR';
      final svc = CountryDefaultService(
        dao: fakeDao,
        localeCountryCodeProvider: () => '',
      );

      final result = await svc.resolveDefault();

      expect(result, 'FR');
    });

    test('locale도 null이고 DAO도 null이면 빈 문자열 반환', () async {
      final fakeDao = _FakeMediaDao()..recentCountryCode = null;
      final svc = CountryDefaultService(
        dao: fakeDao,
        localeCountryCodeProvider: () => null,
      );

      final result = await svc.resolveDefault();

      expect(result, '');
    });

    test('DAO가 빈 문자열을 반환해도 빈 값으로 처리', () async {
      final fakeDao = _FakeMediaDao()..recentCountryCode = '';
      final svc = CountryDefaultService(
        dao: fakeDao,
        localeCountryCodeProvider: () => null,
      );

      final result = await svc.resolveDefault();

      expect(result, '');
    });

    test('DAO가 throw해도 빈 문자열로 안전하게 fallback (사용자 picker 입력)', () async {
      final fakeDao = _FakeMediaDao()..throwOnQuery = StateError('db error');
      final svc = CountryDefaultService(
        dao: fakeDao,
        localeCountryCodeProvider: () => null,
      );

      final result = await svc.resolveDefault();

      expect(result, '');
    });

    test('space 파라미터가 DAO에 전달됨 (personal 등 다른 공간)', () async {
      final fakeDao = _FakeMediaDao()..recentCountryCode = 'DE';
      final svc = CountryDefaultService(
        dao: fakeDao,
        localeCountryCodeProvider: () => null,
      );

      await svc.resolveDefault(space: 'personal');

      expect(fakeDao.lastSpaceQueried, 'personal');
    });
  });
}
