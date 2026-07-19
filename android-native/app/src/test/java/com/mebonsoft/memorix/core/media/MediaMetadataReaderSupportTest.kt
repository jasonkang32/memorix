package com.mebonsoft.memorix.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaMetadataReaderSupportTest {
    @Test
    fun resolveSourceTakenAt_prefersSourceFileTimeOverEmbeddedMetadataTime() {
        val resolved = resolveSourceTakenAt(
            embeddedTakenAt = 1_000L,
            sourceFileTime = 2_000L,
            nowMillis = 3_000L,
        )

        assertEquals(2_000L, resolved)
    }

    @Test
    fun resolveSourceTakenAt_usesFileTimeWhenEmbeddedMetadataMissing() {
        val resolved = resolveSourceTakenAt(
            embeddedTakenAt = null,
            sourceFileTime = 2_000L,
            nowMillis = 3_000L,
        )

        assertEquals(2_000L, resolved)
    }

    @Test
    fun resolveSourceTakenAt_usesCurrentTimeWhenNoMetadataOrFileTime() {
        val resolved = resolveSourceTakenAt(
            embeddedTakenAt = null,
            sourceFileTime = null,
            nowMillis = 3_000L,
        )

        assertEquals(3_000L, resolved)
    }

    @Test
    fun mediaStoreSecondsToMillis_ignoresZeroValues() {
        assertEquals(null, mediaStoreSecondsToMillis(0L))
        assertEquals(1_700_000_000_000L, mediaStoreSecondsToMillis(1_700_000_000L))
    }
}
