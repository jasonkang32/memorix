# Android Gallery Share to Memorix Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Android 갤러리/파일앱에서 사진·영상 1개 또는 여러 개를 선택한 뒤 공유 버튼으로 Memorix를 선택하면, 선택한 모든 미디어가 Memorix 등록 화면으로 열리고 저장할 수 있게 한다.

**Architecture:** Android `ACTION_SEND`/`ACTION_SEND_MULTIPLE` share intent를 `MainActivity`에서 수신하고, 받은 `content://` URI 목록을 기존 `PendingMediaHolder` + `MediaComposeScreen` 등록 흐름으로 넘긴다. 외부 공유 진입은 기본적으로 Work 등록 화면으로 연결하되, 등록 화면 안에서 Work/Personal 공간을 선택·변경할 수 있는 여지를 남긴다.

**Tech Stack:** Android Native, Kotlin, Jetpack Compose, Navigation Compose, Hilt, Activity/Intent API, Android `ClipData`, Room import pipeline.

---

## 현재 코드 기준 확인

- Android Native 루트: `/home/mebon/project/memorix/android-native`
- 현재 `AndroidManifest.xml`에는 Launcher intent만 있고 공유 intent-filter가 없다.
- 현재 `MainActivity.kt`는 `Intent` 처리가 없고 `MemorixNavHost()`만 표시한다.
- 현재 등록 화면 진입은 `PendingMediaHolder.set(uris)` 후 `Routes.WorkCompose` 또는 `Routes.PersonalCompose`로 이동한다.
- 기존 저장/import는 `MediaComposeViewModel.save()` → `MediaRepository.importMedia(...)` 경로를 타므로 공유 기능도 이 경로를 재사용해야 한다.

## UX 결정

- 갤러리에서 사진/영상 1개 또는 여러 개 공유 → Memorix 선택 → 바로 등록 화면 표시.
- **다중선택 공유는 1차 필수 범위**로 포함한다. 갤러리에서 2개 이상 선택 후 공유하면 Android `ACTION_SEND_MULTIPLE` 또는 `ClipData`로 들어오는 전체 URI를 순서 유지하여 등록 화면에 모두 표시해야 한다.
- 다중선택 공유 시 preview 순서는 갤러리/공유 Intent가 전달한 순서를 최대한 유지한다.
- 다중선택 공유 시 중복 URI가 섞이면 한 번만 등록한다.
- 기본 저장 공간은 **Work**로 둔다.
- 등록 화면에는 선택된 미디어 preview, 이벤트 날짜 자동 감지, 메모/태그 입력, 저장 버튼이 기존과 동일하게 보여야 한다.
- 문서 파일 공유는 2차 범위로 처리 가능하지만, Android share target은 `image/*`, `video/*`, `application/pdf`, `*/*`를 어느 정도 받을 수 있게 설계한다. 다만 초기 QA는 사진/영상 중심.
- 앱이 잠금 상태면 우선 잠금 해제 후 공유 URI가 사라지지 않아야 한다.

---

## Task 1: 공유 Intent 파싱 순수 헬퍼 추가

**Objective:** `ACTION_SEND`/`ACTION_SEND_MULTIPLE`에서 URI 목록을 안정적으로 추출하는 순수 Kotlin 헬퍼를 만든다.

**Files:**
- Create: `android-native/app/src/main/java/com/mebonsoft/memorix/app/share/ShareIntentReader.kt`
- Test: `android-native/app/src/test/java/com/mebonsoft/memorix/app/share/ShareIntentReaderTest.kt`

**Step 1: Write failing tests**

