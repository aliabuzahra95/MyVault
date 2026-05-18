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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
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

    suspend fun pushToDrive(): DriveSyncResult = withContext(Dispatchers.IO) {
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

        val backupFile = File(context.cacheDir, "drive-api-sync-export-${System.currentTimeMillis()}.vaultbackup")
        val unzipDir = File(context.cacheDir, "drive-api-sync-export-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            backupRepository.exportBackupToFile(backupFile)
            val entries = backupFile.toDriveEntries(unzipDir)
            val remoteEntries = remoteManifest.toRemoteEntryMap()
            var uploadedMetadata = 0
            var uploadedFiles = 0
            var skippedFiles = 0

            entries.forEach { entry ->
                val remote = remoteEntries[entry.path]
                if (remote?.sha256 == entry.sha256 && remote.size == entry.size) {
                    if (entry.kind == EntryKindFile) skippedFiles += 1
                    return@forEach
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

            val localPaths = entries.map { it.path }.toSet()
            remoteEntries.values
                .filter { it.path !in localPaths && it.cloudFileId.isNotBlank() }
                .forEach { stale -> drive.deleteFile(stale.cloudFileId) }

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
            preferences.markGoogleDriveSync(cloudVersion)

            DriveSyncResult.Success(
                "Drive push complete: $uploadedMetadata metadata file(s), $uploadedFiles file(s) uploaded, $skippedFiles unchanged file(s) skipped.",
            )
        } catch (error: Throwable) {
            DriveSyncResult.Failure(error.driveMessage("Drive push failed"))
        } finally {
            backupFile.delete()
            unzipDir.deleteRecursively()
        }
    }

    suspend fun pullLatestFromDrive(): DriveSyncResult = withContext(Dispatchers.IO) {
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
            ZipOutputStream(zipFile.outputStream().buffered()).use { zip ->
                entries.forEach { entry ->
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
                            drive.downloadFile(fileId).use { it.copyTo(zip) }
                            downloadedFiles += 1
                        }
                    } else {
                        val fileId = entry.cloudFileId.ifBlank { drive.findChild(vault.metadata.id, entry.fileName)?.id.orEmpty() }
                        if (fileId.isBlank()) error("Missing Drive sync metadata: ${entry.fileName}")
                        drive.downloadFile(fileId).use { it.copyTo(zip) }
                    }
                    zip.closeEntry()
                }
            }
            val restored = backupRepository.restoreBackupFromFile(zipFile)
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

    private fun File.toDriveEntries(unzipDir: File): List<DriveEntry> {
        val rawEntries = mutableListOf<Pair<String, File>>()
        ZipInputStream(inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val safeName = entry.name.replace('/', '_')
                    val out = File(unzipDir, safeName)
                    out.outputStream().use { zip.copyTo(it) }
                    rawEntries += entry.name to out
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val attachmentNames = rawEntries.firstOrNull { it.first == "attachments.json" }
            ?.second
            ?.readText()
            ?.let(::attachmentFileNamesById)
            .orEmpty()

        return rawEntries.map { (backupEntry, file) ->
            val isFile = backupEntry.startsWith("files/")
            val attachmentId = backupEntry.removePrefix("files/")
            val fileName = if (isFile) {
                attachmentNames[attachmentId]?.let { "$attachmentId.${it.safeExtension()}" } ?: attachmentId
            } else {
                backupEntry.substringAfterLast('/')
            }
            DriveEntry(
                path = if (isFile) "files/$fileName" else "metadata/$fileName",
                fileName = fileName,
                backupEntry = backupEntry,
                kind = if (isFile) EntryKindFile else EntryKindMetadata,
                mimeType = if (isFile) "application/octet-stream" else "application/json",
                size = file.length(),
                sha256 = file.sha256(),
                file = file,
            )
        }
    }

    private fun attachmentFileNamesById(json: String): Map<String, String> {
        val array = JSONArray(json)
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                put(item.getString("id"), item.optString("fileName", item.getString("id")))
            }
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
            source.inputStream().use { input ->
                uploadBytes(parentId, existingFileId, name, mimeType, input.readBytes())
            }

        fun uploadTextFile(parentId: String, existingFileId: String?, name: String, text: String, mimeType: String): DriveFile =
            uploadBytes(parentId, existingFileId, name, mimeType, text.toByteArray(Charsets.UTF_8))

        private fun uploadBytes(parentId: String, existingFileId: String?, name: String, mimeType: String, bytes: ByteArray): DriveFile {
            val boundary = "myvault-${System.currentTimeMillis()}"
            val metadata = JSONObject()
                .put("name", name)
                .put("mimeType", mimeType)
                .also { if (existingFileId.isNullOrBlank()) it.put("parents", JSONArray().put(parentId)) }
            val body = ByteArrayOutputStream().use { output ->
                output.writeUtf8("--$boundary\r\n")
                output.writeUtf8("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                output.writeUtf8(metadata.toString())
                output.writeUtf8("\r\n--$boundary\r\n")
                output.writeUtf8("Content-Type: $mimeType\r\n\r\n")
                output.write(bytes)
                output.writeUtf8("\r\n--$boundary--\r\n")
                output.toByteArray()
            }
            val url = if (existingFileId.isNullOrBlank()) {
                "$DriveUploadUrl?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime"
            } else {
                "$DriveUploadUrl/${existingFileId.urlPathEncode()}?uploadType=multipart&fields=id,name,mimeType,size,modifiedTime"
            }
            return requestJson(
                method = if (existingFileId.isNullOrBlank()) "POST" else "PATCH",
                url = url,
                body = body,
                contentType = "multipart/related; boundary=$boundary",
            ).toDriveFile()
        }

        fun downloadJsonObject(fileId: String): JSONObject =
            JSONObject(downloadFile(fileId).bufferedReader().use { it.readText() })

        fun downloadFile(fileId: String): InputStream =
            requestStream("GET", "$DriveFilesUrl/${fileId.urlPathEncode()}?alt=media")

        fun deleteFile(fileId: String) {
            requestBytes("DELETE", "$DriveFilesUrl/${fileId.urlPathEncode()}", null, null)
        }

        private fun requestJson(method: String, url: String, body: ByteArray? = null, contentType: String? = null): JSONObject =
            JSONObject(requestBytes(method, url, body, contentType).toString(Charsets.UTF_8))

        private fun requestStream(method: String, url: String): InputStream =
            requestBytes(method, url, null, null).inputStream()

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
        val sha256: String,
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
