package com.myvault.app.data.supabase

import android.content.Context
import com.myvault.app.data.repository.BackupRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseCloudBackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val backupRepository: BackupRepository,
    private val sessionStore: SupabaseSessionStore,
) {
    val session = sessionStore.session

    suspend fun signUp(email: String, password: String): CloudBackupResult = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext CloudBackupResult.Failure(notConfiguredMessage)
        authRequest(path = "/auth/v1/signup", email = email, password = password)
    }

    suspend fun signIn(email: String, password: String): CloudBackupResult = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext CloudBackupResult.Failure(notConfiguredMessage)
        authRequest(path = "/auth/v1/token?grant_type=password", email = email, password = password)
    }

    suspend fun signOut() {
        sessionStore.clear()
    }

    suspend fun uploadLatestBackup(): CloudBackupResult = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext CloudBackupResult.Failure(notConfiguredMessage)
        val session = authenticatedSession()
        if (!session.isSignedIn) return@withContext CloudBackupResult.Failure("Sign in to Supabase first.")

        val file = File(context.cacheDir, "vault-cloud-backup.vaultbackup")
        val path = "${session.userId}/vault-backup-latest.vaultbackup"
        var connection: HttpURLConnection? = null
        runCatching {
            val backup = backupRepository.exportBackupToFile(file)
            val activeConnection = URL("${SupabaseConfig.url}/storage/v1/object/${SupabaseConfig.backupBucket}/$path").openConnection() as HttpURLConnection
            connection = activeConnection
            activeConnection.applyTimeouts()
            activeConnection.requestMethod = "POST"
            activeConnection.doOutput = true
            activeConnection.setRequestProperty("apikey", SupabaseConfig.anonKey)
            activeConnection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            activeConnection.setRequestProperty("Content-Type", "application/octet-stream")
            activeConnection.setRequestProperty("x-upsert", "true")
            file.inputStream().use { input -> activeConnection.outputStream.use { output -> input.copyTo(output) } }

            if (activeConnection.responseCode in 200..299) {
                CloudBackupResult.Success("Cloud backup uploaded: ${backup.noteCount} notes, ${backup.attachmentCount} attachments.")
            } else {
                CloudBackupResult.Failure(activeConnection.errorMessage("Cloud backup upload failed"))
            }
        }.getOrElse { error ->
            CloudBackupResult.Failure(error.message ?: "Cloud backup upload failed.")
        }.also {
            file.delete()
            connection?.disconnect()
        }
    }

    suspend fun restoreLatestBackup(): CloudBackupResult = withContext(Dispatchers.IO) {
        if (!SupabaseConfig.isConfigured) return@withContext CloudBackupResult.Failure(notConfiguredMessage)
        val session = authenticatedSession()
        if (!session.isSignedIn) return@withContext CloudBackupResult.Failure("Sign in to Supabase first.")

        val file = File(context.cacheDir, "vault-cloud-restore.vaultbackup")
        val path = "${session.userId}/vault-backup-latest.vaultbackup"
        val connection = URL("${SupabaseConfig.url}/storage/v1/object/authenticated/${SupabaseConfig.backupBucket}/$path").openConnection() as HttpURLConnection
        runCatching {
            connection.applyTimeouts()
            connection.requestMethod = "GET"
            connection.setRequestProperty("apikey", SupabaseConfig.anonKey)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")

            if (connection.responseCode !in 200..299) {
                return@runCatching CloudBackupResult.Failure(connection.errorMessage("Cloud backup download failed"))
            }

            file.outputStream().use { output -> connection.inputStream.use { input -> input.copyTo(output) } }
            val restore = backupRepository.restoreBackupFromFile(file)
            CloudBackupResult.Success("Cloud restore complete: ${restore.noteCount} notes, ${restore.attachmentCount} attachments.")
        }.getOrElse { error ->
            CloudBackupResult.Failure(error.message ?: "Cloud restore failed.")
        }.also {
            file.delete()
            connection.disconnect()
        }
    }

    private suspend fun authRequest(path: String, email: String, password: String): CloudBackupResult {
        val connection = URL("${SupabaseConfig.url}$path").openConnection() as HttpURLConnection
        return runCatching {
            connection.applyTimeouts()
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("apikey", SupabaseConfig.anonKey)
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("email", email.trim())
                .put("password", password)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) {
                return@runCatching CloudBackupResult.Failure(connection.errorMessage("Supabase sign in failed"))
            }

            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val user = json.optJSONObject("user")
            val accessToken = json.optString("access_token")
            val refreshToken = json.optString("refresh_token")
            val userId = user?.optString("id").orEmpty()
            val userEmail = user?.optString("email").orEmpty().ifBlank { email.trim() }
            if (userId.isBlank() || accessToken.isBlank()) {
                CloudBackupResult.Failure("Check your email to confirm your Supabase account, then sign in.")
            } else {
                sessionStore.save(
                    SupabaseSession(
                        userId = userId,
                        email = userEmail,
                        accessToken = accessToken,
                        refreshToken = refreshToken,
                        expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
                    ),
                )
                CloudBackupResult.Success("Signed in as $userEmail.")
            }
        }.getOrElse { error ->
            CloudBackupResult.Failure(error.message ?: "Supabase sign in failed.")
        }.also {
            connection.disconnect()
        }
    }

    private suspend fun authenticatedSession(): SupabaseSession {
        val session = sessionStore.session.first()
        val expiresSoon = session.expiresAt > 0 && session.expiresAt - System.currentTimeMillis() < 60_000L
        if (!session.isSignedIn || !expiresSoon || session.refreshToken.isBlank()) return session
        return refreshSession(session).getOrElse { session }
    }

    private suspend fun refreshSession(current: SupabaseSession): Result<SupabaseSession> {
        val connection = URL("${SupabaseConfig.url}/auth/v1/token?grant_type=refresh_token").openConnection() as HttpURLConnection
        return runCatching {
            connection.applyTimeouts()
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("apikey", SupabaseConfig.anonKey)
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject()
                .put("refresh_token", current.refreshToken)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode in 200..299) {
                connection.errorMessage("Supabase session refresh failed")
            }

            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val user = json.optJSONObject("user")
            val refreshed = current.copy(
                userId = user?.optString("id").orEmpty().ifBlank { current.userId },
                email = user?.optString("email").orEmpty().ifBlank { current.email },
                accessToken = json.optString("access_token").ifBlank { current.accessToken },
                refreshToken = json.optString("refresh_token").ifBlank { current.refreshToken },
                expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
            )
            sessionStore.save(refreshed)
            refreshed
        }.also {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.applyTimeouts() {
        connectTimeout = 15_000
        readTimeout = 45_000
    }

    private fun HttpURLConnection.errorMessage(prefix: String): String {
        val body = runCatching { errorStream?.bufferedReader()?.readText().orEmpty() }.getOrDefault("")
        return if (body.isBlank()) "$prefix. HTTP $responseCode." else "$prefix. HTTP $responseCode: $body"
    }

    private val notConfiguredMessage =
        "Supabase is not configured yet. Add your project URL and anon key to gradle.properties."
}

sealed interface CloudBackupResult {
    data class Success(val message: String) : CloudBackupResult
    data class Failure(val message: String) : CloudBackupResult
}
