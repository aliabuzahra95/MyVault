package com.myvault.app.data.repository

import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.FolderEntity
import com.myvault.app.data.local.entity.NoteEntity
import com.myvault.app.data.local.entity.PdfReadingProgressEntity
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.preferences.VaultUserPreferences
import com.myvault.app.ui.components.VaultNoteCardData
import com.myvault.app.ui.model.DashboardShortcut
import com.myvault.app.ui.model.DashboardShortcutKind
import com.myvault.app.ui.model.availableDashboardFolders
import com.myvault.app.ui.model.dashboardLibraryRecents
import com.myvault.app.ui.model.dashboardReadingPosition
import com.myvault.app.ui.model.dashboardShortcuts
import com.myvault.app.ui.viewmodel.LibraryFileItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DashboardBackupIntegrationTest {

    private fun note(id: String, isPinned: Boolean = false) = NoteEntity(
        id = id,
        folderId = null,
        parentNoteId = null,
        title = "Note $id",
        bodyPlainText = "Body of $id",
        isPinned = isPinned,
        isFolderPinned = false,
        isFavourite = false,
        createdAt = 1000L,
        updatedAt = 1000L,
    )

    private fun folder(id: String, mode: String = "study", parentId: String? = null, deletedAt: Long? = null) = FolderEntity(
        id = id,
        parentId = parentId,
        name = "Folder $id",
        orderIndex = 0,
        isFavourite = false,
        mode = mode,
        createdAt = 1000L,
        updatedAt = 1000L,
        deletedAt = deletedAt,
    )

    private fun file(id: String, name: String = "Doc $id.pdf", pageIndex: Int? = null, pageCount: Int? = null) = LibraryFileItem(
        id = id,
        name = name,
        kind = "PDF",
        size = "1 MB",
        meta = "",
        mimeType = "application/pdf",
        localPath = "/fake/$id.pdf",
        pageIndex = pageIndex,
        pageCount = pageCount,
    )

    // Scenario 1: Pinned Study note survives backup and restore
    @Test
    fun scenario1_pinnedStudyNoteSurvivesBackupAndRestore() {
        val note = note("n1", isPinned = true)
        val json = note.toJson()
        val restored = json.toNoteEntity()

        assertTrue(restored.isPinned)
        assertEquals("n1", restored.id)

        val shortcuts = dashboardShortcuts(
            notes = listOf(VaultNoteCardData(restored.title, "", restored.id)),
            files = emptyList(),
            folders = emptyList(),
        )
        assertEquals(1, shortcuts.size)
        assertEquals("Note:n1", shortcuts.first().key)
        assertEquals(DashboardShortcutKind.Note, shortcuts.first().kind)
    }

    // Scenario 2: Pinned Study folder survives backup and restore
    @Test
    fun scenario2_pinnedStudyFolderSurvivesBackupAndRestore() {
        val folder = folder("f1", mode = "study")
        val userPrefs = VaultUserPreferences(
            dashboardFolderPins = setOf("f1"),
        )
        val backupJson = userPrefs.toBackupJson()
        val restoredPrefs = backupJson.toValidatedBackupPreferences()

        assertNotNull(restoredPrefs.dashboardFolderPins)
        assertTrue(restoredPrefs.dashboardFolderPins!!.contains("f1"))

        val available = availableDashboardFolders(restoredPrefs.dashboardFolderPins!!, listOf(folder))
        assertEquals(listOf(folder), available)

        val shortcuts = dashboardShortcuts(emptyList(), emptyList(), available)
        assertEquals(1, shortcuts.size)
        assertEquals("Folder:f1", shortcuts.first().key)
        assertEquals("Study · Folder", shortcuts.first().metadata)
    }

    // Scenario 3: Pinned Library PDF reading position is accurately restored
    @Test
    fun scenario3_pinnedLibraryPdfReadingPositionRestored() {
        val progress = PdfReadingProgressEntity(
            attachmentId = "pdf-usul",
            pageIndex = 41,
            pageCount = 300,
            progressPercent = 42f / 300f,
            lastOpenedAt = 5000L,
            updatedAt = 5000L,
        )
        val pdfItem = file("pdf-usul", "Usul al-Fiqh.pdf", pageIndex = progress.pageIndex, pageCount = progress.pageCount)
        val shortcuts = dashboardShortcuts(emptyList(), listOf(pdfItem), emptyList())

        assertEquals(1, shortcuts.size)
        assertEquals("Document:pdf-usul", shortcuts.first().key)
        assertEquals("Library · Page 42", shortcuts.first().metadata)
        assertEquals("Last read · Page 42", dashboardReadingPosition(progress.pageIndex, progress.pageCount))
    }

    // Scenario 4: Pinned Library folder survives backup and restore
    @Test
    fun scenario4_pinnedLibraryFolderSurvivesBackupAndRestore() {
        val libFolder = folder("lf-hadith", mode = "library")
        val userPrefs = VaultUserPreferences(
            dashboardFolderPins = setOf("lf-hadith"),
        )
        val backupJson = userPrefs.toBackupJson()
        val restoredPrefs = backupJson.toValidatedBackupPreferences()

        val available = availableDashboardFolders(restoredPrefs.dashboardFolderPins!!, listOf(libFolder))
        assertEquals(1, available.size)

        val shortcuts = dashboardShortcuts(emptyList(), emptyList(), available)
        assertEquals("Folder:lf-hadith", shortcuts.first().key)
        assertEquals("Library · Folder", shortcuts.first().metadata)
    }

    // Scenario 5: 10 items in custom order retain exact order
    @Test
    fun scenario5_customOrderPreservedAcross10Items() {
        val notes = (1..4).map { VaultNoteCardData("Note $it", "", "n$it") }
        val files = (1..3).map { file("d$it") }
        val folders = listOf(folder("f1", "study"), folder("f2", "library"), folder("f3", "personal"))

        val customOrder = listOf(
            "Folder:f2",
            "Note:n3",
            "Document:d1",
            "Folder:f1",
            "Note:n1",
            "Document:d3",
            "Folder:f3",
            "Note:n4",
            "Document:d2",
            "Note:n2",
        )

        val userPrefs = VaultUserPreferences(dashboardPinnedOrder = customOrder)
        val backupJson = userPrefs.toBackupJson()
        val restoredPrefs = backupJson.toValidatedBackupPreferences()

        assertEquals(customOrder, restoredPrefs.dashboardPinnedOrder)

        val shortcuts = dashboardShortcuts(
            notes = notes,
            files = files,
            folders = folders,
            order = restoredPrefs.dashboardPinnedOrder!!,
        )
        assertEquals(10, shortcuts.size)
        assertEquals(customOrder, shortcuts.map { it.key })
    }

    // Scenario 6: Recently opened Library files (top 5 distinct) retain order and last-opened timestamps
    @Test
    fun scenario6_recentlyOpenedLibraryFilesRetainOrderAndPosition() {
        val items = listOf(
            DashboardActivityItem(DashboardActivityKind.Library, "doc-1", "Doc 1", "Library", 1000L, pageIndex = 10, pageCount = 100),
            DashboardActivityItem(DashboardActivityKind.Library, "doc-2", "Doc 2", "Library", 2000L, pageIndex = 20, pageCount = 100),
            DashboardActivityItem(DashboardActivityKind.Library, "doc-3", "Doc 3", "Library", 3000L, pageIndex = 30, pageCount = 100),
            DashboardActivityItem(DashboardActivityKind.Library, "doc-4", "Doc 4", "Library", 4000L, pageIndex = 40, pageCount = 100),
            DashboardActivityItem(DashboardActivityKind.Library, "doc-5", "Doc 5", "Library", 5000L, pageIndex = 50, pageCount = 100),
            DashboardActivityItem(DashboardActivityKind.Library, "doc-6", "Doc 6", "Library", 600L, pageIndex = 60, pageCount = 100),
        )

        val state = DashboardActivityState(recents = items, libraryRecents = items)
        val activityJson = JSONObject()
            .put("recents", JSONArray().apply { items.forEach { put(JSONObject().put("kind", it.kind.name).put("destinationId", it.destinationId).put("title", it.title).put("context", it.context).put("openedAt", it.openedAt).put("pageIndex", it.pageIndex).put("pageCount", it.pageCount)) } })
            .put("libraryRecents", JSONArray().apply { items.forEach { put(JSONObject().put("kind", it.kind.name).put("destinationId", it.destinationId).put("title", it.title).put("context", it.context).put("openedAt", it.openedAt).put("pageIndex", it.pageIndex).put("pageCount", it.pageCount)) } })

        val userPrefs = VaultUserPreferences(dashboardActivityJson = activityJson.toString())
        val backupJson = userPrefs.toBackupJson()
        val restoredPrefs = backupJson.toValidatedBackupPreferences()

        assertNotNull(restoredPrefs.dashboardActivity)
        val restoredStateJson = JSONObject(restoredPrefs.dashboardActivity!!)
        val restoredRecents = (0 until restoredStateJson.getJSONArray("libraryRecents").length()).map { idx ->
            val obj = restoredStateJson.getJSONArray("libraryRecents").getJSONObject(idx)
            DashboardActivityItem(
                kind = DashboardActivityKind.valueOf(obj.getString("kind")),
                destinationId = obj.getString("destinationId"),
                title = obj.getString("title"),
                context = obj.getString("context"),
                openedAt = obj.getLong("openedAt"),
                pageIndex = obj.optInt("pageIndex"),
                pageCount = obj.optInt("pageCount"),
            )
        }

        val top5 = dashboardLibraryRecents(restoredRecents)
        assertEquals(5, top5.size)
        assertEquals(listOf("doc-5", "doc-4", "doc-3", "doc-2", "doc-1"), top5.map { it.destinationId })
        assertEquals(50, top5[0].pageIndex)
        assertEquals(40, top5[1].pageIndex)
    }

    // Scenario 7: Older backup lacking Dashboard metadata does not wipe local settings
    @Test
    fun scenario7_olderBackupLackingDashboardMetadataPreservesLocalSettings() {
        val olderBackupJson = JSONObject()
            .put("schemaVersion", 1)
            .put("theme", "dark")
            .put("workspace", "islamic_corpus")
            .put("fontSize", "medium")

        val restoredPrefs = olderBackupJson.toValidatedBackupPreferences()

        // Missing fields in older backups resolve to null
        assertNull(restoredPrefs.dashboardFolderPins)
        assertNull(restoredPrefs.dashboardPinnedOrder)
        assertNull(restoredPrefs.dashboardActivity)

        // Existing local values to protect
        val localPins = setOf("local-folder-1")
        val localOrder = listOf("Note:local-note")
        val localActivity = "{\"recents\":[]}"

        var currentPins = localPins
        var currentOrder = localOrder
        var currentActivity = localActivity

        // Apply backup logic preserves local values if restored is null
        restoredPrefs.dashboardFolderPins?.let { currentPins = it }
        restoredPrefs.dashboardPinnedOrder?.let { currentOrder = it }
        restoredPrefs.dashboardActivity?.let { currentActivity = it }

        assertEquals(localPins, currentPins)
        assertEquals(localOrder, currentOrder)
        assertEquals(localActivity, currentActivity)
    }

    // Scenario 8: References to deleted or unavailable items handled gracefully
    @Test
    fun scenario8_deletedOrUnavailableItemsHandledGracefully() {
        val deletedFolder = folder("del-f", deletedAt = 12345L)
        val missingFolderId = "non-existent"
        val activeFolder = folder("ok-f")

        val available = availableDashboardFolders(
            ids = setOf(deletedFolder.id, missingFolderId, activeFolder.id),
            folders = listOf(deletedFolder, activeFolder),
        )
        assertEquals(listOf(activeFolder), available)

        // Reading position with deleted / invalid page bounds falls back cleanly
        assertEquals("Last opened · Position unavailable", dashboardReadingPosition(-1, 100))
        assertEquals("Last opened · Position unavailable", dashboardReadingPosition(100, 100))
        assertEquals("Last opened · Position unavailable", dashboardReadingPosition(null, null))
    }

    // Scenario 9: Incremental backups detect Dashboard changes via BackupFields
    @Test
    fun scenario9_dashboardKeysRegisteredInBackupFields() {
        val backupKeys = VaultPreferences.BackupFields.keys
        assertTrue("dashboardFolderPins must be in BackupFields for incremental detection", backupKeys.contains("dashboardFolderPins"))
        assertTrue("dashboardPinnedOrder must be in BackupFields for incremental detection", backupKeys.contains("dashboardPinnedOrder"))
        assertTrue("dashboardActivity must be in BackupFields for incremental detection", backupKeys.contains("dashboardActivity"))
    }

    // Scenario 10: Validation rejects malformed/malicious settings
    @Test
    fun scenario10_validationRejectsMalformedOrMaliciousSettings() {
        // Path traversal in folder pin
        val badPins = JSONObject().apply {
            put("schemaVersion", 1)
            put("theme", "auto")
            put("workspace", "islamic_corpus")
            put("dashboardFolderPins", JSONArray().put("../../etc/passwd"))
        }
        try {
            badPins.toValidatedBackupPreferences()
            fail("Expected exception for path traversal in folder pins")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("invalid dashboard folder pin state"))
        }

        // Duplicate folder pins
        val duplicatePins = JSONObject().apply {
            put("schemaVersion", 1)
            put("theme", "auto")
            put("workspace", "islamic_corpus")
            put("dashboardFolderPins", JSONArray().put("folder1").put("folder1"))
        }
        try {
            duplicatePins.toValidatedBackupPreferences()
            fail("Expected exception for duplicate folder pins")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("duplicate dashboard folder pin state"))
        }

        // Duplicate pinned order
        val duplicateOrder = JSONObject().apply {
            put("schemaVersion", 1)
            put("theme", "auto")
            put("workspace", "islamic_corpus")
            put("dashboardPinnedOrder", JSONArray().put("Note:n1").put("Note:n1"))
        }
        try {
            duplicateOrder.toValidatedBackupPreferences()
            fail("Expected exception for duplicate pinned order")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("duplicate dashboard pinned order"))
        }
    }
}
