import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class AppTheme {
  // 브랜드 컬러
  static const _primary = Color(0xFF00C896);     // 비비드 에메랄드
  static const _secondary = Color(0xFF7B61FF);   // 바이올렛
  static const _surface = Color(0xFFF6F8FA);
  static const _surfaceDark = Color(0xFF0E1117);

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
            fontSize: 20,
            fontWeight: FontWeight.w700,
            color: Color(0xFF0E1117),
            letterSpacing: -0.3,
          ),
          systemOverlayStyle: SystemUiOverlayStyle.dark,
        ),
        navigationBarTheme: NavigationBarThemeData(
          elevation: 0,
          height: 68,
          backgroundColor: Colors.white,
          surfaceTintColor: Colors.transparent,
          shadowColor: Colors.black12,
          indicatorColor: _primary.withValues(alpha: 0.15),
          labelTextStyle: WidgetStateProperty.resolveWith((states) {
            if (states.contains(WidgetState.selected)) {
              return const TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                color: _primary,
              );
            }
            return const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w500,
              color: Color(0xFF8A9099),
            );
          }),
          iconTheme: WidgetStateProperty.resolveWith((states) {
            if (states.contains(WidgetState.selected)) {
              return const IconThemeData(color: _primary, size: 24);
            }
            return const IconThemeData(color: Color(0xFF8A9099), size: 24);
          }),
        ),
        floatingActionButtonTheme: FloatingActionButtonThemeData(
          backgroundColor: _primary,
          foregroundColor: Colors.white,
          elevation: 4,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
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
          labelStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
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
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        ),
        textTheme: const TextTheme(
          headlineLarge: TextStyle(fontWeight: FontWeight.w800, letterSpacing: -0.5),
          headlineMedium: TextStyle(fontWeight: FontWeight.w700, letterSpacing: -0.3),
          titleLarge: TextStyle(fontWeight: FontWeight.w700, letterSpacing: -0.2),
          titleMedium: TextStyle(fontWeight: FontWeight.w600),
          bodyMedium: TextStyle(fontSize: 14, height: 1.5),
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
            fontSize: 20,
            fontWeight: FontWeight.w700,
            color: Colors.white,
            letterSpacing: -0.3,
          ),
          systemOverlayStyle: SystemUiOverlayStyle.light,
        ),
        navigationBarTheme: NavigationBarThemeData(
          elevation: 0,
          height: 68,
          backgroundColor: Color(0xFF161B22),
          surfaceTintColor: Colors.transparent,
          indicatorColor: Color(0xFF00C896),
          labelTextStyle: WidgetStateProperty.resolveWith((states) {
            if (states.contains(WidgetState.selected)) {
              return const TextStyle(
                fontSize: 11,
                fontWeight: FontWeight.w700,
                color: _primary,
              );
            }
            return const TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w500,
              color: Color(0xFF606672),
            );
          }),
          iconTheme: WidgetStateProperty.resolveWith((states) {
            if (states.contains(WidgetState.selected)) {
              return const IconThemeData(color: _primary, size: 24);
            }
            return const IconThemeData(color: Color(0xFF606672), size: 24);
          }),
        ),
        floatingActionButtonTheme: FloatingActionButtonThemeData(
          backgroundColor: _primary,
          foregroundColor: Colors.white,
          elevation: 4,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
          ),
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
          labelStyle: const TextStyle(fontSize: 12, fontWeight: FontWeight.w500),
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(20),
          ),
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
          contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        ),
        textTheme: const TextTheme(
          headlineLarge: TextStyle(fontWeight: FontWeight.w800, letterSpacing: -0.5),
          headlineMedium: TextStyle(fontWeight: FontWeight.w700, letterSpacing: -0.3),
          titleLarge: TextStyle(fontWeight: FontWeight.w700, letterSpacing: -0.2),
          titleMedium: TextStyle(fontWeight: FontWeight.w600),
          bodyMedium: TextStyle(fontSize: 14, height: 1.5),
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
