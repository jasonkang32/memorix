# Repository Guidelines

## Start Here (current state)

- **Active development root**: `android-native/` (Kotlin + Jetpack Compose).
- Read `android-native/docs/DEVELOPMENT_LOG.md` first. It records recent changes, design decisions, open items, the Windows build setup, and the APK delivery rule.
- Google Drive setup and behavior: `android-native/docs/google-drive-phase1-setup.md`.
- Project rules (Korean): `CLAUDE.md`. User-facing text is Korean by default. Never commit keystores, `local.properties`, OAuth client JSON, or Play credentials.
- After mobile changes run `cd android-native && ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`.
- The Flutter code in `lib/`, `test/`, `android/`, and `ios/` is a legacy reference implementation and is no longer the active release target.

## Project Structure & Module Organization

`android-native/` is the primary Kotlin/Jetpack Compose application (`com.mebonsoft.memorix`):

- `android-native/app/src/main/java/com/mebonsoft/memorix/`
  - `app/` — `MemorixApplication`, top-level Compose navigation (`MemorixNavHost`), and Android share-intent handling.
  - `core/` — Room database (`MemorixDatabase`, DAOs, entities, FTS4 search), media import & EXIF orientation, 3-tier security (`MediaProtection`: Normal / Locked / Secret, `.mrxsecret` encryption, biometric/PIN session), local ZIP backup, Google Drive `appDataFolder` sync & WorkManager auto-backup, Free/Pro monetization & Play Billing, and localization (`AppLanguage`, `MemorixStrings`, `CountryCatalog`).
  - `data/repository/` — repository interfaces and default implementations (`MediaRepository`, `AlbumRepository`, `SearchRepository`).
  - `di/` — Hilt modules (`DatabaseModule`, `RepositoryModule`).
  - `feature/` — UI screens and ViewModels (`home`, `work`, `personal`, `albums`, `detail`, `search`, `hidden`, `auth`, `onboarding`, `settings`).
  - `worker/` — background WorkManager workers (`ImportMediaWorker`).
- `android-native/app/src/test/java/com/mebonsoft/memorix/` — JVM and Robolectric unit tests.
- `android-native/app/schemas/` — exported Room database schemas.
- `android-native/docs/` & `docs/` — development log, Google Drive setup, design specs, and Google Play release plans.

## Build, Test, and Development Commands

Run all commands from `android-native/`:

- `./gradlew :app:testDebugUnitTest --no-daemon` — run native JVM/Robolectric unit tests.
- `./gradlew :app:compileDebugKotlin --no-daemon` — verify Kotlin compilation.
- `./gradlew :app:assembleDebug --no-daemon` — build the debug APK (`app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`).
- `./gradlew :app:bundleRelease --no-daemon` — build the Google Play release AAB (`app/build/outputs/bundle/release/app-release.aab`).

> **Windows JDK Note**: System JDK 22 fails with Gradle/KSP. Set `JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"` (or `/c/Program Files/Android/Android Studio/jbr` in Git Bash) before running `./gradlew`.

## Coding Style & Naming Conventions

Use 4-space indentation for Kotlin. Kotlin types use `UpperCamelCase`, functions/properties use `lowerCamelCase`, and Compose screens use the `*Screen` suffix. Keep domain/storage logic in `core/` or `data/repository/`, pure UI/domain helpers in `*Support.kt` files for easy unit testing, and screen state in `*ViewModel.kt`. User-facing strings default to Korean and use `MemorixStrings` (`AppLanguage`) when localized.

## Testing Guidelines

Name tests with the `*Test.kt` suffix under `android-native/app/src/test/java/com/mebonsoft/memorix/` matching the production package structure. Add unit tests for policies, support helpers, DAOs, backup/crypto managers, and ViewModels. Use `File.invariantSeparatorsPath` when asserting file paths so tests pass on both Windows and Linux.

## Commit & Pull Request Guidelines

Use concise subjects with a conventional prefix, such as `feat: add Google Drive cloud sync`, `fix: 미디어 재생 및 네이티브 앱 기능 보완`, or `docs: update release plan`. Keep commits focused and verify `:app:testDebugUnitTest` and `:app:assembleDebug` before committing.

## Security & Configuration Tips

Do not commit credentials, signing keys (`*.jks`, `*.keystore`, `keystore.properties`), `local.properties`, `client_secret*.json`, private media, or Play Console credentials. Review permission, authentication, encryption (`.mrxsecret`), storage, and backup/export changes carefully.
