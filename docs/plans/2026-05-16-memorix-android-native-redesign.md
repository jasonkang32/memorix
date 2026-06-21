# Memorix Android Native 재설계안

> **For Hermes:** 이 문서는 Flutter 기반 Memorix를 Android Native 단일 앱으로 다시 설계하기 위한 제품/기술 청사진이다.

**Goal:** Memorix를 “서버 연동 없는 로컬 사진/영상 관리 Android 앱”으로 단순화하고, Android에서 빠르고 안정적인 개인 미디어 보관 앱으로 재출발한다.

**Current Context:**
- 현재 Memorix는 Flutter 기반 로컬 우선 미디어 보관 앱으로 설계되어 있다.
- 기존 문서에는 Work/Personal 공간 분리, SQLite FTS 검색, 태그 추천, PDF 보고서, Google Drive 동기화, 앱 잠금, 앨범/인물 태깅 등이 포함되어 있다.
- 그러나 새 방향에서는 사용자 요구가 더 명확하다: **“사진을 관리하기 위한 서버연동 없는 앱”**.
- 따라서 이 설계안은 범위를 줄여 **로컬 갤러리/보관/분류/검색**에 집중한다.

**Decision:**
1. Memorix는 Android Native 단일 앱으로 재개발한다.
2. 서버는 두지 않는다.
3. Google Drive, 팀 기능, 인앱결제, 원격 동기화는 전부 제거한다.
4. 핵심은 “빠른 저장, 쉬운 분류, 강한 검색, 안전한 보관”이다.

---

## 1. 제품 재정의

### 1.1 Memorix의 한 줄 정의
**사진·영상·문서를 내 폰 안에서 조용히 정리하는 Android 로컬 보관함**

### 1.2 왜 단순화해야 하나
기존 Memorix는 훌륭하지만 범위가 넓다.
- Work / Personal 이중 공간
- AI 태깅
- PDF 보고서
- Drive 동기화
- Pro/Team 구독
- 인물/앨범/위치/보고서 기능

이 구조는 “강력한 앱”은 되지만, **로컬 사진 관리 앱**으로서의 핵심 속도를 늦춘다.
새 Android 버전은 다음 질문에 집중해야 한다.
- 사진/영상 넣기 쉬운가?
- 나중에 다시 찾기 쉬운가?
- 정리 스트레스가 적은가?
- 앱이 안정적인가?

---

## 2. Android 제품 전략

### 2.1 앱 성격
- 로컬 우선
- 개인용
- 오프라인 중심
- 서버 없음
- 로그인 없음

### 2.2 핵심 가치
1. **가져오기 빠름** — 카메라/갤러리/폴더에서 쉽게 수집
2. **분류 쉬움** — 앨범/태그/즐겨찾기/메모
3. **검색 강함** — 제목/메모/태그/날짜/앨범 기준 검색
4. **안전함** — 앱 잠금, 내부 저장소 우선, 실수 삭제 방지

### 2.3 제외 기능
초기 Android 버전에서는 아래를 넣지 않는다.
- 서버 동기화
- Google Drive
- 인앱결제/플랜 구조
- 팀 기능
- Work/Personal 이중 앱 구조
- PDF 보고서
- 위치 기반 고급 보고서
- 복잡한 AI 파이프라인

필요하면 나중에 일부를 재도입한다.

---

## 3. 권장 기술 스택

- **언어:** Kotlin
- **UI:** Jetpack Compose
- **아키텍처:** MVVM + Repository
- **DI:** Hilt
- **DB:** Room + FTS4/FTS5 대체 검색 테이블
- **파일 접근:** Android Storage Access Framework / MediaStore import
- **썸네일:** Coil + 자체 preview 생성
- **백그라운드 작업:** WorkManager
- **보안 저장:** EncryptedSharedPreferences
- **앱 잠금:** BiometricPrompt
- **영상 재생:** Media3 ExoPlayer
- **이미지 확대 보기:** Compose + Zoom/Pan 구현 또는 검증된 라이브러리

---

## 4. 프로젝트 구조

**Create:** `/home/mebon/project/memorix/android-native/`

```text
memorix/
├── android-native/
│   ├── app/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── gradle/
├── lib/                    # 기존 Flutter 코드 보관
├── test/
├── TODO.md
└── docs/
    └── plans/
```

