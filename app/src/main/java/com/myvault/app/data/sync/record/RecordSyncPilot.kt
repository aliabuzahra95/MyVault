package com.myvault.app.data.sync.record

import android.content.Context
import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.local.dao.BlockDao
import com.myvault.app.data.local.dao.NoteDao
import com.myvault.app.data.local.dao.NoteTableDao
import com.myvault.app.data.local.dao.RecordSyncDao
import com.myvault.app.data.local.entity.RecordSyncConflictEntity
import com.myvault.app.data.local.entity.RecordSyncFileEntity
import com.myvault.app.data.local.entity.RecordSyncHeadEntity
import com.myvault.app.data.local.entity.RecordSyncPendingEntity
import com.myvault.app.data.repository.NoteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal const val PilotNotePrefix = "SYNC_TEST_CODEX_"

data class PilotNote(val id: String, val title: String)
data class PilotSyncResult(val uploaded: Int, val imported: Int, val conflicts: Int, val accountId: String)

internal fun isPilotNoteId(id: String): Boolean = id.startsWith(PilotNotePrefix) && id.length > PilotNotePrefix.length

internal fun pilotFileKey(account: String, driveFileId: String): String = "pilot-file:$account:$driveFileId"

internal fun knownPilotFile(
    account: String,
    scoped: RecordSyncFileEntity?,
    legacy: RecordSyncFileEntity?,
): Boolean = scoped?.accountId == account || legacy?.accountId == account

internal fun validatePilotRevision(revision: RecordSyncRevision) {
    require(revision.entityType == "note" && isPilotNoteId(revision.entityId)) { "Only test notes may enter the pilot." }
    if (revision.deleted) {
        require(revision.parents.isNotEmpty()) { "A test deletion must follow an existing revision." }
        return
    }
    val payload = JSONObject(requireNotNull(revision.payloadJson))
    require(payload.isNull("folderId") && payload.isNull("parentNoteId")) { "Pilot notes must stay in Study root." }
    require(payload.isNull("deletedAt")) { "Test deletion must use a tombstone." }
    noteFromPayload(payload)
    require(payload.getJSONObject("richText").optJSONArray("noteLinks")?.length() == 0) {
        "Test notes must not reference other notes."
    }
    require(blocksFromPayload(payload).none { it.type == "attachment" || it.type == "image" }) {
        "Test notes containing binary blocks are not supported."
    }
}

internal fun canAdoptOwnEarlierRevision(
    clientId: String,
    currentHead: String?,
    pending: RecordSyncPendingEntity?,
    revision: RecordSyncRevision,
): Boolean = pending != null && revision.clientId == clientId &&
    (if (currentHead == null) revision.parents.isEmpty() else currentHead in revision.parents)

