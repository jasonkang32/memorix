package com.mebonsoft.memorix.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeQuickImportSupportTest {
    @Test
    fun quickImportActions_explainDifferentImportPurposes() {
        assertEquals("사진·영상", HomeQuickImportAction.PHOTO_VIDEO.title)
        assertTrue(HomeQuickImportAction.PHOTO_VIDEO.description.contains("그대로 보관"))

        assertEquals("날짜 하루", HomeQuickImportAction.DATE_RANGE.title)
        assertTrue(HomeQuickImportAction.DATE_RANGE.description.contains("한 번에 등록"))

        assertEquals("카메라 촬영", HomeQuickImportAction.CAMERA.title)
        assertTrue(HomeQuickImportAction.CAMERA.description.contains("새 사진"))

        assertEquals("문서 파일", HomeQuickImportAction.DOCUMENT.title)
        assertTrue(HomeQuickImportAction.DOCUMENT.description.contains("PDF"))
    }

    @Test
    fun cameraPermissionDeniedMessage_isUserActionable() {
        val message = HomeQuickImportSupport.cameraPermissionDeniedMessage()

        assertTrue(message.contains("카메라 권한"))
        assertTrue(message.contains("다시"))
    }
}
