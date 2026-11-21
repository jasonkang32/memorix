package com.mebonsoft.memorix.core.media

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.mebonsoft.memorix.core.database.dao.MediaDao
import com.mebonsoft.memorix.core.database.entity.MediaType
import com.mebonsoft.memorix.core.security.SecretMediaCrypto
import com.mebonsoft.memorix.core.security.SecretMediaPathSupport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "memorix_media_repair"
// v2: 잠금·비밀(암호화) 항목까지 포함. v1에서 처리한 일반 항목도 원본에서 다시 만들므로 결과는 같다.
private const val KEY_THUMBNAIL_ORIENTATION_DONE = "thumbnail_orientation_v2"

/**
 * EXIF 회전을 적용하지 않고 만들어진 기존 사진 썸네일을 한 번만 다시 만든다.
 *
 * 잠금·비밀 항목은 원본을 임시로 복호화해 만들고 썸네일을 다시 암호화해 저장한다.
 */
@Singleton
class ThumbnailOrientationRepairRunner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val metadataReader: MediaMetadataReader,
) {
    suspend fun repairOnce(): Int = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_THUMBNAIL_ORIENTATION_DONE, false)) return@withContext 0
        var repaired = 0
        mediaDao.listLibrary()
            .filter { it.mediaType == MediaType.PHOTO }
            .forEach { item ->
                val thumbPath = item.thumbPath ?: return@forEach
                if (!File(item.filePath).isFile) return@forEach
                val encrypted = SecretMediaPathSupport.isEncryptedPath(item.filePath)
                val workDir = File(context.cacheDir, "thumb-repair").apply { mkdirs() }
                val original = if (encrypted) {
                    // 손상된 암호화 파일 하나 때문에 전체 복구가 매번 멈추지 않도록 건너뛴다.
                    val plain = runCatching { SecretMediaCrypto.decrypt(context, File(item.filePath).readBytes()) }.getOrNull()
                        ?: return@forEach
                    File(workDir, "original").also { it.writeBytes(plain) }
                } else {
                    File(item.filePath)
                }
                try {
                    val exif = runCatching { ExifInterface(original) }.getOrNull() ?: return@forEach
                    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                    if (!ExifOrientationSupport.needsTransform(orientation)) return@forEach

                    // 실패해도 기존 썸네일이 깨지지 않도록 임시 파일에 만든 뒤 교체한다.
                    val thumb = File(thumbPath)
                    val temp = File("$thumbPath.tmp")
                    val created = metadataReader.createPhotoThumbnail(original, temp) != null
                    if (created && encrypted) temp.writeBytes(SecretMediaCrypto.encrypt(context, temp.readBytes()))
                    if (created && temp.renameTo(thumb)) {
                        repaired += 1
                    } else {
                        temp.delete()
                    }

                    // 예전에는 저장된 픽셀 기준 크기를 넣었다. 그 값 그대로일 때만 화면 기준으로 뒤집는다.
                    val rawWidth = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0).takeIf { it > 0 }
                    val rawHeight = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0).takeIf { it > 0 }
                    if (ExifOrientationSupport.swapsDimensions(orientation) &&
                        rawWidth != null && rawWidth != rawHeight &&
                        item.width == rawWidth && item.height == rawHeight
                    ) {
                        mediaDao.update(item.copy(width = rawHeight, height = rawWidth))
                    }
                } finally {
                    if (encrypted) original.delete()
                }
            }
        prefs.edit().putBoolean(KEY_THUMBNAIL_ORIENTATION_DONE, true).apply()
        repaired
    }
}
