package com.myvault.app.data.quran.speech

import android.net.Uri
import java.io.File

data class SpeechRecognitionRequest(
    val audioFile: File,
    val audioUri: Uri,
    val languageCode: String = "ar-SA",
    val surahNumber: Int,
    val ayahNumber: Int,
    val verseKey: String,
    val expectedText: String? = null,
    val durationMs: Long,
    val recordedAtMs: Long,
)

data class SpeechRecognitionResult(
    val transcript: String = "",
    val normalizedTranscript: String = "",
    val providerName: String,
    val modelName: String,
    val confidence: Float? = null,
    val wordTimestamps: List<SpeechRecognitionWord> = emptyList(),
    val latencyMs: Long,
    val errorMessage: String? = null,
    val technicalErrorMessage: String? = null,
) {
    val isSuccess: Boolean
        get() = errorMessage == null
}

data class SpeechRecognitionWord(
    val word: String,
    val startMs: Long? = null,
    val endMs: Long? = null,
    val confidence: Float? = null,
)

interface SpeechRecognitionProvider {
    val providerName: String
    val modelName: String

    suspend fun transcribe(request: SpeechRecognitionRequest): SpeechRecognitionResult
}

fun normalizeArabicTranscript(value: String): String =
    value
        .replace("\u0640", "")
        .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
        .replace(Regex("[\\p{Punct}ۚۖۗۘۙۛۜ۝۞؟،؛«»“”‘’]"), " ")
        .replace('ٱ', 'ا')
        .replace('أ', 'ا')
        .replace('إ', 'ا')
        .replace('آ', 'ا')
        .replace('ى', 'ي')
        .replace(Regex("\\s+"), " ")
        .trim()
