package com.mebonsoft.memorix.core.locale

enum class AppLanguage(
    val code: String,
    val nativeLabel: String,
) {
    KOREAN("ko", "한국어"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    ;

    companion object {
        val supported: List<AppLanguage> = listOf(KOREAN, ENGLISH, JAPANESE)

        fun fromCode(code: String?): AppLanguage = supported.firstOrNull { it.code == code } ?: KOREAN
    }
}

data class MemorixStrings(
    val navHome: String,
    val navWork: String,
    val navPersonal: String,
    val navSettings: String,
    val settingsTitle: String,
    val settingsSubtitle: String,
    val securitySection: String,
    val contentManagementSection: String,
    val appInfoSection: String,
    val tagManagementTitle: String,
    val tagManagementSubtitle: String,
    val languageTitle: String,
    val languageSubtitle: String,
    val storageTitle: String,
    val resetAllDataTitle: String,
    val versionTitle: String,
) {
    companion object {
        fun forLanguage(language: AppLanguage): MemorixStrings = when (language) {
            AppLanguage.KOREAN -> MemorixStrings(
                navHome = "Home",
                navWork = "Work",
                navPersonal = "Personal",
                navSettings = "설정",
                settingsTitle = "설정",
                settingsSubtitle = "로그인, 앱 잠금, 태그, 언어를 기기 안에서 안전하게 관리합니다.",
                securitySection = "로그인·보안",
                contentManagementSection = "콘텐츠 관리",
                appInfoSection = "앱 정보",
                tagManagementTitle = "태그 관리",
                tagManagementSubtitle = "중복·불필요한 태그를 삭제해 선택 목록 정리",
                languageTitle = "언어",
                languageSubtitle = "한국어, English, 日本語",
                storageTitle = "저장소",
                resetAllDataTitle = "전체 초기화",
                versionTitle = "버전",
            )
            AppLanguage.ENGLISH -> MemorixStrings(
                navHome = "Home",
                navWork = "Work",
                navPersonal = "Personal",
                navSettings = "Settings",
                settingsTitle = "Settings",
                settingsSubtitle = "Manage login, app lock, tags, and language safely on this device.",
                securitySection = "Login & security",
                contentManagementSection = "Content management",
                appInfoSection = "App info",
                tagManagementTitle = "Tag management",
                tagManagementSubtitle = "Delete duplicate or unnecessary tags to keep pickers clean",
                languageTitle = "Language",
                languageSubtitle = "Korean, English, Japanese",
                storageTitle = "Storage",
                resetAllDataTitle = "Reset all data",
                versionTitle = "Version",
            )
            AppLanguage.JAPANESE -> MemorixStrings(
                navHome = "Home",
                navWork = "Work",
                navPersonal = "Personal",
                navSettings = "設定",
                settingsTitle = "設定",
                settingsSubtitle = "ログイン、アプリロック、タグ、言語をこの端末で安全に管理します。",
                securitySection = "ログイン・セキュリティ",
                contentManagementSection = "コンテンツ管理",
                appInfoSection = "アプリ情報",
                tagManagementTitle = "タグ管理",
                tagManagementSubtitle = "重複・不要なタグを削除して選択リストを整理",
                languageTitle = "言語",
                languageSubtitle = "韓国語、英語、日本語",
                storageTitle = "ストレージ",
                resetAllDataTitle = "全データ初期化",
                versionTitle = "バージョン",
            )
        }
    }
}
