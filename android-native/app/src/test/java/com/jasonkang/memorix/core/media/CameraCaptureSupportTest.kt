package com.jasonkang.memorix.core.media

import android.net.Uri
import com.jasonkang.memorix.core.database.entity.MediaType
import java.io.File
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraCaptureSupportTest {
    @Test
    fun fileProviderAuthority_usesPackageNameSuffix() {
        val result = CameraCaptureSupport.fileProviderAuthority("com.jasonkang.memorix")

        assertEquals("com.jasonkang.memorix.fileprovider", result)
    }

    @Test
    fun tempPhotoFileName_includesTimestampAndToken() {
        val result = CameraCaptureSupport.tempPhotoFileName(
            capturedAt = LocalDateTime.of(2026, 5, 17, 21, 45, 3),
            token = "abc123",
        )

        assertEquals("camera_capture_20260517_214503_abc123.jpg", result)
    }

    @Test
    fun pendingPhotoCapture_toImportRequest_marksPhotoType() {
        val pending = PendingCameraCapture(
            outputFile = File("/tmp/memorix-camera/camera_capture.jpg"),
            outputUri = Uri.parse("content://com.jasonkang.memorix.fileprovider/memorix-camera/camera_capture.jpg"),
            authority = "com.jasonkang.memorix.fileprovider",
        )

        val result = pending.toImportRequest()

        assertEquals(MediaType.PHOTO, result.mediaType)
        assertEquals(pending.outputUri, result.uri)
    }

    @Test
    fun resolveCapturedUris_returnsSingleUriWhenCaptureSucceeds() {
        val pending = PendingCameraCapture(
            outputFile = File("/tmp/memorix-camera/camera_capture.jpg"),
            outputUri = Uri.parse("content://com.jasonkang.memorix.fileprovider/memorix-camera/camera_capture.jpg"),
            authority = "com.jasonkang.memorix.fileprovider",
        )

        val result = CameraCaptureSupport.resolveCapturedUris(
            success = true,
            pendingCapture = pending,
        )

        assertEquals(listOf(pending.outputUri), result)
    }

    @Test
    fun resolveCapturedUris_returnsEmptyListWhenCaptureFails() {
        val result = CameraCaptureSupport.resolveCapturedUris(
            success = false,
            pendingCapture = null,
        )

        assertTrue(result.isEmpty())
    }
}
