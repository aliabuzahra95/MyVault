package com.myvault.app.data.sync

import com.myvault.app.data.repository.BackupGraphReadinessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LatestBackupDetectionTest {
    @Test fun newerCurrentPendingForkUnsupportedAndDeduplication() {
        val newer = latestBackupNotice(BackupGraphReadinessState.RESTORE_REQUIRED, "c10", null)
        assertEquals(LatestBackupNoticeKind.NEWER, newer?.kind)
        assertNull(latestBackupNotice(BackupGraphReadinessState.CURRENT, "c10", null))
        assertEquals(LatestBackupNoticeKind.LOCAL_CHANGES,
            latestBackupNotice(BackupGraphReadinessState.LOCAL_REMOTE_CHANGES, "c10", null)?.kind)
        assertEquals(LatestBackupNoticeKind.BLOCKED,
            latestBackupNotice(BackupGraphReadinessState.FORK, null, null)?.kind)
        assertEquals(LatestBackupNoticeKind.BLOCKED,
            latestBackupNotice(BackupGraphReadinessState.UNSUPPORTED, null, null)?.kind)
        assertNull(latestBackupNotice(BackupGraphReadinessState.RESTORE_REQUIRED, "c10", "c10"))
        assertEquals("c11", latestBackupNotice(BackupGraphReadinessState.RESTORE_REQUIRED, "c11", "c10")?.remoteCommitId)
        assertNull(latestBackupNotice(BackupGraphReadinessState.CURRENT, "c11", "c10"))
        assertNotEquals(latestBackupNoticeStorageKey("a@example.com", "lineage"),
            latestBackupNoticeStorageKey("b@example.com", "lineage"))
    }
}
