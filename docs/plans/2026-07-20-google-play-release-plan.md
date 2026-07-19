# Memorix Google Play Release Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task when the work is split across independent implementation/review passes.

**Goal:** Prepare Memorix Android Native for Google Play internal testing and public release with an ad-free Free + Pro lifetime purchase model.

**Architecture:** Memorix remains a local-first Android Native app. Release work is split into: codebase/release hygiene, core feature stabilization, Pro entitlement/IAP, store compliance assets, internal testing, and public rollout.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, DataStore, ML Kit Korean OCR, Google Play Billing, Android App Bundle.

---

## Release Positioning

**Product statement:**

> Memorix is a quiet local vault for organizing photos, videos, and documents into Work and Personal memories so they can be found later by date, tag, memo, OCR, and context.

**Business model:**

- No ads.
- Free tier for real evaluation.
- Pro lifetime purchase as the first paid model.
- Optional future Pro+ subscription only if cloud backup, cross-device sync, or paid AI/server features are added.

**Initial monetization target:**

- Product ID: `memorix_pro_lifetime`
- Suggested Korean price: `₩14,900`
- Type: one-time non-consumable in-app product.

---

## Current Findings — 2026-07-20

- Active app root: `/home/mebon/project/memorix/android-native`
- Active Android namespace/applicationId: `com.mebonsoft.memorix`
- Display label: `Memorix`
- Current version: `versionCode = 5`, `versionName = 0.1.4`
- Target SDK: 35
- Current release signing: not configured in Gradle yet.
- Current publish tasks: no dedicated publishing tasks; standard Android bundle task should be used after verification.
- Current package migration state: worktree shows many deleted `com.jasonkang.memorix` files and new untracked `com.mebonsoft` files. Before release, normalize and commit this migration state or restore if unintended.
- Documentation drift: top-level `CLAUDE.md` still describes the older Flutter implementation and should be updated to Android Native release reality.
- APK size mitigation exists through `arm64-v8a` ABI split for debug testing; Play should receive an AAB, not Telegram APK.

---

## Release Gate Checklist

A release candidate is not ready until all of these are true:

- [ ] Working tree is clean except intentional release files.
- [ ] `./gradlew :app:testDebugUnitTest --no-daemon` passes.
- [ ] `./gradlew :app:compileDebugKotlin --no-daemon` passes.
- [ ] `./gradlew :app:assembleDebug --no-daemon` passes.
- [ ] `./gradlew :app:bundleRelease --no-daemon` passes.
- [ ] App bundle is signed with the release keystore or Play App Signing upload key.
- [ ] `aapt dump badging` confirms package, version, label, icons, permissions.
- [ ] Free/Pro restrictions are enforced and restorable.
- [ ] Privacy policy exists and matches actual data behavior.
- [ ] Data safety answers are documented.
- [ ] Store listing text, short description, icon, screenshots, feature graphic are ready.
- [ ] Internal testing track has at least one real phone smoke-test pass.

---

## Task 1: Normalize the Android Native release baseline

**Objective:** Make the current package migration and project state understandable before adding billing/release code.

**Files:**

- Inspect: `android-native/app/build.gradle.kts`
- Inspect: `android-native/app/src/main/AndroidManifest.xml`
- Inspect: `android-native/app/src/main/java/com/mebonsoft/memorix/**`
- Inspect/Update: `CLAUDE.md`
- Inspect/Update: `android-native/README.md`

**Steps:**

1. Run:
   ```bash
   cd /home/mebon/project/memorix/android-native
   git status --short
   ```
2. Confirm `com.jasonkang.memorix` to `com.mebonsoft.memorix` migration is intentional.
3. Run:
   ```bash
   ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
   ```
4. If green, update docs to say Android Native is the active release target.
5. Commit the baseline with a human-style commit message after user approval if needed.

**Verification:**

- Build passes.
- Docs no longer imply Flutter is the active release path.
- Git status reflects intentional tracked files, not a confusing delete/untracked package split.

---

## Task 2: Core feature stabilization pass

**Objective:** Verify the app is usable as a local-first media vault before monetization.

**Acceptance checks:**

- Home launches cleanly.
- Work registration supports photo/video/document where intended.
- Personal registration supports photo/video/camera where intended.
- Date-based Home import has blocking progress and cancel.
- Detail view shows grouped media correctly.
- Tags show registration-count usage, not raw media row count.
- Settings tag management deletes tags without deleting media.
- Backup/restore/reset work as designed or are hidden if not release-ready.
- Hidden vault/auth flows do not trap the user.
- Korean/English/Japanese setting does not crash; visible supported copy switches.

**Commands:**

