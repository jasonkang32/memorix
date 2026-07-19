package com.mebonsoft.memorix.core.media

import android.net.Uri
import com.mebonsoft.memorix.core.database.entity.MediaType
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

private const val CAMERA_FILE_PROVIDER_SUFFIX = ".fileprovider"
private const val CAMERA_FILE_PREFIX = "camera_capture"
private const val TAKEN_AT_TOLERANCE_MILLIS = 30_000L

data class ImportDateRange(
    val startDate: LocalDate,
    val endDate: LocalDate,
) {
    init {
        require(!endDate.isBefore(startDate)) { "endDate must be on or after startDate" }
    }

    val dayCount: Long = ChronoUnit.DAYS.between(startDate, endDate) + 1

    fun contains(epochMillis: Long, zoneId: ZoneId): Boolean {
        val localDate = Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate()
        return !localDate.isBefore(startDate) && !localDate.isAfter(endDate)
    }
}

data class ImportCandidateMetadata(
    val sourceId: String,
    val displayName: String,
    val takenAtEpochMillis: Long?,
    val mediaType: MediaType,
    val fileSizeKb: Long = 0,
)

data class ImportPreviewItem(
    val uri: Uri,
    val sourceId: String,
    val displayName: String,
    val mediaType: MediaType,
    val takenAtEpochMillis: Long?,
    val fileSizeKb: Long,
    val duplicateMatches: List<DuplicateInspection> = emptyList(),
)

data class ImportPreview(
    val items: List<ImportPreviewItem>,
    val filteredOutCount: Int = 0,
    val dateRange: ImportDateRange? = null,
) {
    val duplicateItemCount: Int = items.count { item ->
        item.duplicateMatches.any { inspection -> inspection.confidence != DuplicateConfidence.NONE }
    }
}

data class DateRangeImportSummary(
    val totalCount: Int,
    val totalSizeKb: Long,
    val oldestDate: LocalDate?,
    val newestDate: LocalDate?,
    val countByType: Map<MediaType, Int>,
)

object DateRangeImportSupport {
    fun selectCandidates(
        candidates: List<ImportCandidateMetadata>,
        range: ImportDateRange,
        zoneId: ZoneId,
    ): List<ImportCandidateMetadata> = candidates
        .asSequence()
        .filter { candidate ->
            val takenAt = candidate.takenAtEpochMillis ?: return@filter false
            range.contains(takenAt, zoneId)
        }
        .sortedWith(
            compareByDescending<ImportCandidateMetadata> { it.takenAtEpochMillis ?: Long.MIN_VALUE }
                .thenBy { it.sourceId }
                .thenBy { it.displayName }
        )
        .toList()

    fun summarizeSelection(
        selected: List<ImportCandidateMetadata>,
        zoneId: ZoneId,
    ): DateRangeImportSummary {
        val dates = selected.mapNotNull { candidate ->
            candidate.takenAtEpochMillis?.let { Instant.ofEpochMilli(it).atZone(zoneId).toLocalDate() }
        }
        val countByType = selected.groupingBy { it.mediaType }.eachCount()

        return DateRangeImportSummary(
            totalCount = selected.size,
            totalSizeKb = selected.sumOf { it.fileSizeKb },
            oldestDate = dates.minOrNull(),
            newestDate = dates.maxOrNull(),
            countByType = countByType,
        )
    }
}

data class PendingCameraCapture(
    val outputFile: File,
    val outputUri: Uri,
    val authority: String,
) {
    fun toImportRequest(): MediaImportManager.ImportRequest = MediaImportManager.ImportRequest(
        uri = outputUri,
        mediaType = MediaType.PHOTO,
    )
}

object CameraCaptureSupport {
    fun fileProviderAuthority(packageName: String): String = packageName + CAMERA_FILE_PROVIDER_SUFFIX

