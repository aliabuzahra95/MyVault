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
) {
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
        val vault = drive.ensureMyVaultLayout()
        val previous = drive.readCommittedBackup(vault.manifests.id)
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
                uploads = entries.map { DriveBackupUpload(it.path, it.fileName, it.kind, it.file, it.size, it.sha256) },
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
            if (error.requiresDriveReconnect()) preferences.setGoogleDriveAccountEmail("")
            DriveSyncResult.Failure(error.driveMessage("Drive pull failed"))
        } finally {
            zipFile.delete()
        }
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
                        error("Google Drive returned HTTP $responseCode: ${bytes.toString(Charsets.UTF_8).take(300)}")
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
                        error("Google Drive returned HTTP $responseCode: ${bytes.toString(Charsets.UTF_8).take(300)}")
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
