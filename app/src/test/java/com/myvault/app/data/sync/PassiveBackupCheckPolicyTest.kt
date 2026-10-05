package com.myvault.app.data.sync

import com.myvault.app.data.repository.BackupGraphReadinessState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveBackupCheckPolicyTest {
    private val account = "a@example.com"

    private fun check(state: BackupGraphReadinessState, tip: String = "c10", email: String = account): BackupFreshnessCheck =
        BackupFreshnessCheck(backupFreshness(state), email, "lineage", tip,
            latestBackupNotice(state, tip, null)?.copy(accountEmail = email, lineageId = "lineage"))

    @Test fun verifiedCurrentShowsOnlyQuietBanner() {
        val result = PassiveBackupCheckPolicy().complete(check(BackupGraphReadinessState.CURRENT), 0, null)
        assertEquals(PassiveBackupPresentation.LATEST_BANNER, result.presentation)
        assertNull(result.notice)
        assertEquals(account, result.accountEmail)
    }

    @Test fun thirtySecondForegroundDoesNotRecheckOrRepeatBanner() {
        val policy = PassiveBackupCheckPolicy()
        assertTrue(policy.begin(account, 0))
        policy.complete(check(BackupGraphReadinessState.CURRENT), 0, null)
        assertFalse(policy.begin(account, 30_000))
        assertTrue(policy.begin(account, PassiveBackupCheckPolicy.CheckIntervalMs))
        assertEquals(PassiveBackupPresentation.QUIET,
            policy.complete(check(BackupGraphReadinessState.CURRENT), PassiveBackupCheckPolicy.CheckIntervalMs, null).presentation)
    }

    @Test fun bannerMayReturnAfterMeaningfulInterval() {
        val policy = PassiveBackupCheckPolicy()
        policy.complete(check(BackupGraphReadinessState.CURRENT), 0, null)
        assertEquals(PassiveBackupPresentation.LATEST_BANNER,
            policy.complete(check(BackupGraphReadinessState.CURRENT), PassiveBackupCheckPolicy.BannerIntervalMs, null).presentation)
    }

    @Test fun verifiedNewerPromptsAndLaterDeduplicatesSpecificCommit() {
        val policy = PassiveBackupCheckPolicy()
        val remote = check(BackupGraphReadinessState.RESTORE_REQUIRED)
        val first = policy.complete(remote, 0, null)
        assertEquals(PassiveBackupPresentation.PROMPT, first.presentation)
        assertEquals(LatestBackupNoticeKind.NEWER, first.notice?.kind)
        assertEquals(PassiveBackupPresentation.QUIET, policy.complete(remote, 900_000, "c10").presentation)
        assertEquals(PassiveBackupPresentation.PROMPT,
            policy.complete(check(BackupGraphReadinessState.RESTORE_REQUIRED, "c11"), 1_800_000, "c10").presentation)
    }

    @Test fun localRemoteChangesOfferExplicitAuthoritativeRestore() {
        val result = PassiveBackupCheckPolicy().complete(check(BackupGraphReadinessState.LOCAL_REMOTE_CHANGES), 0, null)
        assertEquals(PassiveBackupPresentation.PROMPT, result.presentation)
        assertEquals(LatestBackupNoticeKind.NEWER, result.notice?.kind)
    }

    @Test fun failedVerificationNeverShowsModalOrFalseSuccess() {
        val policy = PassiveBackupCheckPolicy()
        for (state in listOf(BackupGraphReadinessState.FORK, BackupGraphReadinessState.DIVERGENT,
            BackupGraphReadinessState.UNSUPPORTED, BackupGraphReadinessState.CORRUPT,
            BackupGraphReadinessState.MISSING_ANCESTRY, BackupGraphReadinessState.ACCOUNT_MISMATCH,
            BackupGraphReadinessState.RECOVERY_REQUIRED)) {
            val result = policy.complete(check(state), 0, null)
            assertEquals(state.name, PassiveBackupPresentation.QUIET, result.presentation)
            assertNull(result.notice)
        }
    }

    @Test fun unavailableDriveAndNoRemoteBackupStayQuiet() {
        val policy = PassiveBackupCheckPolicy()
        for (freshness in listOf(BackupFreshness.CHECK_FAILED, BackupFreshness.NO_REMOTE_BACKUP)) {
            assertEquals(PassiveBackupPresentation.QUIET,
                policy.complete(BackupFreshnessCheck(freshness, account), 0, null).presentation)
        }
    }

    @Test fun failuresBackOffAndSuccessResetsBackoff() {
        val policy = PassiveBackupCheckPolicy()
        val failed = BackupFreshnessCheck(BackupFreshness.CHECK_FAILED, account)
        policy.complete(failed, 0, null)
        assertFalse(policy.begin(account, 30_000))
        assertTrue(policy.begin(account, 300_000))
        policy.complete(failed, 300_000, null)
        assertFalse(policy.begin(account, 600_000))
        assertTrue(policy.begin(account, 900_000))
        policy.complete(check(BackupGraphReadinessState.CURRENT), 900_000, null)
        assertFalse(policy.begin(account, 1_200_000))
        assertTrue(policy.begin(account, 1_800_000))
    }

    @Test fun inFlightAdmissionPreventsDuplicateForegroundRequests() {
        val policy = PassiveBackupCheckPolicy()
        assertTrue(policy.begin(account, 0))
        assertFalse(policy.begin(account, 1))
    }

    @Test fun accountAndLineageNotificationStateIsIsolated() {
        val policy = PassiveBackupCheckPolicy()
        assertTrue(policy.begin(account, 0))
        assertTrue(policy.begin("b@example.com", 1))
        policy.complete(check(BackupGraphReadinessState.CURRENT), 0, null)
        assertEquals(PassiveBackupPresentation.LATEST_BANNER,
            policy.complete(check(BackupGraphReadinessState.CURRENT, email = "b@example.com"), 1, null).presentation)
        assertEquals(PassiveBackupPresentation.LATEST_BANNER,
            policy.complete(check(BackupGraphReadinessState.CURRENT).copy(lineageId = "other"), 2, null).presentation)
    }

    @Test fun successfulRestoreToCurrentClearsNewerPrompt() {
        val policy = PassiveBackupCheckPolicy()
        policy.complete(check(BackupGraphReadinessState.RESTORE_REQUIRED), 0, null)
        val result = policy.complete(check(BackupGraphReadinessState.CURRENT), 900_000, "c10")
        assertEquals(PassiveBackupPresentation.LATEST_BANNER, result.presentation)
        assertNull(result.notice)
    }

    @Test fun manualErrorsRemainAvailableWhilePassiveErrorsAreSuppressed() {
        val manual = latestBackupNotice(BackupGraphReadinessState.CORRUPT, null, null)
        assertEquals(LatestBackupNoticeKind.BLOCKED, manual?.kind)
        assertTrue(manual!!.message.contains("Nothing was restored"))
        assertNull(PassiveBackupCheckPolicy().complete(check(BackupGraphReadinessState.CORRUPT), 0, null).notice)
    }

    @Test fun unbackedLocalChangesDoNotProduceFalseAllBackedUpBanner() {
        assertEquals(PassiveBackupPresentation.QUIET,
            PassiveBackupCheckPolicy().complete(check(BackupGraphReadinessState.LOCAL_PENDING), 0, null).presentation)
    }
}