테스트 항목:
- `ACTION_SEND` + `Intent.EXTRA_STREAM` 단일 URI 추출
- `ACTION_SEND_MULTIPLE` + `Intent.EXTRA_STREAM` ArrayList URI 추출
- 갤러리 다중선택 공유처럼 `ACTION_SEND_MULTIPLE` + `ClipData`에 여러 URI가 들어오는 경우 전체 URI 추출
- `ACTION_SEND_MULTIPLE`에서 `EXTRA_STREAM`과 `ClipData`가 동시에 있을 때 중복 제거 + 순서 유지
- `clipData`만 있는 경우 URI 추출
- 지원하지 않는 action이면 빈 목록
- 중복 URI는 제거하고 순서는 유지

**Step 2: Run test to verify failure**

```bash
cd /home/mebon/project/memorix/android-native
./gradlew :app:testDebugUnitTest --tests 'com.mebonsoft.memorix.app.share.ShareIntentReaderTest' --no-daemon
```

Expected: FAIL — `ShareIntentReader` 없음.

**Step 3: Implement helper**

구현 방향:
- `object ShareIntentReader`
- `fun readSharedUris(intent: Intent?): List<Uri>`
- `Intent.ACTION_SEND`, `Intent.ACTION_SEND_MULTIPLE`만 처리
- `Intent.EXTRA_STREAM`와 `clipData` 둘 다 확인
- `LinkedHashSet<Uri>`로 순서 유지 + 중복 제거

**Step 4: Run test to verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mebonsoft.memorix.app.share.ShareIntentReaderTest' --no-daemon
```

Expected: PASS.

---

## Task 2: 공유 진입 상태 Holder 추가

**Objective:** Activity가 받은 공유 URI를 Compose Navigation이 소비할 수 있게 저장하는 작은 상태 holder를 만든다.

**Files:**
- Create: `android-native/app/src/main/java/com/mebonsoft/memorix/app/share/PendingShareImportHolder.kt`
- Test: `android-native/app/src/test/java/com/mebonsoft/memorix/app/share/PendingShareImportHolderTest.kt`

**Step 1: Write failing tests**

테스트 항목:
- `set(uris)` 후 `consume()`하면 같은 URI 목록 반환
- 한 번 consume 후 다시 consume하면 empty
- 빈 목록 set은 기존 pending을 지우거나 empty 유지

**Step 2: Run test to verify failure**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mebonsoft.memorix.app.share.PendingShareImportHolderTest' --no-daemon
```

Expected: FAIL.

**Step 3: Implement holder**

구현 방향:
- `object PendingShareImportHolder`
- 내부 `private var pendingUris: List<Uri> = emptyList()`
- `set(uris: List<Uri>)`, `consume(): List<Uri>`
- 필요하면 `hasPending(): Boolean` 추가

**Step 4: Run test to verify pass**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mebonsoft.memorix.app.share.PendingShareImportHolderTest' --no-daemon
```

Expected: PASS.

---

## Task 3: AndroidManifest에 공유 intent-filter 추가

**Objective:** 갤러리/파일앱 공유 대상 목록에 Memorix가 나타나게 한다.

**Files:**
- Modify: `android-native/app/src/main/AndroidManifest.xml`

**Step 1: Add intent filters under `MainActivity`**

추가할 필터:
- `android.intent.action.SEND`
- `android.intent.action.SEND_MULTIPLE`
- `android.intent.category.DEFAULT`
- MIME:
  - `image/*`
  - `video/*`
  - 선택 범위에 따라 `application/pdf` 또는 `*/*`

권장 1차 구현:
```xml
<intent-filter>
    <action android:name="android.intent.action.SEND" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="image/*" />
    <data android:mimeType="video/*" />
</intent-filter>
<intent-filter>
    <action android:name="android.intent.action.SEND_MULTIPLE" />
    <category android:name="android.intent.category.DEFAULT" />
    <data android:mimeType="image/*" />
    <data android:mimeType="video/*" />
