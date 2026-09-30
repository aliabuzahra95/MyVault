package com.myvault.app.data.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.myvault.app.data.local.dao.AttachmentDao
import com.myvault.app.data.preferences.VaultPreferences
import com.myvault.app.data.repository.BackupRepository
import com.myvault.app.data.repository.IncrementalBackupFormat
import com.myvault.app.data.repository.BackupBinaryDescriptor
import com.myvault.app.data.repository.*
import com.myvault.app.data.local.VaultDatabase
import com.myvault.app.data.preferences.normalizeGoogleDriveAccount
import kotlinx.coroutines.CancellationException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

const val DriveConflictMessage = "Cloud contains newer MyVault changes. Pull latest first, then push again."
internal const val DriveReconnectMessage = "Google Drive access expired. Tap Login, choose your Google account again, then retry."
internal const val DriveConsentMessage = "Google Drive needs your permission. Approve the Google consent screen, then MyVault will continue."
private const val HttpUnauthorized = 401

internal fun shouldRefreshDriveToken(responseCode: Int, attempt: Int): Boolean =
    responseCode == HttpUnauthorized && attempt == 0

internal fun hasNewerRemoteDriveVersion(remoteVersion: Long, accountManifestVersion: Long): Boolean =
    remoteVersion > 0L && remoteVersion > accountManifestVersion

internal fun uploadedBytesMatchManifest(bytes: ByteArray, expectedSize: Long, expectedSha256: String): Boolean =
    bytes.size.toLong() == expectedSize && bytes.sha256() == expectedSha256

private class DriveAuthenticationException : IllegalStateException(DriveReconnectMessage)
private class GraphDriveHttpException(val status: Int, detail: String? = null) : IllegalStateException(
    "Google Drive returned HTTP $status" + (detail?.let { ": $it" } ?: ". No immutable object was overwritten."),
)

private fun Throwable.isInterruptedDriveConnection(): Boolean {
    val text = generateSequence(this) { it.cause }
        .joinToString(" ") { it.message.orEmpty() }
    return this is SocketException ||
        this is SocketTimeoutException ||
        this is UnknownHostException ||
        text.contains("Software caused connection abort", ignoreCase = true) ||
        text.contains("Connection reset", ignoreCase = true) ||
        text.contains("timeout", ignoreCase = true)
}

