# Memorix DESIGN.md

> 디자인 시스템 단일 진실 공급원(SSOT). 모든 화면·위젯 작업은 이 문서를 따른다.

## 1. 브랜드 정체성

**Memorix** — 개인 미디어 보관함. "기억은 빠르게, 보관은 조용하게."

| 축 | 톤 |
|----|-----|
| 분위기 | 차분하지만 살아있는 느낌. 회색 톤 일색이 아니라 액센트가 명확. |
| 정체 | 갤러리 앱이지만 보관·검색·정리에 무게. 사진 SNS 아님. |
| 차별 | Work / Personal **두 공간을 색으로 구분** — 한 앱 안에 두 정체성이 공존. |

색상은 흔한 SaaS 그린이나 인스타그램 핑크-퍼플을 의도적으로 회피. 에메랄드 + 바이올렛 조합으로 차별화.

## 2. 컬러 토큰

**원칙: 화면 코드에서 `Color(0xFF...)` 인라인 절대 금지.** 항상 `AppColors.X` 또는 `Theme.of(ctx).colorScheme.X` 사용.

정의 위치: `lib/shared/theme/app_theme.dart` → `AppColors` 클래스.

### 브랜드

| 토큰 | Hex | 용도 |
|-----|-----|-----|
| `brandPrimary` | `#00C896` | Primary 액션 (FAB, 활성 NavBar, 강조 CTA, brand bar) |
| `brandSecondary` | `#7B61FF` | 보조 액센트 (그라디언트, 보조 강조) |

### 공간 액센트

| 토큰 | Hex | 용도 |
|-----|-----|-----|
| `workAccent` | `#1A73E8` | Work 공간 전용 (Work 그라디언트 시작점, Work 배지) |
| `personalAccent` | `#FF6B9D` | Personal 공간 전용 (Personal 그라디언트 시작점, Personal 배지) |

**규칙**: `workAccent`는 Work feature 스코프 안에서만, `personalAccent`는 Personal 안에서만. 혼용 금지.

### 시맨틱

| 토큰 | Hex | 용도 |
|-----|-----|-----|
| `warning` | `#FFB800` | 경고 배지, 미완료 동기화, 주의 필요 |
| `danger` | `#FF6B35` | 오류, 삭제 액션, 영구 손실 경고 |

`Colors.red` / `Colors.orange` 직접 사용 금지 — 위 토큰으로 통일.

### 표면 (Surface)

| 토큰 | Hex | 용도 (Light) |
|-----|-----|---------------|
| `surfaceLight` | `#F6F8FA` | Scaffold 배경 |
| `onSurfaceLight` | `#0E1117` | 본문 텍스트 |
| `borderLight` | `#ECEFF3` | Divider, Card border |
| `inputBorderLight` | `#DDE1E7` | Input 비활성 border |
| `mutedTextLight` | `#8A9099` | 보조 텍스트, 비활성 NavBar |

| 토큰 | Hex | 용도 (Dark) |
|-----|-----|---------------|
| `surfaceDark` | `#0E1117` | Scaffold 배경 |
| `surfaceDarkRaised` | `#161B22` | Card, Input fill |
| `borderDark` | `#262D37` | Divider, Card border |
| `chipDark` | `#1E2530` | Chip 배경 |
| `mutedTextDark` | `#606672` | 보조 텍스트 |

**다크모드 안전 원칙**: `Colors.white` / `Colors.grey` 인라인은 다크모드에서 깨짐. 가능한 한 `Theme.of(ctx).colorScheme.onSurface` / `.onSurface.withOpacity(0.6)` 같은 sematic 사용. 어쩔 수 없이 흰색이 필요한 자리(예: 항상 컬러 배경 위 텍스트)만 `Colors.white` 허용.

### 그라디언트

`AppTheme` 안에 정의됨. 새 그라디언트 추가 금지 — 이 3개만 사용.

| 그라디언트 | 색상 | 용도 |
|-----|-----|-----|
| `AppTheme.primaryGradient` | `brandPrimary → brandSecondary` | 통합 브랜드 표시 (런처 아이콘, 온보딩 CTA) |
| `AppTheme.workGradient` | `workAccent → brandPrimary` | Work 화면 배지·탭 강조 |
| `AppTheme.personalGradient` | `personalAccent → brandSecondary` | Personal 화면 배지·탭 강조 |

## 3. 타이포 스케일

정의 위치: `AppTheme.light/dark` 안 `textTheme`.

**원칙: `TextStyle(fontSize: N)` 인라인 금지.** 항상 `Theme.of(ctx).textTheme.X` 사용.

