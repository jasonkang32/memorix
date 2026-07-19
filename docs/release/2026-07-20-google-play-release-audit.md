# Memorix Google Play Release Audit — 2026-07-20

## Summary

Memorix Android Native currently builds successfully for debug APK and release AAB, but it is **not Play-ready yet** because release signing, Play Billing/Pro entitlement, store compliance assets, and internal testing setup are still incomplete.

## Verified Commands

From `/home/mebon/project/memorix/android-native`:

```bash
./gradlew :app:testDebugUnitTest :app:compileDebugKotlin :app:assembleDebug --no-daemon
```

Result: `BUILD SUCCESSFUL`

```bash
./gradlew :app:bundleRelease --no-daemon
```

Result: `BUILD SUCCESSFUL`

## Generated Artifacts

Debug APK:

```text
app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
size: 40M
```

Release AAB:

```text
app/build/outputs/bundle/release/app-release.aab
size: 36M
```

## Package/Badging Check

Checked with:

```bash
/home/mebon/Android/Sdk/build-tools/36.0.0/aapt dump badging app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Important values:

- package: `com.mebonsoft.memorix`
- versionCode: `5`
- versionName: `0.1.4`
- minSdk: `26`
- targetSdk: `35`
- app label: `Memorix`

Observed permissions:

- `android.permission.READ_EXTERNAL_STORAGE` maxSdk 32
- `android.permission.READ_MEDIA_IMAGES`
- `android.permission.READ_MEDIA_VIDEO`
- `android.permission.READ_MEDIA_VISUAL_USER_SELECTED`
- `android.permission.CAMERA`
- `android.permission.USE_BIOMETRIC`
- `android.permission.USE_FINGERPRINT`
- `android.permission.WAKE_LOCK`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.RECEIVE_BOOT_COMPLETED`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.INTERNET`
- `com.mebonsoft.memorix.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`

Notes:

- Some permissions are introduced by dependencies such as WorkManager/ML Kit/AndroidX.
- Data Safety and privacy policy must mention actual permission purpose accurately.

## Current Release Blockers

### 1. Git/package migration state is messy

`git status --short` currently shows a large package-path migration:

- deleted tracked files under `app/src/main/java/com/jasonkang/memorix/**`
- untracked current files under `app/src/main/java/com/mebonsoft/**`
- similar test-package migration under `app/src/test/java/com/mebonsoft/**`

This may be intentional because active `applicationId` is `com.mebonsoft.memorix`, but it must be normalized and committed before release work continues.

### 2. Release signing is not configured for Play upload

`app/build.gradle.kts` has no `signingConfigs` block.

Needed:

- upload keystore or Play App Signing setup
- secure local `keystore.properties` or environment-variable config
- `.gitignore` protection for keystore/secrets has been added

### 3. Google Play Billing not implemented

No billing dependency or product integration exists yet.

Needed:

- product ID: `memorix_pro_lifetime`
- non-consumable one-time purchase
- purchase restore
- local entitlement cache
- graceful failure state
- test seams and unit tests

### 4. Free/Pro gates not implemented

The app still behaves as a full local app.

Needed:

- define Free limits
- gate Pro features
- upgrade prompt in Settings/feature entry points
- ensure data is never deleted merely because entitlement changes

### 5. Store compliance docs missing

Needed docs:

- `docs/store/google-play-listing-ko.md`
- `docs/store/privacy-policy-ko.md`
- `docs/store/data-safety-answers.md`
- `docs/store/internal-test-checklist.md`

### 6. Manual smoke test needed

Automated tests pass, but Play readiness needs phone testing through internal testing.

Required smoke areas:

- first launch
- photo/video import
- document import
- Work/Personal separation
- date-based import cancel
- tag create/delete
- search
- detail grouped media view
- hidden vault/auth
- backup/restore/reset
- language switch
- purchase/restore after Billing integration

## Documentation Changes Made

- Created release plan:
  - `docs/plans/2026-07-20-google-play-release-plan.md`
- Updated Android Native README:
  - `android-native/README.md`
- Rewrote project guidance to Android Native release reality:
  - `CLAUDE.md`
- Hardened `.gitignore` for release secrets:
  - `.gitignore`

## Next Recommended Work Order

1. Normalize and commit current Android package migration/doc baseline.
2. Implement Free/Pro entitlement model with tests.
3. Add Google Play Billing for `memorix_pro_lifetime`.
4. Configure release signing and produce a signed AAB.
5. Draft privacy policy, Data Safety answers, and store listing.
6. Upload internal testing build.
7. Run JK phone smoke test for 1–2 weeks before public rollout.