@Singleton
class GoogleDriveIncrementalSyncRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val attachmentDao: AttachmentDao,
    private val preferences: VaultPreferences,
    private val database: VaultDatabase,
    private val backupJournal: BackupChangeJournal,
    private val pendingBackupCapture: PendingBackupCapture,
    private val baselinePreparer: BackupBaselinePreparer,
) {
    /** Production Google sign-in, GET-only Drive inspection and read-only local snapshot. */
    suspend fun checkGraphBackupReadiness(): String = withContext(Dispatchers.IO) {
        val signedIn = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext "Connect Google Drive first. Nothing was changed."
        if (!GoogleSignIn.hasPermissions(signedIn, DriveScope)) return@withContext "Reconnect Google Drive and approve access first. Nothing was changed."
        val email = normalizeGoogleDriveAccount(signedIn.email.orEmpty())
        if ('@' !in email) return@withContext "The Google account could not be verified. Nothing was changed."
        try {
            val drive = DriveApiClient(context, signedIn)
            val inventory = drive.inspectGraphReadiness(email)
            val local = readLocalBackupReadiness(database, email)
            val graph = inventory.lineage?.let { BackupGraph.discover(inventory.objects,inventory.accountId,it) }
            check(normalizeGoogleDriveAccount(GoogleSignIn.getLastSignedInAccount(context)?.email.orEmpty()) == email)
            if (local.lineage != null && inventory.lineage != null && local.lineage != inventory.lineage) {
                "The visible graph has a different lineage from this Vault. Reconciliation is required. Nothing was changed."
            } else reconcileBackupGraph(local,inventory.accountId,inventory.legacyVisible,inventory.namespaceCount,graph,inventory.objects).message()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            "Backup readiness could not be verified. No upload, Restore, deletion or trust change occurred. Check your connection and Google Drive sign-in, then try again."
        }
    }

    suspend fun prepareSignInIntent(): Intent {
        val client = GoogleSignIn.getClient(context, signInOptions())
        val intent = suspendCancellableCoroutine { continuation ->
            client.signOut().addOnCompleteListener {
                if (continuation.isActive) continuation.resume(client.signInIntent)
            }
        }
        preferences.setGoogleDriveAccountEmail("")
        return intent
    }

    private suspend fun openGraphStore(drive: DriveApiClient, email: String, allowEnrollment: Boolean): AccountBoundGraphStore? {
        val (driveId, api) = drive.graphApi(email)
        val identity = IncrementalBackupFormat.sha256(driveId.toByteArray(Charsets.UTF_8))
        val enrollment = GraphNamespaceEnrollment(File(context.filesDir, "backup-graph-enrollment/$identity.json"), api)
        val owned = enrollment.load(email, driveId)
        val roots = api.roots(BackupGraphNamespace)
        check(roots.size <= 1) { "Multiple graph namespaces require reconciliation." }
        val layout = if (owned != null) {
            check(roots.all { it.id == owned.rootId }) { "The graph namespace differs from this account's enrolled namespace." }
            val established = database.backupGraphDao().bindings(email).isNotEmpty() ||
                database.backupGraphRestoreDao().appliedForAccount(email).isNotEmpty() ||
                database.backupGraphDao().unfinished(email).isNotEmpty()
            if (established) check(roots.size == 1) { "The enrolled graph root is no longer visible. It was not recreated." }
            if (allowEnrollment && !established) enrollment.enroll(email, driveId) else owned
        } else if (roots.isNotEmpty()) {
            discoverGraphLayout(api, email, driveId) ?: error("Graph discovery did not complete.")
        } else {
            check(database.backupGraphDao().bindings(email).isEmpty() && database.backupGraphRestoreDao().appliedForAccount(email).isEmpty()) {
                "The trusted graph namespace is not visible. No replacement baseline was created."
            }
            if (!allowEnrollment) return null
            // The initial operation is the only full-inventory path. Legacy data is inspected, never rewritten.
            val remote = drive.inspectGraphReadiness(email)
            val local = readLocalBackupReadiness(database, email)
            val decision = reconcileBackupGraph(local, driveId, remote.legacyVisible, remote.namespaceCount, null, emptyList())
            check(decision.state in setOf(BackupGraphReadinessState.FIRST_BASELINE_REQUIRED, BackupGraphReadinessState.LEGACY_BASELINE_REQUIRED)) {
                "Current Vault/Drive relationship requires reconciliation. Nothing was published."
            }
            enrollment.enroll(email, driveId)
        }
        return AccountBoundGraphStore(GraphWriterContext(email, driveId, layout.lineageId), api, layout)
    }

    private suspend fun runGraphBackup(drive: DriveApiClient, email: String, force: Boolean,
        onProgress: suspend (DriveRestoreProgress) -> Unit): DriveSyncResult = try {
        check(BackupGraphPublicationEnabled && BackupGraphTargetedRestoreEnabled) { "Backup/Restore must be enabled together." }
        val store = openGraphStore(drive, email, true) ?: error("Graph enrollment is required.")
        val writer = InternalBackupGraphWriter(database, backupJournal, pendingBackupCapture, context.filesDir,
            File(context.filesDir, "backup-graph-publications"), store, onProgress = { onProgress(it.toDriveProgress()) })
        val unfinished = database.backupGraphDao().unfinished(email)
        val result = if (unfinished.isNotEmpty()) writer.publish() else {
            check(database.backupGraphRestoreDao().unfinished().isEmpty()) { "Complete the interrupted Restore before Backup." }
            val binding = database.backupGraphDao().binding(email, store.context.lineageId)
            val applied = database.backupGraphRestoreDao().applied(email, store.context.lineageId)
            if (binding == null && applied == null) {
                check(store.commits().isEmpty()) { "A graph already exists. Restore/reconcile it before creating a baseline." }
                val prepared = baselinePreparer.prepare(email) { onProgress(it.toDriveProgress()) }
                try { writer.createRoot(prepared) } finally { prepared.directory.deleteRecursively() }
            } else {
                check(!force) { "Force Backup is not available for graph backups. Normal Backup preserves immutable history and captures pending changes." }
                if (trustedPublicationPosition(database, store.context) == null) {
                    check(adoptVerifiedRestoredParent(database, store.context, store.commits())) {
                        "A verified Restore or baseline is required before Backup. Local changes were preserved."
                    }
                }
                writer.publish()
            }
        }
        if (result.forkDetected) DriveSyncResult.Conflict("Fork detected. Both immutable backups were preserved. Backup/Restore require reconciliation.")
        else DriveSyncResult.Success(if (result.alreadyBackedUp) "Already backed up." else "Backup complete. Your previous legacy backup remains preserved.")
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        DriveSyncResult.Failure(error.driveMessage("Backup failed safely; pending local changes and previous backups were preserved"))
    }

    private suspend fun runGraphRestore(drive: DriveApiClient, email: String,
        onProgress: suspend (DriveRestoreProgress) -> Unit): DriveSyncResult? = try {
        check(BackupGraphPublicationEnabled && BackupGraphTargetedRestoreEnabled) { "Backup/Restore must be enabled together." }
        val store = openGraphStore(drive, email, false)
        if (store == null) null else {
            check(database.backupGraphDao().unfinished(email).isEmpty()) { "Recover the unfinished Backup before Restore." }
            val restoring = "Checking graph backup and missing updates"
            onProgress(DriveRestoreProgress(DriveRestoreStage.Preparing, restoring, detail = restoring))
            val result = InternalBackupGraphRestore(database, backupJournal, context.filesDir,
                File(context.filesDir, "backup-graph-restores"), store, GraphRestorePreferences(preferences)).restore()
            when (result.status) {
                GraphRestoreStatus.APPLIED -> DriveSyncResult.Success("Restore complete. Applied ${result.rowsWritten} updates.")
                GraphRestoreStatus.ALREADY_CURRENT -> DriveSyncResult.Success("Already up to date.")
                GraphRestoreStatus.LOCAL_CHANGES -> DriveSyncResult.Failure("Local changes need to be backed up or resolved before Restore. Nothing was overwritten.")
                GraphRestoreStatus.FORK -> DriveSyncResult.Conflict("Fork detected. Restore cannot choose a branch. Local data was preserved.")
                GraphRestoreStatus.UNSUPPORTED -> DriveSyncResult.Failure("Unsupported backup version. Update MyVault before Restore.")
                GraphRestoreStatus.DIVERGENT -> DriveSyncResult.Failure("Local and remote backup histories diverge. Reconciliation is required.")
                else -> DriveSyncResult.Failure("Restore blocked safely: ${result.status.name.lowercase().replace('_', ' ')}. Local data was preserved.")
            }
        }
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        DriveSyncResult.Failure(error.driveMessage("Restore failed safely; no unverified graph position was applied"))
    }

    suspend fun handleSignInResult(data: Intent?): DriveAuthorizationResult = withContext(Dispatchers.IO) {
        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        }.getOrElse { error ->
            return@withContext DriveAuthorizationResult.Failure(error.googleSignInMessage())
        }
        if (!GoogleSignIn.hasPermissions(account, DriveScope)) {
            return@withContext DriveAuthorizationResult.Failure("Google Drive permission was not granted. Please connect Drive again.")
        }
        authorizeAccount(account)
    }

    suspend fun prepareDriveAuthorization(): DriveAuthorizationResult = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext DriveAuthorizationResult.Failure("Connect Google Drive first.")
        if (!GoogleSignIn.hasPermissions(account, DriveScope)) {
            return@withContext DriveAuthorizationResult.Failure("Google Drive permission is missing. Tap Login and connect your account again.")
        }
        authorizeAccount(account)
    }

    suspend fun pushToDrive(
        force: Boolean = false,
        onProgress: suspend (DriveRestoreProgress) -> Unit = {},
    ): DriveSyncResult = withContext(Dispatchers.IO) {
        onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = "Preparing Google Drive backup"))
        val driveAccount = driveAccountOrFailure() ?: return@withContext DriveSyncResult.Failure("Connect Google Drive first.")
        val drive = driveAccount.client
        if (BackupGraphPublicationEnabled) {
            return@withContext runGraphBackup(drive, driveAccount.email, force, onProgress)
        }
        if (drive.hasGraphNamespace() || database.backupGraphDao().bindings(driveAccount.email).isNotEmpty()) {
            return@withContext DriveSyncResult.Failure("A graph backup exists. The legacy backup was preserved. This version cannot publish until graph Backup is enabled.")
        }
        val vault = drive.ensureMyVaultLayout()
        val previous = drive.readCommittedBackup(vault.manifests.id)
        if (previous != null && JSONObject(previous.text).has("incrementalBackup")) {
            return@withContext DriveSyncResult.Failure("Incremental backup publication is disabled pending the coordinated Android/Web release. This backup was not changed.")
        }
        val remoteVersion = previous?.let { JSONObject(it.text).optLong("cloudVersion", 0L) } ?: 0L
        val lastSyncedVersion = preferences.googleDriveSyncMetadata(driveAccount.email).lastManifestAt
        if (!force && hasNewerRemoteDriveVersion(remoteVersion, lastSyncedVersion)) {
            return@withContext DriveSyncResult.Conflict(DriveConflictMessage)
        }
        val metadataDir = File(context.cacheDir, "drive-api-sync-metadata-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = "Exporting changed metadata"))
            backupRepository.exportMetadataForDriveSync(metadataDir)
            val localFileEntries = metadataDir.localFileDriveEntries()
            metadataDir.reconcileAttachmentFileClaims(localFileEntries.map { it.backupEntry }.toSet())
            val entries = metadataDir.toMetadataDriveEntries() + localFileEntries
            val uploads = entries.map { DriveBackupUpload(it.path, it.fileName, it.kind, it.file, it.size, it.sha256) }
            if (!force && matchesCommittedBackup(previous, uploads)) {
                preferences.markGoogleDriveSync(driveAccount.email, remoteVersion)
                return@withContext DriveSyncResult.Success("Already backed up. No files uploaded or backup snapshot replaced.")
            }
            val cloudVersion = maxOf(System.currentTimeMillis(), remoteVersion + 1)
            val transport = object : DriveBackupPublicationTransport {
                override fun readCommitted() = drive.readCommittedBackup(vault.manifests.id)
                override fun existingMatches(id: String, kind: String, size: Long, sha256: String): Boolean =
                    drive.existingFileMatches(id, if (kind == EntryKindFile) vault.files.id else vault.metadata.id, size, sha256)

                override fun create(upload: DriveBackupUpload): String {
                    // Freeze changed binary bytes before the upload; never modify the original.
                    val source = if (upload.kind == EntryKindFile) {
                        File(metadataDir, "binary-${java.util.UUID.randomUUID()}").also {
                            upload.file.copyTo(it)
                            check(it.length() == upload.size && it.sha256() == upload.sha256) {
                                "The attachment changed while backup was being prepared. Try again."
                            }
                        }
                    } else upload.file
                    return drive.uploadFile(
                        parentId = if (upload.kind == EntryKindFile) vault.files.id else vault.metadata.id,
                        existingFileId = null,
                        name = upload.name,
                        mimeType = if (upload.kind == EntryKindFile) "application/octet-stream" else "application/json",
                        source = source,
                    ).id
                }

                override fun verify(id: String, size: Long, sha256: String) = drive.uploadedFileMatches(id, size, sha256)

                override fun preserve(previous: PublishedDriveBackup) {
                    val bytes = previous.text.toByteArray(Charsets.UTF_8)
                    val copy = drive.uploadTextFile(
                        parentId = vault.backups.id,
                        existingFileId = null,
                        name = "sync-manifest-${remoteVersion}-${java.util.UUID.randomUUID()}.json",
                        text = previous.text,
                        mimeType = "application/json",
                    )
                    check(drive.uploadedFileMatches(copy.id, bytes.size.toLong(), bytes.sha256())) { "Previous manifest preservation failed." }
                }

                override fun publish(previousId: String?, text: String) {
                    drive.uploadTextFile(vault.manifests.id, previousId, SyncManifestFile, text, "application/json")
                }
            }
            val result = DriveBackupPublisher(transport).publish(
                previous = previous,
                uploads = uploads,
                manifest = { ids ->
                    entries.forEach { it.cloudFileId = ids.getValue(it.path) }
                    entries.toManifest(cloudVersion).toString(2)
                },
                checkAccount = {
                    check(GoogleSignIn.getLastSignedInAccount(context)?.email?.trim().equals(driveAccount.email, ignoreCase = true)) {
                        "The Google account changed. Backup stopped without deleting Drive files."
                    }
                },
                progress = { count, entry ->
                    onProgress(DriveRestoreProgress(
                        stage = if (count == entries.size) DriveRestoreStage.Finalising else DriveRestoreStage.Uploading,
                        message = if (count == entries.size) "Verifying and publishing backup" else "Uploading ${entry.name}",
                        current = count,
                        total = entries.size,
                    ))
                },
            )
            preferences.markGoogleDriveSync(driveAccount.email, cloudVersion)
            DriveSyncResult.Success(
                "Drive push complete: ${result.uploadedMetadata} metadata file(s), ${result.uploadedFiles} file(s) uploaded, " +
                    "${result.skippedFiles} unchanged file(s) skipped. Previous backup files retained.",
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error.requiresDriveReconnect()) preferences.setGoogleDriveAccountEmail("")
            DriveSyncResult.Failure(error.driveMessage("Drive push failed"))
        } finally {
            metadataDir.deleteRecursively()
        }
    }
    suspend fun pullLatestFromDrive(
        onProgress: suspend (DriveRestoreProgress) -> Unit = {},
    ): DriveSyncResult = withContext(Dispatchers.IO) {
        onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = "Preparing Google Drive restore"))
        val driveAccount = driveAccountOrFailure() ?: return@withContext DriveSyncResult.Failure("Connect Google Drive first.")
        val drive = driveAccount.client
        if (BackupGraphTargetedRestoreEnabled) {
            val graphResult = runGraphRestore(drive, driveAccount.email, onProgress)
            if (graphResult != null) return@withContext graphResult
        } else if (drive.hasGraphNamespace() || database.backupGraphDao().bindings(driveAccount.email).isNotEmpty() ||
            database.backupGraphRestoreDao().appliedForAccount(driveAccount.email).isNotEmpty()) {
            return@withContext DriveSyncResult.Failure("A graph backup exists. This version cannot Restore it until graph Restore is enabled. Local data was not changed.")
        }
        val vault = drive.ensureMyVaultLayout()
        val manifestFile = drive.findChild(vault.manifests.id, SyncManifestFile)
            ?: return@withContext DriveSyncResult.Failure("No MyVault Drive sync manifest found yet. Push from your latest device first.")
        val manifest = drive.downloadJsonObject(manifestFile.id)
        val entries = manifest.toRemoteEntryMap().values.sortedWith(compareBy<RemoteEntry> { it.kind }.thenBy { it.path })
        if (entries.isEmpty()) return@withContext DriveSyncResult.Failure("Drive sync manifest is empty.")

        val zipFile = File(context.cacheDir, "drive-api-sync-pull-${System.currentTimeMillis()}.vaultbackup")
        var downloadedFiles = 0
        var reusedLocalFiles = 0
        try {
            if (manifest.has("incrementalBackup")) {
                return@withContext restoreIncrementalBackup(drive, manifest, zipFile, driveAccount.email, onProgress)
            }
            val localAttachments = attachmentDao.getAllIncludingDeleted().associateBy { it.id }
            onProgress(
                DriveRestoreProgress(
                    stage = DriveRestoreStage.Downloading,
                    message = "Downloading changed vault files",
                    current = 0,
                    total = entries.size,
                ),
            )
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                entries.forEachIndexed { index, entry ->
                    onProgress(
                        DriveRestoreProgress(
                            stage = DriveRestoreStage.Downloading,
                            message = if (entry.kind == EntryKindFile) "Restoring file ${entry.fileName}" else "Restoring metadata ${entry.fileName}",
                            current = index + 1,
                            total = entries.size,
                        ),
                    )
                    zip.putNextEntry(ZipEntry(entry.backupEntry))
                    if (entry.kind == EntryKindFile) {
                        val attachmentId = entry.backupEntry.removePrefix("files/")
                        val localFile = localAttachments[attachmentId]?.localPath?.let { File(it) }
                        if (localFile != null && localFile.exists() && localFile.isFile && localFile.sha256() == entry.sha256) {
                            localFile.inputStream().use { it.copyTo(zip) }
                            reusedLocalFiles += 1
                        } else {
                            val fileId = entry.cloudFileId.ifBlank { drive.findChild(vault.files.id, entry.fileName)?.id.orEmpty() }
                            if (fileId.isBlank()) error("Missing Drive sync file: ${entry.fileName}")
                            drive.copyFileToWithRetry(fileId, zip)
                            downloadedFiles += 1
                        }
                    } else {
                        val fileId = entry.cloudFileId.ifBlank { drive.findChild(vault.metadata.id, entry.fileName)?.id.orEmpty() }
                        if (fileId.isBlank()) error("Missing Drive sync metadata: ${entry.fileName}")
                        drive.copyFileToWithRetry(fileId, zip)
                    }
                    zip.closeEntry()
                }
            }
            onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Verifying, message = "Verifying restored package"))
            onProgress(DriveRestoreProgress(stage = DriveRestoreStage.RestoringFiles, message = "Rebuilding restored files"))
            onProgress(DriveRestoreProgress(stage = DriveRestoreStage.RestoringDatabase, message = "Restoring vault database"))
            val restored = backupRepository.restoreBackupFromFile(zipFile)
            onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Finalising, message = "Finalising restore"))
            val cloudVersion = manifest.optLong("cloudVersion", System.currentTimeMillis())
            preferences.markGoogleDriveSync(driveAccount.email, cloudVersion)
            val missingFilesMessage = if (restored.missingAttachmentCount > 0) {
                " ${restored.missingAttachmentCount} unavailable attachment file(s) were skipped; all other vault data was restored."
            } else {
                ""
            }
            DriveSyncResult.Success(
                "Drive pull complete: ${restored.noteCount} notes, ${restored.attachmentCount} attachments. " +
                    "$downloadedFiles file(s) downloaded, $reusedLocalFiles reused locally.$missingFilesMessage",
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error.requiresDriveReconnect()) preferences.setGoogleDriveAccountEmail("")
            DriveSyncResult.Failure(error.driveMessage("Drive pull failed"))
        } finally {
            zipFile.delete()
        }
    }

    private suspend fun restoreIncrementalBackup(
        drive: DriveApiClient,
        manifest: JSONObject,
        zipFile: File,
        email: String,
        onProgress: suspend (DriveRestoreProgress) -> Unit,
    ): DriveSyncResult {
        val extension = IncrementalBackupFormat.extension(manifest)!!
        val rawEntries = manifest.getJSONArray("entries")
        val paths = (0 until rawEntries.length()).map { rawEntries.getJSONObject(it).getString("path") }
        check(paths.toSet().size == paths.size) { "Duplicate checkpoint paths." }
        val entries = manifest.toRemoteEntryMap().values
        val checkpoint = linkedMapOf<String, String>()
        entries.filter { it.kind == EntryKindMetadata }.forEach { entry ->
            check(entry.cloudFileId.isNotBlank()) { "Checkpoint lacks an exact Drive file ID." }
            val bytes = drive.downloadBytes(entry.cloudFileId)
            check(uploadedBytesMatchManifest(bytes, entry.size, entry.sha256)) { "Checkpoint checksum verification failed." }
            checkpoint[entry.backupEntry] = bytes.toString(Charsets.UTF_8)
        }
        val checkpointBinaries = if (extension.getInt("version") == 2) entries.filter { it.kind == EntryKindFile }.map { entry ->
            check(entry.backupEntry.startsWith("files/")) { "Invalid checkpoint binary path." }
            val id = entry.backupEntry.removePrefix("files/")
            BackupBinaryDescriptor(id, entry.cloudFileId, entry.sha256, entry.size).validate()
        } else null
        val reconstructed = IncrementalBackupFormat.reconstruct(checkpoint, extension, checkpointBinaries) { descriptor ->
            drive.downloadBytes(descriptor.getString("cloudFileId"))
        }
        val metadata = reconstructed.files.toMutableMap()
        val marker = JSONObject().put("checkpointId", extension.getString("checkpointId")).put("headId", reconstructed.headId)
        metadata["manifest.json"] = JSONObject(metadata.getValue("manifest.json")).put("incrementalBackupApplied", marker).toString()
        metadata["permanent_deletions.json"] = JSONObject().put("format", "myvault-permanent-deletions").put("version", 1)
            .put("state", marker).put("changes", JSONArray(reconstructed.permanentDeletions.map { it.toJson() })).toString()
        val files = reconstructed.binaries?.map { binary ->
            RemoteEntry(path = "files/${binary.attachmentId}", fileName = binary.attachmentId,
                backupEntry = "files/${binary.attachmentId}", kind = EntryKindFile,
                sha256 = binary.sha256, size = binary.size, cloudFileId = binary.cloudFileId)
        } ?: entries.filter { it.kind == EntryKindFile }
        ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
            metadata.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            files.forEachIndexed { index, entry ->
                onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Downloading, message = "Verifying checkpoint file ${index + 1} of ${files.size}", current = index + 1, total = files.size))
                check(entry.cloudFileId.isNotBlank()) { "Checkpoint lacks an exact binary file ID." }
                val staged = File(context.cacheDir, "verified-checkpoint-${java.util.UUID.randomUUID()}")
                try {
                    staged.outputStream().use { drive.copyFileToWithRetry(entry.cloudFileId, it) }
                    check(staged.length() == entry.size && staged.sha256() == entry.sha256) { "Checkpoint file checksum verification failed." }
                    zip.putNextEntry(ZipEntry(entry.backupEntry))
                    staged.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                } finally {
                    staged.delete()
                }
            }
        }
        onProgress(DriveRestoreProgress(stage = DriveRestoreStage.RestoringDatabase, message = "Restoring verified checkpoint and changes"))
        val restored = backupRepository.restoreBackupFromFile(zipFile)
        preferences.markGoogleDriveSync(email, manifest.getLong("cloudVersion"))
        return DriveSyncResult.Success("Drive restore complete: ${restored.noteCount} notes; verified incremental backup ${reconstructed.headId}.")
    }

    suspend fun checkForRemoteUpdates(): DriveSyncResult = withContext(Dispatchers.IO) {
        val driveAccount = driveAccountOrFailure() ?: return@withContext DriveSyncResult.Skipped("Connect Google Drive first.")
        val drive = driveAccount.client
        val vault = drive.ensureMyVaultLayout()
        val remoteVersion = drive.findChild(vault.manifests.id, SyncManifestFile)
            ?.let { drive.downloadJsonObject(it.id).optLong("cloudVersion", 0L) }
            ?: 0L
        val localVersion = preferences.googleDriveSyncMetadata(driveAccount.email).lastManifestAt
        when {
            remoteVersion <= 0L -> DriveSyncResult.Skipped("No Drive sync has been pushed yet.")
            hasNewerRemoteDriveVersion(remoteVersion, localVersion) ->
                DriveSyncResult.Conflict("New MyVault updates are available from Drive.")
            else -> DriveSyncResult.Success("Drive sync is up to date.")
        }
    }

    private fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DriveScope)
            .build()

    private suspend fun driveAccountOrFailure(): AuthorizedDriveAccount? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        if (!GoogleSignIn.hasPermissions(account, DriveScope)) return null
        val email = account.email?.trim().orEmpty()
        if (email.isBlank()) return null
        preferences.setGoogleDriveAccountEmail(email)
        return AuthorizedDriveAccount(
            email = email,
            client = DriveApiClient(context, account),
        )
    }

    private data class AuthorizedDriveAccount(
        val email: String,
        val client: DriveApiClient,
    )

    private fun File.toMetadataDriveEntries(): List<DriveEntry> =
        listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.map { file ->
                DriveEntry(
                    path = "metadata/${file.name}",
                    fileName = file.name,
                    backupEntry = file.name,
                    kind = EntryKindMetadata,
                    mimeType = "application/json",
                    size = file.length(),
                    sha256 = file.sha256(),
                    file = file,
                )
            }
            .orEmpty()

    private fun File.reconcileAttachmentFileClaims(availableBackupEntries: Set<String>) {
        val attachmentsFile = resolve("attachments.json")
        if (!attachmentsFile.exists()) return
        val attachments = JSONArray(attachmentsFile.readText())
        for (index in 0 until attachments.length()) {
            val attachment = attachments.getJSONObject(index)
            val id = attachment.getString("id")
            val backupEntry = "files/$id"
            attachment.put("fileEntry", if (backupEntry in availableBackupEntries) backupEntry else "")
        }
        attachmentsFile.writeText(attachments.toString())
    }

    private fun File.localFileDriveEntries(): List<DriveEntry> {
        val json = JSONArray(resolve("attachments.json").readText())
        val attachments = (0 until json.length()).map { json.getJSONObject(it) }
        return attachments
            .mapNotNull { attachment ->
                val file = File(attachment.getString("localPath"))
                if (!file.exists() || !file.isFile) return@mapNotNull null
                val attachmentId = attachment.getString("id")
                val fileName = "${attachmentId}.${attachment.getString("fileName").safeExtension()}"
                val path = "files/$fileName"
                DriveEntry(
                    path = path,
                    fileName = fileName,
                    backupEntry = "files/$attachmentId",
                    kind = EntryKindFile,
                    mimeType = "application/octet-stream",
                    size = file.length(),
                    sha256 = file.sha256(),
                    file = file,
                )
            }
    }

    private fun List<DriveEntry>.toManifest(cloudVersion: Long): JSONObject =
        JSONObject()
            .put("schemaVersion", 1)
            .put("cloudVersion", cloudVersion)
            .put("storage", "google-drive-api")
            .put("layout", "MyVault/metadata, MyVault/files, MyVault/manifests, MyVault/backups")
            .put(
                "entries",
                JSONArray().also { array ->
                    sortedBy { it.path }.forEach { entry ->
                        array.put(
                            JSONObject()
                                .put("path", entry.path)
                                .put("fileName", entry.fileName)
                                .put("backupEntry", entry.backupEntry)
                                .put("kind", entry.kind)
                                .put("sha256", entry.sha256)
                                .put("size", entry.size)
                                .put("cloudFileId", entry.cloudFileId)
                                .put("updatedAt", cloudVersion),
                        )
                    }
                },
            )

    private fun JSONObject?.toRemoteEntryMap(): Map<String, RemoteEntry> {
        if (this == null) return emptyMap()
        val entries = optJSONArray("entries") ?: return emptyMap()
        return buildMap {
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val path = item.getString("path")
                put(
                    path,
                    RemoteEntry(
                        path = path,
                        fileName = item.optString("fileName", path.substringAfterLast('/')),
                        backupEntry = item.optString("backupEntry", path.removePrefix("metadata/")),
                        kind = item.optString("kind", if (path.startsWith("files/")) EntryKindFile else EntryKindMetadata),
                        sha256 = item.getString("sha256"),
                        size = item.optLong("size", -1L),
                        cloudFileId = item.optString("cloudFileId"),
                    ),
                )
            }
        }
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun DriveEntry.ensureSha256() {
        if (sha256.isBlank()) sha256 = file.sha256()
    }

    private fun String.safeExtension(): String {
        val raw = substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
            .filter { it.isLetterOrDigit() }
        return raw.takeIf { it.isNotBlank() && it.length <= 8 } ?: "bin"
    }

    private fun Throwable.driveMessage(prefix: String): String =
        when {
            requiresDriveConsent() -> "$prefix: $DriveConsentMessage Open Backup & restore and tap Login if the consent screen did not open."
            requiresDriveReconnect() -> "$prefix: $DriveReconnectMessage"
            isInterruptedDriveConnection() -> "$prefix: Google Drive connection was interrupted while downloading. Check Wi-Fi/mobile signal and try Restore again."
            else -> message?.let { "$prefix: $it" } ?: prefix
        }

    private fun Throwable.requiresDriveReconnect(): Boolean =
        generateSequence(this) { it.cause }.any { it is DriveAuthenticationException }

    private fun Throwable.requiresDriveConsent(): Boolean =
        generateSequence(this) { it.cause }.any { error ->
            error is UserRecoverableAuthException || error.message.isRemoteConsentMessage()
        }

    private suspend fun authorizeAccount(account: GoogleSignInAccount): DriveAuthorizationResult {
        return try {
            val androidAccount = account.account
                ?: return DriveAuthorizationResult.Failure("Google account is unavailable. Tap Login and connect Drive again.")
            GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DriveScopeUrl")
            preferences.setGoogleDriveAccountEmail(account.email.orEmpty())
            DriveAuthorizationResult.Ready(
                "Google Drive connected${account.email?.let { " as $it" }.orEmpty()}.",
            )
        } catch (error: UserRecoverableAuthException) {
            error.intent?.let { recoveryIntent ->
                DriveAuthorizationResult.ConsentRequired(recoveryIntent, DriveConsentMessage)
            } ?: DriveAuthorizationResult.Failure(
                "$DriveConsentMessage Tap Login to reopen Google's permission screen.",
            )
        } catch (error: Throwable) {
            val message = if (error.requiresDriveConsent()) {
                "$DriveConsentMessage Tap Login to reopen Google's permission screen."
            } else {
                error.googleSignInMessage()
            }
            DriveAuthorizationResult.Failure(message)
        }
    }

    private fun Throwable.googleSignInMessage(): String =
        if (this is ApiException && statusCode == GoogleSignInStatusCodes.DEVELOPER_ERROR) {
            "Google Drive sign in is not configured for this signed app yet. Add the matching Android OAuth client in Google Cloud or Firebase, then retry."
        } else if (this is ApiException) {
            "Google Drive sign in failed: ${GoogleSignInStatusCodes.getStatusCodeString(statusCode)} ($statusCode)."
        } else {
            message ?: "Google Drive sign in was cancelled."
        }

    private class DriveApiClient(
        private val context: Context,
        private val account: GoogleSignInAccount,
    ) {
        fun hasGraphNamespace(): Boolean = readinessList("name = '${BackupGraphNamespace.escapeDriveQuery()}' and mimeType = '$FolderMimeType' and trashed = false").isNotEmpty()

        fun graphApi(email: String): Pair<String, GraphDriveApi> {
            fun assertActive() {
                check(normalizeGoogleDriveAccount(account.email.orEmpty()) == email &&
                    normalizeGoogleDriveAccount(GoogleSignIn.getLastSignedInAccount(context)?.email.orEmpty()) == email) {
                    "Google account changed. Backup/Restore stopped without acknowledging local changes."
                }
            }
            assertActive()
            val user = requestJson("GET", "https://www.googleapis.com/drive/v3/about?fields=user(permissionId,emailAddress)").getJSONObject("user")
            check(normalizeGoogleDriveAccount(user.getString("emailAddress")) == email)
            val driveId = user.getString("permissionId").also(BackupGraphProtocol::id)
            assertActive()
            fun JSONObject.graphFile() = GraphDriveFile(getString("id"), getString("name"), getString("mimeType"),
                optJSONArray("parents")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                optBoolean("trashed"), if (has("size")) getString("size").toLong() else null,
                optString("sha256Checksum").takeIf { it.isNotEmpty() })
            val fields = "id,name,mimeType,parents,trashed,size,sha256Checksum"
            fun list(query: String): List<GraphDriveFile> {
                assertActive(); val result = mutableListOf<GraphDriveFile>(); var page: String? = null
                do {
                    val suffix = page?.let { "&pageToken=${it.urlEncode()}" }.orEmpty()
                    val json = requestJson("GET", "$DriveFilesUrl?q=${query.urlEncode()}&spaces=drive&pageSize=1000&fields=nextPageToken,files($fields)$suffix")
                    val files = json.getJSONArray("files")
                    for (i in 0 until files.length()) result += files.getJSONObject(i).graphFile()
                    page = json.optString("nextPageToken").takeIf { it.isNotBlank() }
                    assertActive()
                } while (page != null)
                return result
            }
            val api = object : GraphDriveApi {
                override suspend fun assertAccount(account: String, driveAccountId: String) {
                    check(account == email && driveAccountId == driveId); assertActive()
                }
                override suspend fun roots(name: String): List<GraphDriveFile> {
                    check(name == BackupGraphNamespace)
                    return list("name = '${name.escapeDriveQuery()}' and mimeType = '$FolderMimeType' and trashed = false")
                }
                override suspend fun children(parent: String): List<GraphDriveFile> {
                    BackupGraphProtocol.id(parent)
                    return list("'${parent.escapeDriveQuery()}' in parents and trashed = false")
                }
                override suspend fun metadata(id: String): GraphDriveFile? {
                    BackupGraphProtocol.id(id); assertActive()
                    return try { requestJson("GET", "$DriveFilesUrl/${id.urlPathEncode()}?fields=$fields").graphFile().also { assertActive() } }
                    catch (e: GraphDriveHttpException) { assertActive(); if (e.status == 404) null else throw e }
                }
                override suspend fun download(id: String): java.io.InputStream {
                    BackupGraphProtocol.id(id); assertActive()
                    return openGraphDownload(id) { assertActive() }
                }
                override suspend fun reserveIds(count: Int): List<String> {
                    check(count in 1..1000); assertActive()
                    val ids = requestJson("GET", "$DriveFilesUrl/generateIds?space=drive&type=files&count=$count").getJSONArray("ids")
                    assertActive(); return (0 until ids.length()).map { ids.getString(it) }
                }
                override suspend fun createFolder(id: String, name: String, parent: String?) {
                    assertActive(); BackupGraphProtocol.id(id)
                    val body = JSONObject().put("id", id).put("name", name).put("mimeType", FolderMimeType)
                    if (parent != null) body.put("parents", JSONArray().put(parent))
                    try { check(requestJson("POST", "$DriveFilesUrl?fields=id", body.toString().toByteArray(), "application/json").getString("id") == id) }
                    catch (e: GraphDriveHttpException) { if (e.status != 409) throw e }
                    assertActive()
                }
                override suspend fun createObject(id: String, name: String, parent: String, mimeType: String, source: File) {
                    assertActive(); BackupGraphProtocol.id(id)
                    val metadata = JSONObject().put("id", id).put("name", name).put("parents", JSONArray().put(parent)).put("mimeType", mimeType)
                    if (source.length() > 5L * 1024 * 1024) {
                        uploadGraphResumable(id, metadata, mimeType, source, ::assertActive)
                        assertActive(); return
                    }
                    val boundary = "myvault-graph-${java.util.UUID.randomUUID()}"
                    try {
                        val created = requestJsonStreaming("POST", "$DriveUploadUrl?uploadType=multipart&fields=id", "multipart/related; boundary=$boundary") { output ->
                            output.writeUtf8("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n")
                            output.writeUtf8("--$boundary\r\nContent-Type: $mimeType\r\n\r\n")
                            source.inputStream().buffered().use { it.copyTo(output) }
                            output.writeUtf8("\r\n--$boundary--\r\n")
                        }
                        check(created.getString("id") == id)
                    } catch (e: GraphDriveHttpException) { if (e.status != 409) throw e }
                    assertActive()
                }
            }
            return driveId to api
        }

        private fun uploadGraphResumable(id: String, metadata: JSONObject, mime: String, source: File, assertActive: () -> Unit) {
            var token = accessToken()
            var session: String? = null
            for (attempt in 0..1) {
                assertActive()
                val body = metadata.toString().toByteArray(Charsets.UTF_8)
                val connection = (URL("$DriveUploadUrl?uploadType=resumable&fields=id").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; doOutput = true; instanceFollowRedirects = false
                    connectTimeout = 30_000; readTimeout = 120_000; setFixedLengthStreamingMode(body.size)
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("X-Upload-Content-Type", mime)
                    setRequestProperty("X-Upload-Content-Length", source.length().toString())
                }
                try {
                    connection.outputStream.use { it.write(body) }
                    val status = connection.responseCode
                    if (shouldRefreshDriveToken(status, attempt)) { token = refreshAccessToken(token); continue }
                    if (status == 409) return // Caller performs exact immutable object readback.
                    if (status == HttpUnauthorized) throw DriveAuthenticationException()
                    if (status !in 200..299) throw GraphDriveHttpException(status)
                    session = connection.getHeaderField("Location") ?: error("Drive did not return an upload session.")
                    val url = URL(session)
                    check(url.protocol == "https" && url.host == "www.googleapis.com" && url.port in listOf(-1, 443) &&
                        url.userInfo == null && url.ref == null && url.path == "/upload/drive/v3/files") { "Unexpected upload session origin." }
                    assertActive(); break
                } finally { connection.disconnect() }
            }
            val destination = session ?: throw DriveAuthenticationException()
            for (attempt in 0..1) {
                assertActive()
                val connection = (URL(destination).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PUT"; doOutput = true; instanceFollowRedirects = false
                    connectTimeout = 30_000; readTimeout = 120_000; setFixedLengthStreamingMode(source.length())
                    setRequestProperty("Authorization", "Bearer $token"); setRequestProperty("Content-Type", mime)
                }
                try {
                    source.inputStream().buffered().use { input -> connection.outputStream.use { input.copyTo(it) } }
                    val status = connection.responseCode
                    if (shouldRefreshDriveToken(status, attempt)) { token = refreshAccessToken(token); continue }
                    if (status == HttpUnauthorized) throw DriveAuthenticationException()
                    if (status == 409) return
                    if (status !in 200..299) throw GraphDriveHttpException(status)
                    val created = connection.inputStream.use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
                    check(created.getString("id") == id); assertActive(); return
                } finally { connection.disconnect() }
            }
            throw DriveAuthenticationException()
        }

        private fun openGraphDownload(id: String, assertActive: () -> Unit): java.io.InputStream {
            var token = accessToken()
            for (attempt in 0..1) {
                assertActive()
                val connection = (URL("$DriveFilesUrl/${id.urlPathEncode()}?alt=media").openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"; connectTimeout = 30_000; readTimeout = 120_000
                    setRequestProperty("Authorization", "Bearer $token")
                }
                try {
                    val status = connection.responseCode
                    if (shouldRefreshDriveToken(status, attempt)) { connection.disconnect(); token = refreshAccessToken(token); continue }
                    if (status == HttpUnauthorized) throw DriveAuthenticationException()
                    if (status !in 200..299) throw GraphDriveHttpException(status)
                    assertActive()
                    return object : java.io.FilterInputStream(connection.inputStream) {
                        override fun close() { try { super.close(); assertActive() } finally { connection.disconnect() } }
                    }
                } catch (e: Throwable) { connection.disconnect(); throw e }
            }
            throw DriveAuthenticationException()
        }

        fun inspectGraphReadiness(email: String): ReadinessInventory {
            val user = requestJson("GET", "https://www.googleapis.com/drive/v3/about?fields=user(permissionId,emailAddress)").getJSONObject("user")
            val identity = user.getString("permissionId").also { BackupGraphProtocol.id(it) }
            check(normalizeGoogleDriveAccount(user.getString("emailAddress")) == email)
            val legacyRoots = readinessList("name = '${MyVaultRoot.escapeDriveQuery()}' and mimeType = '$FolderMimeType' and trashed = false")
            check(legacyRoots.size <= 1) { "Ambiguous legacy namespace." }
            val legacyVisible = legacyRoots.singleOrNull()?.let { root ->
                val folders = readinessList("'${root.getString("id").escapeDriveQuery()}' in parents and name = 'manifests' and mimeType = '$FolderMimeType' and trashed = false")
                check(folders.size <= 1)
                folders.singleOrNull()?.let { folder ->
                    val manifests = readinessList("'${folder.getString("id").escapeDriveQuery()}' in parents and name = '$SyncManifestFile' and trashed = false")
                    check(manifests.size <= 1)
                    manifests.singleOrNull()?.let { file ->
                        val value = downloadJsonObject(file.getString("id"))
                        check(value.getInt("schemaVersion") == 1 && value.getString("storage") == "google-drive-api" && value.getJSONArray("entries").length() > 0)
                        true
                    }
                }
            } ?: false
            val roots = readinessList("name = '${BackupGraphNamespace.escapeDriveQuery()}' and mimeType = '$FolderMimeType' and trashed = false")
            if (roots.size != 1) return ReadinessInventory(identity,legacyVisible,roots.size,null,emptyList())
            val directories = readinessList("'${roots.single().getString("id").escapeDriveQuery()}' in parents and name = 'commits' and mimeType = '$FolderMimeType' and trashed = false")
            if (directories.size != 1) return ReadinessInventory(identity,legacyVisible,if (directories.size > 1) 2 else 1,null,emptyList())
            val files = readinessList("'${directories.single().getString("id").escapeDriveQuery()}' in parents and trashed = false")
            val objects = files.map { file ->
                val size = file.getString("size").toLong(); check(size in 1..65536)
                val ref = GraphObjectRef(file.getString("id"),file.getString("sha256Checksum"),size)
                BackupGraphProtocol.reference(ref,65536)
                val bytes = requestBytes("GET", "$DriveFilesUrl/${ref.cloudFileId}?alt=media", null, null)
                BackupGraphProtocol.verify(ref,bytes)
                GraphObject(ref,bytes)
            }
            val lineages = objects.map { JSONObject(BackupGraphProtocol.utf8(it.bytes)).getString("lineageId").also(BackupGraphProtocol::id) }.distinct()
            return ReadinessInventory(identity,legacyVisible,if(lineages.size > 1) 2 else 1,lineages.singleOrNull(),objects)
        }

        private fun readinessList(query: String): List<JSONObject> {
            val result = mutableListOf<JSONObject>(); var page: String? = null
            do {
                val suffix = page?.let { "&pageToken=${it.urlEncode()}" }.orEmpty()
                val response = requestJson("GET", "$DriveFilesUrl?q=${query.urlEncode()}&spaces=drive&pageSize=1000&fields=nextPageToken,files(id,name,mimeType,size,sha256Checksum)$suffix")
                val files = response.getJSONArray("files")
                for (i in 0 until files.length()) result += files.getJSONObject(i)
                page = response.optString("nextPageToken").takeIf { it.isNotBlank() }
            } while (page != null)
            return result
        }

        fun ensureMyVaultLayout(): DriveVaultFolder {
            val root = ensureFolder(parentId = "root", name = MyVaultRoot)
            return DriveVaultFolder(
                root = root,
                metadata = ensureFolder(root.id, "metadata"),
                files = ensureFolder(root.id, "files"),
                manifests = ensureFolder(root.id, "manifests"),
                backups = ensureFolder(root.id, "backups"),
            )
        }

        fun ensureFolder(parentId: String, name: String): DriveFile =
            findChild(parentId, name, FolderMimeType) ?: createFolder(parentId, name)

        fun findChild(parentId: String, name: String, mimeType: String? = null): DriveFile? {
            val query = buildString {
                append("'").append(parentId.escapeDriveQuery()).append("' in parents")
                append(" and name = '").append(name.escapeDriveQuery()).append("'")
                append(" and trashed = false")
                if (mimeType != null) append(" and mimeType = '").append(mimeType.escapeDriveQuery()).append("'")
            }
            val json = requestJson(
                method = "GET",
                url = "$DriveFilesUrl?q=${query.urlEncode()}&spaces=drive&fields=files(id,name,mimeType,size,modifiedTime)",
            )
            val files = json.optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            return files.getJSONObject(0).toDriveFile()
        }

        fun listChildren(parentId: String): List<DriveFile> {
            val query = "'${parentId.escapeDriveQuery()}' in parents and trashed = false"
            val children = mutableListOf<DriveFile>()
            var pageToken: String? = null
            do {
                val tokenParameter = pageToken?.let { "&pageToken=${it.urlEncode()}" }.orEmpty()
                val json = requestJson(
                    method = "GET",
                    url = "$DriveFilesUrl?q=${query.urlEncode()}&spaces=drive&pageSize=1000&fields=nextPageToken,files(id,name,mimeType,size,modifiedTime)$tokenParameter",
                )
                val files = json.optJSONArray("files") ?: JSONArray()
                for (index in 0 until files.length()) {
                    children += files.getJSONObject(index).toDriveFile()
                }
                pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            } while (pageToken != null)
            return children
        }

        fun createFolder(parentId: String, name: String): DriveFile {
            val body = JSONObject()
                .put("name", name)
                .put("mimeType", FolderMimeType)
                .put("parents", JSONArray().put(parentId))
            return requestJson(
                method = "POST",
                url = "$DriveFilesUrl?fields=id,name,mimeType,modifiedTime",
                body = body.toString().toByteArray(Charsets.UTF_8),
                contentType = "application/json; charset=UTF-8",
            ).toDriveFile()
        }

        fun uploadFile(parentId: String, existingFileId: String?, name: String, mimeType: String, source: File): DriveFile =
            uploadMultipart(parentId, existingFileId, name, mimeType) { output ->
                source.inputStream().buffered().use { input ->
                    input.copyTo(output)
                }
            }

        fun uploadTextFile(parentId: String, existingFileId: String?, name: String, text: String, mimeType: String): DriveFile =
            uploadBytes(parentId, existingFileId, name, mimeType, text.toByteArray(Charsets.UTF_8))

        private fun uploadBytes(parentId: String, existingFileId: String?, name: String, mimeType: String, bytes: ByteArray): DriveFile {
            return uploadMultipart(parentId, existingFileId, name, mimeType) { output ->
                output.write(bytes)
            }
        }

        private fun uploadMultipart(
            parentId: String,
            existingFileId: String?,
            name: String,
            mimeType: String,
            writeMedia: (OutputStream) -> Unit,
        ): DriveFile {
            val boundary = "myvault-${System.currentTimeMillis()}"
            val metadata = JSONObject()
                .put("name", name)
                .put("mimeType", mimeType)
                .also { if (existingFileId.isNullOrBlank()) it.put("parents", JSONArray().put(parentId)) }
            val url = if (existingFileId.isNullOrBlank()) {
                "$DriveUploadUrl?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime"
            } else {
                "$DriveUploadUrl/${existingFileId.urlPathEncode()}?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime"
            }
            return requestJsonStreaming(
                method = "POST",
                url = url,
                contentType = "multipart/related; boundary=$boundary",
                methodOverride = if (!existingFileId.isNullOrBlank()) "PATCH" else null,
            ) { output ->
                output.writeUtf8("--$boundary\r\n")
                output.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                output.writeUtf8(metadata.toString())
                output.writeUtf8("\r\n--$boundary\r\n")
                output.writeUtf8("Content-Type: $mimeType\r\n\r\n")
                writeMedia(output)
                output.writeUtf8("\r\n--$boundary--\r\n")
            }.toDriveFile()
        }

        fun downloadJsonObject(fileId: String): JSONObject =
            JSONObject(requestBytes("GET", "$DriveFilesUrl/${fileId.urlPathEncode()}?alt=media", null, null).toString(Charsets.UTF_8))

        fun downloadBytes(fileId: String): ByteArray =
            requestBytes("GET", "$DriveFilesUrl/${fileId.urlPathEncode()}?alt=media", null, null)

        fun readCommittedBackup(manifestsId: String): PublishedDriveBackup? {
            val matches = listChildren(manifestsId).filter { it.name == SyncManifestFile }
            check(matches.size <= 1) { "Multiple committed manifests exist. Backup stopped; no files were changed." }
            val file = matches.singleOrNull() ?: return null
            val text = requestBytes("GET", "$DriveFilesUrl/${file.id.urlPathEncode()}?alt=media", null, null).toString(Charsets.UTF_8)
            return PublishedDriveBackup(file.id, text)
        }

        fun existingFileMatches(fileId: String, parentId: String, size: Long, sha256: String): Boolean {
            val metadata = requestJson("GET", "$DriveFilesUrl/${fileId.urlPathEncode()}?fields=size,sha256Checksum,parents")
            val parents = metadata.optJSONArray("parents") ?: return false
            if ((0 until parents.length()).none { parents.optString(it) == parentId }) return false
            if (metadata.optLong("size", -1L) != size) return false
            val checksum = metadata.optString("sha256Checksum")
            return if (checksum.isNotBlank()) checksum.equals(sha256, ignoreCase = true)
            else uploadedFileMatches(fileId, size, sha256)
        }

        fun uploadedFileMatches(fileId: String, expectedSize: Long, expectedSha256: String): Boolean {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            val output = object : OutputStream() {
                override fun write(value: Int) { digest.update(value.toByte()); count += 1 }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    digest.update(bytes, offset, length)
                    count += length
                }
            }
            requestToOutput("GET", "$DriveFilesUrl/${fileId.urlPathEncode()}?alt=media", output)
            return count == expectedSize && digest.digest().joinToString("") { "%02x".format(it) }.equals(expectedSha256, ignoreCase = true)
        }

        suspend fun copyFileToWithRetry(fileId: String, output: OutputStream) {
            val temp = File.createTempFile("myvault-drive-download-", ".tmp", context.cacheDir)
            try {
                retryDriveRequest {
                    temp.outputStream().buffered().use { tempOutput ->
                        requestToOutput("GET", "$DriveFilesUrl/${fileId.urlPathEncode()}?alt=media", tempOutput)
                    }
                }
                temp.inputStream().buffered().use { it.copyTo(output) }
            } finally {
                temp.delete()
            }
        }

        fun deleteFile(fileId: String) {
            requestBytes("DELETE", "$DriveFilesUrl/${fileId.urlPathEncode()}", null, null)
        }

        private fun requestJson(method: String, url: String, body: ByteArray? = null, contentType: String? = null): JSONObject =
            JSONObject(requestBytes(method, url, body, contentType).toString(Charsets.UTF_8))

        private fun requestJsonStreaming(
            method: String,
            url: String,
            contentType: String,
            methodOverride: String? = null,
            writeBody: (OutputStream) -> Unit,
        ): JSONObject =
            JSONObject(requestBytesStreaming(method, url, contentType, methodOverride, writeBody).toString(Charsets.UTF_8))

        private fun requestBytes(method: String, url: String, body: ByteArray?, contentType: String?): ByteArray {
            var token = accessToken()
            for (attempt in 0..1) {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    val actualMethod = if (method == "PATCH") "POST" else method
                    requestMethod = actualMethod
                    if (method == "PATCH") {
                        setRequestProperty("X-HTTP-Method-Override", "PATCH")
                    }
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    setRequestProperty("Authorization", "Bearer $token")
                    if (contentType != null) setRequestProperty("Content-Type", contentType)
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Length", body.size.toString())
                    }
                }
                try {
                    if (body != null) connection.outputStream.use { it.write(body) }
                    val responseCode = connection.responseCode
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                    if (shouldRefreshDriveToken(responseCode, attempt)) {
                        token = refreshAccessToken(token)
                        continue
                    }
                    if (responseCode == HttpUnauthorized) throw DriveAuthenticationException()
                    if (responseCode !in 200..299) {
                        throw GraphDriveHttpException(responseCode, bytes.toString(Charsets.UTF_8).take(300))
                    }
                    return bytes
                } finally {
                    connection.disconnect()
                }
            }
            throw DriveAuthenticationException()
        }

        private fun requestBytesStreaming(
            method: String,
            url: String,
            contentType: String,
            methodOverride: String?,
            writeBody: (OutputStream) -> Unit,
        ): ByteArray {
            var token = accessToken()
            for (attempt in 0..1) {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    val actualMethod = if (method == "PATCH") "POST" else method
                    requestMethod = actualMethod
                    if (method == "PATCH" || methodOverride == "PATCH") {
                        setRequestProperty("X-HTTP-Method-Override", "PATCH")
                    }
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    doOutput = true
                    setChunkedStreamingMode(DEFAULT_BUFFER_SIZE)
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("Content-Type", contentType)
                }
                try {
                    connection.outputStream.use(writeBody)
                    val responseCode = connection.responseCode
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                    if (shouldRefreshDriveToken(responseCode, attempt)) {
                        token = refreshAccessToken(token)
                        continue
                    }
                    if (responseCode == HttpUnauthorized) throw DriveAuthenticationException()
                    if (responseCode !in 200..299) {
                        throw GraphDriveHttpException(responseCode, bytes.toString(Charsets.UTF_8).take(300))
                    }
                    return bytes
                } finally {
                    connection.disconnect()
                }
            }
            throw DriveAuthenticationException()
        }

        private fun requestToOutput(method: String, url: String, output: OutputStream) {
            var token = accessToken()
            for (attempt in 0..1) {
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = method
                    connectTimeout = 30_000
                    readTimeout = 120_000
                    setRequestProperty("Authorization", "Bearer $token")
                }
                try {
                    val responseCode = connection.responseCode
                    val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
                    if (shouldRefreshDriveToken(responseCode, attempt)) {
                        stream?.close()
                        token = refreshAccessToken(token)
                        continue
                    }
                    if (responseCode == HttpUnauthorized) throw DriveAuthenticationException()
                    if (responseCode !in 200..299) {
                        val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                        error("Google Drive returned HTTP $responseCode: ${bytes.toString(Charsets.UTF_8).take(300)}")
                    }
                    stream?.use { it.copyTo(output) }
                    return
                } finally {
                    connection.disconnect()
                }
            }
            throw DriveAuthenticationException()
        }

        private suspend fun retryDriveRequest(block: () -> Unit) {
            var lastError: Throwable? = null
            repeat(DriveDownloadRetryCount) { attempt ->
                try {
                    block()
                    return
                } catch (error: Throwable) {
                    lastError = error
                    if (!error.isInterruptedDriveConnection() || attempt == DriveDownloadRetryCount - 1) throw error
                    delay(DriveDownloadRetryDelayMs * (attempt + 1))
                }
            }
            throw lastError ?: IllegalStateException("Google Drive download failed.")
        }

        private fun accessToken(): String {
            val androidAccount = account.account ?: error("Google account is unavailable. Connect Drive again.")
            return GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DriveScopeUrl")
        }

        private fun refreshAccessToken(staleToken: String): String {
            GoogleAuthUtil.clearToken(context, staleToken)
            return accessToken()
        }

        private fun JSONObject.toDriveFile(): DriveFile =
            DriveFile(
                id = getString("id"),
                name = optString("name"),
                mimeType = optString("mimeType"),
            )
    }

    private data class DriveVaultFolder(
        val root: DriveFile,
        val metadata: DriveFile,
        val files: DriveFile,
        val manifests: DriveFile,
        val backups: DriveFile,
    )

    private data class ReadinessInventory(val accountId: String, val legacyVisible: Boolean, val namespaceCount: Int, val lineage: String?, val objects: List<GraphObject>)

    private data class DriveFile(
        val id: String,
        val name: String,
        val mimeType: String,
    )

    private data class DriveEntry(
        val path: String,
        val fileName: String,
        val backupEntry: String,
        val kind: String,
        val mimeType: String,
        val size: Long,
        var sha256: String,
        val file: File,
        var cloudFileId: String = "",
    )

    private data class RemoteEntry(
        val path: String,
        val fileName: String,
        val backupEntry: String,
        val kind: String,
        val sha256: String,
        val size: Long,
        val cloudFileId: String,
    )

    private companion object {
        const val MyVaultRoot = "MyVault"
        const val SyncManifestFile = "sync_manifest.json"
        const val DriveDownloadRetryCount = 3
        const val DriveDownloadRetryDelayMs = 900L
        const val EntryKindMetadata = "metadata"
        const val EntryKindFile = "file"
        const val DriveScopeUrl = "https://www.googleapis.com/auth/drive.file"
        val DriveScope = Scope(DriveScopeUrl)
        const val FolderMimeType = "application/vnd.google-apps.folder"
        const val DriveFilesUrl = "https://www.googleapis.com/drive/v3/files"
        const val DriveUploadUrl = "https://www.googleapis.com/upload/drive/v3/files"
    }
}

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8")

