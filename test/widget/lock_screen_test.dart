import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/features/auth/screens/lock_screen.dart';

void main() {
  group('LockScreen', () {
    testWidgets('shows default unlock prompt', (tester) async {
      FlutterSecureStorage.setMockInitialValues({});
      tester.view.physicalSize = const Size(390, 844); // iPhone 14 크기
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            home: LockScreen(onUnlocked: () {}),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.text('Memorix'), findsOneWidget);
      expect(find.text('지문인식으로 잠금을 해제하세요'), findsOneWidget);
    });

    testWidgets('LockScreen widget exists', (tester) async {
      FlutterSecureStorage.setMockInitialValues({});
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        ProviderScope(
          child: MaterialApp(
            home: LockScreen(onUnlocked: () {}),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(LockScreen), findsOneWidget);
    });
  });
}
