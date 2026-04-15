import 'dart:async';
import 'dart:developer' as developer;
import 'dart:io';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'core/services/connectivity_service.dart';
import 'core/services/drive_service.dart';
import 'core/utils/app_lifecycle_observer.dart';
import 'core/utils/app_router.dart';
import 'features/auth/providers/lock_provider.dart';
import 'features/auth/screens/lock_screen.dart';
import 'features/onboarding/screens/onboarding_screen.dart';
import 'shared/theme/app_theme.dart';

// Flavor: memorix | memorix_work | memorix_personal
const String appFlavor =
    String.fromEnvironment('APP_FLAVOR', defaultValue: 'memorix');

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  // 글로벌 Flutter 에러 핸들러 — 크래시 원인을 logcat에서 확인 가능
  FlutterError.onError = (details) {
    developer.log(
      'FlutterError: ${details.exceptionAsString()}',
      error: details.exception,
      stackTrace: details.stack,
      name: 'memorix.flutter',
    );
    // 릴리스 빌드에서도 fatal error는 기존 방식으로 처리
    FlutterError.presentError(details);
  };

  // Zone 내 비동기 에러도 캡처
  PlatformDispatcher.instance.onError = (error, stack) {
    developer.log(
      'PlatformDispatcher error: $error',
      error: error,
      stackTrace: stack,
      name: 'memorix.platform',
    );
    return false; // false = 기본 처리 계속
  };

  await initializeDateFormatting('ko', null);

  // 데스크탑(Linux/macOS/Windows)에서 sqflite FFI 초기화
  if (Platform.isLinux || Platform.isWindows || Platform.isMacOS) {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
  }

  await SystemChrome.setPreferredOrientations([
    DeviceOrientation.portraitUp,
    DeviceOrientation.portraitDown,
  ]);

  runApp(
    const ProviderScope(
      child: MemorixApp(),
    ),
  );
}

class MemorixApp extends ConsumerStatefulWidget {
  const MemorixApp({super.key});

  @override
  ConsumerState<MemorixApp> createState() => _MemorixAppState();
}

class _MemorixAppState extends ConsumerState<MemorixApp> {
  late final AppLifecycleObserver _observer;
  StreamSubscription<dynamic>? _connectivitySub;

  @override
  void initState() {
    super.initState();
    _observer = AppLifecycleObserver(ref);
    WidgetsBinding.instance.addObserver(_observer);
    _connectivitySub = ConnectivityService.listen(_onNetworkRecovered);
  }

  Future<void> _onNetworkRecovered() async {
    await DriveService.syncPending();
  }

  @override
  void dispose() {
    _connectivitySub?.cancel();
    WidgetsBinding.instance.removeObserver(_observer);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final lockState = ref.watch(lockProvider);

    return MaterialApp.router(
      title: _appTitle,
      theme: AppTheme.light,
      darkTheme: AppTheme.dark,
      themeMode: ThemeMode.system,
      routerConfig: appRouter,
      debugShowCheckedModeBanner: false,
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [
        Locale('ko', 'KR'),
        Locale('en', 'US'),
      ],
      builder: (context, child) {
        // 온보딩 체크
        final onboardAsync = ref.watch(onboardingDoneProvider);
        return onboardAsync.when(
          loading: () => const Scaffold(
            body: Center(child: CircularProgressIndicator()),
          ),
          error: (e, st) => child ?? const SizedBox.shrink(),
          data: (done) {
            if (!done) {
              return OnboardingScreen(
                onDone: () => ref.invalidate(onboardingDoneProvider),
              );
            }
            // 잠금 체크
            if (lockState == AppLockState.checking) {
              return const Scaffold(
                body: Center(child: CircularProgressIndicator()),
              );
            }
            if (lockState == AppLockState.locked) {
              return LockScreen(
                onUnlocked: () => ref.read(lockProvider.notifier).unlock(),
              );
            }
            return child ?? const SizedBox.shrink();
          },
        );
      },
    );
  }

  String get _appTitle => switch (appFlavor) {
        'memorix_work' => 'Memorix Work',
        'memorix_personal' => 'Memorix Personal',
        _ => 'Memorix',
      };
}

