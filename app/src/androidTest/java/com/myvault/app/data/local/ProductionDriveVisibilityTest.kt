package com.myvault.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.Scope
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.UUID

/** Does not open Room, read Vault files, or inspect any pre-existing Drive folder. */
@RunWith(AndroidJUnit4::class)
class ProductionDriveVisibilityTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val stateFile get() = File(instrumentation.targetContext.filesDir, "disposable-oauth-visibility-test").apply { mkdirs() }
        .resolve("production-drive-visibility.json")
    private val scope = "https://www.googleapis.com/auth/drive.file"
    private val api = "https://www.googleapis.com/drive/v3"
    private val androidContent = "Disposable Android visibility proof: English العربية"
    private val webContent = "Disposable Web visibility proof: English العربية"

    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

    private class Session(private val token: String) {
        fun call(url: String, method: String = "GET", body: ByteArray? = null, type: String = "application/json"): Pair<Int, ByteArray> {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = 30000
                connection.readTimeout = 30000
                connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", type)
                    connection.outputStream.use { it.write(body) }
                }
                val status = connection.responseCode
                val bytes = (if (status in 200..299) connection.inputStream else connection.errorStream)?.use { it.readBytes() } ?: byteArrayOf()
                return status to bytes
            } finally { connection.disconnect() }
        }
        fun json(url: String, method: String = "GET", body: JSONObject? = null): JSONObject {
            val (status, bytes) = call(url, method, body?.toString()?.toByteArray(Charsets.UTF_8))
            check(status in 200..299) { "Disposable Drive request failed (HTTP $status); credentials and response omitted" }
            return JSONObject(bytes.toString(Charsets.UTF_8))
        }
    }

    private fun save(state: JSONObject) { stateFile.writeText(state.toString()) }
    fun redactedEvidence(): String = if (stateFile.exists()) load().toString() else "{}"
    private fun load(): JSONObject = JSONObject(stateFile.readText()).also {
        check(it.getString("rootName").startsWith("MYVAULT-OAUTH-VISIBILITY-DISPOSABLE-"))
        UUID.fromString(it.getString("operationId"))
        for (key in listOf("rootId", "androidFileId")) check(it.getString(key).matches(Regex("[A-Za-z0-9_-]+")))
    }
    private fun createExact(session: Session, id: String, metadata: JSONObject, bytes: ByteArray? = null) {
        metadata.put("id", id)
        val (status, _) = if (bytes == null) session.call("$api/files?fields=id", "POST", metadata.toString().toByteArray(Charsets.UTF_8))
        else {
            val boundary = "myvault-disposable-${UUID.randomUUID()}"
            val header = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n--$boundary\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8)
            val body = header + bytes + "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)
            session.call("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id", "POST", body, "multipart/related; boundary=$boundary")
        }
        check(status in 200..299 || status == 409) { "Disposable create failed (HTTP $status)" }
        val actual = session.json("$api/files/$id?fields=id,name,mimeType,parents")
        assertEquals(metadata.getString("name"), actual.getString("name"))
        if (metadata.has("parents")) assertEquals(metadata.getJSONArray("parents").getString(0), actual.getJSONArray("parents").getString(0))
        if (bytes != null) {
            val (readStatus, read) = session.call("$api/files/$id?alt=media")
            assertEquals(200, readStatus)
            assertTrue(bytes.contentEquals(read))
        }
    }

    @Test fun verifyIsolatedProductionOAuthVisibility() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Requires explicitly approved physical disposable verification", args.getString("visibilityApproved") == "true")
        val phase = args.getString("visibilityPhase")
        check(phase in setOf("create", "read-web", "cleanup"))
        val context = instrumentation.targetContext
        assertEquals("com.myvault.app", context.packageName)
        val account = checkNotNull(GoogleSignIn.getLastSignedInAccount(context)) { "Connect Google Drive in MyVault first" }
        check(GoogleSignIn.hasPermissions(account, Scope(scope))) { "Approve existing drive.file access in MyVault first" }
        val session = Session(GoogleAuthUtil.getToken(context, checkNotNull(account.account), "oauth2:$scope"))
        val identity = session.json("$api/about?fields=user(permissionId,emailAddress)").getJSONObject("user")
        check(identity.getString("emailAddress").trim().equals(account.email?.trim(), ignoreCase = true)) { "Drive account mismatch" }
        val permissionId = identity.getString("permissionId")
        if (phase == "create") {
            check(!stateFile.exists() || load().optString("status") == "CLEANED") { "An earlier disposable operation needs recovery or cleanup" }
            val operation = UUID.randomUUID().toString()
            val ids = session.json("$api/files/generateIds?count=2&space=drive&type=files").getJSONArray("ids")
            val state = JSONObject().put("operationId", operation).put("accountId", permissionId)
                .put("rootName", "MYVAULT-OAUTH-VISIBILITY-DISPOSABLE-$operation")
                .put("rootId", ids.getString(0)).put("androidFileId", ids.getString(1))
                .put("androidSha256", hash(androidContent.toByteArray(Charsets.UTF_8))).put("status", "INTENDED")
            save(state)
            createExact(session, state.getString("rootId"), JSONObject().put("name", state.getString("rootName")).put("mimeType", "application/vnd.google-apps.folder"))
            createExact(session, state.getString("androidFileId"), JSONObject().put("name", "android-proof.txt")
                .put("parents", org.json.JSONArray(listOf(state.getString("rootId")))), androidContent.toByteArray(Charsets.UTF_8))
            save(state.put("status", "ANDROID_VERIFIED"))
        } else {
            val state = load()
            check(state.getString("accountId") == permissionId) { "Account changed; disposable operation blocked" }
            if (phase == "read-web") {
                val id = checkNotNull(args.getString("webFileId"))
                check(id.matches(Regex("[A-Za-z0-9_-]+")))
                val metadata = session.json("$api/files/$id?fields=id,name,parents")
                assertEquals("web-proof.txt", metadata.getString("name"))
                assertEquals(state.getString("rootId"), metadata.getJSONArray("parents").getString(0))
                val (status, bytes) = session.call("$api/files/$id?alt=media")
                assertEquals(200, status)
                assertEquals(webContent, bytes.toString(Charsets.UTF_8))
                save(state.put("webFileId", id).put("webSha256", hash(bytes)).put("status", "BIDIRECTIONAL_VERIFIED"))
            } else {
                // Only IDs recorded by this operation are eligible, never arbitrary folder contents.
                val ids = buildList { if (state.has("webFileId")) add(state.getString("webFileId")); add(state.getString("androidFileId")) }
                for (id in ids) {
                    val (status, bytes) = session.call("$api/files/$id?fields=parents")
                    if (status == 404) continue
                    assertEquals(200, status)
                    assertEquals(state.getString("rootId"), JSONObject(bytes.toString(Charsets.UTF_8)).getJSONArray("parents").getString(0))
                    assertEquals(204, session.call("$api/files/$id", "DELETE").first)
                }
                val rootId = state.getString("rootId")
                val remaining = session.json("$api/files?q=%27$rootId%27%20in%20parents%20and%20trashed%20%3D%20false&fields=files(id),nextPageToken")
                check(remaining.getJSONArray("files").length() == 0 && !remaining.has("nextPageToken")) { "Unexpected disposable contents; root deletion refused" }
                assertEquals(state.getString("rootName"), session.json("$api/files/$rootId?fields=name").getString("name"))
                assertEquals(204, session.call("$api/files/$rootId", "DELETE").first)
                assertEquals(404, session.call("$api/files/$rootId?fields=id").first)
                save(state.put("status", "CLEANED"))
            }
        }
    }
}
