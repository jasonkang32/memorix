import 'package:geolocator/geolocator.dart';
import 'package:geocoding/geocoding.dart';

class LocationResult {
  final double latitude;
  final double longitude;
  final String countryCode;
  final String region;

  const LocationResult({
    required this.latitude,
    required this.longitude,
    required this.countryCode,
    required this.region,
  });
}

class LocationService {
  /// 현재 위치를 가져와 국가 코드와 지역명을 반환
  /// 권한 없거나 실패 시 null 반환
  static Future<LocationResult?> getCurrentLocation() async {
    try {
      final permission = await _checkPermission();
      if (!permission) return null;

      final pos = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.medium,
          timeLimit: Duration(seconds: 8),
        ),
      );

      final placemarks = await placemarkFromCoordinates(
        pos.latitude,
        pos.longitude,
      );

      if (placemarks.isEmpty) return null;
      final place = placemarks.first;

      return LocationResult(
        latitude: pos.latitude,
        longitude: pos.longitude,
        countryCode: place.isoCountryCode ?? '',
        region: place.administrativeArea ?? place.locality ?? '',
      );
    } catch (_) {
      return null;
    }
  }

  static Future<bool> _checkPermission() async {
    LocationPermission permission = await Geolocator.checkPermission();
    if (permission == LocationPermission.denied) {
      permission = await Geolocator.requestPermission();
    }
    return permission == LocationPermission.whileInUse ||
        permission == LocationPermission.always;
  }
}
