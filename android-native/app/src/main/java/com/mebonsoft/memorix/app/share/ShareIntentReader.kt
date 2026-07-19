package com.mebonsoft.memorix.app.share

import android.content.Intent
import android.net.Uri
import android.os.Build

object ShareIntentReader {
    fun readSharedUris(intent: Intent?): List<Uri> {
        if (intent?.action != Intent.ACTION_SEND && intent?.action != Intent.ACTION_SEND_MULTIPLE) {
            return emptyList()
        }

        val orderedUris = LinkedHashSet<Uri>()
        readExtraStreamUris(intent).forEach(orderedUris::add)
        val clipData = intent.clipData
        if (clipData != null) {
            for (index in 0 until clipData.itemCount) {
                clipData.getItemAt(index).uri?.let(orderedUris::add)
            }
        }
        return orderedUris.toList()
    }

    private fun readExtraStreamUris(intent: Intent): List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.readParcelableExtra(Intent.EXTRA_STREAM))
        Intent.ACTION_SEND_MULTIPLE -> intent.readParcelableArrayListExtra(Intent.EXTRA_STREAM).orEmpty()
        else -> emptyList()
    }
}

@Suppress("DEPRECATION")
private fun Intent.readParcelableExtra(key: String): Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    getParcelableExtra(key, Uri::class.java)
} else {
    getParcelableExtra(key) as? Uri
}

@Suppress("DEPRECATION")
private fun Intent.readParcelableArrayListExtra(key: String): ArrayList<Uri>? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(key, Uri::class.java)
    } else {
        getParcelableArrayListExtra(key)
    }
