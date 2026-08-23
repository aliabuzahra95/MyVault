package com.myvault.app.data.repository

/** Keeps pre-decommission backups restorable while refusing to revive retired chat data. */
internal object BackupCompatibilityPolicy {
    val retiredMetadataEntries: Set<String> = setOf(
        "ai_conversations.json",
        "ai_messages.json",
        "home_chat_history.json",
    )

    fun removeRetiredMetadata(entries: MutableMap<String, String>) {
        retiredMetadataEntries.forEach(entries::remove)
    }
}
