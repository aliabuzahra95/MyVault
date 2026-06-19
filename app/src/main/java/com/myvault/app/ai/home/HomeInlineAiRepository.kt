package com.myvault.app.ai.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeInlineAiRepository @Inject constructor(
    private val dao: HomeChatHistoryDao,
    private val client: HomeInlineAiClient,
) {
    suspend fun searchTitles(query: String): List<HomeAiAttachableItem> = withContext(Dispatchers.IO) {
        val normalized = query.trim().take(48)
        if (normalized.length < 2) return@withContext emptyList()
        dao.searchAttachableTitles(normalized.escapeLike(), limit = 5)
    }

    suspend fun pickerItems(): List<HomeAiAttachableItem> = withContext(Dispatchers.IO) {
        (dao.recentStudyItems(18) + dao.recentCourseItems(12) + dao.recentConceptItems(12))
            .distinctBy { it.type to it.id }
            .sortedByDescending { it.updatedAt }
            .take(36)
    }

    suspend fun loadContexts(items: List<HomeAiAttachableItem>): List<HomeAiContextItem> = withContext(Dispatchers.IO) {
        val studyIds = items.filter { it.type == HomeAiAttachableType.Study }.map { it.id }
        val courseIds = items.filter { it.type == HomeAiAttachableType.Course }.map { it.id }
        val conceptIds = items.filter { it.type == HomeAiAttachableType.ConceptCard }.map { it.id }

        val rows = buildList {
            if (studyIds.isNotEmpty()) addAll(dao.studyContexts(studyIds))
            if (courseIds.isNotEmpty()) addAll(dao.courseContexts(courseIds))
            if (conceptIds.isNotEmpty()) addAll(dao.conceptContexts(conceptIds))
        }
        items.mapNotNull { item ->
            val row = rows.firstOrNull { it.id == item.id && it.type == item.type.name } ?: return@mapNotNull null
            HomeAiContextItem(item = item, body = row.body)
        }
    }

    fun streamAnswer(
        question: String,
        contexts: List<HomeAiContextItem>,
        provider: HomeAiProvider,
        modelMode: HomeAiModelMode,
    ): Flow<String> {
        val prompt = HomeInlineAiPromptBuilder.buildUserPrompt(question = question, contexts = contexts)
        val system = HomeInlineAiPromptBuilder.buildSystemInstruction(hasContext = contexts.isNotEmpty())
        return client.streamText(provider = provider, modelMode = modelMode, systemInstruction = system, prompt = prompt)
    }

    suspend fun saveHistory(
        question: String,
        answer: String,
        attachedItems: List<HomeAiAttachableItem>,
        modelId: String,
    ) = withContext(Dispatchers.IO) {
        dao.insertHistory(
            HomeChatHistoryEntity(
                id = UUID.randomUUID().toString(),
                userQuery = question,
                assistantAnswer = answer,
                attachedTitles = JSONArray(attachedItems.map { it.title }).toString(),
                modelId = modelId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        dao.pruneHistory()
    }

    fun estimateContextChars(contexts: List<HomeAiContextItem>): Int =
        HomeInlineAiPromptBuilder.estimatePayloadChars(contexts)

    fun maskedKeyLabel(provider: HomeAiProvider): String = client.maskedKeyLabel(provider)

    fun providerStatuses(): List<HomeAiProviderStatus> = client.providerStatuses()

    fun resolvedModelId(provider: HomeAiProvider, modelMode: HomeAiModelMode): String =
        client.resolveModelId(provider, modelMode)

    suspend fun recentHistory(): List<HomeInlineAiHistoryItem> = withContext(Dispatchers.IO) {
        dao.recentHistory().map { it.toHistoryItem() }
    }

    suspend fun historyById(id: String): HomeChatHistoryEntity? = withContext(Dispatchers.IO) {
        dao.historyById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        dao.clearHomeInlineAiHistory()
    }

    private fun HomeChatHistoryEntity.toHistoryItem(): HomeInlineAiHistoryItem = HomeInlineAiHistoryItem(
        id = id,
        title = userQuery.toPreview(max = 46),
        preview = userQuery.toPreview(max = 92),
        assistantPreview = assistantAnswer.toPreview(max = 110),
        attachedTitles = parseAttachedTitles(attachedTitles),
        modelId = modelId,
        createdAt = createdAt,
    )

    private fun parseAttachedTitles(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun String.toPreview(max: Int): String {
        val singleLine = replace(Regex("\\s+"), " ").trim()
        return if (singleLine.length <= max) singleLine else singleLine.take(max - 1).trimEnd() + "…"
    }

    private fun String.escapeLike(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
}
