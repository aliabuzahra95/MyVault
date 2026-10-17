package com.myvault.app.data.repository

/** Fold verified history while retaining every known identity, including authenticated removals. */
internal class BackupGraphRestoreState {
    private val records = linkedMapOf<BackupRecordIdentity, BackupRecordChange>()
    val origins = mutableMapOf<BackupRecordIdentity, String>()
    val changes: List<BackupRecordChange> get() = records.values.toList()

    fun apply(changes: List<BackupRecordChange>, checkpointGroups: Set<String>? = null, commitId: String? = null): List<BackupRecordChange> {
        val incoming = changes.map { BackupRecordIdentity(it.file, it.key) }.toSet()
        // Only groups actually present in a full checkpoint replace prior state. Historical
        // optional groups may be absent; delta omissions never authorize deletion.
        val removals = if (checkpointGroups == null) emptyList() else records.filter { (id, row) ->
            id.group in checkpointGroups && id !in incoming && !row.deleted
        }.map { (id, _) -> BackupRecordChange(id.group, id.key) }
        return (removals + changes).also { applied ->
            applied.forEach {
                val id = BackupRecordIdentity(it.file, it.key)
                records[id] = it
                if (commitId != null) origins[id] = commitId
            }
        }
    }
}

internal class BackupGraphRestoreFailure(message: String, val diagnostic: String) : IllegalStateException(message)
