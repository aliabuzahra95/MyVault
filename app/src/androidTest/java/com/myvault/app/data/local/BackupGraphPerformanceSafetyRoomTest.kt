package com.myvault.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class BackupGraphPerformanceSafetyRoomTest {
    @Test fun sharedParsedPreflightCannotMaskMissingOrChangedPriorObjectAndRetryIsSafe() = runBlocking {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        for (missing in listOf(true, false)) BackupGraphWriterRoomTest().Fixture().use { f ->
            f.root()
            val before = f.binding()
            val manifest = File(f.store.directory, before.checkpointFileId)
            val original = manifest.readBytes()
            f.edit("Pending change must not be acknowledged")
            var checks = 0
            val writer = InternalBackupGraphWriter(f.db, f.journal,
                PendingBackupCapture(f.db, f.journal, VaultPreferences(base, f.journal)), base.filesDir,
                File(f.root, "staging"), f.store, onProgress = {
                    if (it.stage == GraphBackupStage.CHECKING && ++checks == 2) {
                        // Initial remote fold AND proposed-state preparation have completed.
                        // The resumed precommit must revalidate, not trust the parsed cache.
                        if (missing) check(manifest.delete()) else manifest.writeText("corrupt")
                    }
                })
            try { writer.publish(); fail("Required history object changed between preflights") }
            catch (_: IllegalStateException) { }
            assertEquals(2, checks)
            assertEquals(before, f.binding()); assertEquals(1, f.store.commits().size)
            assertFalse(f.pending().isEmpty())
            val intent = f.db.backupGraphDao().unfinished(f.ctx.accountScope).single()
            manifest.writeBytes(original)
            f.reopen()
            assertFalse(f.writer().resume(intent.operationId).forkDetected)
            assertEquals(2, f.store.commits().size); assertTrue(f.pending().isEmpty())
        }
    }
}