</intent-filter>
```

문서 공유까지 바로 열려면 `application/pdf` 또는 `*/*`를 별도 필터로 추가한다. 단, `*/*`는 공유 대상에 너무 자주 노출될 수 있으므로 사진/영상 QA 후 확장한다.

다중선택 공유 검증 기준:
- `SEND_MULTIPLE` 필터가 반드시 있어야 한다.
- 일부 갤러리 앱은 여러 파일을 `EXTRA_STREAM` ArrayList가 아니라 `ClipData`로만 넘기므로, Manifest 추가만으로 끝내지 말고 Task 1 parser에서 `clipData.itemCount` 전체를 읽어야 한다.
- 일부 갤러리 앱은 사진+영상 혼합 선택 시 MIME type을 `*/*` 또는 `image/*`로만 보낼 수 있으므로, 초기 QA에서 갤러리별 동작을 확인하고 필요하면 `*/*` 필터를 후속으로 추가한다.

**Step 2: Verify manifest merge/compile**

```bash
./gradlew :app:processDebugManifest --no-daemon
```

Expected: SUCCESS.

---

## Task 4: MainActivity에서 최초 공유 Intent 수신 처리

**Objective:** 앱이 꺼진 상태에서 갤러리 공유로 실행되면 URI를 읽어 pending holder에 저장한다.

**Files:**
- Modify: `android-native/app/src/main/java/com/mebonsoft/memorix/MainActivity.kt`
- Test indirectly through `ShareIntentReaderTest`

**Step 1: Wire reader in `onCreate`**

구현 방향:
- `ShareIntentReader.readSharedUris(intent)` 호출
- 결과가 비어 있지 않으면 `PendingShareImportHolder.set(uris)`
- 이후 기존 `setContent { MemorixNavHost() }`

주의:
- Android 공유 URI 권한은 Intent를 통해 일시 부여된다. 앱 내부 import가 같은 Activity 생명주기에서 바로 복사하므로 우선 `takePersistableUriPermission`은 사용하지 않는다.
- 단, 일부 provider에서 persistable flag가 있으면 안전하게 시도하고 실패는 무시하는 helper를 추가할 수 있다.

**Step 2: Compile check**

```bash
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: SUCCESS.

---

## Task 5: 앱 실행 중 새 공유 Intent 처리

**Objective:** 앱이 이미 떠 있는 상태에서 갤러리 공유로 다시 열려도 URI가 반영되게 한다.

**Files:**
- Modify: `android-native/app/src/main/java/com/mebonsoft/memorix/MainActivity.kt`

**Step 1: Override `onNewIntent`**

구현 방향:
- `override fun onNewIntent(intent: Intent)` 추가
- `super.onNewIntent(intent)`
- `setIntent(intent)`
- `ShareIntentReader.readSharedUris(intent)` → pending holder 저장

**Step 2: Decide UI notification mechanism**

문제:
- `PendingShareImportHolder`에만 저장하면 Compose가 자동으로 알지 못할 수 있다.

권장 구현:
- `PendingShareImportHolder`를 `MutableStateFlow<List<Uri>>` 기반으로 만든다.
- `set(uris)`가 flow를 갱신한다.
- `MemorixNavHost`에서 `collectAsStateWithLifecycle()` 또는 `LaunchedEffect`로 pending 공유 URI를 감지해 등록 route로 이동한다.

**Step 3: Add holder flow tests**

테스트 항목:
- set 후 flow/consume이 동작
- consume 후 pending clear

