package com.mebonsoft.memorix.core.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.mebonsoft.memorix.core.database.MemorixDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val DATABASE_NAME = "memorix-native.db"
private const val MEDIA_DIR_NAME = "memorix"
private const val MANIFEST_ENTRY = "manifest.json"
private const val DB_DIR_ENTRY = "db/"
private const val FILES_DIR_ENTRY = "files/"

@Serializable
data class MemorixBackupManifest(
    val app: String = "Memorix",
    val packageName: String = "com.mebonsoft.memorix",
    val version: Int = 2,
    val createdAt: Long = System.currentTimeMillis(),
    val databaseName: String = DATABASE_NAME,
    val portableSecretMediaKey: Boolean = true,
    val mode: String = BackupExportMode.Full.name,
)

data class ManagedStorageUsage(
    val mediaBytes: Long,
    val databaseBytes: Long,
) {
    val totalBytes: Long = mediaBytes + databaseBytes
}

enum class BackupExportMode {
    Full,
    Quick,
}

data class BackupProgress(
    val completedFiles: Int,
    val totalFiles: Int,
)

interface MemorixBackupOperations {
    suspend fun calculateManagedStorageUsage(): ManagedStorageUsage
    suspend fun exportBackup(
        destination: Uri,
        mode: BackupExportMode = BackupExportMode.Full,
        onProgress: (BackupProgress) -> Unit = {},
    ): ManagedStorageUsage
    suspend fun exportBackupToFile(
        destination: File,
        mode: BackupExportMode = BackupExportMode.Full,
        onProgress: (BackupProgress) -> Unit = {},
    ): ManagedStorageUsage
    suspend fun restoreBackup(source: Uri): ManagedStorageUsage
    suspend fun restoreBackupFromFile(source: File): ManagedStorageUsage
    suspend fun resetAllData(): ManagedStorageUsage
}

@Singleton
class MemorixBackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MemorixDatabase,
) : MemorixBackupOperations {
    override suspend fun calculateManagedStorageUsage(): ManagedStorageUsage = withContext(Dispatchers.IO) {
        ManagedStorageUsage(
            mediaBytes = mediaRoot().directorySize(),
            databaseBytes = databaseFiles().sumOf { it.lengthOrZero() },
        )
    }

    override suspend fun exportBackup(
        destination: Uri,
        mode: BackupExportMode,
        onProgress: (BackupProgress) -> Unit,
    ): ManagedStorageUsage = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(destination)?.use { output ->
            exportBackupToStream(output, mode, onProgress)
        } ?: error("백업 파일을 열 수 없습니다.")
    }

    override suspend fun exportBackupToFile(
        destination: File,
        mode: BackupExportMode,
        onProgress: (BackupProgress) -> Unit,
    ): ManagedStorageUsage = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        destination.outputStream().use { output -> exportBackupToStream(output, mode, onProgress) }
    }

    override suspend fun restoreBackup(source: Uri): ManagedStorageUsage = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(source)?.use { input ->
            restoreBackupFromStream(input)
        } ?: error("복구 파일을 열 수 없습니다.")
    }

    override suspend fun restoreBackupFromFile(source: File): ManagedStorageUsage = withContext(Dispatchers.IO) {
        source.inputStream().use { input -> restoreBackupFromStream(input) }
    }

    private suspend fun exportBackupToStream(
        output: OutputStream,
        mode: BackupExportMode,
        onProgress: (BackupProgress) -> Unit,
    ): ManagedStorageUsage {
        checkpointDatabase()
        val usage = calculateManagedStorageUsage()
        val dbFiles = databaseFiles().filter { it.exists() }
        val mediaFiles = exportableMediaFiles(mode)
        val totalFiles = dbFiles.size + mediaFiles.size
        var completedFiles = 0
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(Json.encodeToString(MemorixBackupManifest(mode = mode.name)).toByteArray())
            zip.closeEntry()

            dbFiles.forEach { file ->
                zip.writeFile(file, DB_DIR_ENTRY + file.name)
                completedFiles += 1
                onProgress(BackupProgress(completedFiles, totalFiles))
            }

            val root = mediaRoot()
            mediaFiles.forEach { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                zip.writeFile(file, FILES_DIR_ENTRY + MEDIA_DIR_NAME + "/" + relative)
                completedFiles += 1
                onProgress(BackupProgress(completedFiles, totalFiles))
            }
        }
        return usage
    }

    private fun exportableMediaFiles(mode: BackupExportMode): List<File> {
        val root = mediaRoot()
        if (!root.exists()) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                mode == BackupExportMode.Full || !file.relativeTo(root).invariantSeparatorsPath.startsWith("originals/")
            }
            .toList()
    }

    private suspend fun restoreBackupFromStream(input: InputStream): ManagedStorageUsage {
        val tempRoot = File(context.cacheDir, "memorix_restore_${System.currentTimeMillis()}")
        tempRoot.deleteRecursively()
        tempRoot.mkdirs()

        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val safeName = entry.name.safeZipPath()
                    if (!entry.isDirectory && (safeName.startsWith(DB_DIR_ENTRY) || safeName.startsWith(FILES_DIR_ENTRY))) {
                        val outFile = File(tempRoot, safeName)
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { zip.copyTo(it) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            val restoredDb = File(tempRoot, DB_DIR_ENTRY + DATABASE_NAME)
            require(restoredDb.exists()) { "Memorix 백업 DB를 찾을 수 없습니다." }

            database.close()
            mediaRoot().deleteRecursively()
            val restoredMediaRoot = File(tempRoot, FILES_DIR_ENTRY + MEDIA_DIR_NAME)
            if (restoredMediaRoot.exists()) {
                restoredMediaRoot.copyRecursively(mediaRoot(), overwrite = true)
            }

            databaseFiles().forEach { it.delete() }
            File(tempRoot, DB_DIR_ENTRY).listFiles().orEmpty().forEach { file ->
                file.copyTo(File(databasePath().parentFile, file.name), overwrite = true)
            }

            return calculateManagedStorageUsage()
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    override suspend fun resetAllData(): ManagedStorageUsage = withContext(Dispatchers.IO) {
        database.clearAllTables()
        checkpointDatabase()
        mediaRoot().deleteRecursively()
        File(context.cacheDir, "memorix-camera").deleteRecursively()
        calculateManagedStorageUsage()
    }

    private suspend fun checkpointDatabase() {
        runCatching {
            database.withTransaction {
                database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            }
        }
    }

    private fun mediaRoot(): File = File(context.filesDir, MEDIA_DIR_NAME)

    private fun databasePath(): File = context.getDatabasePath(DATABASE_NAME)

    private fun databaseFiles(): List<File> {
        val db = databasePath()
        return listOf(db, File(db.path + "-wal"), File(db.path + "-shm"))
    }
}

private fun ZipOutputStream.writeFile(file: File, entryName: String) {
    putNextEntry(ZipEntry(entryName))
    file.inputStream().use { it.copyTo(this) }
    closeEntry()
}

private fun File.directorySize(): Long {
    if (!exists()) return 0L
    if (isFile) return length()
    return walkTopDown().filter { it.isFile }.sumOf { it.lengthOrZero() }
}

private fun File.lengthOrZero(): Long = if (exists() && isFile) length() else 0L

private fun String.safeZipPath(): String {
    val normalized = replace('\\', '/')
    require(!normalized.startsWith("/") && !normalized.contains("../") && normalized != "..") {
        "안전하지 않은 백업 경로입니다: $this"
    }
    return normalized
}
