package com.memorix.memorix

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class MainActivity : FlutterFragmentActivity() {
    private val channelName = "memorix/picker"
    private val tag = "MemorixPicker"
    private lateinit var visualPickerLauncher: ActivityResultLauncher<PickVisualMediaRequest>
    private lateinit var openDocumentLauncher: ActivityResultLauncher<Array<String>>

    companion object {
        // companion object에 저장해 액티비티 재생성 시에도 유지
        private var pendingResult: Result? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        visualPickerLauncher = registerForActivityResult(
            ActivityResultContracts.PickMultipleVisualMedia(50)
        ) { uris ->
            handlePickResult(uris)
        }

        openDocumentLauncher = registerForActivityResult(
            ActivityResultContracts.OpenMultipleDocuments()
        ) { uris ->
            handlePickResult(uris)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "pickMedia" -> {
                        pickMedia(result)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun pickMedia(result: Result) {
        Log.d(tag, "pickMedia called pendingWas=${pendingResult != null}")
        pendingResult?.let {
            Log.w(tag, "overwriting stale pendingResult")
            try { it.success(emptyList<String>()) } catch (_: Exception) {}
        }
        pendingResult = result

        try {
            val request = PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageAndVideo
            )
            visualPickerLauncher.launch(request)
        } catch (e: Exception) {
            Log.w(tag, "visual picker launch failed, fallback to open document: ${e.message}")
            try {
                openDocumentLauncher.launch(arrayOf("image/*", "video/*"))
            } catch (fallbackError: Exception) {
                Log.e(tag, "fallback launch failed: ${fallbackError.message}")
                pendingResult = null
                result.error("LAUNCH_FAILED", fallbackError.message, null)
            }
        }
    }

    private fun handlePickResult(uris: List<Uri>) {
        Log.d(tag, "handlePickResult picked=${uris.size}")
        val r = pendingResult
        if (r == null) {
            Log.w(tag, "no pending result")
            return
        }
        pendingResult = null

        if (uris.isEmpty()) {
            r.success(emptyList<String>())
            return
        }

        Thread {
            val copyPool = Executors.newSingleThreadExecutor()
            val paths = mutableListOf<String>()
            for ((idx, uri) in uris.withIndex()) {
                val ext = extForUri(uri)
                val tmp = File.createTempFile(
                    "pick_${System.currentTimeMillis()}_${idx}_",
                    ".$ext",
                    cacheDir
                )
                val future = copyPool.submit {
                    contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                try {
                    future.get(60, TimeUnit.SECONDS)
                    if (tmp.exists() && tmp.length() > 0) {
                        paths.add(tmp.absolutePath)
                    } else {
                        Log.w(tag, "empty copy for $uri")
                        tmp.delete()
                    }
                } catch (e: TimeoutException) {
                    Log.e(tag, "timeout copying $uri after 60s, skipping")
                    future.cancel(true)
                    tmp.delete()
                } catch (e: Exception) {
                    Log.e(tag, "copy failed for $uri: ${e.message}")
                    tmp.delete()
                }
            }
            copyPool.shutdown()
            Log.d(tag, "returning ${paths.size} paths")
            runOnUiThread {
                try {
                    r.success(paths)
                } catch (e: Exception) {
                    Log.e(tag, "result.success threw: ${e.message}")
                }
            }
        }.start()
    }

    private fun extForUri(uri: Uri): String {
        val mime = contentResolver.getType(uri) ?: ""
        return when {
            mime.contains("jpeg") -> "jpg"
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("heic") -> "heic"
            mime.contains("heif") -> "heif"
            mime.contains("bmp") -> "bmp"
            mime.contains("mp4") -> "mp4"
            mime.contains("quicktime") -> "mov"
            mime.startsWith("video/") -> "mp4"
            mime.startsWith("image/") -> "jpg"
            else -> "bin"
        }
    }
}
