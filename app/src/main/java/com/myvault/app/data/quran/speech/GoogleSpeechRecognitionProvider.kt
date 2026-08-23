package com.myvault.app.data.quran.speech

import android.util.Base64
import com.myvault.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.system.measureTimeMillis

class GoogleSpeechRecognitionProvider(
    private val fallbackAccessToken: String = BuildConfig.GOOGLE_SPEECH_ACCESS_TOKEN.trim(),
    private val serviceAccountJsonBase64: String = BuildConfig.GOOGLE_SPEECH_SERVICE_ACCOUNT_JSON_BASE64.trim(),
    private val projectId: String = BuildConfig.GOOGLE_SPEECH_PROJECT_ID.trim(),
    private val location: String = BuildConfig.GOOGLE_SPEECH_LOCATION.trim().ifBlank { "us" },
    override val modelName: String = BuildConfig.GOOGLE_SPEECH_MODEL.trim().ifBlank { "chirp_3" },
) : SpeechRecognitionProvider {
    override val providerName: String = "Google Speech"

    private val accessTokenLock = Any()
    private var cachedServiceAccountToken: CachedAccessToken? = null

    override suspend fun transcribe(request: SpeechRecognitionRequest): SpeechRecognitionResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            var measuredLatency = 0L
            try {
                validateConfig()
                if (!request.audioFile.exists() || !request.audioFile.isFile) {
                    throw FileNotFoundException("Recording file was not found: ${request.audioFile.absolutePath}")
                }

                lateinit var response: JSONObject
                measuredLatency = measureTimeMillis {
                    response = postRecognize(request)
                }
                response.toSpeechRecognitionResult(latencyMs = measuredLatency)
            } catch (error: Throwable) {
                val latency = measuredLatency.takeIf { it > 0L } ?: (System.currentTimeMillis() - startedAt)
                SpeechRecognitionResult(
                    providerName = providerName,
                    modelName = modelName,
                    latencyMs = latency,
                    errorMessage = error.toUserMessage(),
                    technicalErrorMessage = error.message,
                )
            }
        }

    private fun validateConfig() {
        when {
            projectId.isBlank() -> error("Google Speech project ID is missing. Add MYVAULT_GOOGLE_SPEECH_PROJECT_ID to local.properties.")
            serviceAccountJsonBase64.isBlank() && fallbackAccessToken.isBlank() -> error("Google Speech credentials are missing. Add the service account key to local.properties.")
            location.isBlank() -> error("Google Speech location is missing. Use us or eu for Chirp 3.")
        }
    }

    private fun postRecognize(request: SpeechRecognitionRequest): JSONObject {
        val connection = (URL(endpointUrl()).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 35_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer ${accessToken()}")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")

            val body = buildRequestBody(request)
            connection.outputStream.use { stream ->
                stream.write(body.toString().toByteArray(Charsets.UTF_8))
            }

            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GoogleSpeechApiException(connection.responseCode, errorBody)
            }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun endpointUrl(): String {
        val host = if (location.equals("global", ignoreCase = true)) {
            "speech.googleapis.com"
        } else {
            "${location.lowercase()}-speech.googleapis.com"
        }
        return "https://$host/v2/projects/$projectId/locations/$location/recognizers/_:recognize"
    }

    private fun accessToken(): String {
        if (serviceAccountJsonBase64.isBlank()) return fallbackAccessToken
        val nowMs = System.currentTimeMillis()
        cachedServiceAccountToken
            ?.takeIf { it.expiresAtMs - TOKEN_REFRESH_BUFFER_MS > nowMs }
            ?.let { return it.value }

        return synchronized(accessTokenLock) {
            val refreshedNowMs = System.currentTimeMillis()
            cachedServiceAccountToken
                ?.takeIf { it.expiresAtMs - TOKEN_REFRESH_BUFFER_MS > refreshedNowMs }
                ?.value
                ?: fetchServiceAccountAccessToken().also { cachedServiceAccountToken = it }.value
        }
    }

    private fun fetchServiceAccountAccessToken(): CachedAccessToken {
        val credentials = serviceAccountCredentials()
        val assertion = signedJwt(credentials)
        val connection = (URL(credentials.tokenUri).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")

            val body = listOf(
                "grant_type" to JWT_BEARER_GRANT_TYPE,
                "assertion" to assertion,
            ).joinToString("&") { (key, value) ->
                "${key.urlEncoded()}=${value.urlEncoded()}"
            }
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }

            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw GoogleSpeechAuthException(connection.responseCode, errorBody)
            }
            val response = JSONObject(responseText)
            val token = response.optString("access_token")
            if (token.isBlank()) {
                throw GoogleSpeechAuthException(connection.responseCode, "Google token response did not include access_token.")
            }
            val expiresInSeconds = response.optLong("expires_in", 3600L).coerceAtLeast(60L)
            return CachedAccessToken(
                value = token,
                expiresAtMs = System.currentTimeMillis() + expiresInSeconds * 1000L,
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun serviceAccountCredentials(): ServiceAccountCredentials {
        val json = runCatching {
            String(java.util.Base64.getDecoder().decode(serviceAccountJsonBase64), Charsets.UTF_8)
        }.getOrElse {
            throw GoogleSpeechAuthException(0, "Google Speech service account key is not valid base64.")
        }
        val credentials = JSONObject(json)
        val clientEmail = credentials.optString("client_email")
        val privateKey = credentials.optString("private_key")
        val tokenUri = credentials.optString("token_uri").ifBlank { DEFAULT_TOKEN_URI }
        if (clientEmail.isBlank() || privateKey.isBlank()) {
            throw GoogleSpeechAuthException(0, "Google Speech service account key is missing client_email or private_key.")
        }
        return ServiceAccountCredentials(
            clientEmail = clientEmail,
            privateKey = privateKey,
            tokenUri = tokenUri,
        )
    }

    private fun signedJwt(credentials: ServiceAccountCredentials): String {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val header = JSONObject()
            .put("alg", "RS256")
            .put("typ", "JWT")
        val claimSet = JSONObject()
            .put("iss", credentials.clientEmail)
            .put("scope", GOOGLE_CLOUD_SCOPE)
            .put("aud", credentials.tokenUri)
            .put("iat", nowSeconds)
            .put("exp", nowSeconds + 3600L)
        val signingInput = "${header.toString().base64UrlEncoded()}.${claimSet.toString().base64UrlEncoded()}"
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(credentials.privateKey.toPrivateKey())
        signature.update(signingInput.toByteArray(Charsets.UTF_8))
        return "$signingInput.${signature.sign().base64UrlEncoded()}"
    }

    private fun buildRequestBody(request: SpeechRecognitionRequest): JSONObject =
        JSONObject()
            .put(
                "config",
                JSONObject()
                    .put("autoDecodingConfig", JSONObject())
                    .put("languageCodes", JSONArray().put(request.languageCode))
                    .put("model", modelName)
                    .put(
                        "features",
                        JSONObject()
                            .put("enableAutomaticPunctuation", false)
                            .put("enableWordTimeOffsets", true),
                    ),
            )
            .put("content", Base64.encodeToString(request.audioFile.readBytes(), Base64.NO_WRAP))

    private fun JSONObject.toSpeechRecognitionResult(latencyMs: Long): SpeechRecognitionResult {
        val results = optJSONArray("results") ?: JSONArray()
        val transcriptParts = mutableListOf<String>()
        val words = mutableListOf<SpeechRecognitionWord>()
        val confidenceValues = mutableListOf<Float>()

        for (index in 0 until results.length()) {
            val alternative = results.optJSONObject(index)
                ?.optJSONArray("alternatives")
                ?.optJSONObject(0)
                ?: continue
            alternative.optString("transcript").takeIf { it.isNotBlank() }?.let(transcriptParts::add)
            val confidence = alternative.optDouble("confidence", Double.NaN)
                .takeIf { !it.isNaN() && it > 0.0 }
                ?.toFloat()
            confidence?.let(confidenceValues::add)

            val wordArray = alternative.optJSONArray("words") ?: JSONArray()
            for (wordIndex in 0 until wordArray.length()) {
                val word = wordArray.optJSONObject(wordIndex) ?: continue
                words += SpeechRecognitionWord(
                    word = word.optString("word"),
                    startMs = word.optString("startOffset").durationToMsOrNull(),
                    endMs = word.optString("endOffset").durationToMsOrNull(),
                    confidence = word.optDouble("confidence", Double.NaN)
                        .takeIf { !it.isNaN() && it > 0.0 }
                        ?.toFloat(),
                )
            }
        }

        val transcript = transcriptParts.joinToString(separator = " ").trim()
        return SpeechRecognitionResult(
            transcript = transcript,
            normalizedTranscript = normalizeArabicTranscript(transcript),
            providerName = providerName,
            modelName = modelName,
            confidence = confidenceValues.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            wordTimestamps = words.filter { it.word.isNotBlank() },
            latencyMs = latencyMs,
            errorMessage = if (transcript.isBlank()) "Google Speech returned an empty transcript. Try recording again a little closer to the microphone." else null,
            technicalErrorMessage = if (transcript.isBlank()) "Empty Google Speech response: $this" else null,
        )
    }

    private fun Throwable.toUserMessage(): String =
        when (this) {
            is GoogleSpeechAuthException -> "Google Speech service account could not create an access token. Check the service account key and permissions."
            is GoogleSpeechApiException -> toUserMessage()
            is UnknownHostException -> "No internet connection. Please connect to the internet and try again."
            is SocketTimeoutException -> "Google Speech took too long to answer. Please try again."
            is FileNotFoundException -> "The recording could not be found. Please record the ayah again."
            else -> message?.takeIf {
                it.contains("Google Speech", ignoreCase = true)
            } ?: "Transcription failed. Please try again."
        }

    private class GoogleSpeechApiException(
        val statusCode: Int,
        val responseBody: String,
    ) : RuntimeException("Google Speech API returned HTTP $statusCode: $responseBody") {
        fun toUserMessage(): String {
            val providerMessage = runCatching {
                JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            return when (statusCode) {
                400 -> "Google Speech could not read this recording format. Please re-record and try again."
                401 -> "Google Speech credentials were rejected. Check the service account key."
                403 -> when {
                    providerMessage.contains("billing", ignoreCase = true) -> "Google Speech billing is not enabled for this project."
                    providerMessage.contains("quota", ignoreCase = true) -> "Google Speech quota was reached. Please try again later."
                    else -> "Google Speech permission is missing for this project."
                }
                404 -> "Google Speech project, location, or recognizer was not found. Check the project ID and location."
                429 -> "Google Speech is rate-limiting requests. Please wait a moment and try again."
                in 500..599 -> "Google Speech is temporarily unavailable. Please try again."
                else -> providerMessage.ifBlank { "Google Speech request failed with HTTP $statusCode." }
            }
        }
    }

    private data class CachedAccessToken(
        val value: String,
        val expiresAtMs: Long,
    )

    private data class ServiceAccountCredentials(
        val clientEmail: String,
        val privateKey: String,
        val tokenUri: String,
    )

    private class GoogleSpeechAuthException(
        val statusCode: Int,
        val responseBody: String,
    ) : RuntimeException("Google Speech auth failed with HTTP $statusCode: $responseBody")
}

private const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"
private const val GOOGLE_CLOUD_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
private const val JWT_BEARER_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:jwt-bearer"
private const val TOKEN_REFRESH_BUFFER_MS = 5 * 60 * 1000L

private fun String.base64UrlEncoded(): String =
    java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(toByteArray(Charsets.UTF_8))

private fun ByteArray.base64UrlEncoded(): String =
    java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(this)

private fun String.urlEncoded(): String =
    URLEncoder.encode(this, Charsets.UTF_8.name())

private fun String.toPrivateKey(): java.security.PrivateKey {
    val cleanKey = replace("-----BEGIN PRIVATE KEY-----", "")
        .replace("-----END PRIVATE KEY-----", "")
        .replace("\\s".toRegex(), "")
    val keyBytes = java.util.Base64.getDecoder().decode(cleanKey)
    return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
}

private fun String.durationToMsOrNull(): Long? {
    if (isBlank()) return null
    val seconds = removeSuffix("s").toDoubleOrNull() ?: return null
    return (seconds * 1000.0).toLong()
}
