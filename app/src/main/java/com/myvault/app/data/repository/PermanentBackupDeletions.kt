package com.myvault.app.data.repository

import org.json.JSONObject

/** No cascades or absence-based deletes: every SQL predicate comes from an explicit change key. */
internal fun permanentBackupDeletionSql(change: BackupRecordChange): Pair<String, Array<Any>> {
    check(change.deleted)
    val fields = BackupRecordKeys.getValue(change.file)
    check(fields.size == change.key.size && change.key.all { it.isNotBlank() })
    val table = backupRecordTable(change.file)
    return "DELETE FROM `$table` WHERE ${fields.joinToString(" AND ") { "`$it` = ?" }}" to
        change.key.map { it as Any }.toTypedArray()
}

internal fun readPermanentBackupDeletions(entries: Map<String, String>): List<BackupRecordChange> {
    val marker = JSONObject(entries.getValue("manifest.json")).optJSONObject("incrementalBackupApplied")
    val text = entries["permanent_deletions.json"]
    if (marker == null) {
        // Historical backups ignore unknown optional metadata, including an unmarked sidecar.
        return emptyList()
    }
    check(text != null) { "Incremental restore is missing its verified permanent-deletion list." }
    val json = JSONObject(text)
    check(json.getString("format") == "myvault-permanent-deletions" && json.getInt("version") == 1)
    val state = json.getJSONObject("state")
    check(state.getString("checkpointId") == marker.getString("checkpointId") && state.getString("headId") == marker.getString("headId"))
    if (json.getJSONArray("changes").length() == 0) return emptyList()
    val delta = JSONObject().put("format", "myvault-backup-delta").put("version", 1)
        .put("checkpointId", marker.getString("checkpointId")).put("parentId", marker.getString("checkpointId"))
        .put("deltaId", marker.getString("headId")).put("changes", json.getJSONArray("changes"))
    return IncrementalBackupFormat.parseChanges(delta).also { changes -> check(changes.all { it.deleted }) }
}
