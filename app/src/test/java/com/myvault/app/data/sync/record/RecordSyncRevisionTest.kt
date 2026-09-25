package com.myvault.app.data.sync.record

import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class RecordSyncRevisionTest {
    private val base = RecordSyncRevision(
        entityType = "note", entityId = "note-381", revisionId = "rev-a", mutationId = "rev-a",
        clientId = "phone-a", parents = emptyList(), deleted = false, payloadJson = "{}",
        contentHash = "hash", dependencies = emptyList(), publishedAt = 1,
    )

    @Test
    fun revisionAncestryDistinguishesForwardUpdateFromOfflineConflict() {
        assertEquals("apply", revisionDecision(null, base))
        val next = base.copy(revisionId = "rev-b", mutationId = "rev-b", parents = listOf("rev-a"))
        assertEquals("apply", revisionDecision("rev-a", next))
        assertEquals("conflict", revisionDecision("rev-other", next))
        assertEquals("already-applied", revisionDecision("rev-b", next))
    }

    @Test
    fun deletionIsExplicitAndStillRequiresAncestry() {
        val deletion = base.copy(revisionId = "rev-delete", mutationId = "rev-delete", parents = listOf("rev-a"), deleted = true, payloadJson = null)
        assertEquals("apply", revisionDecision("rev-a", deletion))
        assertEquals("conflict", revisionDecision("rev-offline-edit", deletion))
    }

    @Test
    fun arabicRichTextUsesTheSharedCanonicalWireEncoding() {
        val payload = JSONObject()
            .put("title", "English العربية")
            .put("richText", JSONObject().put("text", "قُلْ: One").put("styleMarks", JSONArray()).put("noteLinks", JSONArray()))
            .put("id", "note-381")
        assertEquals(
            """{"id":"note-381","richText":{"noteLinks":[],"styleMarks":[],"text":"قُلْ: One"},"title":"English العربية"}""",
            canonicalJson(payload),
        )
    }
}
