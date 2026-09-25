package com.myvault.app.data.sync.record

import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.FolderDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.RecordSyncDao
import com.myvault.app.data.local.entity.RecordSyncConflictEntity
import com.myvault.app.data.local.entity.RecordSyncControlEntity
import com.myvault.app.data.local.entity.RecordSyncFileEntity
import com.myvault.app.data.local.entity.RecordSyncHeadEntity
import com.myvault.app.data.local.entity.RecordSyncPendingEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

data class RecordSyncStatus(
    val enabled: Boolean,
    val paused: Boolean,
    val pending: Int,
    val excluded: Int,
    val conflicts: Int,
)

@Singleton
internal class RecordSyncCoordinator @Inject constructor(
    private val database: VaultDatabase,
    private val syncDao: RecordSyncDao,
    private val noteDao: NoteDao,
    private val folderDao: FolderDao,
    private val blockDao: BlockDao,
    private val attachmentDao: AttachmentDao,
    private val noteTableDao: NoteTableDao,
    private val drive: RecordSyncDriveClient,
) {
    private val mutex = Mutex()

    suspend fun status(): RecordSyncStatus {
        val control = syncDao.control() ?: RecordSyncControlEntity()
        val account = control.accountId.orEmpty()
        return RecordSyncStatus(control.enabled, control.paused, syncDao.pending(account).size,
            syncDao.excluded(account).size, syncDao.unresolvedConflicts(account).size)
    }

    suspend fun enrol(): RecordSyncStatus = mutex.withLock { withContext(Dispatchers.IO) {
        val accountId = drive.accountId()
        val old = syncDao.control() ?: RecordSyncControlEntity()
        require(old.accountId == null || old.accountId == accountId) { "This Vault is paired with another Google Drive account. Sync was not started." }
        val control = old.copy(accountId = accountId, clientId = old.clientId.ifBlank { UUID.randomUUID().toString() }, enabled = true, paused = true)
        syncDao.saveControl(control)
        val startToken = drive.startPageToken()
        val recordsFolder = drive.ensureRecordsFolder()
        val remote = drive.listRecords(recordsFolder).map { file -> file to parseFile(file) }
        applyBatch(accountId, remote)
        seedLocal(accountId)
        pullChanges(accountId, recordsFolder, startToken)
        syncDao.saveControl(requireNotNull(syncDao.control()).copy(paused = false))
        status()
    } }

    suspend fun runOnce(): RecordSyncStatus = mutex.withLock { withContext(Dispatchers.IO) {
        val control = syncDao.control() ?: return@withContext status()
        if (!control.enabled || control.paused || control.accountId == null) return@withContext status()
        require(drive.accountId() == control.accountId) { "Google Drive account changed. Automatic Sync stopped before touching data." }
        val recordsFolder = drive.findRecordsFolder() ?: error("The Automatic Sync folder is missing. Local data was preserved.")
        val cursor = requireNotNull(control.cursor) { "Automatic Sync has no Drive cursor. Re-enrol before publishing." }
        pullChanges(control.accountId, recordsFolder, cursor)
        publishPending(control.accountId, control.clientId, recordsFolder)
        status()
    } }

    suspend fun pauseForRestore() = mutex.withLock {
        val control = syncDao.control() ?: return@withLock
        if (control.enabled) syncDao.saveControl(control.copy(enabled = false, paused = true))
    }

    suspend fun disable() = mutex.withLock {
        val control = syncDao.control() ?: return@withLock
        syncDao.saveControl(control.copy(enabled = false, paused = true))
    }

    private suspend fun seedLocal(accountId: String) {
        val folders = folderDao.getAllIncludingDeleted().filter { it.mode == "study" }
        val folderIds = folders.map { it.id }.toSet()
        val notes = noteDao.getAllIncludingDeleted().filter { it.folderId == null || it.folderId in folderIds }
        val now = System.currentTimeMillis()
        database.withTransaction {
            folders.forEach { folder ->
                if (syncDao.head(accountId, "folder", folder.id) == null)
                    syncDao.seedPending(RecordSyncPendingEntity(accountId, "folder", folder.id, 1, now, null))
            }
            notes.forEach { note ->
                if (syncDao.head(accountId, "note", note.id) == null)
                    syncDao.seedPending(RecordSyncPendingEntity(accountId, "note", note.id, 1, now, null))
            }
        }
    }

    private suspend fun publishPending(accountId: String, clientId: String, recordsFolder: String) {
        syncDao.pending(accountId).forEach { pending ->
            if (syncDao.conflictCount(accountId, pending.entityType, pending.entityId) > 0) return@forEach
            if (System.currentTimeMillis() - pending.changedAt < RecordSyncDebounceMs) return@forEach
            val payload = when (pending.entityType) {
                "folder" -> folderDao.getByIdIncludingDeleted(pending.entityId)
                    ?.takeIf { it.mode == "study" && it.deletedAt == null }?.let(::folderPayload)
                "note" -> {
                    val note = noteDao.getByIdIncludingDeleted(pending.entityId)
                    if (note == null || note.deletedAt != null || !isStudyNote(note.folderId)) null
                    else {
                        val attachments = attachmentDao.getForNotes(listOf(note.id))
                        val tables = noteTableDao.countForNote(note.id)
                        val blocks = blockDao.getForNote(note.id)
                        val binaryBlocks = blocks.any { it.type == "attachment" || it.type == "image" }
                        if (attachments.isNotEmpty() || tables > 0 || binaryBlocks) {
                            syncDao.exclude(accountId, "note", note.id,
                                if (attachments.isNotEmpty() || binaryBlocks) "Contains an attachment or image; binary sync is not in Phase 1."
                                else "Contains a note table; table sync is not in Phase 1.")
                            return@forEach
                        }
                        notePayload(note, blocks)
                    }
                }
                else -> error("Unsupported sync entity type.")
            }
            val current = syncDao.pendingFor(accountId, pending.entityType, pending.entityId) ?: return@forEach
            if (current.generation != pending.generation) return@forEach
            val revision = if (current.preparedGeneration == current.generation && current.preparedRevisionJson != null)
                RecordSyncRevision.parse(JSONObject(current.preparedRevisionJson))
            else {
                val prepared = RecordSyncRevision.create(pending.entityType, pending.entityId, clientId,
                    listOfNotNull(current.baseRevisionId), payload,
                    if (payload != null && pending.entityType == "note") {
                        val note = JSONObject(payload)
                        listOfNotNull(note.optString("folderId").takeIf { it.isNotBlank() && it != "null" }?.let { "folder:$it" })
                    } else emptyList())
                if (syncDao.prepare(accountId, pending.entityType, pending.entityId, current.generation, prepared.toJson().toString()) == 0) return@forEach
                prepared
            }
            val fileId = drive.upload(recordsFolder, revision)
            database.withTransaction {
                syncDao.saveFile(RecordSyncFileEntity(fileId, accountId, revision.entityType, revision.entityId, revision.revisionId, revision.mutationId))
                syncDao.saveHead(RecordSyncHeadEntity(accountId, revision.entityType, revision.entityId, revision.revisionId, revision.contentHash, revision.deleted))
                syncDao.acknowledge(accountId, revision.entityType, revision.entityId, current.generation)
                syncDao.advancePendingBase(accountId, revision.entityType, revision.entityId, current.generation, revision.revisionId)
            }
        }
    }

    private suspend fun pullChanges(accountId: String, recordsFolder: String, startingToken: String) {
        var token = startingToken
        val incoming = mutableListOf<Pair<RecordSyncDriveFile, RecordSyncRevision>>()
        val seen = mutableSetOf<String>()
        while (true) {
            val page = drive.changes(token)
            page.changes.forEach { change ->
                if (change.removed) {
                    if (syncDao.fileById(change.fileId)?.accountId == accountId)
                        error("A sync revision disappeared from Drive. Sync paused; this is not treated as a note deletion.")
                } else if (change.file != null && recordsFolder in change.file.parents && seen.add(change.fileId)) {
                    incoming += change.file to parseFile(change.file)
                }
            }
            if (page.nextPageToken != null) token = page.nextPageToken
            else {
                applyBatch(accountId, incoming)
                syncDao.saveControl(requireNotNull(syncDao.control()).copy(cursor = page.newStartPageToken ?: token))
                return
            }
        }
    }

    private fun parseFile(file: RecordSyncDriveFile): RecordSyncRevision {
        val revision = RecordSyncRevision.parse(JSONObject(drive.download(file.id)))
        require(file.name == "${revision.revisionId}.json") { "Sync file name does not match its revision." }
        return revision
    }

    private suspend fun applyBatch(accountId: String, incoming: List<Pair<RecordSyncDriveFile, RecordSyncRevision>>) {
        val remaining = incoming.distinctBy { it.first.id }.toMutableList()
        while (remaining.isNotEmpty()) {
            val next = remaining.firstOrNull { (_, revision) ->
                revision.parents.all { parent -> remaining.none { it.second.revisionId == parent } } &&
                    (revision.deleted || revision.entityType != "note" || revision.dependencies.all { dependency ->
                        !dependency.startsWith("folder:") || folderDao.getByIdIncludingDeleted(dependency.removePrefix("folder:")) != null
                    })
            } ?: error("Sync records have a missing parent or folder dependency; local data was preserved.")
            applyOne(accountId, next.first, next.second)
            remaining.remove(next)
        }
    }

    private suspend fun applyOne(accountId: String, file: RecordSyncDriveFile, revision: RecordSyncRevision) {
        if (syncDao.fileById(file.id) != null || syncDao.fileByMutation(accountId, revision.mutationId) != null) return
        val head = syncDao.head(accountId, revision.entityType, revision.entityId)
        val pending = syncDao.pendingFor(accountId, revision.entityType, revision.entityId)
        val localPayload = when (revision.entityType) {
            "folder" -> folderDao.getByIdIncludingDeleted(revision.entityId)?.let(::folderPayload)
            "note" -> noteDao.getByIdIncludingDeleted(revision.entityId)?.let { note -> notePayload(note, blockDao.getForNote(note.id)) }
            else -> null
        }
        val sameLocal = localPayload != null && sha256(localPayload) == revision.contentHash
        val isSafeForward = head != null && head.revisionId in revision.parents && pending == null
        val isFresh = head == null && localPayload == null && pending == null
        val isAdoption = head == null && sameLocal && pending == null
        if (!isSafeForward && !isFresh && !isAdoption) {
            database.withTransaction {
                syncDao.saveConflict(RecordSyncConflictEntity(
                    id = "$accountId:${revision.entityType}:${revision.entityId}:${revision.revisionId}",
                    accountId = accountId, entityType = revision.entityType, entityId = revision.entityId,
                    localRevisionId = head?.revisionId, remoteRevisionId = revision.revisionId,
                    remotePayloadJson = revision.payloadJson, remoteDeleted = revision.deleted,
                    conflictCopyId = null, createdAt = System.currentTimeMillis(),
                ))
                syncDao.saveFile(RecordSyncFileEntity(file.id, accountId, revision.entityType, revision.entityId, revision.revisionId, revision.mutationId))
            }
            return
        }
        if (revision.entityType == "note") {
            if (attachmentDao.getForNotes(listOf(revision.entityId)).isNotEmpty() ||
                blockDao.getForNote(revision.entityId).any { it.type == "attachment" || it.type == "image" }
            ) error("A note with attachments or image blocks cannot be changed by Phase 1 sync.")
            if (noteTableDao.countForNote(revision.entityId) > 0)
                error("A note with tables cannot be changed by Phase 1 sync.")
            if (!revision.deleted && blocksFromPayload(JSONObject(requireNotNull(revision.payloadJson)))
                    .any { it.type == "attachment" || it.type == "image" })
                error("Incoming note has binary blocks, which Phase 1 cannot safely import.")
        }
        database.withTransaction {
            val control = requireNotNull(syncDao.control())
            syncDao.saveControl(control.copy(applyingRemote = true))
            when (revision.entityType) {
                "folder" -> if (revision.deleted) {
                    folderDao.getByIdIncludingDeleted(revision.entityId)?.let {
                        folderDao.updateDeletedAt(listOf(it.id), System.currentTimeMillis(), System.currentTimeMillis())
                    }
                } else folderDao.upsertAll(listOf(folderFromPayload(JSONObject(requireNotNull(revision.payloadJson)))))
                "note" -> if (revision.deleted) {
                    noteDao.getByIdIncludingDeleted(revision.entityId)?.let {
                        noteDao.updateDeletedAt(listOf(it.id), System.currentTimeMillis(), System.currentTimeMillis())
                    }
                } else {
                    val payload = JSONObject(requireNotNull(revision.payloadJson))
                    noteDao.upsertAll(listOf(noteFromPayload(payload)))
                    blockDao.deleteForNotes(listOf(revision.entityId))
                    blockDao.upsertAll(blocksFromPayload(payload))
                }
            }
            syncDao.saveHead(RecordSyncHeadEntity(accountId, revision.entityType, revision.entityId, revision.revisionId, revision.contentHash, revision.deleted))
            syncDao.saveFile(RecordSyncFileEntity(file.id, accountId, revision.entityType, revision.entityId, revision.revisionId, revision.mutationId))
            syncDao.saveControl(control)
        }
    }

    private suspend fun isStudyNote(folderId: String?): Boolean = folderId == null || folderDao.getByIdIncludingDeleted(folderId)?.mode == "study"
}
