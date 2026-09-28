package com.myvault.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.local.entity.AttachmentEntity
import com.myvault.app.data.local.entity.BackupBinaryFingerprint
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

internal data class BackupByteFingerprint(val size: Long, val sha256: String)

internal fun fingerprintBackupBytes(input: InputStream, output: OutputStream? = null): BackupByteFingerprint {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    var size = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) break
        if (count == 0) continue
        output?.write(buffer, 0, count)
        digest.update(buffer, 0, count)
        size = Math.addExact(size, count.toLong())
    }
    return BackupByteFingerprint(size, digest.digest().joinToString("") { "%02x".format(it) })
}

internal fun isDurableBackupBinary(filesDir: File, file: File): Boolean =
    listOf("attachments", "library").any { name ->
        file.canonicalPath.startsWith(File(filesDir, name).canonicalPath + File.separator)
    }

@Singleton
class BackupBinaryStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: VaultDatabase,
    private val journal: BackupChangeJournal,
) {
    /** Caller holds binaryMutex and publishes the row + fingerprint in one Room transaction. */
    internal fun writeNew(file: File, input: InputStream): BackupByteFingerprint {
        check(isDurableBackupBinary(context.filesDir, file)) { "Only durable attachment/library files can be tracked." }
        check(!file.exists()) { "Binary writes must stage a new file, not overwrite the current attachment." }
        file.parentFile?.mkdirs()
        try {
            return file.outputStream().use { output -> fingerprintBackupBytes(input, output) }
        } catch (error: Throwable) { file.delete(); throw error }
    }

    /** Restore holds binaryMutex and invalidates baseline trust before entering this write path. */
    internal fun invalidateBeforeRestoreWrite(id: String) {
        // Persist uncertainty before overwriting bytes, even if the later Restore transaction fails.
        database.openHelper.writableDatabase.execSQL(
            "UPDATE backup_binary_fingerprints SET sha256=NULL, status='UNKNOWN' WHERE attachmentId=?",
            arrayOf(id),
        )
    }

    internal suspend fun persistWritten(attachment: AttachmentEntity, digest: BackupByteFingerprint, mutated: Boolean = true) = database.withTransaction {
        check(isDurableBackupBinary(context.filesDir, File(attachment.localPath)))
        val current = database.attachmentDao().getByIdIncludingDeleted(attachment.id)
        check(current?.localPath == attachment.localPath && current.sizeBytes == attachment.sizeBytes) { "Attachment changed before its fingerprint could be recorded." }
        val dao = database.backupJournalDao()
        if (mutated && dao.clock().suppressionDepth == 0) {
            dao.tick()
            dao.dirtyBinary(attachment.id)
        }
        // Historical metadata may have an inaccurate byte size. Do not turn that into a false equality claim.
        val verified = digest.size == attachment.sizeBytes
        dao.putFingerprint(BackupBinaryFingerprint(attachment.id, attachment.localPath, digest.size,
            digest.sha256.takeIf { verified }, if (verified) "VERIFIED" else "UNKNOWN", dao.clock().generation))
    }

    /** Explicit one-file verification, for pre-journal files or repair; never invoked by normal capture. */
    internal suspend fun verifyExisting(id: String): BackupBinaryFingerprint = journal.binaryMutex.withLock {
        val attachment = database.attachmentDao().getByIdIncludingDeleted(id) ?: error("Attachment no longer exists.")
        val file = File(attachment.localPath)
        check(isDurableBackupBinary(context.filesDir, file))
        val digest = file.inputStream().use { fingerprintBackupBytes(it) }
        val old = database.backupJournalDao().fingerprint(id)
        persistWritten(attachment, digest, mutated = old?.status == "VERIFIED" && old.sha256 != digest.sha256)
        database.backupJournalDao().fingerprint(id)!!
    }

    /** Freeze only a required binary for a future writer. A stale/replaced/deleted source aborts safely. */
    internal suspend fun stageCaptured(dependency: CapturedBackupBinary, destination: File): BackupByteFingerprint = journal.binaryMutex.withLock {
        check(dependency.status == "VERIFIED" && dependency.sha256 != null)
        check(destination.canonicalPath.startsWith(context.cacheDir.canonicalPath + File.separator) && !destination.exists())
        val source = File(dependency.localPath)
        check(isDurableBackupBinary(context.filesDir, source))
        destination.parentFile?.mkdirs()
        try {
            val digest = source.inputStream().use { input -> destination.outputStream().use { fingerprintBackupBytes(input, it) } }
            check(digest.size == dependency.byteSize && digest.sha256 == dependency.sha256) { "Captured binary bytes changed. Discard this staged batch and capture again." }
            digest
        } catch (error: Throwable) { destination.delete(); throw error }
    }
}
