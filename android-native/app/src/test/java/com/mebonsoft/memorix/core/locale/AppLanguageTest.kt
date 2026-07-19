package com.mebonsoft.memorix.core.locale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLanguageTest {
    @Test
    fun supportedLanguages_areKoreanEnglishAndJapaneseOnly() {
        assertEquals(listOf(AppLanguage.KOREAN, AppLanguage.ENGLISH, AppLanguage.JAPANESE), AppLanguage.supported)
    }

    @Test
    fun fromCode_fallsBackToKoreanForUnknownCode() {
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromCode("ko"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromCode("ja"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromCode("fr"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromCode(null))
    }

    @Test
    fun settingsTitle_isTranslatedForEachSupportedLanguage() {
        assertEquals("설정", MemorixStrings.forLanguage(AppLanguage.KOREAN).settingsTitle)
        assertEquals("Settings", MemorixStrings.forLanguage(AppLanguage.ENGLISH).settingsTitle)
        assertEquals("設定", MemorixStrings.forLanguage(AppLanguage.JAPANESE).settingsTitle)
        assertTrue(MemorixStrings.forLanguage(AppLanguage.JAPANESE).tagManagementTitle.contains("タグ"))
    }
}
