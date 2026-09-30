package com.myvault.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.BackupBinaryReference
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.preferences.normalizeGoogleDriveAccount
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

internal data class PreparedBaselineObject(
    val path: String, val backupEntry: String, val kind: String, val file: File,
    val byteSize: Long, val sha256: String, val attachmentId: String? = null,
)
internal data class PreparedBackupBaseline(
    val preparationId: String,
    val journal: CapturedBackupChanges,
    val directory: File,
    val objects: List<PreparedBaselineObject>,
)

/** Future authenticated transport supplies bytes from this exact remote file/account, not timestamps. */
internal class VerifiedRemoteBackupObject private constructor(
    val account: String, val fileId: String, val size: Long, val sha256: String,
) {
    companion object {
        fun read(account: String, fileId: String, remoteBytes: InputStream): VerifiedRemoteBackupObject {
            check(fileId.isNotBlank())
            val scope = normalizeGoogleDriveAccount(account)
            check('@' in scope)
            val digest = fingerprintBackupBytes(remoteBytes)
            return VerifiedRemoteBackupObject(scope, fileId, digest.size, digest.sha256)
        }
    }
}

internal class VerifiedBackupBaseline private constructor(
    val preparationId: String,
    val commit: ConfirmedBackupCommit,
    val binaries: List<BackupBinaryReference>,
) {
    companion object {
        /** Exact staged inventory + verified objects + committed manifest readback must all agree. */
        fun verify(
            prepared: PreparedBackupBaseline,
            account: String,
            manifestId: String,
            candidate: ByteArray,
            committedReadback: ByteArray,
            objects: List<VerifiedRemoteBackupObject>,
        ): VerifiedBackupBaseline {
            val scope = normalizeGoogleDriveAccount(account)
            check(scope == prepared.journal.account.accountScope && manifestId.isNotBlank())
            check(prepared.objects.map { it.path }.toSet().size == prepared.objects.size)
            check(prepared.objects.filter { it.kind == "metadata" }.map { it.backupEntry }.toSet() == BackupRecordKeys.keys + setOf("settings.json", "manifest.json")) {
                "Trust requires the complete staged checkpoint, not a partial inventory."
            }
            val attachmentMetadata = prepared.objects.single { it.backupEntry == "attachments.json" }
            val attachmentBytes = attachmentMetadata.file.readBytes()
            check(IncrementalBackupFormat.sha256(attachmentBytes) == attachmentMetadata.sha256)
            val attachmentRows = JSONArray(attachmentBytes.toString(Charsets.UTF_8))
            val claims = (0 until attachmentRows.length()).map { attachmentRows.getJSONObject(it) }.associateBy { it.getString("id") }
            val fileObjects = prepared.objects.filter { it.kind == "file" }.associateBy { it.attachmentId }
            check(claims.size == attachmentRows.length() && fileObjects.size == prepared.objects.count { it.kind == "file" } && claims.keys == fileObjects.keys)
            claims.forEach { (id, row) ->
                val file = fileObjects.getValue(id)
                check(row.getString("fileEntry") == file.backupEntry && row.getLong("sizeBytes") == file.byteSize)
            }
            check(candidate.contentEquals(committedReadback)) { "Committed manifest does not match the staged backup." }
            val manifest = JSONObject(committedReadback.toString(Charsets.UTF_8))
            check(manifest.getInt("schemaVersion") == 1 && manifest.getString("storage") == "google-drive-api")
            check(manifest.getLong("cloudVersion") > 0 && !manifest.has(IncrementalBackupField)) { "This baseline requires a full checkpoint, not an inferred delta state." }
            val entries = manifest.getJSONArray("entries")
            val indexed = (0 until entries.length()).map { entries.getJSONObject(it) }.associateBy { it.getString("path") }
            check(indexed.size == entries.length() && indexed.keys == prepared.objects.map { it.path }.toSet())
            val receipts = objects.associateBy { it.fileId }
            check(receipts.size == objects.size && objects.all { it.account == scope })
            check(receipts.keys == indexed.values.map { it.getString("cloudFileId") }.toSet())
            val hash = IncrementalBackupFormat.sha256(committedReadback)
            // Legacy full manifests have no checkpoint UUID. Their exact committed content hash is the equivalent identity.
            val checkpoint = "full-$hash"
            val references = prepared.objects.mapNotNull { expected ->
                val entry = indexed.getValue(expected.path)
                val receipt = receipts.getValue(entry.getString("cloudFileId"))
                check(entry.getString("backupEntry") == expected.backupEntry && entry.getString("kind") == expected.kind)
                check(entry.getLong("size") == expected.byteSize && entry.getString("sha256") == expected.sha256)
                check(receipt.size == expected.byteSize && receipt.sha256 == expected.sha256) { "Remote object was not byte-verified against this snapshot." }
                expected.attachmentId?.let { id ->
                    BackupBinaryReference(scope, id, checkpoint, checkpoint, receipt.fileId, receipt.size, receipt.sha256)
                }
            }
            return VerifiedBackupBaseline(prepared.preparationId,
                ConfirmedBackupCommit(scope, prepared.journal.generation, manifestId, hash, checkpoint, checkpoint), references)
        }
    }
}