@Singleton
internal class RecordSyncPilot @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: VaultDatabase,
    private val syncDao: RecordSyncDao,
    private val noteDao: NoteDao,
    private val blockDao: BlockDao,
    private val attachmentDao: AttachmentDao,
    private val noteTableDao: NoteTableDao,
    private val noteRepository: NoteRepository,
    private val drive: RecordSyncDriveClient,
    private val scheduler: PilotSyncScheduler,
) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("record_sync_pilot", Context.MODE_PRIVATE)

    suspend fun createTestNote(): String = withContext(Dispatchers.IO) {
        val id = PilotNotePrefix + UUID.randomUUID()
        noteRepository.createNote(folderId = null, title = "Test note ${id.removePrefix(PilotNotePrefix).take(8)}", noteId = id)
        noteRepository.saveRichText(id, "Android sync test. Edit this note on either phone.", "[]")
        register(id)
        scheduler.scheduleAfterEdit(id)
        id
    }

    suspend fun notes(): List<PilotNote> = withContext(Dispatchers.IO) {
        knownIds().mapNotNull { id ->
            noteDao.getByIdIncludingDeleted(id)?.takeIf { it.deletedAt == null }
                ?.let { PilotNote(id, it.title) }
        }.sortedBy { it.title }
    }

    suspend fun syncNow(): PilotSyncResult = mutex.withLock { withContext(Dispatchers.IO) {
        val driveAccountId = drive.accountId()
        val storedAccount = prefs.getString("accountId", null)
        require(storedAccount == null || storedAccount == driveAccountId) {
            "This sync test belongs to another Google account. No note was changed."
        }
        if (storedAccount == null) check(prefs.edit().putString("accountId", driveAccountId).commit()) {
            "Could not save the sync test account."
        }
        val account = "pilot:$driveAccountId"
        val clientId = prefs.getString("clientId", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("clientId", it).commit()) { "Could not save the sync test device ID." }
        }
        val folder = drive.ensurePilotRecordsFolder()
        markChangedTestNotes(account)
        val remote = drive.listRecords(folder).mapNotNull { file ->
            if (isKnownFile(account, file.id)) return@mapNotNull null
            val revision = RecordSyncRevision.parse(JSONObject(drive.download(file.id)))
            require(file.name == "${revision.revisionId}.json") { "A pilot file name does not match its revision." }
            validatePilotRevision(revision)
            file to revision
        }
        val remaining = remote.toMutableList()
        var imported = 0
        while (remaining.isNotEmpty()) {
            val next = remaining.firstOrNull { (_, revision) ->
                revision.parents.none { parent -> remaining.any { it.second.revisionId == parent } }
            } ?: error("Pilot revisions have an ancestry cycle. Local notes were preserved.")
            if (applyOne(account, clientId, next.first, next.second)) imported++
            remaining.remove(next)
        }
        var uploaded = 0
        for (pending in syncDao.pending(account)) {
            if (!isPilotNoteId(pending.entityId) || pending.entityType != "note") continue
            if (syncDao.conflictCount(account, "note", pending.entityId) > 0) continue
            val note = noteDao.getByIdIncludingDeleted(pending.entityId) ?: continue
            require(pending.entityId in knownIds()) { "Unregistered note blocked from pilot upload." }
            if (note.deletedAt != null && syncDao.head(account, "note", note.id) == null) {
                syncDao.acknowledge(account, "note", note.id, pending.generation)
                continue
            }
            val payload = localPayload(note.id)
            val current = syncDao.pendingFor(account, "note", note.id) ?: continue
            val prepared = current.preparedRevisionJson?.let { RecordSyncRevision.parse(JSONObject(it)) }
            val revision = if (prepared != null && current.preparedGeneration == current.generation) prepared else {
                RecordSyncRevision.create("note", note.id, clientId, listOfNotNull(current.baseRevisionId), payload).also {
                    validatePilotRevision(it)
                    require(syncDao.prepare(account, "note", note.id, current.generation, it.toJson().toString()) == 1)
                }
            }
            val fileId = drive.upload(folder, revision)
            database.withTransaction {
                syncDao.saveFile(RecordSyncFileEntity(pilotFileKey(account, fileId), account, "note", note.id, revision.revisionId, revision.mutationId))
                syncDao.saveHead(RecordSyncHeadEntity(account, "note", note.id, revision.revisionId, revision.contentHash, revision.deleted))
                syncDao.acknowledge(account, "note", note.id, current.generation)
            }
            uploaded++
        }
        PilotSyncResult(uploaded, imported, syncDao.unresolvedConflicts(account).size, driveAccountId)
    } }

    private suspend fun markChangedTestNotes(account: String) {
        for (id in knownIds()) {
            val note = noteDao.getByIdIncludingDeleted(id) ?: continue
            val payload = localPayload(id)
            val hash = sha256(payload ?: "null")
            val head = syncDao.head(account, "note", id)
            if (head?.contentHash == hash) continue
            val previous = syncDao.pendingFor(account, "note", id)
            val prepared = previous?.preparedRevisionJson?.let { RecordSyncRevision.parse(JSONObject(it)) }
            if (prepared?.contentHash == hash) continue
            syncDao.savePending(RecordSyncPendingEntity(account, "note", id, (previous?.generation ?: 0) + 1,
                System.currentTimeMillis(), head?.revisionId))
        }
    }

    private suspend fun localPayload(id: String): String? {
        val note = noteDao.getByIdIncludingDeleted(id) ?: return null
        require(isPilotNoteId(id) && id in knownIds()) { "Only registered test notes may sync." }
        if (note.deletedAt != null) return null
        require(note.folderId == null && note.parentNoteId == null) {
            "Test note was moved out of Study root; pilot sync stopped without uploading it."
        }
        require(attachmentDao.getForNotes(listOf(id)).isEmpty() && noteTableDao.countForNote(id) == 0) {
            "Test note has an attachment or table; pilot sync stopped without uploading it."
        }
        val blocks = blockDao.getForNote(id)
        require(blocks.none { it.type == "attachment" || it.type == "image" }) {
            "Test note has an image; pilot sync stopped without uploading it."
        }
        return notePayload(note, blocks).also { payload ->
            require(JSONObject(payload).getJSONObject("richText").optJSONArray("noteLinks")?.length() == 0) {
                "Test note links to another note; pilot sync stopped without uploading it."
            }
        }
    }

    private suspend fun applyOne(account: String, clientId: String, file: RecordSyncDriveFile, revision: RecordSyncRevision): Boolean {
        if (isKnownFile(account, file.id)) return false
        val id = revision.entityId
        val head = syncDao.head(account, "note", id)
        val local = noteDao.getByIdIncludingDeleted(id)
        require(local == null || id in knownIds()) { "An existing note ID collided with a pilot record." }
        val pending = syncDao.pendingFor(account, "note", id)
        val localHash = local?.let { if (it.deletedAt != null) sha256("null") else sha256(localPayload(id)!!) }
        val sameLocal = localHash == revision.contentHash
        val prepared = pending?.preparedRevisionJson?.let { RecordSyncRevision.parse(JSONObject(it)) }
        val canApply = (head == null && local == null && pending == null) ||
            (head != null && head.revisionId in revision.parents && localHash == head.contentHash && pending == null)
        val ownLinearRevision = canAdoptOwnEarlierRevision(clientId, head?.revisionId, pending, revision)
        val canAdopt = sameLocal && (head == null || prepared?.revisionId == revision.revisionId || ownLinearRevision)
        val canAdoptOlder = !sameLocal && ownLinearRevision
        if (!canApply && !canAdopt && !canAdoptOlder && head?.revisionId != revision.revisionId) {
            syncDao.saveConflict(RecordSyncConflictEntity("$account:note:$id:${revision.revisionId}", account,
                "note", id, head?.revisionId, revision.revisionId, revision.payloadJson, revision.deleted,
                null, System.currentTimeMillis()))
            syncDao.saveFile(RecordSyncFileEntity(pilotFileKey(account, file.id), account, "note", id, revision.revisionId, revision.mutationId))
            return false
        }
        database.withTransaction {
            if (canApply) {
                if (revision.deleted) {
                    local?.let { noteDao.updateDeletedAt(listOf(id), System.currentTimeMillis(), System.currentTimeMillis()) }
                } else {
                    val payload = JSONObject(requireNotNull(revision.payloadJson))
                    noteDao.upsertAll(listOf(noteFromPayload(payload)))
                    blockDao.deleteForNotes(listOf(id))
                    blockDao.upsertAll(blocksFromPayload(payload))
                }
            }
            syncDao.saveHead(RecordSyncHeadEntity(account, "note", id, revision.revisionId, revision.contentHash, revision.deleted))
            syncDao.saveFile(RecordSyncFileEntity(pilotFileKey(account, file.id), account, "note", id, revision.revisionId, revision.mutationId))
            if (canAdoptOlder) {
                syncDao.savePending(pending!!.copy(
                    baseRevisionId = revision.revisionId,
                    preparedGeneration = null,
                    preparedRevisionJson = null,
                ))
            }
            if (sameLocal && (prepared?.revisionId == revision.revisionId || ownLinearRevision)) {
                pending?.let { syncDao.acknowledge(account, "note", id, it.generation) }
            }
        }
        register(id)
        return canApply
    }

    private fun knownIds(): Set<String> = prefs.getStringSet("noteIds", emptySet()).orEmpty().filter(::isPilotNoteId).toSet()

    private suspend fun isKnownFile(account: String, driveFileId: String): Boolean = knownPilotFile(
        account,
        syncDao.fileById(pilotFileKey(account, driveFileId)),
        syncDao.fileById(driveFileId),
    )

    private fun register(id: String) {
        require(isPilotNoteId(id))
        check(prefs.edit().putStringSet("noteIds", knownIds() + id).commit()) {
            "Could not save the sync test note ID."
        }
    }
}