private fun String.urlPathEncode(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")

private fun String.escapeDriveQuery(): String = replace("\\", "\\\\").replace("'", "\\'")

private fun OutputStream.writeUtf8(value: String) {
    write(value.toByteArray(Charsets.UTF_8))
}

internal fun reusableDriveEntryId(
    isAttachmentFile: Boolean,
    contentMatches: Boolean,
    currentFileIdByName: String?,
): String? {
    if (!isAttachmentFile || !contentMatches) return null
    return currentFileIdByName?.takeIf { it.isNotBlank() }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }

sealed interface DriveSyncResult {
    data class Success(val message: String) : DriveSyncResult
    data class Conflict(val message: String) : DriveSyncResult
    data class Skipped(val message: String) : DriveSyncResult
    data class Failure(val message: String) : DriveSyncResult
}

sealed interface DriveAuthorizationResult {
    data class Ready(val message: String) : DriveAuthorizationResult
    data class ConsentRequired(val intent: Intent, val message: String) : DriveAuthorizationResult
    data class Failure(val message: String) : DriveAuthorizationResult
}

internal fun String?.isRemoteConsentMessage(): Boolean =
    this?.contains("NeedRemoteConsent", ignoreCase = true) == true

enum class DriveRestoreStage(val label: String) {
    Idle("Idle"),
    Preparing("Preparing"),
    Uploading("Uploading"),
    Downloading("Downloading"),
    Verifying("Verifying"),
    RestoringDatabase("Restoring database"),
    RestoringFiles("Restoring files"),
    Finalising("Finalising"),
    Complete("Complete"),
    Failed("Failed"),
}

data class DriveRestoreProgress(
    val stage: DriveRestoreStage = DriveRestoreStage.Idle,
    val message: String = "",
    val current: Int = 0,
    val total: Int = 0,
    val detail: String? = null,
) {
    val percent: Int?
        get() = total.takeIf { it > 0 }?.let { ((current.toFloat() / it.toFloat()) * 100f).toInt().coerceIn(0, 100) }
}

enum class DriveSyncOperation {
    None,
    Backup,
    Restore,
}

data class DriveRestoreState(
    val active: Boolean = false,
    val progress: DriveRestoreProgress = DriveRestoreProgress(),
    val message: String? = null,
    val operation: DriveSyncOperation = DriveSyncOperation.None,
    val completedAt: Long = 0L,
) {
    val isFinished: Boolean
        get() = progress.stage == DriveRestoreStage.Complete || progress.stage == DriveRestoreStage.Failed
}
