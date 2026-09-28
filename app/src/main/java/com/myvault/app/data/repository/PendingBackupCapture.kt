package com.myvault.app.data.repository

import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.dao.BackupCaptureDao
import com.myvault.app.data.local.entity.BackupBinaryFingerprint
import com.myvault.app.data.local.entity.BackupPendingChange
import com.myvault.app.data.local.entity.BackupTrackingAccount
import com.myvault.app.data.local.entity.BackupBinaryReference
import com.myvault.app.data.preferences.VaultPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

internal data class BackupRecordIdentity(val group: String, val key: List<String>)
internal data class CapturedBackupBinary(
    val attachmentId: String, val localPath: String, val byteSize: Long, val sha256: String?, val status: String,
    val reusableCloudFileId: String? = null,
)
internal data class CapturedBackupRecord(
    val group: String, val key: List<String>, val operation: String, val generation: Long,
    val payloadJson: String?, val dependencies: List<BackupRecordIdentity>,
) {
    fun protocolChange() = BackupRecordChange(group, key, payloadJson?.let(::JSONObject))
}
internal data class PendingBackupBatch(
    val journal: CapturedBackupChanges,
    val records: List<CapturedBackupRecord>,
    val binaries: List<CapturedBackupBinary>,
    val queriedRecords: List<BackupRecordIdentity>,
    val settingsReadCount: Int,
)

internal fun captureBinaryFingerprint(row: JSONObject, stored: BackupBinaryFingerprint?): BackupBinaryFingerprint =
    stored?.takeIf { it.attachmentId == row.getString("id") && it.localPath == row.getString("localPath") && it.byteSize == row.getLong("sizeBytes") }
        ?: BackupBinaryFingerprint(row.getString("id"), row.getString("localPath"), row.getLong("sizeBytes"), null, "UNKNOWN", 0)

internal fun reusableBackupBinary(baseline: BackupTrackingAccount, fingerprint: BackupBinaryFingerprint, reference: BackupBinaryReference?): String? =
    reference?.takeIf {
        baseline.trusted && it.accountScope == baseline.accountScope && it.attachmentId == fingerprint.attachmentId &&
            it.checkpointId == baseline.checkpointId && fingerprint.status == "VERIFIED" &&
            it.sha256 == fingerprint.sha256 && it.byteSize == fingerprint.byteSize
    }?.cloudFileId

internal fun pendingBackupRecordSql(group: String): String =
    "SELECT * FROM `${backupRecordTable(group)}` WHERE ${BackupRecordKeys.getValue(group).joinToString(" AND ") { "`$it` = ?" }}"

/** Uses the same entity serializers as full Backup, without querying an entire table. */
internal suspend fun BackupCaptureDao.readBackupRecord(group: String, key: List<String>): JSONObject? {
    check(key.size == BackupRecordKeys.getValue(group).size)
    val query = SimpleSQLiteQuery(pendingBackupRecordSql(group), key.toTypedArray())
    return when (group) {
        "folders.json" -> folders(query).singleOrNull()?.toBackupJsonObject()
        "notes.json" -> notes(query).singleOrNull()?.toJson()
        "blocks.json" -> blocks(query).singleOrNull()?.toJson()
        "tags.json" -> tags(query).singleOrNull()?.toJson()
        "note_tags.json" -> noteTags(query).singleOrNull()?.toJson()
        "note_tables.json" -> tables(query).singleOrNull()?.toJson()
        "note_versions.json" -> versions(query).singleOrNull()?.toJson()
        "attachments.json" -> attachments(query).singleOrNull()?.toJson()
        "folder_sticky_notes.json" -> stickyNotes(query).singleOrNull()?.toJson()
        "courses.json" -> courses(query).singleOrNull()?.toJson()
        "course_concept_cards.json" -> cards(query).singleOrNull()?.toJson()
        "course_folders.json" -> courseFolders(query).singleOrNull()?.toJson()
        "course_notes.json" -> courseNotes(query).singleOrNull()?.toJson()
        "course_sticky_notes.json" -> courseStickyNotes(query).singleOrNull()?.toJson()
        "pdf_reading_progress.json" -> progress(query).singleOrNull()?.toJson()
        "pdf_annotations.json" -> annotations(query).singleOrNull()?.toJson()
        "pdf_annotation_geometry.json" -> geometry(query).singleOrNull()?.toJson()
        "source_backlinks.json" -> backlinks(query).singleOrNull()?.toJson()
        "knowledge_tags.json" -> knowledgeTags(query).singleOrNull()?.toJson()
        "knowledge_tag_links.json" -> knowledgeLinks(query).singleOrNull()?.toJson()
        else -> error("Protocol group has no capture adapter: $group")
    }
}

