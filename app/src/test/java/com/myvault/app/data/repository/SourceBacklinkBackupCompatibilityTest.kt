package com.myvault.app.data.repository

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceBacklinkBackupCompatibilityTest {
    @Test
    fun restorePreservesPdfDocumentCoordinates() {
        val restored = JSONObject()
            .put("id", "backlink-1")
            .put("noteId", "note-1")
            .put("attachmentId", "attachment-1")
            .put("annotationId", "annotation-1")
            .put("pageIndex", 2)
            .put("left", 118f)
            .put("top", 43f)
            .put("right", 160f)
            .put("bottom", 53f)
            .put("createdAt", 1234L)
            .toSourceBacklinkEntity()

        assertEquals(118f, restored.left)
        assertEquals(43f, restored.top)
        assertEquals(160f, restored.right)
        assertEquals(53f, restored.bottom)
    }
}
