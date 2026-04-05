import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:memorix/features/onboarding/screens/onboarding_screen.dart';

void main() {
  group('OnboardingScreen', () {
    testWidgets('renders first page correctly', (tester) async {
      bool doneCalled = false;
      await tester.pumpWidget(
        MaterialApp(
          home: OnboardingScreen(onDone: () => doneCalled = true),
        ),
      );

      // 첫 페이지 내용 확인
      expect(find.text('건너뛰기'), findsOneWidget);
      expect(doneCalled, isFalse);
    });

    testWidgets('skip button is visible', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: OnboardingScreen(onDone: () {}),
        ),
      );
      // '건너뛰기' 버튼이 첫 페이지에서 보여야 함
      expect(find.text('건너뛰기'), findsOneWidget);
    });

    testWidgets('page view exists', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: OnboardingScreen(onDone: () {}),
        ),
      );
      expect(find.byType(PageView), findsOneWidget);
    });
  });
}