**Step 4: Run tests**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mebonsoft.memorix.app.share.*' --no-daemon
```

Expected: PASS.

---

## Task 6: MemorixNavHost에서 공유 URI를 등록 화면으로 라우팅

**Objective:** Pending 공유 URI가 있으면 Work 등록 화면으로 자동 이동시킨다.

**Files:**
- Modify: `android-native/app/src/main/java/com/mebonsoft/memorix/app/navigation/MemorixNavHost.kt`
- Modify or reuse: `android-native/app/src/main/java/com/mebonsoft/memorix/feature/work/compose/PendingMediaHolder.kt`

**Step 1: Add share import observer**

구현 방향:
- `MemorixUnlockedNavHost` 내부에서 pending share state 수집
- URI가 비어 있지 않으면:
  - `PendingMediaHolder.set(uris)`
  - `PendingShareImportHolder.consume()` 또는 clear
  - `navController.navigate(Routes.WorkCompose)`

**Step 2: Avoid duplicate navigation**

주의:
- Compose recomposition마다 같은 URI로 반복 navigate하면 안 된다.
- consume/clear를 navigate 직전에 확실히 호출한다.
- 현재 route가 `work/compose`이고 기존 작성 중인 내용이 있다면 덮어쓰기 위험이 있다.

초기 정책:
- 외부 공유 intent가 들어오면 새 등록 작업으로 간주하고 `WorkCompose`로 이동한다.
- 기존 compose 상태가 있으면 Back stack을 Work까지 정리한 뒤 새 URI로 compose를 연다.

**Step 3: Route cleanup**

권장:
```kotlin
navController.popBackStack(Routes.Work, inclusive = false)
PendingMediaHolder.set(sharedUris)
navController.navigate(Routes.WorkCompose)
```

`Routes.Work`가 스택에 없으면 `navigate(Routes.Work)` 후 compose로 가는 helper가 필요하다.

**Step 4: Test route helper as pure function where possible**

이미 있는 `TopLevelNavigationSupportTest` 패턴처럼, route 분기/중복 방지 helper를 순수 함수로 분리한다.

---

## Task 7: 잠금 화면과 공유 URI 보존 확인

**Objective:** 앱 잠금이 켜져 있어도 공유 URI가 잠금 해제 후 등록 화면으로 이어지게 한다.

**Files:**
- Modify if needed: `android-native/app/src/main/java/com/mebonsoft/memorix/app/navigation/MemorixNavHost.kt`
- Test: holder unit tests 중심

**Step 1: Validate current auth flow**

현재 구조:
- `MemorixNavHost()`가 `AuthGateState`를 보고 Locked/Unlocked 분기
- 공유 URI holder가 Activity-level singleton이면 Locked 상태에서도 데이터는 보존됨
- `AuthGateState.Unlocked`로 바뀌면 `MemorixUnlockedNavHost`에서 pending share를 consume해야 함

**Step 2: Ensure consume happens only after Unlocked**

구현 정책:
- `MainActivity`는 holder에만 저장
- 실제 consume/navigation은 `MemorixUnlockedNavHost`에서만 처리

**Step 3: Manual QA scenario**

- 앱 잠금 설정 ON
- 갤러리 사진 공유 → Memorix 선택
- PIN/생체 해제
- Work 등록 화면에 공유 사진이 보여야 함

---

## Task 8: 등록 화면 저장/import 기존 기능 회귀 테스트

**Objective:** 공유로 들어온 URI가 기존 등록과 동일하게 저장되는지 ViewModel 수준에서 보장한다.

**Files:**
- Modify: `android-native/app/src/test/java/com/mebonsoft/memorix/feature/work/compose/MediaComposeViewModelTest.kt`

**Step 1: Add regression test**

테스트 항목:
- `setMediaUris(sharedUris)` 후 `save()` 호출
- `FakeMediaRepository.importMedia()`가 `MediaSpace.WORK`와 같은 URI 목록으로 호출됨
- `saveComplete == true`
- 다중선택 공유 URI 3개를 `setMediaUris(sharedUris)`로 넣은 뒤 `save()`하면 3개 URI 전체가 순서대로 `importMedia()`에 전달됨

**Step 2: Run targeted test**

```bash
./gradlew :app:testDebugUnitTest --tests 'com.mebonsoft.memorix.feature.work.compose.MediaComposeViewModelTest' --no-daemon
```

Expected: PASS.

---

## Task 9: 기기 수동 QA 체크리스트

**Objective:** 실제 Android 공유 동작은 unit test만으로 부족하므로 APK 설치 후 수동 검증한다.

**Files:**
- No code files

**QA scenarios:**
1. 갤러리에서 사진 1장 선택 → 공유 → Memorix → Work 등록 화면 표시
2. **갤러리에서 사진 여러 장 다중선택 → 공유 → Memorix → 선택한 개수만큼 preview 표시**
3. **갤러리에서 사진+영상 혼합 다중선택 → 공유 → Memorix → 전체 preview 표시**
4. 갤러리에서 영상 1개 공유 → preview/event date 표시
5. 앱이 완전히 종료된 상태에서 다중선택 공유
6. 앱이 백그라운드에 있는 상태에서 다중선택 공유
7. 앱 잠금 ON 상태에서 다중선택 공유 → 잠금 해제 후 등록 화면
8. 다중선택 공유 등록 화면에서 저장 → Work 목록/Home 통계에 전체 개수 반영
9. 등록 화면에서 Back → 작성 취소 dialog 또는 이전 화면 정상
10. 공유 URI가 저장 후 앱 내부 파일로 복사되어 원본 삭제 후에도 항목이 열리는지 확인

**Optional adb checks:**

공유 intent를 adb로 흉내 내기는 provider URI 권한 때문에 제한이 있으므로, 실제 갤러리/파일앱 수동 테스트를 우선한다.

---

## Task 10: 전체 검증 및 APK 전달

**Objective:** 전체 테스트와 APK 빌드를 통과한 산출물을 JK에게 전달한다.

**Commands:**

```bash
cd /home/mebon/project/memorix/android-native
./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon
ls -lh app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

