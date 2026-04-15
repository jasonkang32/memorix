package com.memorix.memorix

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File

class MainActivity : FlutterFragmentActivity() {
    private val channelName = "memorix/picker"
    private val tag = "MemorixPicker"
    private var pendingResult: Result? = null
    private lateinit var pickLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 현대적 ActivityResultLauncher — 매 호출마다 정상 콜백 보장
        pickLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            handlePickResult(result.resultCode, result.data)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "pickMedia" -> {
                        val maxItems = (call.argument<Int>("maxItems") ?: 50).coerceAtLeast(2)
                        pickMedia(maxItems, result)
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun pickMedia(maxItems: Int, result: Result) {
        Log.d(tag, "pickMedia called maxItems=$maxItems pendingWas=${pendingResult != null}")
        // 이전 호출의 잔여 state 가 있더라도 강제로 덮어씀 (사용자가 이미 이전 픽을 끝냈을 경우)
        pendingResult?.let {
            Log.w(tag, "overwriting stale pendingResult")
            try { it.success(emptyList<String>()) } catch (_: Exception) {}
        }
        pendingResult = result

        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxItems)
            }
        } else {
            Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
            }
        }
        try {
            pickLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(tag, "launch failed: ${e.message}")
            pendingResult = null
            result.error("LAUNCH_FAILED", e.message, null)
        }
    }

    private fun handlePickResult(resultCode: Int, data: Intent?) {
        Log.d(tag, "handlePickResult code=$resultCode hasData=${data != null}")
        val r = pendingResult
        if (r == null) {
            Log.w(tag, "no pending result")
            return
        }
        pendingResult = null

        if (resultCode != Activity.RESULT_OK || data == null) {
            r.success(emptyList<String>())
            return
        }

        val uris = mutableListOf<Uri>()
        val clip = data.clipData
        if (clip != null) {
            for (i in 0 until clip.itemCount) {
                clip.getItemAt(i).uri?.let { uris.add(it) }
            }
        } else {
            data.data?.let { uris.add(it) }
        }
        Log.d(tag, "picked ${uris.size} uris")

        // URI 권한이 만료되기 전에 즉시 캐시 디렉토리로 복사
        Thread {
            val paths = mutableListOf<String>()
            for ((idx, uri) in uris.withIndex()) {
                try {
                    val ext = extForUri(uri)
                    val tmp = File.createTempFile("pick_${System.currentTimeMillis()}_${idx}_", ".$ext", cacheDir)
                    contentResolver.openInputStream(uri)?.use { input ->
                        tmp.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (tmp.exists() && tmp.length() > 0) {
                        paths.add(tmp.absolutePath)
                    } else {
                        Log.w(tag, "empty copy for $uri")
                    }
                } catch (e: Exception) {
                    Log.e(tag, "copy failed for $uri: ${e.message}")
                }
            }
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
