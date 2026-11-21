package com.mebonsoft.memorix.core.media

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface

/**
 * EXIF orientation 처리.
 *
 * BitmapFactory는 EXIF 회전을 적용하지 않고, 새로 저장한 썸네일 JPEG에는 EXIF가 없다.
 * 그래서 세로로 찍은 사진(대부분 ORIENTATION_ROTATE_90)의 썸네일이 가로로 저장되던 문제를 막는다.
 */
object ExifOrientationSupport {
    fun needsTransform(orientation: Int): Boolean =
        orientation != ExifInterface.ORIENTATION_NORMAL && orientation != ExifInterface.ORIENTATION_UNDEFINED

    /** 90/270도 계열이면 화면에 보이는 가로·세로가 저장된 픽셀과 뒤바뀐다. */
    fun swapsDimensions(orientation: Int): Boolean = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_ROTATE_270,
        ExifInterface.ORIENTATION_TRANSPOSE,
        ExifInterface.ORIENTATION_TRANSVERSE -> true
        else -> false
    }

    /** 화면에 보이는 방향 기준의 (width, height). */
    fun displayDimensions(width: Int?, height: Int?, orientation: Int): Pair<Int?, Int?> =
        if (swapsDimensions(orientation)) height to width else width to height

    fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        if (!needsTransform(orientation)) return bitmap
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
