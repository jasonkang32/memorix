package com.mebonsoft.memorix.core.security

import com.mebonsoft.memorix.core.database.dao.MediaDao
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecretMediaMigrationRunner @Inject constructor(
    private val mediaDao: MediaDao,
    private val secretMediaStorageManager: SecretMediaStorageManager,
) {
    suspend fun hardenExistingSecretFiles(): Int {
        var updatedCount = 0
        mediaDao.listLibrary()
            .filter { it.isSecret }
            .forEach { item ->
                val hardened = secretMediaStorageManager.hardenSecretItem(item)
                if (hardened.filePath != item.filePath || hardened.thumbPath != item.thumbPath) {
                    mediaDao.update(hardened)
                    updatedCount += 1
                }
            }
        return updatedCount
    }
}
