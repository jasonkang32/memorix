package com.mebonsoft.memorix.feature.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class HomeQuickImportAction(
    val title: String,
    val description: String,
) {
    PHOTO_VIDEO(
        title = "사진·영상",
        description = "선택한 파일을 그대로 보관합니다.",
    ),
    DATE_RANGE(
        title = "날짜 하루",
        description = "선택한 하루의 사진·영상을 한 번에 등록합니다.",
    ),
    CAMERA(
        title = "카메라 촬영",
        description = "새 사진을 찍어 바로 Memorix에 저장합니다.",
    ),
    DOCUMENT(
        title = "문서 파일",
        description = "PDF·문서·스캔 파일을 보관합니다.",
    ),
}

object HomeQuickImportSupport {
    fun hasCameraPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun hasMediaReadPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return mediaReadPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun mediaReadPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        else -> emptyArray()
    }

    fun cameraPermissionDeniedMessage(): String = "카메라 권한이 필요합니다. 권한을 허용한 뒤 다시 눌러주세요."

    fun mediaPermissionDeniedMessage(): String = "해당 날짜의 모든 사진·영상을 찾으려면 사진/동영상 전체 접근 권한이 필요합니다."
}
