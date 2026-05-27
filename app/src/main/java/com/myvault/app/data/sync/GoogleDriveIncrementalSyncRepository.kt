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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleDriveIncrementalSyncRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val attachmentDao: AttachmentDao,
    private val preferences: VaultPreferences,
) {
    fun signInIntent(): Intent =
        GoogleSignIn.getClient(context, signInOptions()).signInIntent

    suspend fun handleSignInResult(data: Intent?): DriveSyncResult = withContext(Dispatchers.IO) {
        val account = runCatching {
            GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException::class.java)
        }.getOrElse { error ->
            return@withContext DriveSyncResult.Failure(error.googleSignInMessage())
        }
        if (!GoogleSignIn.hasPermissions(account, DriveScope)) {
            return@withContext DriveSyncResult.Failure("Google Drive permission was not granted. Please connect Drive again.")
        }
        preferences.setGoogleDriveAccountEmail(account.email.orEmpty())
        DriveSyncResult.Success("Google Drive connected${account.email?.let { " as $it" }.orEmpty()}.")
    }

    suspend fun pushToDrive(
        onProgress: suspend (DriveRestoreProgress) -> Unit = {},
    ): DriveSyncResult = withContext(Dispatchers.IO) {
        onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = "Preparing Google Drive backup"))
        val drive = driveOrFailure() ?: return@withContext DriveSyncResult.Failure("Connect Google Drive first.")
        val vault = drive.ensureMyVaultLayout()
        val remoteManifestFile = drive.findChild(vault.manifests.id, SyncManifestFile)
        val remoteManifest = remoteManifestFile?.let { drive.downloadJsonObject(it.id) }
        val remoteVersion = remoteManifest?.optLong("cloudVersion", 0L) ?: 0L
        val lastSyncedVersion = preferences.userPreferences.first().lastGoogleDriveManifestAt
        if (remoteVersion > 0L && remoteVersion > lastSyncedVersion) {
            return@withContext DriveSyncResult.Conflict(
                "Cloud contains newer MyVault changes. Pull latest first, then push again.",
            )
        }

        val metadataDir = File(context.cacheDir, "drive-api-sync-metadata-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = "Exporting changed metadata"))
            backupRepository.exportMetadataForDriveSync(metadataDir)
            val remoteEntries = remoteManifest.toRemoteEntryMap()
            val entries = metadataDir.toMetadataDriveEntries() + localFileDriveEntries(remoteEntries)
            var uploadedMetadata = 0
            var uploadedFiles = 0
            var skippedFiles = 0

            onProgress(
                DriveRestoreProgress(
                    stage = DriveRestoreStage.Uploading,
                    message = "Uploading changed vault files",
                    current = 0,
                    total = entries.size,
                ),
            )
            entries.forEachIndexed { index, entry ->
                onProgress(
                    DriveRestoreProgress(
                        stage = DriveRestoreStage.Uploading,
                        message = if (entry.kind == EntryKindFile) "Uploading file ${entry.fileName}" else "Uploading metadata ${entry.fileName}",
                        current = index + 1,
                        total = entries.size,
                    ),
                )
                val remote = remoteEntries[entry.path]
                if (entry.kind == EntryKindFile && remote != null && remote.size == entry.size && remote.sha256.isNotBlank()) {
                    entry.sha256 = remote.sha256
                    entry.cloudFileId = remote.cloudFileId.ifBlank {
                        drive.findChild(vault.files.id, entry.fileName)?.id.orEmpty()
                    }
                    skippedFiles += 1
                    return@forEachIndexed
                }
                entry.ensureSha256()
                if (remote?.sha256 == entry.sha256 && remote.size == entry.size) {
                    entry.cloudFileId = remote.cloudFileId.ifBlank {
                        val parentId = if (entry.kind == EntryKindFile) vault.files.id else vault.metadata.id
                        drive.findChild(parentId, entry.fileName)?.id.orEmpty()
                    }
                    if (entry.kind == EntryKindFile) skippedFiles += 1
                    return@forEachIndexed
                }
                val parentId = if (entry.kind == EntryKindFile) vault.files.id else vault.metadata.id
                val existingId = remote?.cloudFileId ?: drive.findChild(parentId, entry.fileName)?.id
                val uploaded = drive.uploadFile(
                    parentId = parentId,
                    existingFileId = existingId,
                    name = entry.fileName,
                    mimeType = entry.mimeType,
                    source = entry.file,
                )
                entry.cloudFileId = uploaded.id
                if (entry.kind == EntryKindFile) uploadedFiles += 1 else uploadedMetadata += 1
            }

            onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Finalising, message = "Finalising Drive backup"))
            val cloudVersion = System.currentTimeMillis()
            val manifest = entries.toManifest(cloudVersion)
            val existingManifestId = remoteManifestFile?.id ?: drive.findChild(vault.manifests.id, SyncManifestFile)?.id
            drive.uploadTextFile(
                parentId = vault.manifests.id,
                existingFileId = existingManifestId,
                name = SyncManifestFile,
                text = manifest.toString(2),
                mimeType = "application/json",
            )

            val localPaths = entries.map { it.path }.toSet()
            remoteEntries.values
                .filter { it.path !in localPaths && it.cloudFileId.isNotBlank() }
                .forEach { stale ->
                    runCatching { drive.deleteFile(stale.cloudFileId) }
                }

            preferences.markGoogleDriveSync(cloudVersion)

            DriveSyncResult.Success(
                "Drive push complete: $uploadedMetadata metadata file(s), $uploadedFiles file(s) uploaded, $skippedFiles unchanged file(s) skipped.",
            )
        } catch (error: Throwable) {
            DriveSyncResult.Failure(error.driveMessage("Drive push failed"))
        } finally {
            metadataDir.deleteRecursively()
        }
    }

    suspend fun pullLatestFromDrive(
        onProgress: suspend (DriveRestoreProgress) -> Unit = {},
    ): DriveSyncResult = withContext(Dispatchers.IO) {
        onProgress(DriveRestoreProgress(stage = DriveRestoreStage.Preparing, message = "Preparing Google Drive restore"))
        val drive = driveOrFailure() ?: return@withContext DriveSyncResult.Failure("Connect Google Drive first.")
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
                            drive.copyFileTo(fileId, zip)
                            downloadedFiles += 1
                        }
                    } else {
                        val fileId = entry.cloudFileId.ifBlank { drive.findChild(vault.metadata.id, entry.fileName)?.id.orEmpty() }
                        if (fileId.isBlank()) error("Missing Drive sync metadata: ${entry.fileName}")
                        drive.copyFileTo(fileId, zip)
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
            preferences.markGoogleDriveSync(cloudVersion)
            DriveSyncResult.Success(
                "Drive pull complete: ${restored.noteCount} notes, ${restored.attachmentCount} attachments. $downloadedFiles file(s) downloaded, $reusedLocalFiles reused locally.",
            )
        } catch (error: Throwable) {
            DriveSyncResult.Failure(error.driveMessage("Drive pull failed"))
        } finally {
            zipFile.delete()
        }
    }

    suspend fun checkForRemoteUpdates(): DriveSyncResult = withContext(Dispatchers.IO) {
        val drive = driveOrFailure() ?: return@withContext DriveSyncResult.Skipped("Connect Google Drive first.")
        val vault = drive.ensureMyVaultLayout()
        val remoteVersion = drive.findChild(vault.manifests.id, SyncManifestFile)
            ?.let { drive.downloadJsonObject(it.id).optLong("cloudVersion", 0L) }
            ?: 0L
        val localVersion = preferences.userPreferences.first().lastGoogleDriveManifestAt
        when {
            remoteVersion <= 0L -> DriveSyncResult.Skipped("No Drive sync has been pushed yet.")
            remoteVersion > localVersion -> DriveSyncResult.Conflict("New MyVault updates are available from Drive.")
            else -> DriveSyncResult.Success("Drive sync is up to date.")
        }
    }

    private fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(DriveScope)
            .build()

    private fun driveOrFailure(): DriveApiClient? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        if (!GoogleSignIn.hasPermissions(account, DriveScope)) return null
        return DriveApiClient(context, account)
    }

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

    private suspend fun localFileDriveEntries(remoteEntries: Map<String, RemoteEntry>): List<DriveEntry> {
        val attachments = attachmentDao.getAllIncludingDeleted()
        return attachments
            .filter { it.deletedAt == null }
            .mapNotNull { attachment ->
                val file = File(attachment.localPath)
                if (!file.exists() || !file.isFile) return@mapNotNull null
                val fileName = "${attachment.id}.${attachment.fileName.safeExtension()}"
                val path = "files/$fileName"
                val remote = remoteEntries[path]
                DriveEntry(
                    path = path,
                    fileName = fileName,
                    backupEntry = "files/${attachment.id}",
                    kind = EntryKindFile,
                    mimeType = "application/octet-stream",
                    size = file.length(),
                    sha256 = if (remote?.size == file.length()) remote.sha256 else "",
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
        when (this) {
            is UserRecoverableAuthException -> "$prefix: Google Drive needs permission again. Connect Drive, then retry."
            else -> message?.let { "$prefix: $it" } ?: prefix
        }

    private fun Throwable.googleSignInMessage(): String =
        if (this is ApiException && statusCode == GoogleSignInStatusCodes.DEVELOPER_ERROR) {
            "Google Drive sign in is not configured for this signed APK yet. In Google Cloud or Firebase, add an Android OAuth client in project myvault-fbfd1 with package com.myvault.app and release SHA-1 77:D0:EE:6A:B8:DF:03:59:6D:50:B7:13:68:58:03:D7:76:F9:18:16, then retry."
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
                method = if (existingFileId.isNullOrBlank()) "POST" else "PATCH",
                url = url,
                contentType = "multipart/related; boundary=$boundary",
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

        fun copyFileTo(fileId: String, output: OutputStream) {
            requestToOutput("GET", "$DriveFilesUrl/${fileId.urlPathEncode()}?alt=media", output)
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
            writeBody: (OutputStream) -> Unit,
        ): JSONObject =
            JSONObject(requestBytesStreaming(method, url, contentType, writeBody).toString(Charsets.UTF_8))

        private fun requestBytes(method: String, url: String, body: ByteArray?, contentType: String?): ByteArray {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("Authorization", "Bearer ${accessToken()}")
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Length", body.size.toString())
                }
            }
            if (body != null) connection.outputStream.use { it.write(body) }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (responseCode !in 200..299) {
                error("Google Drive returned HTTP $responseCode: ${bytes.toString(Charsets.UTF_8).take(300)}")
            }
            return bytes
        }

        private fun requestBytesStreaming(
            method: String,
            url: String,
            contentType: String,
            writeBody: (OutputStream) -> Unit,
        ): ByteArray {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30_000
                readTimeout = 120_000
                doOutput = true
                setChunkedStreamingMode(DEFAULT_BUFFER_SIZE)
                setRequestProperty("Authorization", "Bearer ${accessToken()}")
                setRequestProperty("Content-Type", contentType)
            }
            connection.outputStream.use(writeBody)
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (responseCode !in 200..299) {
                error("Google Drive returned HTTP $responseCode: ${bytes.toString(Charsets.UTF_8).take(300)}")
            }
            return bytes
        }

        private fun requestToOutput(method: String, url: String, output: OutputStream) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("Authorization", "Bearer ${accessToken()}")
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            if (responseCode !in 200..299) {
                val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
                error("Google Drive returned HTTP $responseCode: ${bytes.toString(Charsets.UTF_8).take(300)}")
            }
            stream?.use { it.copyTo(output) }
        }

        private fun accessToken(): String {
            val androidAccount = account.account ?: error("Google account is unavailable. Connect Drive again.")
            return GoogleAuthUtil.getToken(context, androidAccount, "oauth2:$DriveScopeUrl")
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

sealed interface DriveSyncResult {
    data class Success(val message: String) : DriveSyncResult
    data class Conflict(val message: String) : DriveSyncResult
    data class Skipped(val message: String) : DriveSyncResult
    data class Failure(val message: String) : DriveSyncResult
}

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

data class DriveRestoreState(
    val active: Boolean = false,
    val progress: DriveRestoreProgress = DriveRestoreProgress(),
    val message: String? = null,
) {
    val isFinished: Boolean
        get() = progress.stage == DriveRestoreStage.Complete || progress.stage == DriveRestoreStage.Failed
}
