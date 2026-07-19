# Repository Guidelines

## Project Structure & Module Organization

Memorix is primarily a Flutter app. Dart code lives in `lib/`, with shared models, screens, widgets, and theme code under `lib/shared/`, and infrastructure such as database access, services, providers, and routing under `lib/core/`. Feature UI is under `lib/features/`. Flutter tests are in `test/unit/` and `test/widget/`. Bundled images belong in `assets/images/`.

`android-native/` is a parallel Kotlin/Jetpack Compose implementation. Its source is under `android-native/app/src/main/java/`, organized by `core/`, `data/`, `feature/`, and `di/`; JVM tests are under `android-native/app/src/test/`. Flutter platform projects are in `android/` and `ios/`. Plans and design notes are in `docs/`.

## Build, Test, and Development Commands

- `flutter pub get` — install dependencies.
- `flutter analyze` — run configured `flutter_lints` checks.
- `dart format lib test` — format Dart source and tests.
- `flutter test` — run Flutter unit and widget tests.
- `flutter run` — launch on a selected device.
- `cd android-native && ./gradlew test` — run native JVM tests.
- `cd android-native && ./gradlew assembleDebug` — build the native debug APK.

Run the relevant analyzer and test suite for every change; UI changes should also be checked on a device or emulator.

## Coding Style & Naming Conventions

Use two-space indentation and let `dart format` own Dart whitespace. Name Dart files/directories in `lower_snake_case`; use `UpperCamelCase` for classes/widgets and `lowerCamelCase` for members. Kotlin types use `UpperCamelCase` types , and Compose screens use the `*Screen` suffix. Keep domain logic in services/repositories and UI state in its ViewModel/provider.

## Testing Guidelines

Name tests with the `_test.dart` or `*Test.kt` suffix and place them in the matching test category/package. Add unit tests for models, services, persistence, and ViewModel logic; add widget tests for user-visible behavior. No coverage threshold is configured, so prioritize changed behavior.

## Commit & Pull Request Guidelines

Use concise, imperative subjects with a conventional prefix, such as `feat: add media import flow`, `fix: handle empty albums`, or `docs: update migration plan`. Keep commits focused. Pull requests should explain impact, list validation commands, link an issue or plan when applicable, and include UI screenshots or a recording. state whether Flutter, `android-native/`, or both.

## Security & Configuration Tips

Do not commit credentials, signing keys, private media, or machine-local configuration. Review permission, authentication, storage, and export changes carefully. Avoid committing generated directories such as `.dart_tool/`, Gradle build outputs, or IDE metadata.
