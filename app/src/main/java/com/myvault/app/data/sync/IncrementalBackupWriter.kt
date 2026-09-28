package com.myvault.app.data.sync

import com.myvault.app.data.repository.BackupRecordChange
import com.myvault.app.data.repository.IncrementalBackupFormat
import com.myvault.app.data.repository.IncrementalBackupPublicationEnabled
import org.json.JSONObject

internal interface IncrementalBackupTransport {
    suspend fun readCommitted(): PublishedDriveBackup
    suspend fun createDelta(deltaId: String, bytes: ByteArray): String
    suspend fun readObject(id: String): ByteArray
    suspend fun preserve(previous: PublishedDriveBackup)
    suspend fun commit(previous: PublishedDriveBackup, manifest: String)
}

internal class IncrementalBackupWriter(private val transport: IncrementalBackupTransport) {
    suspend fun publish(previous: PublishedDriveBackup, delta: JSONObject?): PublishedDriveBackup {
        check(IncrementalBackupPublicationEnabled) { "Incremental backup publication is disabled pending coordinated release acceptance." }
        return publishVerifiedDelta(previous, delta)
    }

    /** Transport-bound protocol, exercised with disposable stores; not wired to manual Backup yet. */
    internal suspend fun publishVerifiedDelta(previous: PublishedDriveBackup, delta: JSONObject?): PublishedDriveBackup {
        if (delta == null) return previous
        IncrementalBackupFormat.parseChanges(delta)
        val bytes = delta.toString().toByteArray(Charsets.UTF_8)
        val observed = transport.readCommitted()
        if (observed != previous) {
            val extension = IncrementalBackupFormat.extension(JSONObject(observed.text))
            val head = extension?.getJSONArray("deltas")?.takeIf { it.length() > 0 }?.let { it.getJSONObject(it.length() - 1) }
            check(head?.optString("deltaId") == delta.getString("deltaId") && head.optString("sha256") == IncrementalBackupFormat.sha256(bytes)) {
                "The selected backup changed. Nothing was published."
            }
            check(transport.readObject(head.getString("cloudFileId")).contentEquals(bytes)) { "Committed delta verification failed." }
            return observed
        }
        val parentManifest = JSONObject(previous.text)
        // Validate ancestry before allocating any remote object.
        IncrementalBackupFormat.append(parentManifest, delta, "pending", parentManifest.getLong("cloudVersion") + 1)
        val id = transport.createDelta(delta.getString("deltaId"), bytes)
        check(transport.readObject(id).contentEquals(bytes)) { "Uploaded delta verification failed. Previous backup remains committed." }
        transport.preserve(previous)
        check(transport.readCommitted() == previous) { "The selected backup changed during upload. Nothing was published." }
        val candidate = IncrementalBackupFormat.append(parentManifest, delta, id, parentManifest.getLong("cloudVersion") + 1).toString()
        try {
            transport.commit(previous, candidate)
        } catch (error: Exception) {
            if (transport.readCommitted() != PublishedDriveBackup(previous.id, candidate)) throw error
        }
        return transport.readCommitted().also { check(it == PublishedDriveBackup(previous.id, candidate)) { "Backup publication could not be confirmed." } }
    }
}

/** Capture entries are already explicit operations. Last mutation wins locally, never by absence. */
internal fun coalescedBackupChanges(changes: List<BackupRecordChange>): List<BackupRecordChange> {
    val result = linkedMapOf<Pair<String, List<String>>, BackupRecordChange>()
    changes.forEach { result[it.file to it.key] = it }
    return result.values.toList()
}