| 토큰 | size / weight | 용도 |
|-----|--------------|-----|
| `headlineLarge` | 32 / w800 | 빈 화면 큰 제목, 온보딩 |
| `headlineMedium` | 26 / w700 | 화면 진입 페이지 타이틀 |
| `headlineSmall` | 22 / w700 | AppBar 타이틀 (이미 `appBarTheme`에 적용) |
| `titleLarge` | 20 / w700 | 섹션 헤더, 다이얼로그 제목 |
| `titleMedium` | 17 / w600 | 카드 제목, 리스트 항목 강조 |
| `titleSmall` | 15 / w600 | 작은 제목, 폼 라벨 |
| `bodyLarge` | 16 / regular, h1.6 | 본문 |
| `bodyMedium` | 15 / regular, h1.6 | 기본 본문 |
| `bodySmall` | 13 / regular, h1.5 | 보조 본문, 메타 정보 |
| `labelLarge` | 15 / w600 | 버튼 라벨 |
| `labelMedium` | 13 / w500 | 작은 라벨, NavBar |
| `labelSmall` | 12 / regular | 캡션, 타임스탬프 |

letter-spacing은 headline/title에 음수(-0.5/-0.3/-0.2). 타이트한 모던 톤.

## 4. 간격 (Spacing)

정의 위치: `AppColors`와 같은 파일의 `AppSpacing`.

| 토큰 | px | 용도 |
|-----|---|-----|
| `xs` | 4 | 인접 요소 (아이콘+텍스트) |
| `s` | 8 | 항목 내부, chip 간격 |
| `m` | 12 | 카드 내부 패딩, 리스트 아이템 |
| `l` | 16 | 섹션 가장자리, 카드 외부 |
| `xl` | 24 | 화면 가로 여백, 큰 섹션 사이 |
| `xxl` | 32 | 빈 화면 여백, 온보딩 |

**원칙**: `EdgeInsets.symmetric(horizontal: 14)` 같은 magic number 사용 금지 — 위 토큰 또는 곱셈(`AppSpacing.l + AppSpacing.xs`)으로 표현.

## 5. 모서리 둥글기 (Radius)

정의 위치: `AppRadius`. 9가지 산재된 값을 5단계로 정리.

| 토큰 | px | 용도 |
|-----|---|-----|
| `small` | 8 | chip, badge, 태그, 작은 컨테이너 |
| `medium` | 12 | input, button, 일반 card |
| `large` | 16 | surface card (테마 기본), bottom sheet |
| `pill` | 20 | 둥근 chip, avatar 보더 |
| `blob` | 28 | hero badge, 큰 아이콘 컨테이너 (예: empty state 아이콘) |

**원칙**: `BorderRadius.circular(14)` 같은 임의 값 금지. 신규 디자인이 위 5단계로 표현 안 되면 디자인 자체를 재고.

## 6. 컴포넌트 가이드

테마(`appBarTheme`, `cardTheme`, `chipTheme`, `inputDecorationTheme`, `floatingActionButtonTheme`, `navigationBarTheme`)에 이미 스타일이 정의됨. **위젯 코드에서 이 스타일을 덮어쓰지 말 것.**

| 컴포넌트 | 기본 동작 | 주의 |
|---------|---------|------|
| `AppBar` | `centerTitle: false`, 22px w700, `surface` 배경, elevation 0 | 화면별 인라인 `AppBar` 정의 시 스타일 다시 박지 말 것 — 테마에 맡김 |
| `Card` | 흰색(다크: surfaceDarkRaised), 1px border, radius 16, elevation 0 | shadow 추가 금지 |
| `Chip` | 회색 배경(다크: chipDark), 선택 시 primary 15% alpha, radius 20 | 선택 상태는 chipTheme에 위임 |
| `Input` | 흰색 fill, radius 12, focused 시 primary 1.5px | 외곽선 색 인라인 변경 금지 |
| `FAB` | brandPrimary 배경, radius 16, elevation 4 | 색상·크기 변경 금지 — 모든 화면 일관 |
| `NavigationBar` | 72px 높이, 활성 26px 아이콘 + primary, 비활성 muted | 인라인 변경 금지 |

### 미디어 위젯 (`lib/shared/widgets/`)

| 위젯 | 책임 |
|-----|------|
| `MediaThumbnailCard` | 그리드/타임라인의 단일 항목 카드 (썸네일 + 오버레이). 영상은 play, 미동기화는 cloud_upload |
| `MediaGrid` | 단순 SliverGrid 래퍼 |
| `MediaTimeline` | 월별 섹션 헤더 + 그리드 |
| `CaptureBottomSheet` | 카메라/갤러리/문서 선택 진입점 |
| `EncryptedImage` | Personal vault 이미지 디스플레이 (복호화 처리 포함) |
| `TagChips` / `PeopleChips` | 미디어 메타데이터 칩 |

