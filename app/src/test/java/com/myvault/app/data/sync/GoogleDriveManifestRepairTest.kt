package com.myvault.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveManifestRepairTest {
    @Test
    fun staleAttachmentIdIsNotReusedWhenDriveFileIsMissing() {
        assertNull(
            reusableDriveEntryId(
                isAttachmentFile = true,
                contentMatches = true,
                currentFileIdByName = null,
            ),
        )
    }

    @Test
    fun replacementAttachmentIdIsUsedWhenFileStillExistsByName() {
        assertEquals(
            "current-drive-id",
            reusableDriveEntryId(
                isAttachmentFile = true,
                contentMatches = true,
                currentFileIdByName = "current-drive-id",
            ),
        )
    }

    @Test
    fun metadataIsReuploadedEvenWhenTheOldManifestClaimsItIsUnchanged() {
        assertNull(
            reusableDriveEntryId(
                isAttachmentFile = false,
                contentMatches = true,
                currentFileIdByName = null,
            ),
        )
    }

    @Test
    fun uploadedMetadataMustMatchBothManifestSizeAndChecksum() {
        val bytes = "[{\"id\":\"attachment-id\"}]".toByteArray()
        val checksum = "ef54ae1f5a65c59c51827df8bfef4b8ff28e48b409f04a2d6a7443da9603c107"

        assertTrue(uploadedBytesMatchManifest(bytes, bytes.size.toLong(), checksum))
        assertFalse(uploadedBytesMatchManifest(bytes + '!'.code.toByte(), bytes.size.toLong(), checksum))
        assertFalse(uploadedBytesMatchManifest(bytes, bytes.size.toLong(), "0".repeat(64)))
    }
}