### 패키지 구조
```text
android-native/app/src/main/java/com/jasonkang/memorix/
├── app/
│   ├── MemorixApplication.kt
│   ├── di/
│   └── navigation/
├── core/
│   ├── common/
│   ├── designsystem/
│   ├── database/
│   ├── media/
│   ├── security/
│   └── util/
├── feature/
│   ├── home/
│   ├── importmedia/
│   ├── albums/
│   ├── viewer/
│   ├── search/
│   ├── detail/
│   ├── settings/
│   └── trash/
└── worker/
```

---

## 5. 데이터 모델 재설계

기존 Work/Personal 이중 구조는 과감히 단순화한다.

### 5.1 핵심 엔티티

#### media_items
- `id: String`
- `type: photo | video | document`
- `title: String?`
- `note: String?`
- `album_id: String?`
- `file_path: String`
- `thumb_path: String?`
- `mime_type: String`
- `file_size: Long`
- `captured_at: Instant?`
- `imported_at: Instant`
- `is_favorite: Boolean`
- `is_archived: Boolean`
- `is_trashed: Boolean`
- `duration_ms: Long?`
- `width: Int?`
- `height: Int?`

#### albums
- `id: String`
- `name: String`
- `cover_media_id: String?`
- `created_at: Instant`
- `sort_order: Int`

#### tags
- `id: String`
- `name: String`
- `color: String?`
- `created_at: Instant`

#### media_tags
- `media_id: String`
- `tag_id: String`

#### people (선택, v1.5)
초기 MVP에서는 제외 가능

#### media_search
- `media_id`
- `title`
- `note`
- `tag_text`
- `album_text`

Room + FTS 테이블로 검색한다.

### 5.2 삭제 정책
- 앱 내 삭제 → 휴지통 이동
- 휴지통에서 30일 후 완전 삭제 옵션
- 즉시 영구 삭제는 상세 화면에서만 노출

---

## 6. 파일 저장 전략

### 6.1 저장 원칙
- 외부 서버 업로드 없음
- 앱 내 관리용 원본은 **앱 전용 저장소**에 복사 보관
- 원본 갤러리 삭제는 사용자가 명시적으로 선택할 때만

### 6.2 권장 디렉터리
```text
App-specific storage
memorix/
  originals/
    2026/05/{uuid}.jpg
    2026/05/{uuid}.mp4
  thumbs/
    2026/05/{uuid}.jpg
  documents/
    2026/05/{uuid}.pdf
  exports/
  db/
    memorix.db
```

### 6.3 가져오기 흐름
1. 사용자가 카메라/갤러리/문서 선택
2. URI 메타데이터 읽기
3. 내부 저장소에 복사
4. 썸네일 생성
5. DB 레코드 저장
6. 필요 시 앨범/태그 선택 바텀시트

### 6.4 대량 가져오기
기존 TODO에 있던 “기간 지정 자동 가져오기”는 좋은 기능이다.
다만 초기에 이렇게 단순화한다.
- 1차: 다중 선택 가져오기
- 2차: 날짜 범위 기반 일괄 가져오기
- 3차: 중복 감지와 추천 정리

---

## 7. MVP 화면 설계

### 7.1 포함 화면
1. **Home**
2. **Albums**
3. **Import**
4. **Search**
5. **Viewer**
6. **Media Detail Edit**
7. **Settings**
8. **Trash**

### 7.2 하단 탭
- 홈
- 앨범
- 검색
- 설정

가져오기는 Floating Action Button 하나로 통일.

### 7.3 Home
홈에는 아래를 보여준다.
- 최근 추가한 항목
- 즐겨찾기
- 미분류 항목
- 최근 앨범
- 빠른 가져오기 버튼

### 7.4 Albums
- 앨범 목록
- 새 앨범 만들기
- 앨범별 그리드 보기
- 앨범 커버 지정

### 7.5 Search
검색 필터:
- 제목/메모 텍스트
- 태그
- 앨범
- 기간
- 사진/영상 타입
- 즐겨찾기 여부

### 7.6 Viewer
- 사진 확대/축소
- 영상 재생
- 좌우 스와이프
- 즐겨찾기
- 편집 진입
- 삭제/휴지통 이동

### 7.7 Detail Edit
- 제목
- 메모
- 태그
- 앨범 이동
- 촬영일 수정
- 즐겨찾기

---

## 8. 보안/프라이버시 설계

