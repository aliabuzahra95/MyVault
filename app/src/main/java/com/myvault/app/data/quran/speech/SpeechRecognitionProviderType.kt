package com.myvault.app.data.quran.speech

enum class SpeechRecognitionProviderType(
    val displayName: String,
    val shortName: String,
) {
    GoogleChirp(
        displayName = "Google Chirp",
        shortName = "Google",
    ),
    OpenAiTranscribe(
        displayName = "OpenAI Transcribe",
        shortName = "OpenAI",
    );

    fun createProvider(): SpeechRecognitionProvider =
        when (this) {
            GoogleChirp -> GoogleSpeechRecognitionProvider()
            OpenAiTranscribe -> OpenAiSpeechRecognitionProvider()
        }

    companion object {
        fun fromName(value: String): SpeechRecognitionProviderType =
            entries.firstOrNull { it.name == value } ?: GoogleChirp
    }
}
