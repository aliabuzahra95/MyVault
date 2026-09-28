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
internal suspend fun BackupCaptureDao.readBackupRecord(group: String, key: List<String>, timing: BackupGraphTiming = BackupGraphTiming()): JSONObject? {
    check(key.size == BackupRecordKeys.getValue(group).size)
    val query = SimpleSQLiteQuery(pendingBackupRecordSql(group), key.toTypedArray())
    fun <T> encode(row: T?, serializer: (T) -> JSONObject): JSONObject? = timing.local("payload.serialization") { row?.let(serializer) }
    return when (group) {
        "folders.json" -> encode(folders(query).singleOrNull()) { it.toBackupJsonObject() }
        "notes.json" -> encode(notes(query).singleOrNull()) { it.toJson() }
        "blocks.json" -> encode(blocks(query).singleOrNull()) { it.toJson() }
        "tags.json" -> encode(tags(query).singleOrNull()) { it.toJson() }
        "note_tags.json" -> encode(noteTags(query).singleOrNull()) { it.toJson() }
        "note_tables.json" -> encode(tables(query).singleOrNull()) { it.toJson() }
        "note_versions.json" -> encode(versions(query).singleOrNull()) { it.toJson() }
        "attachments.json" -> encode(attachments(query).singleOrNull()) { it.toJson() }
        "folder_sticky_notes.json" -> encode(stickyNotes(query).singleOrNull()) { it.toJson() }
        "courses.json" -> encode(courses(query).singleOrNull()) { it.toJson() }
        "course_concept_cards.json" -> encode(cards(query).singleOrNull()) { it.toJson() }
        "course_folders.json" -> encode(courseFolders(query).singleOrNull()) { it.toJson() }
        "course_notes.json" -> encode(courseNotes(query).singleOrNull()) { it.toJson() }
        "course_sticky_notes.json" -> encode(courseStickyNotes(query).singleOrNull()) { it.toJson() }
        "pdf_reading_progress.json" -> encode(progress(query).singleOrNull()) { it.toJson() }
        "pdf_annotations.json" -> encode(annotations(query).singleOrNull()) { it.toJson() }
        "pdf_annotation_geometry.json" -> encode(geometry(query).singleOrNull()) { it.toJson() }
        "source_backlinks.json" -> encode(backlinks(query).singleOrNull()) { it.toJson() }
        "knowledge_tags.json" -> encode(knowledgeTags(query).singleOrNull()) { it.toJson() }
        "knowledge_tag_links.json" -> encode(knowledgeLinks(query).singleOrNull()) { it.toJson() }
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
    internal suspend fun capture(email: String, timing: BackupGraphTiming = BackupGraphTiming()): PendingBackupBatch = journal.binaryMutex.withLock {
        journal.settingsMutex.withLock {
            if (database.backupJournalDao().clock().settingsToken != null) {
                preferences.userPreferences.first()
                journal.recoverInterruptedSettingsWrite()
            }
            database.withTransaction {
                val snapshot = timing.measure("journal.rows") { journal.capture(email) }
                val queried = mutableListOf<BackupRecordIdentity>()
                val cache = mutableMapOf<BackupRecordIdentity, JSONObject>()
                val binaries = linkedMapOf<String, CapturedBackupBinary>()
                suspend fun read(identity: BackupRecordIdentity): JSONObject = cache.getOrPutSuspend(identity) {
                    queried += identity
                    timing.measure("payload.read") { database.backupCaptureDao().readBackupRecord(identity.group, identity.key, timing) }
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
                        val dependencies = timing.measure("binary.dependencies") {
                            val deps = backupRecordDependencies(change.recordGroup, row)
                            if (change.recordGroup == "attachments.json") {
                                binary(key.single())
                                row.put("fileEntry", "files/${key.single()}")
                            }
                            deps.forEach { resolveBinary(it) }
                            deps
                        }
                        CapturedBackupRecord(change.recordGroup, key, change.operation, change.generation, timing.local("payload.serialization") { row.toString() }, dependencies)
                    }
                }
                PendingBackupBatch(snapshot, records, binaries.values.toList(), queried.toList(), settingsReads)
            }
        }
    }
}

private suspend fun <K, V> MutableMap<K, V>.getOrPutSuspend(key: K, value: suspend () -> V): V =
    this[key] ?: value().also { this[key] = it }