    fun tempPhotoFileName(
        capturedAt: LocalDateTime,
        token: String,
    ): String = buildString {
        append(CAMERA_FILE_PREFIX)
        append('_')
        append(capturedAt.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")))
        append('_')
        append(token)
        append(".jpg")
    }

    fun resolveCapturedUris(
        success: Boolean,
        pendingCapture: PendingCameraCapture?,
    ): List<Uri> = if (success && pendingCapture != null) listOf(pendingCapture.outputUri) else emptyList()
}

enum class DuplicateConfidence(val priority: Int) {
    NONE(0),
    LOW(1),
    HIGH(2),
    EXACT(3),
}

enum class DuplicateReason {
    SAME_MEDIA_TYPE,
    MEDIA_TYPE_MISMATCH,
    SAME_NORMALIZED_NAME,
    SAME_FILE_SIZE,
    SAME_TAKEN_AT,
    TAKEN_AT_WITHIN_TOLERANCE,
}

data class MediaDuplicateProbe(
    val id: String? = null,
    val mediaType: MediaType,
    val displayName: String,
    val fileSizeKb: Long,
    val takenAtEpochMillis: Long?,
)

data class DuplicateInspection(
    val probe: MediaDuplicateProbe,
    val confidence: DuplicateConfidence,
    val reasons: List<DuplicateReason>,
)

object MediaDuplicateDetector {
    fun normalizeDisplayName(displayName: String): String {
        val withoutExtension = displayName.substringBeforeLast('.', displayName)
        val normalized = withoutExtension
            .lowercase(Locale.US)
            .replace(Regex("[_\\-.]+"), " ")
            .replace(Regex("\\(\\d+\\)"), " ")
            .replace(Regex("\\b(copy|edited)\\b"), " ")
            .replace(Regex("\\b(img|image|vid|video|scan|photo|screenshot)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return normalized
    }

    fun inspect(
        imported: MediaDuplicateProbe,
        existing: MediaDuplicateProbe,
    ): DuplicateInspection {
        val reasons = linkedSetOf<DuplicateReason>()
        val sameMediaType = imported.mediaType == existing.mediaType
        if (sameMediaType) {
            reasons += DuplicateReason.SAME_MEDIA_TYPE
        } else {
            reasons += DuplicateReason.MEDIA_TYPE_MISMATCH
        }

        val importedName = normalizeDisplayName(imported.displayName)
        val existingName = normalizeDisplayName(existing.displayName)
        val sameNormalizedName = importedName.isNotBlank() && importedName == existingName
        if (sameNormalizedName) {
            reasons += DuplicateReason.SAME_NORMALIZED_NAME
        }

        val sameFileSize = imported.fileSizeKb > 0 && imported.fileSizeKb == existing.fileSizeKb
        if (sameFileSize) {
            reasons += DuplicateReason.SAME_FILE_SIZE
        }

        val sameTakenAt = imported.takenAtEpochMillis != null && imported.takenAtEpochMillis == existing.takenAtEpochMillis
        val withinTolerance = !sameTakenAt && imported.takenAtEpochMillis != null && existing.takenAtEpochMillis != null &&
            kotlin.math.abs(imported.takenAtEpochMillis - existing.takenAtEpochMillis) <= TAKEN_AT_TOLERANCE_MILLIS
        if (sameTakenAt) {
            reasons += DuplicateReason.SAME_TAKEN_AT
        } else if (withinTolerance) {
            reasons += DuplicateReason.TAKEN_AT_WITHIN_TOLERANCE
        }

        val confidence = when {
            !sameMediaType -> DuplicateConfidence.LOW
            sameNormalizedName && sameFileSize && sameTakenAt -> DuplicateConfidence.EXACT
            sameNormalizedName && sameFileSize && withinTolerance -> DuplicateConfidence.HIGH
            sameNormalizedName && (sameFileSize || sameTakenAt || withinTolerance) -> DuplicateConfidence.LOW
            sameFileSize -> DuplicateConfidence.LOW
            else -> DuplicateConfidence.NONE
        }

        return DuplicateInspection(
            probe = existing,
            confidence = confidence,
            reasons = reasons.toList(),
        )
    }

    fun rankCandidates(
        imported: MediaDuplicateProbe,
        existing: List<MediaDuplicateProbe>,
    ): List<DuplicateInspection> = existing
        .map { candidate -> inspect(imported, candidate) }
        .sortedWith(
            compareByDescending<DuplicateInspection> { it.confidence.priority }
                .thenByDescending { score(it) }
                .thenBy { it.probe.id ?: it.probe.displayName }
        )

    private fun score(inspection: DuplicateInspection): Int = inspection.reasons.fold(0) { acc, reason ->
        acc + when (reason) {
            DuplicateReason.SAME_MEDIA_TYPE -> 1
            DuplicateReason.MEDIA_TYPE_MISMATCH -> -2
            DuplicateReason.SAME_NORMALIZED_NAME -> 3
            DuplicateReason.SAME_FILE_SIZE -> 2
            DuplicateReason.SAME_TAKEN_AT -> 3
            DuplicateReason.TAKEN_AT_WITHIN_TOLERANCE -> 2
        }
    }
}