신규 미디어 관련 UI는 위 위젯을 우선 재사용. 직접 `Container` + `Image.file`로 만들지 말 것.

## 7. 화면 구조 원칙

- **Scaffold 배경 = surface**. 그 위에 카드를 올려 깊이감.
- **AppBar 우측 액션 최대 3개**. 4개 이상이면 더보기 메뉴.
- **FAB는 화면당 1개**. Work 추가/Personal 추가 같은 단일 액션.
- **빈 상태(empty state)는 기능이다**. 일러스트 + 한 줄 설명 + 1차 액션 필수. 그냥 "데이터 없음" 텍스트 금지.
- **로딩**: 데이터 로딩은 `CircularProgressIndicator` (테마 기본). 진행률 있을 때만 `LinearProgressIndicator`.
- **에러**: SnackBar 빨강(`danger` 토큰), 메시지에 다음 액션 포함 ("다시 시도" 버튼 등).

## 8. 다크모드

Light/Dark 양쪽 정의됨. 새 화면 작성 시:

1. `Colors.white` / `Colors.grey` / `Colors.black` 인라인 금지 (정당한 예외 있음 — 컬러 배지 위 흰 텍스트 등)
2. 색상은 `Theme.of(ctx).colorScheme.X` 우선
3. 카드/표면은 `Theme.of(ctx).cardColor` 사용
4. 본문 텍스트는 textTheme 사용 (자동으로 다크 컬러 적용)

## 9. 모션 (Motion)

현 단계 (Stage 11 완료) 모션은 거의 없음. 추가 시 원칙:

- **목적이 있는 모션만**. 장식적 애니메이션 금지.
- **120-200ms** 사이 짧게. 250ms 넘기지 말 것.
- **easing**: 진입 `Curves.easeOut`, 나가기 `Curves.easeIn`, 양방향 `Curves.easeInOut`.
- **Hero transitions**: 미디어 썸네일 → 풀스크린 viewer 같은 명확한 연속성에만.

## 10. 접근성 체크리스트

신규 화면 머지 전 확인:

- [ ] 본문 폰트 ≥ 14px (`bodyMedium` 15px / `bodySmall` 13px은 캡션/메타에만)
- [ ] 색상 대비 4.5:1 이상 (텍스트). 토큰을 따르면 대부분 통과
- [ ] 터치 타겟 최소 44×44 (FAB 56, NavBar 26 아이콘 + 패딩으로 충분)
- [ ] 색만으로 정보 전달 안 함 (예: 동기화 상태는 색 + 아이콘)
- [ ] Personal vault 잠금 화면에는 `Semantics(label: ...)` 명시

## 11. 안티패턴 (절대 하지 말 것)

| 안티패턴 | 대신 |
|---------|-----|
| `Color(0xFF00C896)` 인라인 | `AppColors.brandPrimary` |
| `Colors.grey[500]` | `Theme.of(ctx).colorScheme.onSurface.withOpacity(0.6)` 또는 `AppColors.mutedTextLight` |
| `TextStyle(fontSize: 13)` | `Theme.of(ctx).textTheme.bodySmall` |
| `EdgeInsets.symmetric(horizontal: 14)` | `EdgeInsets.symmetric(horizontal: AppSpacing.l)` |
| `BorderRadius.circular(10)` | `BorderRadius.circular(AppRadius.medium)` (12) — 임의 값 금지 |
| 새 그라디언트 추가 | 기존 3개 (primary/work/personal) 중 선택, 정 안되면 `office-hours` 토론 |
| 새 액센트 컬러 도입 | 위 7개 토큰 (brandPrimary/Secondary, work/personalAccent, warning/danger, mutedText) 안에서 해결 |
| FAB 배경색 변경 | 금지 — 일관된 primary 액션 |
| AppBar 인라인 스타일 | 테마에 위임 |
| 카드에 그림자 추가 | 금지 — `cardTheme`은 elevation 0 + 1px border 디자인 결정 |

## 12. 리뷰 트리거

다음 작업 시 디자인 리뷰 필수:

- 신규 화면 추가 (`*_screen.dart`)
- 신규 공통 위젯 (`lib/shared/widgets/*.dart`)
- 컬러/타이포 토큰 추가·변경
- 그라디언트 추가
- 다크모드 시각적 변경

도구: `/plan-design-review` (plan 단계), `/design-review` (구현 후 시각 QA).

---

**최종 업데이트**: 2026-04-30
**준수 점수 (현재)**: 5/10 — 토큰은 정의됨, 기존 화면의 인라인 hex/fontSize는 점진 교체 필요. 신규 코드는 즉시 이 문서 준수.