```bash
cd /home/mebon/project/memorix/android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

**Verification:**

- Manual smoke-test checklist completed on JK phone.
- Blocking release bugs are fixed before IAP work.

---

## Task 3: Add Free/Pro entitlement model

**Objective:** Introduce a testable entitlement layer before Google Play Billing integration.

**Files:**

- Create: `app/src/main/java/com/mebonsoft/memorix/core/billing/ProEntitlement.kt`
- Create: `app/src/main/java/com/mebonsoft/memorix/core/billing/EntitlementRepository.kt`
- Create: `app/src/test/java/com/mebonsoft/memorix/core/billing/ProEntitlementTest.kt`
- Modify: Settings and feature gates where Pro limits apply.

**Initial Free limits:**

- `maxItems = 300`
- Basic photo/video import allowed.
- Pro-gated candidates:
  - Document/PDF import
  - OCR search/execution
  - Hidden vault
  - Backup/restore
  - Advanced tag management
  - Date-day bulk import

**TDD requirement:**

Write tests first for:

- free tier allows up to 300 registered groups/items according to the chosen limit basis.
- free tier blocks Pro-only features with a user-facing upgrade prompt.
- Pro tier allows all gated features.

---

## Task 4: Integrate Google Play Billing

**Objective:** Connect `memorix_pro_lifetime` as a non-consumable purchase.

**Files:**

- Modify: `app/build.gradle.kts`
- Create/Modify: `core/billing/**`
- Modify: Settings screen with Pro purchase/restore state.

**Implementation notes:**

- Use Google Play Billing Library.
- Product ID: `memorix_pro_lifetime`
- Store local entitlement in DataStore only as a cache; source of truth is Play purchase query.
- Provide `구매 복원` action.
- Fail gracefully when Play Billing is unavailable.
- Keep debug/test override separate and impossible to ship accidentally.

**Verification:**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Then verify through Google Play internal test purchase flow after upload.

---

## Task 5: Configure release signing and AAB generation

**Objective:** Produce a Google Play-uploadable signed Android App Bundle.

**Files:**

- Modify: `android-native/app/build.gradle.kts`
- Do not commit keystore or passwords.
- Optional local file: `android-native/keystore.properties` in `.gitignore`.

**Steps:**

1. Create upload keystore if not already created.
2. Configure Gradle release signing from environment variables or `keystore.properties`.
3. Build:
   ```bash
   ./gradlew :app:bundleRelease --no-daemon
   ```
4. Verify output:
   ```bash
   ls -lh app/build/outputs/bundle/release/*.aab
   ```

**Verification:**

- AAB exists and is signed.
- No secrets are tracked by git.

---

## Task 6: Store compliance and listing assets

**Objective:** Prepare the non-code assets required by Google Play Console.

**Create docs:**

- `docs/store/google-play-listing-ko.md`
- `docs/store/privacy-policy-ko.md`
- `docs/store/data-safety-answers.md`
- `docs/store/internal-test-checklist.md`

**Store listing draft direction:**

- Title: `Memorix`
- Short description: `업무와 개인 사진·영상·문서를 조용히 정리하는 로컬 기록함`
- Long description should emphasize:
  - local-first storage
  - Work/Personal separation
  - tag and memo search
  - document/OCR support where Pro applies
  - no ads
  - user-controlled backup/restore

**Privacy policy must state:**

- Media/files are stored locally in app storage unless user exports/backups/shares.
- Camera/media permissions are used only for user-initiated capture/import.
- Biometrics are handled by Android system APIs; Memorix does not store biometric data.
- If OCR is on-device ML Kit only, state that text recognition runs on device.
- No advertising SDK.

---

## Task 7: Internal testing track

**Objective:** Upload the first AAB to Google Play internal testing and run real-device QA.

**Checklist:**

- [ ] Create Play Console app.
- [ ] Set package name `com.mebonsoft.memorix`.
- [ ] Add app category/productivity or tools.
- [ ] Add content rating questionnaire.
- [ ] Add Data Safety answers.
- [ ] Add privacy policy URL.
- [ ] Create in-app product `memorix_pro_lifetime`.
- [ ] Upload signed AAB.
- [ ] Add JK tester account.
- [ ] Install through Play internal testing link.
- [ ] Run smoke test for import, search, tag delete, hidden vault, backup, purchase/restore.

---

## Task 8: Public rollout readiness

**Objective:** Decide whether to launch publicly after internal testing.

**Criteria:**

- No data-loss bugs.
- No purchase/restore bugs.
- No startup crash.
- Permissions and privacy wording accepted by Play.
- Store screenshots match actual UI.
- JK has used it for at least 1–2 weeks or explicitly approves faster release.

---

## Immediate Next Action

Proceed with Task 1 first:

1. Build/test audit.
2. Normalize docs to Android Native release reality.
3. Resolve the package migration git status.
4. Produce a release-readiness issue list.
