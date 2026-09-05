package com.myvault.app.data.sync

import org.json.JSONObject
import java.io.File

internal data class PublishedDriveBackup(val id: String, val text: String)

internal data class DriveBackupUpload(
    val path: String,
    val name: String,
    val kind: String,
    val file: File,
    val size: Long,
    val sha256: String,
)

internal interface DriveBackupPublicationTransport {
    fun readCommitted(): PublishedDriveBackup?
    fun existingMatches(id: String, kind: String, size: Long, sha256: String): Boolean
    fun create(upload: DriveBackupUpload): String
    fun verify(id: String, size: Long, sha256: String): Boolean
    fun preserve(previous: PublishedDriveBackup)
    fun publish(previousId: String?, text: String)
}

internal data class DriveBackupPublicationResult(
    val uploadedMetadata: Int,
    val uploadedFiles: Int,
    val skippedFiles: Int,
    val retainedObjects: Int,
    val retainedBytes: Long,
)

internal class DriveBackupPublicationFailure(cause: Exception, count: Int, bytes: Long) :
    IllegalStateException("${cause.message} Staging retained: at least $count object(s), $bytes bytes; no Drive files deleted.", cause)

/** The production publication sequence. Transport injection permits failures at
 * real upload/readback/publication boundaries without accessing a user's Drive. */
internal class DriveBackupPublisher(private val transport: DriveBackupPublicationTransport) {
    suspend fun publish(
        previous: PublishedDriveBackup?,
        uploads: List<DriveBackupUpload>,
        manifest: (Map<String, String>) -> String,
        checkAccount: () -> Unit,
        progress: suspend (Int, DriveBackupUpload) -> Unit = { _, _ -> },
    ): DriveBackupPublicationResult {
        var count = 0
        var bytes = 0L
        var metadataCount = 0
        var fileCount = 0
        var skipped = 0
        try {
            checkAccount()
            check(transport.readCommitted() == previous) { "The committed Drive backup changed before preparation. Nothing was published." }
            val previousEntries = previous?.let { JSONObject(it.text).getJSONArray("entries") }
            val oldByPath = buildMap<String, JSONObject> {
                if (previousEntries != null) for (index in 0 until previousEntries.length()) {
                    val entry = previousEntries.getJSONObject(index)
                    val id = entry.optString("cloudFileId")
                    check(id.isNotBlank()) { "This older backup lacks exact Drive file IDs. Backup stopped before creating duplicate names; compatibility review is required." }
                    val kind = entry.getString("kind")
                    check(transport.existingMatches(id, kind, entry.getLong("size"), entry.getString("sha256"))) {
                        "The previous backup's ${entry.getString("fileName")} cannot be verified. Nothing was published."
                    }
                    put(entry.getString("path"), entry)
                }
            }
            // Preserve the index as well as the immutable objects it identifies.
            previous?.let {
                transport.preserve(it)
                count += 1
                bytes += it.text.toByteArray(Charsets.UTF_8).size
            }
            val ids = mutableMapOf<String, String>()
            uploads.forEachIndexed { index, upload ->
                checkAccount()
                progress(index, upload)
                val old = oldByPath[upload.path]
                if (old != null && old.getString("sha256").equals(upload.sha256, ignoreCase = true) && old.getLong("size") == upload.size) {
                    ids[upload.path] = old.getString("cloudFileId")
                    if (upload.kind == "file") skipped += 1
                } else {
                    val id = transport.create(upload)
                    count += 1
                    bytes += upload.size
                    check(id.isNotBlank()) { "Drive returned no ID for ${upload.name}." }
                    check(transport.verify(id, upload.size, upload.sha256)) { "Drive byte verification failed for ${upload.name}. Nothing was published." }
                    ids[upload.path] = id
                    if (upload.kind == "file") fileCount += 1 else metadataCount += 1
                }
                progress(index + 1, upload)
            }
            val candidate = manifest(ids)
            checkAccount()
            check(transport.readCommitted() == previous) { "The committed Drive backup changed during upload. Nothing was published." }
            try {
                transport.publish(previous?.id, candidate)
            } catch (error: Exception) {
                // A failed HTTP response is not proof that the server rejected it.
                val observed = runCatching { transport.readCommitted() }.getOrNull()
                if (observed?.text != candidate || (previous != null && observed.id != previous.id)) throw error
            }
            val committed = transport.readCommitted()
            check(committed?.text == candidate && (previous == null || committed.id == previous.id)) {
                "Publication could not be confirmed. Files were retained; check the latest backup before retrying."
            }
            checkAccount()
            return DriveBackupPublicationResult(metadataCount, fileCount, skipped, count, bytes)
        } catch (error: Exception) {
            throw DriveBackupPublicationFailure(error, count, bytes)
        }
    }
}