### 8.1 필수
- BiometricPrompt 기반 앱 잠금
- 백그라운드 30초 이상 시 재인증 옵션
- 최근 앱 화면 스크린샷 차단(`FLAG_SECURE`) 옵션

### 8.2 선택
- 특정 앨범 잠금
- 숨김 앨범

초기 MVP에서는 **앱 전체 잠금만 먼저** 구현한다.

---

## 9. 성능 원칙

- 그리드는 Paging 사용 고려
- 썸네일은 원본 직접 렌더링 금지
- 대량 import는 WorkManager로 분리
- 검색은 Room FTS 우선, 메모리 필터 최소화
- 영상 썸네일/재생은 메인 스레드 차단 금지

---

## 10. Flutter에서 가져갈 것 / 버릴 것

### 가져갈 것
- 로컬 우선 철학
- 내부 저장소 구조 아이디어
- SQLite 기반 검색 개념
- 앨범/태그/상세 편집 흐름
- 잠금/보안 요구사항
- TODO.md의 실제 불편사항

### 버릴 것
- Work/Personal 이중 공간 구조
- Pro/Team/구독 구조
- Google Drive 동기화
- Flutter flavor 전략
- PDF 보고서 중심 사고
- “기능이 많아야 좋다”는 방향

---

## 11. 5주 MVP 로드맵

### Week 1
- Android 프로젝트 생성
- Compose / Hilt / Room 기본 세팅
- DB 스키마 정의
- 앱 잠금 기초

### Week 2
- 홈/앨범/상세 없는 기본 미디어 그리드
- 카메라/갤러리/문서 import
- 내부 저장소 복사 + 썸네일 생성

### Week 3
- 앨범 생성/이동
- 메모/제목/즐겨찾기 편집
- Viewer 구현

### Week 4
- 검색(FTS)
- 태그 시스템
- 휴지통

### Week 5
- 안정화
- 대량 가져오기 개선
- 날짜 범위 import 1차 검토
- APK 내부 테스트

---

## 12. 성공 기준

다음이 되면 MVP 성공이다.
- 사진/영상 가져오기가 안정적이다.
- 앱이 저장 중 꺼지지 않는다.
- 앨범/태그/메모로 다시 찾기 쉽다.
- 검색이 빠르다.
- 로컬 앱으로서 심리적 신뢰감이 있다.

### 현재 TODO와 연결되는 우선 수정 포인트
기존 `TODO.md` 기준으로 Android Native에서도 우선순위가 높다.
1. **사진/영상 통합 등록 시 크래시 방지** → 가장 높은 우선순위
2. **날짜 선택 UI 안정화**
3. **입력 필드 placeholder 동작 정리**
4. **기간 지정 대량 import** → MVP 후반 또는 1.1

---

## 13. 추가 확장 순서
MVP 이후:
1. 날짜 범위 자동 import
2. 중복 사진 감지
3. 숨김/보안 앨범
4. 사람 태깅
5. ML Kit 기반 자동 태그 추천
6. PDF export 또는 선택적 외부 공유 개선

AI 태깅은 넣을 수 있지만, **초기 안정성보다 우선하지 않는다.**

---

## 14. 첫 구현 체크리스트

### Task Group A: 기반
- Create: `android-native/`
- Configure: Compose / Hilt / Room / Media3
- Create: app lock

### Task Group B: 데이터
- Create: `media_items`, `albums`, `tags`, `media_tags`, `media_search`
- Create: DAO / Repository
- Create: import pipeline

### Task Group C: UI
- Home
- Albums
- Search
- Viewer
- Detail Edit
- Settings
- Trash

### Task Group D: 검증
- 대량 이미지 import 테스트
- 사진+영상 혼합 import 테스트
- 휴지통 복구 테스트
- 검색 정확도 테스트
- 저사양 기기 스크롤 테스트

---

## 최종 판단
Memorix는 Android Native로 갈 때 오히려 더 좋아질 가능성이 높다.
이유는 다음과 같다.
1. 서버가 없으므로 구조가 단순하다.
2. Android의 미디어/파일 API를 직접 다루는 편이 유리하다.
3. 앱의 핵심은 UI 화려함보다 저장/검색/안정성이다.
4. Flutter에서 넓어진 범위를 Native에서 다시 줄이면 제품의 본질이 더 선명해진다.

이 설계안의 핵심은 **“많은 기능의 사진앱”이 아니라 “믿고 맡길 수 있는 로컬 보관함”** 이다.
