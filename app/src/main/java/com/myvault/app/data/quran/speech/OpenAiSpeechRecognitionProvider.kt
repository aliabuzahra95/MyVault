package com.myvault.app.data.quran.speech

import com.myvault.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import java.util.UUID
import kotlin.system.measureTimeMillis

class OpenAiSpeechRecognitionProvider(
    private val apiKey: String = BuildConfig.OPENAI_API_KEY.trim(),
    override val modelName: String = BuildConfig.OPENAI_TRANSCRIBE_MODEL.trim().ifBlank { DEFAULT_OPENAI_TRANSCRIBE_MODEL },
) : SpeechRecognitionProvider {
    override val providerName: String = "OpenAI"

    override suspend fun transcribe(request: SpeechRecognitionRequest): SpeechRecognitionResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            var measuredLatency = 0L
            try {
                validateConfig(request.audioFile)
                lateinit var response: JSONObject
                measuredLatency = measureTimeMillis {
                    response = postTranscription(request)
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

    private fun validateConfig(audioFile: File) {
        when {
            apiKey.isBlank() -> error("OpenAI API key is missing. Add MYVAULT_OPENAI_API_KEY to local.properties.")
            !audioFile.exists() || !audioFile.isFile -> throw FileNotFoundException("Recording file was not found: ${audioFile.absolutePath}")
            audioFile.length() > OPENAI_AUDIO_UPLOAD_LIMIT_BYTES -> error("OpenAI transcription supports recordings up to 25 MB. Please use a shorter recording.")
        }
    }

    private fun postTranscription(request: SpeechRecognitionRequest): JSONObject {
        val boundary = "MyVaultOpenAiBoundary${UUID.randomUUID().toString().replace("-", "")}"
        val connection = (URL(OPENAI_TRANSCRIPTIONS_ENDPOINT).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 10_000
            connection.readTimeout = 90_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "application/json")

            BufferedOutputStream(connection.outputStream).use { output ->
                output.writeFormField(boundary, "model", modelName)
                output.writeFormField(boundary, "language", request.openAiLanguageCode())
                output.writeFormField(boundary, "response_format", "json")
                output.writeFileField(
                    boundary = boundary,
                    name = "file",
                    file = request.audioFile,
                    contentType = "audio/wav",
                )
                output.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))
                output.flush()
            }

            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw OpenAiTranscriptionException(connection.responseCode, errorBody)
            }
            return JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.toSpeechRecognitionResult(latencyMs: Long): SpeechRecognitionResult {
        val transcript = optString("text").trim()
        return SpeechRecognitionResult(
            transcript = transcript,
            normalizedTranscript = normalizeArabicTranscript(transcript),
            providerName = providerName,
            modelName = modelName,
            confidence = null,
            wordTimestamps = emptyList(),
            latencyMs = latencyMs,
            errorMessage = if (transcript.isBlank()) "OpenAI returned an empty transcript. Try recording again a little closer to the microphone." else null,
            technicalErrorMessage = if (transcript.isBlank()) "Empty OpenAI transcription response: $this" else null,
        )
    }

    private fun Throwable.toUserMessage(): String =
        when (this) {
            is OpenAiTranscriptionException -> toUserMessage()
            is UnknownHostException -> "No internet connection. Please connect to the internet and try again."
            is SocketTimeoutException -> "OpenAI took too long to answer. Please try again."
            is FileNotFoundException -> "The recording could not be found. Please record again."
            else -> message?.takeIf {
                it.contains("OpenAI", ignoreCase = true)
            } ?: "OpenAI transcription failed. Please try again."
        }

    private class OpenAiTranscriptionException(
        val statusCode: Int,
        val responseBody: String,
    ) : RuntimeException("OpenAI transcription returned HTTP $statusCode: $responseBody") {
        fun toUserMessage(): String {
            val providerMessage = runCatching {
                JSONObject(responseBody).optJSONObject("error")?.optString("message").orEmpty()
            }.getOrDefault("")
            return when (statusCode) {
                400 -> providerMessage.ifBlank { "OpenAI could not read this recording format. Please re-record and try again." }
                401 -> "OpenAI API key was rejected. Check MYVAULT_OPENAI_API_KEY."
                403 -> "OpenAI transcription permission is missing for this API key."
                413 -> "This recording is too large for OpenAI transcription. Please use a shorter recording."
                429 -> "OpenAI is rate-limiting requests. Please wait a moment and try again."
                in 500..599 -> "OpenAI transcription is temporarily unavailable. Please try again."
                else -> providerMessage.ifBlank { "OpenAI transcription failed with HTTP $statusCode." }
            }
        }
    }
}

private const val DEFAULT_OPENAI_TRANSCRIBE_MODEL = "gpt-4o-transcribe"
private const val OPENAI_TRANSCRIPTIONS_ENDPOINT = "https://api.openai.com/v1/audio/transcriptions"
private const val OPENAI_AUDIO_UPLOAD_LIMIT_BYTES = 25L * 1024L * 1024L

private fun SpeechRecognitionRequest.openAiLanguageCode(): String =
    if (languageCode.startsWith("ar", ignoreCase = true)) "ar" else languageCode.substringBefore("-")

private fun BufferedOutputStream.writeFormField(boundary: String, name: String, value: String) {
    write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
    write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray(Charsets.UTF_8))
    write(value.toByteArray(Charsets.UTF_8))
    write("\r\n".toByteArray(Charsets.UTF_8))
}

private fun BufferedOutputStream.writeFileField(
    boundary: String,
    name: String,
    file: File,
    contentType: String,
) {
    write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
    write("Content-Disposition: form-data; name=\"$name\"; filename=\"${file.name}\"\r\n".toByteArray(Charsets.UTF_8))
    write("Content-Type: $contentType\r\n\r\n".toByteArray(Charsets.UTF_8))
    file.inputStream().use { input -> input.copyTo(this) }
    write("\r\n".toByteArray(Charsets.UTF_8))
}
