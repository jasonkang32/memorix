Build the Memorix Android APK.

## Steps

1. Read the current version from `pubspec.yaml` (the `version:` field, e.g. `1.0.0+1` → use only `1.0.0` part before `+`).
2. Run the build command:
   ```
   flutter build apk --dart-define=APP_FLAVOR=memorix
   ```
3. If the build succeeds:
   - Rename the output APK from `build/app/outputs/flutter-apk/app-release.apk` to `build/app/outputs/flutter-apk/memorix_<version>.apk` (e.g. `memorix_1.0.0.apk`)
   - Report the renamed APK path
   - Report the version
   - Show file size of the APK
4. If the build fails:
   - Show the error output
   - Diagnose and fix the issue, then retry the build