/** Explicit one-time local full staging; never called by the production Backup button. */
@Singleton
class BackupBaselinePreparer @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: VaultDatabase,
    private val journal: BackupChangeJournal,
    private val preferences: VaultPreferences,
    private val backupRepository: BackupRepository,
    private val binaries: BackupBinaryStore,
) {
    internal suspend fun prepare(
        account: String,
        onProgress: suspend (GraphBackupProgress) -> Unit = {},
        persistFingerprints: Boolean = true,
    ): PreparedBackupBaseline = journal.binaryMutex.withLock {
        journal.settingsMutex.withLock {
            preferences.userPreferences.first()
            journal.recoverInterruptedSettingsWrite()
            val preparation = UUID.randomUUID().toString()
            val directory = File(context.cacheDir, "backup-baseline-$preparation").apply { mkdirs() }
            try {
                val metadata = File(directory, "metadata")
                onProgress(GraphBackupProgress(GraphBackupStage.READING_BASELINE))
                val captured = database.withTransaction {
                    backupRepository.exportMetadataForDriveSync(metadata)
                    journal.capture(account)
                }
                val required = BackupRecordKeys.keys + setOf("settings.json", "manifest.json")
                val files = metadata.listFiles()?.filter { it.isFile } ?: error("Baseline metadata could not be staged.")
                check(files.map { it.name }.toSet() == required)
                onProgress(GraphBackupProgress(GraphBackupStage.STAGING_METADATA, 0, files.size))
                val objects = files.mapIndexed { index, file ->
                    val digest = file.inputStream().use { fingerprintBackupBytes(it) }
                    onProgress(GraphBackupProgress(GraphBackupStage.STAGING_METADATA, index + 1, files.size))
                    PreparedBaselineObject("metadata/${file.name}", file.name, "metadata", file, digest.size, digest.sha256)
                }.toMutableList()
                val attachments = JSONArray(File(metadata, "attachments.json").readText())
                if (attachments.length() > 0) onProgress(GraphBackupProgress(GraphBackupStage.STAGING_FILES, 0, attachments.length()))
                for (i in 0 until attachments.length()) {
                    val row = attachments.getJSONObject(i)
                    val id = row.getString("id")
                    check(row.getString("fileEntry") == "files/$id") { "A baseline cannot be trusted while an attachment's bytes are unavailable." }
                    val source = File(row.getString("localPath"))
                    check(isDurableBackupBinary(context.filesDir, source))
                    val target = File(directory, "files/$id").apply { parentFile?.mkdirs() }
                    val digest = source.inputStream().use { input -> target.outputStream().use { fingerprintBackupBytes(input, it) } }
                    check(digest.size == row.getLong("sizeBytes")) { "Attachment size is inconsistent; baseline remains untrusted." }
                    val current = database.attachmentDao().getByIdIncludingDeleted(id) ?: error("Attachment was deleted during baseline staging.")
                    check(current.localPath == source.absolutePath && current.sizeBytes == digest.size)
                    if (persistFingerprints) binaries.persistWritten(current, digest, mutated = false)
                    objects += PreparedBaselineObject("files/$id", "files/$id", "file", target, digest.size, digest.sha256, id)
                    onProgress(GraphBackupProgress(GraphBackupStage.STAGING_FILES, i + 1, attachments.length()))
                }
                PreparedBackupBaseline(preparation, captured, directory, objects.toList())
            } catch (error: Throwable) {
                directory.deleteRecursively() // Only this newly-created private staging directory.
                throw error
            }
        }
    }
}
