package com.myvault.app.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiResearchLocalPreferences @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun selectedProvider(): AiResearchProvider =
        AiResearchProvider.entries.firstOrNull { it.id == preferences.getString(ProviderKey, null) }
            ?: AiResearchProvider.ChatGpt

    fun setSelectedProvider(provider: AiResearchProvider) {
        preferences.edit().putString(ProviderKey, provider.id).apply()
    }

    private companion object {
        const val PreferencesName = "ai_research_device_local"
        const val ProviderKey = "selected_provider"
    }
}

enum class AiResearchProvider(val id: String, val label: String) {
    ChatGpt("openai", "ChatGPT"),
    Gemini("gemini", "Gemini"),
    Kimi("kimi", "Kimi"),
}
