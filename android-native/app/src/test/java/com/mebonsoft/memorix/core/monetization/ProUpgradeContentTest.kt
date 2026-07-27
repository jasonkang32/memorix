package com.mebonsoft.memorix.core.monetization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProUpgradeContentTest {
    @Test
    fun backupRestoreUpgradeCopyExplainsLocalSafetyWithoutQuantityLimit() {
        val content = ProUpgradeContent.forFeature(ProFeature.BackupRestore)

        assertEquals("백업/복구는 Memorix Pro에서", content.title)
        assertTrue(content.body.contains("휴대폰 변경"))
        assertTrue(content.body.contains("등록 수량 제한"))
        assertTrue(!content.body.contains("50개"))
        assertTrue(!content.body.contains("300개"))
        assertTrue(!content.body.contains("500개"))
    }

    @Test
    fun tagAndOcrUpgradeCopyMatchesProValue() {
        val tag = ProUpgradeContent.forFeature(ProFeature.TagManagement)
        val ocr = ProUpgradeContent.forFeature(ProFeature.OcrSearch)

        assertTrue(tag.body.contains("태그"))
        assertTrue(tag.body.contains("정리"))
        assertTrue(ocr.body.contains("사진 속 글자"))
        assertTrue(ocr.body.contains("검색"))
    }
}
