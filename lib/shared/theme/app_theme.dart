import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// 디자인 토큰 — 컬러
///
/// 화면 코드에서 `Color(0xFF...)` 인라인 대신 이 토큰을 사용한다.
/// 다크모드 분기는 `Theme.of(ctx).colorScheme.X` 또는 sematic 토큰을 우선 사용.
class AppColors {
  AppColors._();

  // ── 브랜드 ───────────────────────────────────────────────
  /// Primary 브랜드 컬러 (비비드 에메랄드).
  static const brandPrimary = Color(0xFF00C896);

  /// Secondary 브랜드 컬러 (바이올렛).
  static const brandSecondary = Color(0xFF7B61FF);

  // ── 공간(Space) 액센트 ───────────────────────────────────
  /// Work 공간 액센트 (구글 블루).
  static const workAccent = Color(0xFF1A73E8);

  /// Personal 공간 액센트 (코랄 핑크).
  static const personalAccent = Color(0xFFFF6B9D);

  // ── 시맨틱 ───────────────────────────────────────────────
  /// 경고 (배지·알림).
  static const warning = Color(0xFFFFB800);

  /// 오류·삭제·위험 액션.
  static const danger = Color(0xFFFF6B35);

  // ── 표면 (Light) ─────────────────────────────────────────
  static const surfaceLight = Color(0xFFF6F8FA);
  static const onSurfaceLight = Color(0xFF0E1117);
  static const borderLight = Color(0xFFECEFF3);
  static const inputBorderLight = Color(0xFFDDE1E7);
  static const mutedTextLight = Color(0xFF8A9099);

  // ── 표면 (Dark) ──────────────────────────────────────────
  static const surfaceDark = Color(0xFF0E1117);
  static const surfaceDarkRaised = Color(0xFF161B22);
  static const borderDark = Color(0xFF262D37);
  static const chipDark = Color(0xFF1E2530);
  static const mutedTextDark = Color(0xFF606672);
}

/// 디자인 토큰 — 간격 (8px 베이스).
///
/// `EdgeInsets.symmetric(horizontal: 12)` 같은 magic number 대신 사용.
class AppSpacing {
  AppSpacing._();

  static const xs = 4.0;
  static const s = 8.0;
  static const m = 12.0;
  static const l = 16.0;
  static const xl = 24.0;
  static const xxl = 32.0;
}

/// 디자인 토큰 — 모서리 둥글기.
///
/// 9가지 산재된 값 (4/6/8/10/12/14/16/20/28) 대신 5단계로 정리.
/// - small: chip·badge·태그 (8)
/// - medium: input·card·button (12)
/// - large: surface card·sheet (16)
/// - pill: 둥근 chip·avatar 보더 (20)
/// - blob: hero badge·아이콘 컨테이너 (28)
class AppRadius {
  AppRadius._();

  static const small = 8.0;
  static const medium = 12.0;
  static const large = 16.0;
  static const pill = 20.0;
  static const blob = 28.0;
}

class AppTheme {
  // 기존 코드와의 호환을 위한 별칭. 신규 코드는 AppColors를 직접 사용한다.
  static const _primary = AppColors.brandPrimary;
  static const _secondary = AppColors.brandSecondary;
  static const _surface = AppColors.surfaceLight;
  static const _surfaceDark = AppColors.surfaceDark;

