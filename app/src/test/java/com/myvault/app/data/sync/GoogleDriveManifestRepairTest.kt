package com.myvault.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleDriveManifestRepairTest {
    @Test
    fun staleAttachmentIdIsNotReusedWhenDriveFileIsMissing() {
        assertNull(
            reusableDriveEntryId(
                isAttachmentFile = true,
                contentMatches = true,
                manifestFileId = "deleted-drive-id",
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
                manifestFileId = "deleted-drive-id",
                currentFileIdByName = "current-drive-id",
            ),
        )
    }

    @Test
    fun unchangedMetadataMayStillReuseItsManifestId() {
        assertEquals(
            "metadata-drive-id",
            reusableDriveEntryId(
                isAttachmentFile = false,
                contentMatches = true,
                manifestFileId = "metadata-drive-id",
                currentFileIdByName = null,
            ),
        )
    }
}
