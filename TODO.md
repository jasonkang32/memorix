# Memorix 남은 작업 및 체크리스트 (`android-native` 기준)

> 상세 변경 이력과 배경은 [`android-native/docs/DEVELOPMENT_LOG.md`](android-native/docs/DEVELOPMENT_LOG.md)를 참고하세요.

## 1. 최신 구현 검증 및 실기기 테스트
- [ ] **DB 7 → 8 마이그레이션 및 3단계 보호(일반/잠금/비밀) 검증**
  - 기존 `isSecret` 항목이 `isLocked`(잠금)로 정상 전환되어 목록에 잠금 카드로 표시되는지 확인
  - 잠금/비밀 항목 상세 진입 시 생체인증 우선 → PIN 폴백 동작 및 60초 세션 유지 확인
  - 암호화 파일(`.mrxsecret`) 이미지 미리보기(Coil `SecretFileFetcher`) 및 영상 재생/공유/내보내기 복호화 확인
- [ ] **세로 사진 썸네일 회전 보정 확인**
  - 신규 등록 시 EXIF 회전 보정(`ExifOrientationSupport`) 및 앱 시작 시 기존 썸네일 1회 자동 복구(`ThumbnailOrientationRepairRunner`) 동작 확인
- [ ] **국가 선택기 및 입력 순서 확인**
  - 등록 화면(`이벤트 날짜 → 위치(국가·지역) → 메모 → 태그`) 및 국가 검색/직접 입력(`CountryPickerField`) 동작 확인
- [ ] **Google Drive 백업/복원 실기기 검증**
  - Drive 연결, 자동 백업(WorkManager), 전체/빠른(`_quick`) 백업 및 최신 백업 복원 확인

## 2. 기능 및 기술 개선 검토
- [ ] **대용량 암호화 영상 복호화 개선**
  - 현재 `SecretMediaCrypto` 복호화가 파일 전체를 메모리에 적재하므로 대용량 영상에서 OOM 가능성이 있음 → 스트리밍/버퍼 기반 복호화 및 비동기 처리 검토
- [ ] **수동 클라우드 백업("지금 동기화") 범위 정책 결정**
  - 현재 수동 백업이 자동 백업 범위 설정(전체/빠른)을 따라감 → 수동 백업은 항상 전체 백업으로 고정할지 결정
- [ ] **Drive 백업 보관 개수(기본 7개) 설정 UI 여부 검토**

## 3. Google Play 출시 준비
- [ ] `android-native/app/build.gradle.kts`에 Release `signingConfigs` 구성 (키스토어/비밀번호는 `local.properties` 또는 환경변수로 분리)
- [ ] Google Cloud Console에 Play App Signing SHA-1 기반 Android OAuth 클라이언트 추가 등록
- [ ] `versionCode` / `versionName` 자동 또는 릴리즈 갱신 규칙 확정 (현재 `5` / `0.1.4`)
- [ ] Google Play Console 내부 테스트 트랙에 서명된 AAB(`app-release.aab`) 업로드 및 `memorix_pro_lifetime` 결제/복원 테스트
