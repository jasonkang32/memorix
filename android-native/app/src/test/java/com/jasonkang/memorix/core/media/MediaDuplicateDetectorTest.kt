package com.jasonkang.memorix.core.media

import com.jasonkang.memorix.core.database.entity.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaDuplicateDetectorTest {
    @Test
    fun normalizeDisplayName_removesNoiseTokensAndExtension() {
        val normalized = MediaDuplicateDetector.normalizeDisplayName("IMG_20240501 (1).JPG")

        assertEquals("20240501", normalized)
    }

    @Test
    fun inspect_returnsExactConfidenceWhenCoreSignalsMatch() {
        val imported = MediaDuplicateProbe(
            mediaType = MediaType.PHOTO,
            displayName = "IMG_20240501.JPG",
            fileSizeKb = 2048,
            takenAtEpochMillis = 1_715_000_000_000,
        )
        val existing = MediaDuplicateProbe(
            mediaType = MediaType.PHOTO,
            displayName = "img-20240501 (1).jpeg",
            fileSizeKb = 2048,
            takenAtEpochMillis = 1_715_000_000_000,
        )

        val result = MediaDuplicateDetector.inspect(imported, existing)

        assertEquals(DuplicateConfidence.EXACT, result.confidence)
        assertEquals(
            setOf(
                DuplicateReason.SAME_MEDIA_TYPE,
                DuplicateReason.SAME_NORMALIZED_NAME,
                DuplicateReason.SAME_FILE_SIZE,
                DuplicateReason.SAME_TAKEN_AT,
            ),
            result.reasons.toSet(),
        )
    }

    @Test
    fun inspect_returnsHighConfidenceWhenTakenAtIsWithinTolerance() {
        val imported = MediaDuplicateProbe(
            mediaType = MediaType.VIDEO,
            displayName = "VID_9001.mp4",
            fileSizeKb = 8192,
            takenAtEpochMillis = 1_715_000_000_000,
        )
        val existing = MediaDuplicateProbe(
            mediaType = MediaType.VIDEO,
            displayName = "vid 9001 copy.mp4",
            fileSizeKb = 8192,
            takenAtEpochMillis = 1_715_000_020_000,
        )

        val result = MediaDuplicateDetector.inspect(imported, existing)

        assertEquals(DuplicateConfidence.HIGH, result.confidence)
        assertTrue(DuplicateReason.TAKEN_AT_WITHIN_TOLERANCE in result.reasons)
    }

    @Test
    fun inspect_downgradesWhenMediaTypeDiffersEvenIfNameAndSizeMatch() {
        val imported = MediaDuplicateProbe(
            mediaType = MediaType.PHOTO,
            displayName = "scan_001.jpg",
            fileSizeKb = 512,
            takenAtEpochMillis = 1_715_000_000_000,
        )
        val existing = MediaDuplicateProbe(
            mediaType = MediaType.DOCUMENT,
            displayName = "scan 001.pdf",
            fileSizeKb = 512,
            takenAtEpochMillis = 1_715_000_000_000,
        )

        val result = MediaDuplicateDetector.inspect(imported, existing)

        assertEquals(DuplicateConfidence.LOW, result.confidence)
        assertTrue(DuplicateReason.MEDIA_TYPE_MISMATCH in result.reasons)
    }

    @Test
    fun rankCandidates_ordersMostLikelyDuplicateFirst() {
        val imported = MediaDuplicateProbe(
            mediaType = MediaType.PHOTO,
            displayName = "IMG_7777.jpg",
            fileSizeKb = 1024,
            takenAtEpochMillis = 1_715_000_000_000,
        )
        val weak = MediaDuplicateProbe(
            id = "weak",
            mediaType = MediaType.PHOTO,
            displayName = "IMG_7777_edited.jpg",
            fileSizeKb = 1024,
            takenAtEpochMillis = 1_715_999_000_000,
        )
        val exact = MediaDuplicateProbe(
            id = "exact",
            mediaType = MediaType.PHOTO,
            displayName = "img-7777 (1).jpeg",
            fileSizeKb = 1024,
            takenAtEpochMillis = 1_715_000_000_000,
        )

        val ranked = MediaDuplicateDetector.rankCandidates(imported, listOf(weak, exact))

        assertEquals(listOf("exact", "weak"), ranked.mapNotNull { it.probe.id })
        assertEquals(DuplicateConfidence.EXACT, ranked.first().confidence)
    }
}
