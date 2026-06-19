package com.myvault.app.ai.home

enum class HomeAiAttachableType(val label: String) {
    Study("Study"),
    Course("Course"),
    ConceptCard("Concept Card"),
}

data class HomeAiAttachableItem(
    val id: String,
    val title: String,
    val type: HomeAiAttachableType,
    val subtitle: String = "",
    val updatedAt: Long = 0L,
)

data class HomeAiContextItem(
    val item: HomeAiAttachableItem,
    val body: String,
)

enum class HomeAiProvider(
    val label: String,
) {
    OPENAI("OpenAI"),
    GEMINI("Gemini"),
}

enum class HomeAiModelMode(
    val label: String,
) {
    FAST("Fast"),
    SMART("Smart"),
}

data class HomeAiProviderStatus(
    val provider: HomeAiProvider,
    val configured: Boolean,
    val implemented: Boolean = true,
    val maskedKeyLabel: String = "",
) {
    val selectable: Boolean
        get() = configured && implemented

    val statusLabel: String
        get() = when {
            !implemented -> "unavailable"
            configured -> "configured"
            else -> "not configured"
        }
}

enum class HomeInlineAiRole {
    User,
    Assistant,
    Error,
}

enum class HomeAiPanelMode {
    Chat,
    Settings,
    AttachNotes,
}

data class HomeInlineAiMessage(
    val id: String,
    val role: HomeInlineAiRole,
    val text: String,
    val attachedTitles: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
)

data class HomeInlineAiHistoryItem(
    val id: String,
    val title: String,
    val preview: String,
    val assistantPreview: String,
    val attachedTitles: List<String>,
    val modelId: String,
    val createdAt: Long,
)

sealed class HomeInlineAiError(val userMessage: String) {
    data object NetworkDead : HomeInlineAiError("Network timeout. Check connection and try again.")
    data object AuthFailure : HomeInlineAiError("API key or provider permission rejected.")
    data object ModelOverloaded : HomeInlineAiError("Provider rate limit reached. Try again later.")
    data object ModelNotFound : HomeInlineAiError("Model or endpoint not found.")
    data object RequestFormatRejected : HomeInlineAiError("Request format rejected. Check provider/model configuration.")
    data object MaxTokenBoundsExceeded : HomeInlineAiError("The attached context is too large. Remove one attachment and try again.")
    data class Unknown(val detail: String) : HomeInlineAiError(detail.ifBlank { "AI request failed. Check provider settings." })
}

data class HomeInlineAiState(
    val chatInputText: String = "",
    val attachedItems: List<HomeAiAttachableItem> = emptyList(),
    val suggestedTitles: List<HomeAiAttachableItem> = emptyList(),
    val isPanelOpen: Boolean = false,
    val isStreaming: Boolean = false,
    val currentStreamingAnswer: String = "",
    val chatMessages: List<HomeInlineAiMessage> = emptyList(),
    val selectedProvider: HomeAiProvider = HomeAiProvider.GEMINI,
    val selectedModelMode: HomeAiModelMode = HomeAiModelMode.FAST,
    val resolvedModelId: String = "",
    val providerStatuses: List<HomeAiProviderStatus> = emptyList(),
    val panelMode: HomeAiPanelMode = HomeAiPanelMode.Chat,
    val maskedKeyStatus: String = "",
    val warning: String? = null,
    val error: HomeInlineAiError? = null,
    val lastRequestQuestion: String = "",
    val lastRequestAttachments: List<HomeAiAttachableItem> = emptyList(),
    val pickerItems: List<HomeAiAttachableItem> = emptyList(),
    val historyItems: List<HomeInlineAiHistoryItem> = emptyList(),
)
