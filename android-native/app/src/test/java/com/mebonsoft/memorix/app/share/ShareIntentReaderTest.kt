package com.mebonsoft.memorix.app.share

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ShareIntentReaderTest {
    @Test
    fun readSharedUris_extractsSingleExtraStreamUriFromSendIntent() {
        val uri = Uri.parse("content://gallery/photo/1")
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
        }

        assertEquals(listOf(uri), ShareIntentReader.readSharedUris(intent))
    }

    @Test
    fun readSharedUris_extractsMultipleExtraStreamUrisFromSendMultipleIntent() {
        val first = Uri.parse("content://gallery/photo/1")
        val second = Uri.parse("content://gallery/photo/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
        }

        assertEquals(listOf(first, second), ShareIntentReader.readSharedUris(intent))
    }

    @Test
    fun readSharedUris_extractsAllClipDataUrisFromSendMultipleIntent() {
        val first = Uri.parse("content://gallery/photo/1")
        val second = Uri.parse("content://gallery/video/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            clipData = ClipData("shared-media", arrayOf("*/*"), ClipData.Item(first)).apply {
                addItem(ClipData.Item(second))
            }
        }

        assertEquals(listOf(first, second), ShareIntentReader.readSharedUris(intent))
    }

    @Test
    fun readSharedUris_deduplicatesExtraStreamAndClipDataWhileKeepingOrder() {
        val first = Uri.parse("content://gallery/photo/1")
        val second = Uri.parse("content://gallery/photo/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            clipData = ClipData("shared-media", arrayOf("image/*"), ClipData.Item(first)).apply {
                addItem(ClipData.Item(second))
            }
        }

        assertEquals(listOf(first, second), ShareIntentReader.readSharedUris(intent))
    }

    @Test
    fun readSharedUris_returnsEmptyForUnsupportedAction() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("content://gallery/photo/1")
        }

        assertTrue(ShareIntentReader.readSharedUris(intent).isEmpty())
    }
}
