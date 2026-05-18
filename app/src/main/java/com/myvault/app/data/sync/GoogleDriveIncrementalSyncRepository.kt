package com.myvault.app.data.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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
import java.io.InputStream
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
    suspend fun setSyncFolder(uri: Uri): DriveSyncResult = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        val root = uri.openDocumentTreeRoot()
            ?: return@withContext DriveSyncResult.Failure("Unable to open the selected Drive folder.")
        root.ensureMyVaultLayout()
        preferences.setGoogleDriveSyncFolderUri(uri.toString())
        DriveSyncResult.Success("Google Drive sync folder is ready.")
    }

    suspend fun pushToDrive(): DriveSyncResult = withContext(Dispatchers.IO) {
        val root = selectedRootOrFailure() ?: return@withContext DriveSyncResult.Failure("Choose a Google Drive sync folder first.")
        val vault = root.ensureMyVaultLayout()
        val remoteManifest = vault.manifests.findFile(SyncManifestFile)?.readJsonObjectOrNull()
        val remoteVersion = remoteManifest?.optLong("cloudVersion", 0L) ?: 0L
        val lastSyncedVersion = preferences.userPreferences.first().lastGoogleDriveManifestAt
        if (remoteVersion > 0L && remoteVersion > lastSyncedVersion) {
            return@withContext DriveSyncResult.Conflict(
                "Cloud contains newer MyVault changes. Pull latest first, then push again.",
            )
        }

        val backupFile = File(context.cacheDir, "drive-sync-export-${System.currentTimeMillis()}.vaultbackup")
        val unzipDir = File(context.cacheDir, "drive-sync-export-${System.currentTimeMillis()}").apply { mkdirs() }
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
                val targetFolder = if (entry.kind == EntryKindFile) vault.files else vault.metadata
                targetFolder.writeChildFile(entry.fileName, entry.mimeType, entry.file)
                if (entry.kind == EntryKindFile) uploadedFiles += 1 else uploadedMetadata += 1
            }

            val localPaths = entries.map { it.path }.toSet()
            remoteEntries.values
                .filter { it.path !in localPaths }
                .forEach { stale ->
                    val folder = if (stale.kind == EntryKindFile) vault.files else vault.metadata
                    folder.findFile(stale.fileName)?.delete()
                }

            val cloudVersion = System.currentTimeMillis()
            val manifest = entries.toManifest(cloudVersion)
            vault.manifests.writeTextFile(SyncManifestFile, manifest.toString(2), "application/json")
            preferences.markGoogleDriveSync(cloudVersion)

            DriveSyncResult.Success(
                "Drive push complete: $uploadedMetadata metadata file(s), $uploadedFiles file(s) uploaded, $skippedFiles unchanged file(s) skipped.",
            )
        } catch (error: Throwable) {
            DriveSyncResult.Failure(error.message ?: "Drive push failed.")
        } finally {
            backupFile.delete()
            unzipDir.deleteRecursively()
        }
    }

    suspend fun pullLatestFromDrive(): DriveSyncResult = withContext(Dispatchers.IO) {
        val root = selectedRootOrFailure() ?: return@withContext DriveSyncResult.Failure("Choose a Google Drive sync folder first.")
        val vault = root.ensureMyVaultLayout()
        val manifest = vault.manifests.findFile(SyncManifestFile)?.readJsonObjectOrNull()
            ?: return@withContext DriveSyncResult.Failure("No MyVault Drive sync manifest found yet. Push from your latest device first.")
        val entries = manifest.toRemoteEntryMap().values.sortedWith(compareBy<RemoteEntry> { it.kind }.thenBy { it.path })
        if (entries.isEmpty()) return@withContext DriveSyncResult.Failure("Drive sync manifest is empty.")

        val zipFile = File(context.cacheDir, "drive-sync-pull-${System.currentTimeMillis()}.vaultbackup")
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
                            vault.files.requireFile(entry.fileName).openInputStream().use { it.copyTo(zip) }
                            downloadedFiles += 1
                        }
                    } else {
                        vault.metadata.requireFile(entry.fileName).openInputStream().use { it.copyTo(zip) }
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
            DriveSyncResult.Failure(error.message ?: "Drive pull failed.")
        } finally {
            zipFile.delete()
        }
    }

    suspend fun checkForRemoteUpdates(): DriveSyncResult = withContext(Dispatchers.IO) {
        val root = selectedRootOrFailure() ?: return@withContext DriveSyncResult.Skipped("Choose a Google Drive sync folder first.")
        val vault = root.ensureMyVaultLayout()
        val remoteVersion = vault.manifests.findFile(SyncManifestFile)?.readJsonObjectOrNull()?.optLong("cloudVersion", 0L) ?: 0L
        val localVersion = preferences.userPreferences.first().lastGoogleDriveManifestAt
        when {
            remoteVersion <= 0L -> DriveSyncResult.Skipped("No Drive sync has been pushed yet.")
            remoteVersion > localVersion -> DriveSyncResult.Conflict("New MyVault updates are available from Drive.")
            else -> DriveSyncResult.Success("Drive sync is up to date.")
        }
    }

    private suspend fun selectedRootOrFailure(): DocumentFile? {
        val uri = preferences.userPreferences.first().googleDriveSyncFolderUri.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: return null
        return uri.openDocumentTreeRoot()
    }

    private fun Uri.openDocumentTreeRoot(): DocumentFile? =
        DocumentFile.fromTreeUri(context, this)?.takeIf { it.exists() && it.isDirectory }

    private fun DocumentFile.ensureMyVaultLayout(): DriveVaultFolder {
        val myVault = ensureDirectory(MyVaultRoot)
        return DriveVaultFolder(
            root = myVault,
            metadata = myVault.ensureDirectory("metadata"),
            files = myVault.ensureDirectory("files"),
            manifests = myVault.ensureDirectory("manifests"),
            backups = myVault.ensureDirectory("backups"),
        )
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
            .put("storage", "google-drive-saf")
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
                    ),
                )
            }
        }
    }

    private fun DocumentFile.ensureDirectory(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isDirectory } ?: createDirectory(name) ?: error("Unable to create Drive folder: $name")

    private fun DocumentFile.requireFile(name: String): DocumentFile =
        findFile(name)?.takeIf { it.isFile } ?: error("Missing Drive sync file: $name")

    private fun DocumentFile.writeTextFile(name: String, text: String, mimeType: String) {
        val file = findFile(name)?.takeIf { it.isFile } ?: createFile(mimeType, name) ?: error("Unable to create Drive file: $name")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: error("Unable to write Drive file: $name")
    }

    private fun DocumentFile.writeChildFile(name: String, mimeType: String, source: File) {
        val file = findFile(name)?.takeIf { it.isFile } ?: createFile(mimeType, name) ?: error("Unable to create Drive file: $name")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
            source.inputStream().use { it.copyTo(output) }
        } ?: error("Unable to write Drive file: $name")
    }

    private fun DocumentFile.openInputStream(): InputStream =
        context.contentResolver.openInputStream(uri) ?: error("Unable to read Drive file: ${name.orEmpty()}")

    private fun DocumentFile.readJsonObjectOrNull(): JSONObject? =
        runCatching { JSONObject(openInputStream().bufferedReader().use { it.readText() }) }.getOrNull()

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

    private data class DriveVaultFolder(
        val root: DocumentFile,
        val metadata: DocumentFile,
        val files: DocumentFile,
        val manifests: DocumentFile,
        val backups: DocumentFile,
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
    )

    private data class RemoteEntry(
        val path: String,
        val fileName: String,
        val backupEntry: String,
        val kind: String,
        val sha256: String,
        val size: Long,
    )

    private companion object {
        const val MyVaultRoot = "MyVault"
        const val SyncManifestFile = "sync_manifest.json"
        const val EntryKindMetadata = "metadata"
        const val EntryKindFile = "file"
    }
}

sealed interface DriveSyncResult {
    data class Success(val message: String) : DriveSyncResult
    data class Conflict(val message: String) : DriveSyncResult
    data class Skipped(val message: String) : DriveSyncResult
    data class Failure(val message: String) : DriveSyncResult
}
