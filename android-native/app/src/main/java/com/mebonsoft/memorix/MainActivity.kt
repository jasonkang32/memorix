package com.mebonsoft.memorix

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.mebonsoft.memorix.app.navigation.MemorixNavHost
import com.mebonsoft.memorix.app.share.PendingShareImportHolder
import com.mebonsoft.memorix.app.share.ShareIntentReader
import com.mebonsoft.memorix.core.designsystem.theme.MemorixTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleShareIntent(intent)
        enableEdgeToEdge()
        setContent {
            MemorixTheme {
                MemorixNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    private fun handleShareIntent(intent: Intent?) {
        val sharedUris = ShareIntentReader.readSharedUris(intent)
        if (sharedUris.isEmpty()) return
        persistReadPermissionIfPossible(intent, sharedUris)
        PendingShareImportHolder.set(sharedUris)
    }

    private fun persistReadPermissionIfPossible(intent: Intent?, uris: List<Uri>) {
        if (intent == null || intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return
        uris.forEach { uri ->
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    }
}