  static ThemeData get light => ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: _primary,
      secondary: _secondary,
      brightness: Brightness.light,
      surface: _surface,
    ),
    scaffoldBackgroundColor: _surface,
    appBarTheme: AppBarTheme(
      centerTitle: false,
      elevation: 0,
      scrolledUnderElevation: 0,
      backgroundColor: _surface,
      surfaceTintColor: Colors.transparent,
      titleTextStyle: const TextStyle(
        fontSize: 22,
        fontWeight: FontWeight.w700,
        color: Color(0xFF0E1117),
        letterSpacing: -0.3,
      ),
      systemOverlayStyle: SystemUiOverlayStyle.dark,
    ),
    navigationBarTheme: NavigationBarThemeData(
      elevation: 0,
      height: 72,
      backgroundColor: Colors.white,
      surfaceTintColor: Colors.transparent,
      shadowColor: Colors.black12,
      indicatorColor: _primary.withValues(alpha: 0.15),
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.selected)) {
          return const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: _primary,
          );
        }
        return const TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w500,
          color: Color(0xFF8A9099),
        );
      }),
      iconTheme: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.selected)) {
          return const IconThemeData(color: _primary, size: 26);
        }
        return const IconThemeData(color: Color(0xFF8A9099), size: 26);
      }),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: _primary,
      foregroundColor: Colors.white,
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: Colors.white,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Color(0xFFECEFF3), width: 1),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: const Color(0xFFECEFF3),
      selectedColor: _primary.withValues(alpha: 0.15),
      labelStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      side: BorderSide.none,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: Colors.white,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: Color(0xFFDDE1E7)),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: Color(0xFFDDE1E7)),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _primary, width: 1.5),
      ),
      labelStyle: const TextStyle(fontSize: 15),
      hintStyle: const TextStyle(fontSize: 15),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
    ),
    textTheme: const TextTheme(
      headlineLarge: TextStyle(
        fontSize: 32,
        fontWeight: FontWeight.w800,
        letterSpacing: -0.5,
      ),
      headlineMedium: TextStyle(
        fontSize: 26,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.3,
      ),
      headlineSmall: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
      titleLarge: TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
      ),
      titleMedium: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
      titleSmall: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      bodyLarge: TextStyle(fontSize: 16, height: 1.6),
      bodyMedium: TextStyle(fontSize: 15, height: 1.6),
      bodySmall: TextStyle(fontSize: 13, height: 1.5),
      labelLarge: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      labelMedium: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
      labelSmall: TextStyle(fontSize: 12),
    ),
    dividerTheme: const DividerThemeData(
      color: Color(0xFFECEFF3),
      thickness: 1,
      space: 1,
    ),
  );

  static ThemeData get dark => ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: _primary,
      secondary: _secondary,
      brightness: Brightness.dark,
      surface: _surfaceDark,
    ),
    scaffoldBackgroundColor: _surfaceDark,
    appBarTheme: const AppBarTheme(
      centerTitle: false,
      elevation: 0,
      scrolledUnderElevation: 0,
      backgroundColor: _surfaceDark,
      surfaceTintColor: Colors.transparent,
      titleTextStyle: TextStyle(
        fontSize: 22,
        fontWeight: FontWeight.w700,
        color: Colors.white,
        letterSpacing: -0.3,
      ),
      systemOverlayStyle: SystemUiOverlayStyle.light,
    ),
    navigationBarTheme: NavigationBarThemeData(
      elevation: 0,
      height: 72,
      backgroundColor: Color(0xFF161B22),
      surfaceTintColor: Colors.transparent,
      indicatorColor: Color(0xFF00C896),
      labelTextStyle: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.selected)) {
          return const TextStyle(
            fontSize: 13,
            fontWeight: FontWeight.w700,
            color: _primary,
          );
        }
        return const TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w500,
          color: Color(0xFF606672),
        );
      }),
      iconTheme: WidgetStateProperty.resolveWith((states) {
        if (states.contains(WidgetState.selected)) {
          return const IconThemeData(color: _primary, size: 26);
        }
        return const IconThemeData(color: Color(0xFF606672), size: 26);
      }),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: _primary,
      foregroundColor: Colors.white,
      elevation: 4,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    cardTheme: CardThemeData(
      elevation: 0,
      color: const Color(0xFF161B22),
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Color(0xFF262D37), width: 1),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: const Color(0xFF1E2530),
      selectedColor: _primary.withValues(alpha: 0.2),
      labelStyle: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      side: BorderSide.none,
    ),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: const Color(0xFF161B22),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: Color(0xFF262D37)),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: Color(0xFF262D37)),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: _primary, width: 1.5),
      ),
      labelStyle: const TextStyle(fontSize: 15),
      hintStyle: const TextStyle(fontSize: 15),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
    ),
    textTheme: const TextTheme(
      headlineLarge: TextStyle(
        fontSize: 32,
        fontWeight: FontWeight.w800,
        letterSpacing: -0.5,
      ),
      headlineMedium: TextStyle(
        fontSize: 26,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.3,
      ),
      headlineSmall: TextStyle(fontSize: 22, fontWeight: FontWeight.w700),
      titleLarge: TextStyle(
        fontSize: 20,
        fontWeight: FontWeight.w700,
        letterSpacing: -0.2,
      ),
      titleMedium: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
      titleSmall: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      bodyLarge: TextStyle(fontSize: 16, height: 1.6),
      bodyMedium: TextStyle(fontSize: 15, height: 1.6),
      bodySmall: TextStyle(fontSize: 13, height: 1.5),
      labelLarge: TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      labelMedium: TextStyle(fontSize: 13, fontWeight: FontWeight.w500),
      labelSmall: TextStyle(fontSize: 12),
    ),
    dividerTheme: const DividerThemeData(
      color: Color(0xFF262D37),
      thickness: 1,
      space: 1,
    ),
  );

  // 그라디언트 헬퍼
  static const LinearGradient primaryGradient = LinearGradient(
    colors: [Color(0xFF00C896), Color(0xFF7B61FF)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient workGradient = LinearGradient(
    colors: [Color(0xFF1A73E8), Color(0xFF00C896)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );

  static const LinearGradient personalGradient = LinearGradient(
    colors: [Color(0xFFFF6B9D), Color(0xFF7B61FF)],
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
  );
}