internal fun backupRecordDependencies(group: String, row: JSONObject): List<BackupRecordIdentity> = buildList {
    fun field(name: String, target: String) {
        if (!row.isNull(name)) row.optString(name).takeIf { it.isNotBlank() }?.let { add(BackupRecordIdentity(target, listOf(it))) }
    }
    when (group) {
        "folders.json" -> field("parentId", "folders.json")
        "notes.json" -> { field("folderId", "folders.json"); field("parentNoteId", "notes.json") }
        "blocks.json", "note_tables.json", "note_versions.json" -> field("noteId", "notes.json")
        "note_tags.json" -> { field("noteId", "notes.json"); field("tagName", "tags.json") }
        "folder_sticky_notes.json" -> field("folderId", "folders.json")
        "courses.json" -> field("rootFolderId", "folders.json")
        "course_concept_cards.json", "course_folders.json", "course_sticky_notes.json" -> field("courseId", "courses.json")
        "course_notes.json" -> { field("courseId", "courses.json"); field("folderId", "course_folders.json") }
        "attachments.json" -> { field("noteId", "notes.json"); field("libraryFolderId", "folders.json") }
        "pdf_reading_progress.json", "pdf_annotations.json" -> field("attachmentId", "attachments.json")
        "pdf_annotation_geometry.json" -> field("annotationId", "pdf_annotations.json")
        "source_backlinks.json" -> { field("noteId", "notes.json"); field("attachmentId", "attachments.json"); field("annotationId", "pdf_annotations.json") }
        "knowledge_tag_links.json" -> {
            field("tagId", "knowledge_tags.json")
            when (row.optString("targetType")) {
                KnowledgeRepository.TargetNote -> field("targetId", "notes.json")
                KnowledgeRepository.TargetAttachment -> field("targetId", "attachments.json")
                KnowledgeRepository.TargetAnnotation -> field("targetId", "pdf_annotations.json")
                else -> error("Unknown knowledge target type.")
            }
        }
    }
}

@Singleton
class PendingBackupCapture @Inject constructor(
    private val database: VaultDatabase,
    private val journal: BackupChangeJournal,
    private val preferences: VaultPreferences,
) {
    internal suspend fun capture(email: String): PendingBackupBatch = journal.binaryMutex.withLock {
        journal.settingsMutex.withLock {
            if (database.backupJournalDao().clock().settingsToken != null) {
                preferences.userPreferences.first()
                journal.recoverInterruptedSettingsWrite()
            }
            database.withTransaction {
                val snapshot = journal.capture(email)
                val queried = mutableListOf<BackupRecordIdentity>()
                val cache = mutableMapOf<BackupRecordIdentity, JSONObject>()
                val binaries = linkedMapOf<String, CapturedBackupBinary>()
                suspend fun read(identity: BackupRecordIdentity): JSONObject = cache.getOrPutSuspend(identity) {
                    queried += identity
                    database.backupCaptureDao().readBackupRecord(identity.group, identity.key)
                        ?: error("Pending/dependent record is missing; a verified full baseline is required. Absence is not deletion.")
                }
                suspend fun binary(id: String) {
                    if (id in binaries) return
                    val row = read(BackupRecordIdentity("attachments.json", listOf(id)))
                    val stored = database.backupJournalDao().fingerprint(id)
                    val fingerprint = captureBinaryFingerprint(row, stored)
                    val reference = if (snapshot.account.trusted) database.backupJournalDao().binaryReference(snapshot.account.accountScope, id) else null
                    val reuse = reusableBackupBinary(snapshot.account, fingerprint, reference)
                    binaries[id] = CapturedBackupBinary(id, fingerprint.localPath, fingerprint.byteSize, fingerprint.sha256, fingerprint.status, reuse)
                }
                suspend fun resolveBinary(identity: BackupRecordIdentity) {
                    when (identity.group) {
                        "attachments.json" -> binary(identity.key.single())
                        "pdf_annotations.json" -> binary(read(identity).getString("attachmentId"))
                    }
                }
                var settingsReads = 0
                val records = snapshot.changes.map { change: BackupPendingChange ->
                    val key = change.stableKey()
                    if (change.operation == "DELETE") {
                        check(change.recordGroup != "settings.json")
                        CapturedBackupRecord(change.recordGroup, key, change.operation, change.generation, null, emptyList())
                    } else {
                        check(change.operation == "UPSERT")
                        val row = if (change.recordGroup == "settings.json") {
                            settingsReads++
                            preferences.userPreferences.first().toBackupJson()
                        } else read(BackupRecordIdentity(change.recordGroup, key))
                        val dependencies = backupRecordDependencies(change.recordGroup, row)
                        if (change.recordGroup == "attachments.json") {
                            binary(key.single())
                            row.put("fileEntry", "files/${key.single()}")
                        }
                        dependencies.forEach { resolveBinary(it) }
                        CapturedBackupRecord(change.recordGroup, key, change.operation, change.generation, row.toString(), dependencies)
                    }
                }
                PendingBackupBatch(snapshot, records, binaries.values.toList(), queried.toList(), settingsReads)
            }
        }
    }
}

private suspend fun <K, V> MutableMap<K, V>.getOrPutSuspend(key: K, value: suspend () -> V): V =
    this[key] ?: value().also { this[key] = it }
