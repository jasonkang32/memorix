Build the Memorix Android Native APK.

## Steps

1. Read `versionName` from `android-native/app/build.gradle.kts`.
2. From `android-native/`, run unit tests and assemble the debug APK (on Windows, ensure `JAVA_HOME` points to Android Studio's JBR `/c/Program Files/Android/Android Studio/jbr`):
   ```bash
   cd android-native
   ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
   ```
3. If the build succeeds:
   - Locate `android-native/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk`.
   - Copy it to `G:\내 드라이브\MEBON\develop\memorix\memorix-<versionName>_<yyMMdd>.apk` (overwrite if same version and date).
   - Report the APK path, version, and file size.
4. If the build fails:
   - Show the error output, diagnose and fix the issue, then retry the build.
