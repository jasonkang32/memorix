package com.mebonsoft.memorix.app.share

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PendingShareImportHolderTest {
    @Test
    fun consume_returnsPendingUrisOnceAndClearsState() {
        val first = Uri.parse("content://gallery/photo/1")
        val second = Uri.parse("content://gallery/video/2")
        PendingShareImportHolder.set(listOf(first, second))

        assertTrue(PendingShareImportHolder.hasPending())
        assertEquals(listOf(first, second), PendingShareImportHolder.consume())
        assertFalse(PendingShareImportHolder.hasPending())
        assertEquals(emptyList<Uri>(), PendingShareImportHolder.consume())
    }

    @Test
    fun setWithEmptyListClearsExistingPendingUris() {
        val uri = Uri.parse("content://gallery/photo/1")
        PendingShareImportHolder.set(listOf(uri))

        PendingShareImportHolder.set(emptyList())

        assertFalse(PendingShareImportHolder.hasPending())
        assertEquals(emptyList<Uri>(), PendingShareImportHolder.consume())
    }
}
