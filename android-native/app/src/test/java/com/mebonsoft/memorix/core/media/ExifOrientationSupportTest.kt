package com.mebonsoft.memorix.core.media

import androidx.exifinterface.media.ExifInterface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExifOrientationSupportTest {
    @Test
    fun portraitCameraPhoto_swapsStoredDimensions() {
        // 휴대폰 세로 사진은 가로로 저장되고 EXIF에 ROTATE_90이 붙는다.
        assertEquals(3000 to 4000, ExifOrientationSupport.displayDimensions(4000, 3000, ExifInterface.ORIENTATION_ROTATE_90))
        assertEquals(3000 to 4000, ExifOrientationSupport.displayDimensions(4000, 3000, ExifInterface.ORIENTATION_ROTATE_270))
    }

    @Test
    fun normalOrUpsideDownPhoto_keepsDimensions() {
        assertEquals(4000 to 3000, ExifOrientationSupport.displayDimensions(4000, 3000, ExifInterface.ORIENTATION_NORMAL))
        assertEquals(4000 to 3000, ExifOrientationSupport.displayDimensions(4000, 3000, ExifInterface.ORIENTATION_ROTATE_180))
        assertEquals(null to null, ExifOrientationSupport.displayDimensions(null, null, ExifInterface.ORIENTATION_ROTATE_90))
    }

    @Test
    fun needsTransform_skipsNormalAndUndefined() {
        assertFalse(ExifOrientationSupport.needsTransform(ExifInterface.ORIENTATION_NORMAL))
        assertFalse(ExifOrientationSupport.needsTransform(ExifInterface.ORIENTATION_UNDEFINED))
        assertTrue(ExifOrientationSupport.needsTransform(ExifInterface.ORIENTATION_ROTATE_90))
        assertTrue(ExifOrientationSupport.needsTransform(ExifInterface.ORIENTATION_FLIP_HORIZONTAL))
    }
}
