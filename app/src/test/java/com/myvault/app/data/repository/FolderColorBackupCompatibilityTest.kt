package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.FOLDER_COLOR_BLUE
import com.myvault.app.data.local.entity.FolderEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderColorBackupCompatibilityTest {
    private val folder = FolderEntity(
        id = "folder-1",
        parentId = null,
        name = "Sources",
        orderIndex = 0,
        isFavourite = false,
        createdAt = 10L,
        updatedAt = 20L,
        colorKey = FOLDER_COLOR_BLUE,
    )

    @Test
    fun newBackup_roundTripsSemanticFolderColor() {
        assertEquals(FOLDER_COLOR_BLUE, folder.toBackupJsonObject().toBackupFolderEntity().colorKey)
    }

    @Test
    fun legacyBackup_withoutFolderColorRestoresNeutralDefault() {
        val legacyJson = folder.toBackupJsonObject().apply { remove("colorKey") }

        assertNull(legacyJson.toBackupFolderEntity().colorKey)
    }

    @Test
    fun unknownFolderColorRestoresNeutralDefault() {
        val futureJson = folder.toBackupJsonObject().put("colorKey", "orange")

        assertNull(futureJson.toBackupFolderEntity().colorKey)
    }

    @Test
    fun optionalColorFieldDoesNotChangeLegacyFolderKeys() {
        val json = folder.toBackupJsonObject()
        val neutralJson = folder.copy(colorKey = null).toBackupJsonObject()

        assertEquals(neutralJson.keys().asSequence().toSet(), json.keys().asSequence().toSet() - "colorKey")
    }
}
