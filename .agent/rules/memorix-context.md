# Memorix 작업 컨텍스트

- 활성 개발 루트는 `android-native/`(Kotlin + Jetpack Compose)다. `lib/`의 Flutter 코드는 참고용이다.
- 작업 전에 `android-native/docs/DEVELOPMENT_LOG.md`를 먼저 읽는다. 최근 변경, 설계 결정, 남은 일, Windows 빌드 설정, APK 전달 규칙이 있다.
- 저장소 규칙은 `AGENTS.md`와 `CLAUDE.md`를 따른다. 사용자에게 보이는 문구는 한국어가 기본이다.
- keystore, `local.properties`, OAuth 클라이언트 JSON, Play 자격증명은 커밋하지 않는다.
- 모바일 코드를 바꾸면 `cd android-native && ./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`을 실행한다.
- 작업을 마치면 `DEVELOPMENT_LOG.md` 맨 위에 날짜별로 변경과 이유를 추가한다.
