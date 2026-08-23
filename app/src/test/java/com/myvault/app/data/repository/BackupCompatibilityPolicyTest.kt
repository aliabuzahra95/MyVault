package com.myvault.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCompatibilityPolicyTest {
    @Test
    fun oldAskAiMetadataIsIgnoredWithoutRemovingSupportedBackupEntries() {
        val entries = mutableMapOf(
            "manifest.json" to "{}",
            "notes.json" to "[]",
            "attachments.json" to "[]",
            "ai_conversations.json" to "[{\"id\":\"legacy-conversation\"}]",
            "ai_messages.json" to "[{\"id\":\"legacy-message\"}]",
            "home_chat_history.json" to "[{\"id\":\"legacy-home-chat\"}]",
        )

        BackupCompatibilityPolicy.removeRetiredMetadata(entries)

        assertEquals(setOf("manifest.json", "notes.json", "attachments.json"), entries.keys)
        assertTrue("notes.json" in entries)
        assertFalse(BackupCompatibilityPolicy.retiredMetadataEntries.any(entries::containsKey))
    }
}
