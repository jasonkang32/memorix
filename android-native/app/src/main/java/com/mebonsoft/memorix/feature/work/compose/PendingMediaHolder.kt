package com.mebonsoft.memorix.feature.work.compose

import android.net.Uri

object PendingMediaHolder {
    private var pendingUris: List<Uri> = emptyList()

    fun set(uris: List<Uri>) {
        pendingUris = uris.toList()
    }

    fun consume(): List<Uri> {
        val result = pendingUris
        pendingUris = emptyList()
        return result
    }
}
