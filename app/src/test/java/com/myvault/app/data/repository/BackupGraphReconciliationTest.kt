package com.myvault.app.data.repository

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class BackupGraphReconciliationTest {
    private val bundle = BackupGraphFixtures.generate("readiness")
    private fun objects(name: String): List<GraphObject> {
        val cases = bundle.getJSONArray("cases")
        val fixture = (0 until cases.length()).map(cases::getJSONObject).single { it.getString("name") == name }
        val refs = fixture.getJSONArray("refs")
        return (0 until refs.length()).map { index ->
            val ref = BackupGraphFixtures.ref(refs.getJSONObject(index))
            GraphObject(ref, Base64.getDecoder().decode(bundle.getJSONObject("objects").getString(ref.cloudFileId)))
        }
    }
    private fun local(position: Int? = null, pending: Int = 0): LocalBackupReadiness {
        val id = position?.let(BackupGraphFixtures::uuid)
        val receipt = id?.let { value -> objects("linear").single { BackupGraphProtocol.parse(it).commitId == value }.objectRef }
        return LocalBackupReadiness(pending, true, id, receipt, if (id == null) null else BackupGraphFixtures.lineage,
            if (id == null) null else BackupGraphFixtures.account, id != null, false)
    }
    private fun decision(local: LocalBackupReadiness, name: String? = "linear", legacy: Boolean = true, count: Int = if (name == null) 0 else 1): BackupGraphReadiness {
        val input = name?.let(::objects).orEmpty()
        return reconcileBackupGraph(local, BackupGraphFixtures.account, legacy, count,
            name?.let { BackupGraph.discover(input, BackupGraphFixtures.account, BackupGraphFixtures.lineage) }, input)
    }
    @Test fun historicalVisibilityNeverEstablishesTrustOrEquality() {
        assertEquals(BackupGraphReadinessState.LEGACY_BASELINE_REQUIRED, decision(local(), null).state)
        assertEquals(BackupGraphReadinessState.FIRST_BASELINE_REQUIRED, decision(local(), null, false).state)
        assertEquals(BackupGraphReadinessState.ADOPTION_REQUIRED, decision(local()).state)
        assertEquals(BackupGraphReadinessState.ADOPTION_REQUIRED, decision(local().copy(hasData = false)).state)
        assertEquals(BackupGraphReadinessState.MISSING_NAMESPACE, decision(local(4), null).state)
    }
    @Test fun currentAndBehindStatesProtectPendingLocalWork() {
        assertEquals(BackupGraphReadinessState.CURRENT, decision(local(4)).state)
        assertEquals(BackupGraphReadinessState.LOCAL_PENDING, decision(local(4, 1)).state)
        assertEquals(BackupGraphReadinessState.RESTORE_REQUIRED, decision(local(2)).state)
        assertEquals(BackupGraphReadinessState.LOCAL_REMOTE_CHANGES, decision(local(2, 1)).state)
    }
    @Test fun unsafeInventoryFailsClosed() {
        val expectations = mapOf("fork" to BackupGraphReadinessState.FORK,
            "deeper-fork" to BackupGraphReadinessState.FORK,
            "unsupported" to BackupGraphReadinessState.UNSUPPORTED,
            "missing-parent" to BackupGraphReadinessState.MISSING_ANCESTRY,
            "corrupt-bytes" to BackupGraphReadinessState.CORRUPT,
            "wrong-checkpoint" to BackupGraphReadinessState.CORRUPT)
        expectations.forEach { (fixture, expected) -> assertEquals(fixture, expected, decision(local(1), fixture).state) }
        assertEquals(BackupGraphReadinessState.AMBIGUOUS_NAMESPACE, decision(local(4), count = 2).state)
        assertEquals(BackupGraphReadinessState.DIVERGENT, decision(local(4), "metadata").state)
    }
    @Test fun accountReceiptsAndOriginProofCannotBeBorrowed() {
        assertEquals(BackupGraphReadinessState.ACCOUNT_MISMATCH, decision(local(4).copy(accountId = "another-account")).state)
        assertEquals(BackupGraphReadinessState.RECOVERY_REQUIRED, decision(local(4).copy(proofValid = false)).state)
        assertEquals(BackupGraphReadinessState.RECOVERY_REQUIRED, decision(local(4).copy(recoveryRequired = true)).state)
        assertEquals(BackupGraphReadinessState.DIVERGENT, decision(local(4).copy(expectedReference = local(4).expectedReference!!.copy(cloudFileId = "different-object"))).state)
    }
    @Test fun everyDiagnosticStateKeepsPublicationDisabled() {
        BackupGraphReadinessState.entries.forEach { state ->
            val result = BackupGraphReadiness(state, local(), true)
            assertFalse(result.publicationAllowed)
            assertTrue(result.message().contains("Read-only check"))
            assertFalse(result.message().contains("Production graph mode remains disabled"))
        }
    }
}
