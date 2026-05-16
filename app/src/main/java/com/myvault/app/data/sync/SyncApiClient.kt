package com.myvault.app.data.sync

import com.myvault.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncApiClient @Inject constructor() {
    private val proxyUrl: String = BuildConfig.SYNC_PROXY_URL.trimEnd('/')
    private val proxyToken: String = BuildConfig.SYNC_PROXY_TOKEN

    suspend fun push(snapshot: SyncSnapshot): SyncResult = withContext(Dispatchers.IO) {
        if (proxyUrl.isBlank()) return@withContext SyncResult.Skipped("SYNC_PROXY_URL is not configured.")
        if (proxyToken.isBlank()) return@withContext SyncResult.Skipped("SYNC_PROXY_TOKEN is not configured.")

        runCatching {
            val connection = (URL("$proxyUrl/sync/push").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $proxyToken")
            }

            connection.outputStream.use { stream ->
                stream.write(snapshot.toJson().toString().toByteArray(Charsets.UTF_8))
            }

            if (connection.responseCode in 200..299) {
                SyncResult.Success
            } else {
                SyncResult.Failure("Worker push failed with HTTP ${connection.responseCode}.")
            }
        }.getOrElse { error -> SyncResult.Failure(error.message ?: "Unknown sync error.") }
    }

    suspend fun pullRaw(): Result<String> = withContext(Dispatchers.IO) {
        if (proxyUrl.isBlank()) return@withContext Result.failure(IllegalStateException("SYNC_PROXY_URL is not configured."))
        if (proxyToken.isBlank()) return@withContext Result.failure(IllegalStateException("SYNC_PROXY_TOKEN is not configured."))

        runCatching {
            val connection = (URL("$proxyUrl/sync/pull").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 30_000
                setRequestProperty("Authorization", "Bearer $proxyToken")
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            stream.bufferedReader().use { it.readText() }
        }
    }
}
