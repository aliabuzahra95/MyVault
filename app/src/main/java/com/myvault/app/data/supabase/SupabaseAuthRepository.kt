package com.myvault.app.data.supabase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseAuthRepository @Inject constructor(
    private val sessionStore: SupabaseSessionStore,
) {
    suspend fun signInWithPassword(email: String, password: String): Result<SupabaseSession> = withContext(Dispatchers.IO) {
        runCatching {
            if (!SupabaseConfig.isConfigured) {
                error("Supabase is not configured for ChatGPT yet.")
            }
            val cleanEmail = email.trim()
            if (cleanEmail.isBlank() || password.isBlank()) {
                error("Enter your email and password.")
            }
            val connection = URL("${SupabaseConfig.url}/auth/v1/token?grant_type=password").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 30_000
                connection.doOutput = true
                connection.setRequestProperty("apikey", SupabaseConfig.anonKey)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { output ->
                    output.write(
                        JSONObject()
                            .put("email", cleanEmail)
                            .put("password", password)
                            .toString()
                            .toByteArray(Charsets.UTF_8),
                    )
                }
                val responseText = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText().orEmpty()
                }
                val json = JSONObject(responseText.ifBlank { "{}" })
                if (connection.responseCode !in 200..299) {
                    error(json.optString("msg").ifBlank { json.optString("error_description").ifBlank { "Supabase sign in failed." } })
                }
                val user = json.optJSONObject("user")
                val session = SupabaseSession(
                    userId = user?.optString("id").orEmpty(),
                    email = user?.optString("email").orEmpty().ifBlank { cleanEmail },
                    accessToken = json.optString("access_token"),
                    refreshToken = json.optString("refresh_token"),
                    expiresAt = System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L,
                )
                if (!session.isSignedIn || session.refreshToken.isBlank()) {
                    error("Supabase did not return a valid AI session.")
                }
                sessionStore.save(session)
                session
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun signOut() {
        sessionStore.clear()
    }
}
