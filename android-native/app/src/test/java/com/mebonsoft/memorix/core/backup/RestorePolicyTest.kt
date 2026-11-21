package com.mebonsoft.memorix.core.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorePolicyTest {
    @Test
    fun automaticRestoreIsAllowedOnlyWhenBothDatabaseAndMediaAreEmpty() {
        assertTrue(RestorePolicy.canAutomaticallyRestore(databaseHasRecords = false, mediaFileCount = 0))
        assertFalse(RestorePolicy.canAutomaticallyRestore(databaseHasRecords = true, mediaFileCount = 0))
        assertFalse(RestorePolicy.canAutomaticallyRestore(databaseHasRecords = false, mediaFileCount = 1))
    }

    @Test
    fun automaticRestoreRequiresUserOptIn() {
        assertFalse(RestorePolicy.shouldAutomaticallyRestore(enabled = false, databaseHasRecords = false, mediaFileCount = 0))
        assertTrue(RestorePolicy.shouldAutomaticallyRestore(enabled = true, databaseHasRecords = false, mediaFileCount = 0))
    }
}
