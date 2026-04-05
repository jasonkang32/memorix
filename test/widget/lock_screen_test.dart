import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/features/auth/screens/lock_screen.dart';

void main() {
  group('LockScreen', () {
    testWidgets('shows PIN keypad numbers', (tester) async {
      tester.view.physicalSize = const Size(390, 844); // iPhone 14 크기
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        MaterialApp(
          home: LockScreen(onUnlocked: () {}),
        ),
      );
      await tester.pump();

      expect(find.text('1'), findsOneWidget);
      expect(find.text('5'), findsOneWidget);
      expect(find.text('0'), findsOneWidget);
    });

    testWidgets('LockScreen widget exists', (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);

      await tester.pumpWidget(
        MaterialApp(
          home: LockScreen(onUnlocked: () {}),
        ),
      );
      await tester.pump();

      expect(find.byType(LockScreen), findsOneWidget);
    });
  });
}