Expected:
- `BUILD SUCCESSFUL`
- `app-arm64-v8a-debug.apk` 생성

**Delivery:**

Telegram 최종 응답에 아래 파일을 첨부한다.

```text
MEDIA:/home/mebon/project/memorix/android-native/app/build/outputs/apk/debug/app-arm64-v8a-debug.apk
```

---

## Risks / Pitfalls

- `*/*` share target을 열면 Memorix가 너무 많은 앱의 공유 목록에 나타날 수 있다. 1차는 `image/*`, `video/*` 중심이 안전하다.
- Android 공유 URI 권한은 일시 권한이다. 등록 화면까지 너무 오래 방치한 뒤 저장하면 provider에 따라 접근 실패 가능성이 있다. 이 경우 `takePersistableUriPermission` 시도 helper를 추가한다.
- 잠금 화면에서 holder를 너무 빨리 consume하면 잠금 해제 후 URI가 사라질 수 있다. consume은 반드시 Unlocked 이후.
- Compose `LaunchedEffect`가 같은 pending URI를 반복 처리하면 등록 화면이 여러 번 열릴 수 있다. consume/clear 순서를 테스트한다.
- 기존 `PendingMediaHolder`는 단순 singleton이다. 공유 intent와 화면 내부 picker가 동시에 들어오는 edge case를 피하려면 공유용 holder와 화면 handoff holder를 분리한다.
- 외부 공유로 들어온 미디어는 기본 Work로 들어가지만, JK가 원하면 후속으로 공유 진입 시 `Work / Personal 선택` 작은 진입 화면을 추가한다.

---

## Definition of Done

- Android 공유 목록에 Memorix가 표시된다.
- 사진 1장/영상 1개 공유가 Work 등록 화면으로 열린다.
- 사진 여러 장, 사진+영상 혼합 다중선택 공유가 Work 등록 화면으로 열리고 전체 preview가 표시된다.
- 공유로 열린 등록 화면에서 기존 preview, 이벤트 날짜 감지, 메모/태그, 저장 기능이 정상 동작한다.
- 앱 잠금/백그라운드/종료 상태에서도 공유 URI가 사라지지 않는다.
- 단위 테스트와 APK 빌드가 성공한다.
- APK가 Telegram으로 전달된다.
