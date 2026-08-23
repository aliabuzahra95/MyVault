package com.myvault.app.data.formatting

enum class NoteFormattingAction(val displayName: String) {
    StructureOnly("Structure Only"),
    IntelligentStructure("Intelligent Structure"),
    CleanFormat("Format / Organise Note"),
    FormatNote("Format Note"),
}

enum class NoteFormattingProvider(val displayName: String) {
    Gemini("Gemini"),
    ChatGPT("ChatGPT"),
    Kimi("Kimi"),
}

enum class NoteFormattingModel(val displayName: String) {
    Fast("Fast"),
    Smart("Smart"),
}

data class NoteFormattingRequest(
    val action: NoteFormattingAction,
    val provider: NoteFormattingProvider,
    val model: NoteFormattingModel,
    val title: String,
    val body: String,
)

data class NoteFormattingResult(
    val editorHtml: String,
)

class NoteFormattingException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
