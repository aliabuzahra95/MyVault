package com.myvault.app.data.sync.record

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

internal data class RecordSyncDriveFile(val id: String, val name: String, val parents: List<String>)
internal data class RecordSyncDriveChange(val fileId: String, val removed: Boolean, val file: RecordSyncDriveFile?)
internal data class RecordSyncChangePage(val changes: List<RecordSyncDriveChange>, val nextPageToken: String?, val newStartPageToken: String?)

internal class RecordSyncDriveClient @Inject constructor(@param:ApplicationContext private val context: Context) {
    private val scope = "https://www.googleapis.com/auth/drive.file"
    private val filesUrl = "https://www.googleapis.com/drive/v3/files"
    private val changesUrl = "https://www.googleapis.com/drive/v3/changes"

    fun accountId(): String {
        val id = get("https://www.googleapis.com/drive/v3/about?fields=user(permissionId)")
            .getJSONObject("user").getString("permissionId")
        require(id.isNotBlank()) { "Google Drive did not provide a stable account ID." }
        return id
    }

    fun ensureRecordsFolder(): String {
        val root = ensureFolder("root", "MyVault")
        val sync = ensureFolder(root, "sync-v1")
        return ensureFolder(sync, "records")
    }

    fun findRecordsFolder(): String? {
        val root = findFolder("root", "MyVault") ?: return null
        val sync = findFolder(root, "sync-v1") ?: return null
        return findFolder(sync, "records")
    }

    fun startPageToken(): String = get("$changesUrl/startPageToken").getString("startPageToken")

    fun changes(pageToken: String): RecordSyncChangePage {
        val fields = "nextPageToken,newStartPageToken,changes(fileId,removed,file(id,name,parents,mimeType))"
        val json = get("$changesUrl?pageToken=${pageToken.encoded()}&includeRemoved=true&spaces=drive&pageSize=1000&fields=${fields.encoded()}")
        val rows = json.optJSONArray("changes") ?: JSONArray()
        val changes = (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val file = row.optJSONObject("file")
            RecordSyncDriveChange(row.getString("fileId"), row.optBoolean("removed"), file?.toFile())
        }
        return RecordSyncChangePage(changes, json.optString("nextPageToken").ifBlank { null }, json.optString("newStartPageToken").ifBlank { null })
    }

    fun listRecords(folderId: String): List<RecordSyncDriveFile> {
        val records = mutableListOf<RecordSyncDriveFile>()
        var token: String? = null
        do {
            val query = "'$folderId' in parents and trashed = false"
            val next = token?.let { "&pageToken=${it.encoded()}" }.orEmpty()
            val json = get("$filesUrl?q=${query.encoded()}&spaces=drive&pageSize=1000&fields=${"nextPageToken,files(id,name,parents,mimeType)".encoded()}$next")
            val files = json.optJSONArray("files") ?: JSONArray()
            for (index in 0 until files.length()) records += files.getJSONObject(index).toFile()
            token = json.optString("nextPageToken").ifBlank { null }
        } while (token != null)
        return records
    }

    fun download(fileId: String): String = request("GET", "$filesUrl/${fileId.encoded()}?alt=media").toString(Charsets.UTF_8)

    fun upload(folderId: String, revision: RecordSyncRevision): String {
        val name = "${revision.revisionId}.json"
        val already = findRecordByName(folderId, name)
        if (already.isNotEmpty()) {
            require(already.all {
                val existing = RecordSyncRevision.parse(JSONObject(download(it.id)))
                existing.revisionId == revision.revisionId && existing.contentHash == revision.contentHash && existing.mutationId == revision.mutationId
            }) { "A sync revision name is already in use with different content." }
            return already.first().id
        }
        val boundary = "myvault-sync-${revision.revisionId}"
        val metadata = JSONObject()
            .put("name", name)
            .put("mimeType", "application/json")
            .put("parents", JSONArray().put(folderId))
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(metadata.toString())
            append("\r\n--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n")
            append(revision.toJson().toString())
            append("\r\n--$boundary--\r\n")
        }.toByteArray(Charsets.UTF_8)
        return JSONObject(request("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", body, "multipart/related; boundary=$boundary").toString(Charsets.UTF_8)).getString("id")
    }

    private fun findRecordByName(parent: String, name: String): List<RecordSyncDriveFile> {
        val query = "'$parent' in parents and name = '$name' and trashed = false"
        val json = get("$filesUrl?q=${query.encoded()}&spaces=drive&fields=${"nextPageToken,files(id,name,parents)".encoded()}")
        val files = json.optJSONArray("files") ?: JSONArray()
        return (0 until files.length()).map { files.getJSONObject(it).toFile() }
    }

    private fun ensureFolder(parent: String, name: String): String = findFolder(parent, name) ?: run {
        val body = JSONObject().put("name", name).put("mimeType", "application/vnd.google-apps.folder")
            .put("parents", JSONArray().put(parent)).toString().toByteArray(Charsets.UTF_8)
        JSONObject(request("POST", "$filesUrl?fields=id", body, "application/json; charset=UTF-8").toString(Charsets.UTF_8)).getString("id")
    }

    private fun findFolder(parent: String, name: String): String? {
        val query = "'$parent' in parents and name = '$name' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val json = get("$filesUrl?q=${query.encoded()}&spaces=drive&fields=${"nextPageToken,files(id,name)".encoded()}")
        val files = json.optJSONArray("files") ?: return null
        require(files.length() <= 1) { "Multiple MyVault sync folders exist; sync stopped without changing data." }
        return if (files.length() == 0) null else files.getJSONObject(0).getString("id")
    }

    private fun get(url: String) = JSONObject(request("GET", url).toString(Charsets.UTF_8))

    private fun request(method: String, url: String, body: ByteArray? = null, contentType: String? = null): ByteArray {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?.takeIf { GoogleSignIn.hasPermissions(it, Scope(scope)) }?.account
            ?: error("Connect the same Google Drive account before using Automatic Sync.")
        var token = GoogleAuthUtil.getToken(context, account, "oauth2:$scope")
        for (attempt in 0..1) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Authorization", "Bearer $token")
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body) }
                }
            }
            try {
                val code = connection.responseCode
                if (code == 401 && attempt == 0) {
                    GoogleAuthUtil.clearToken(context, token)
                    token = GoogleAuthUtil.getToken(context, account, "oauth2:$scope")
                    continue
                }
                val response = (if (code in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: ByteArray(0)
                if (code !in 200..299) error("Google Drive sync HTTP $code: ${response.toString(Charsets.UTF_8).take(240)}")
                return response
            } finally {
                connection.disconnect()
            }
        }
        error("Google Drive authorization expired.")
    }
}

private fun String.encoded() = URLEncoder.encode(this, "UTF-8")

private fun JSONObject.toFile(): RecordSyncDriveFile {
    val rows = optJSONArray("parents") ?: JSONArray()
    return RecordSyncDriveFile(getString("id"), getString("name"), (0 until rows.length()).map { rows.getString(it) })
}
