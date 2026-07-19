package com.mebonsoft.memorix.app.share

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object PendingShareImportHolder {
    private val pendingUris = MutableStateFlow<List<Uri>>(emptyList())
    val pending: StateFlow<List<Uri>> = pendingUris

    fun set(uris: List<Uri>) {
        pendingUris.value = uris.toList()
    }

    fun consume(): List<Uri> {
        val result = pendingUris.value
        pendingUris.value = emptyList()
        return result
    }

    fun hasPending(): Boolean = pendingUris.value.isNotEmpty()
}
