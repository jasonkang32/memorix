package com.mebonsoft.memorix.feature.home

import android.content.ContentResolver
import android.content.ContentUris
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.mebonsoft.memorix.core.database.entity.MediaSpace
import com.mebonsoft.memorix.core.media.ImportDateRange
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaStoreDayImportSupport {
    fun selectedDayRange(selectedMillis: Long?, zoneId: ZoneId = ZoneId.systemDefault()): ImportDateRange? {
        if (selectedMillis == null) return null
        val selectedDate = Instant.ofEpochMilli(selectedMillis).atZone(zoneId).toLocalDate()
        return ImportDateRange(startDate = selectedDate, endDate = selectedDate)
    }

    fun dayImportRequest(
        selectedMillis: Long?,
        space: MediaSpace,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): MediaStoreDayImportRequest? {
        val selectedDate = selectedDayRange(selectedMillis, zoneId)?.startDate ?: return null
        return MediaStoreDayImportRequest(selectedDate = selectedDate, space = space)
    }

    suspend fun queryVisualMediaUrisForDay(
        resolver: ContentResolver,
        selectedDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<Uri> = withContext(Dispatchers.IO) {
        val startMillis = selectedDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = selectedDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val imageUris = queryMediaUrisForDay(
            resolver = resolver,
            collectionUri = imageCollectionUri(),
            dateTakenColumn = MediaStore.Images.Media.DATE_TAKEN,
            startMillis = startMillis,
            endMillis = endMillis,
        )
        val videoUris = queryMediaUrisForDay(
            resolver = resolver,
            collectionUri = videoCollectionUri(),
            dateTakenColumn = MediaStore.Video.Media.DATE_TAKEN,
            startMillis = startMillis,
            endMillis = endMillis,
        )
        (imageUris + videoUris).distinctBy { it.toString() }
    }

    private fun queryMediaUrisForDay(
        resolver: ContentResolver,
        collectionUri: Uri,
        dateTakenColumn: String,
        startMillis: Long,
        endMillis: Long,
    ): List<Uri> {
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "$dateTakenColumn >= ? AND $dateTakenColumn < ?"
        val args = arrayOf(startMillis.toString(), endMillis.toString())
        val sortOrder = "$dateTakenColumn DESC, ${MediaStore.MediaColumns._ID} DESC"

        return runCatching {
            resolver.query(collectionUri, projection, selection, args, sortOrder).use { cursor ->
                if (cursor == null) return emptyList()
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                buildList {
                    while (cursor.moveToNext()) {
                        add(ContentUris.withAppendedId(collectionUri, cursor.getLong(idColumn)))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun imageCollectionUri(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }

    private fun videoCollectionUri(): Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }
}

data class MediaStoreDayImportRequest(
    val selectedDate: LocalDate,
    val space: MediaSpace,
)
