package com.myvault.app.data.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveAccountMetadataTest {
    @Test
    fun `account metadata round trips independently`() {
        val source = mapOf(
            "account-a@example.com" to GoogleDriveSyncMetadata(lastSyncAt = 100L, lastManifestAt = 90L),
            "account-b@example.com" to GoogleDriveSyncMetadata(lastSyncAt = 200L, lastManifestAt = 180L),
        )

        val restored = source.toGoogleDriveSyncMetadataEntries().toGoogleDriveSyncMetadataByAccount()

        assertEquals(source, restored)
        assertEquals(90L, restored.getValue("account-a@example.com").lastManifestAt)
        assertEquals(180L, restored.getValue("account-b@example.com").lastManifestAt)
    }

    @Test
    fun `account identity is case insensitive and trimmed`() {
        val entries = mapOf(
            "  Account-A@Example.com " to GoogleDriveSyncMetadata(lastSyncAt = 11L, lastManifestAt = 7L),
        ).toGoogleDriveSyncMetadataEntries()

        val restored = entries.toGoogleDriveSyncMetadataByAccount()

        assertEquals(setOf("account-a@example.com"), restored.keys)
        assertEquals(7L, restored.getValue("account-a@example.com").lastManifestAt)
    }

    @Test
    fun `malformed and negative metadata is ignored`() {
        val valid = mapOf(
            "valid@example.com" to GoogleDriveSyncMetadata(lastSyncAt = 4L, lastManifestAt = 3L),
        ).toGoogleDriveSyncMetadataEntries().single()
        val restored = setOf(valid, "not-valid", "%%%|1|2", "dmFsaWRAZXhhbXBsZS5jb20|-1|2")
            .toGoogleDriveSyncMetadataByAccount()

        assertEquals(setOf("valid@example.com"), restored.keys)
    }

    @Test
    fun `unknown account does not inherit another account metadata`() {
        val restored = mapOf(
            "account-a@example.com" to GoogleDriveSyncMetadata(lastSyncAt = 100L, lastManifestAt = 90L),
        ).toGoogleDriveSyncMetadataEntries().toGoogleDriveSyncMetadataByAccount()

        assertFalse(restored.containsKey("account-b@example.com"))
        assertEquals(GoogleDriveSyncMetadata(), restored["account-b@example.com"] ?: GoogleDriveSyncMetadata())
    }

    @Test
    fun `unattributed legacy globals are not assigned to active account`() {
        val resolved = resolveGoogleDriveSyncMetadata(
            accountEmail = "account-a@example.com",
            scopedEntries = emptySet(),
            legacyLastSyncAt = 9_000L,
            legacyLastManifestAt = 8_000L,
        )

        assertEquals(GoogleDriveSyncMetadata(), resolved)
    }

    @Test
    fun `encoded entries do not expose account email`() {
        val entries = mapOf(
            "private@example.com" to GoogleDriveSyncMetadata(lastSyncAt = 8L, lastManifestAt = 5L),
        ).toGoogleDriveSyncMetadataEntries()

        assertTrue(entries.single().contains("|8|5"))
        assertFalse(entries.single().contains("private@example.com"))
    }
}
