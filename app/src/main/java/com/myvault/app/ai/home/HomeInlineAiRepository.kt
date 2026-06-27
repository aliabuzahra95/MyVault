package com.myvault.app.ai.home

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeInlineAiRepository @Inject constructor(
    private val dao: HomeChatHistoryDao,
    private val client: HomeInlineAiClient,
) {
    suspend fun searchTitles(
        query: String,
        scope: HomeAiAttachmentScope = HomeAiAttachmentScope.Notes,
        courseId: String? = null,
    ): List<HomeAiAttachableItem> = withContext(Dispatchers.IO) {
        val normalized = query.trim().take(48)
        if (normalized.length < 2) return@withContext emptyList()
        when (scope) {
            HomeAiAttachmentScope.Notes -> dao.searchAttachableTitles(normalized.escapeLike(), limit = 5)
            HomeAiAttachmentScope.LibraryPdfs -> dao.searchPdfTitles(normalized.escapeLike(), limit = 5)
            HomeAiAttachmentScope.Course -> dao.searchCourseAttachableTitles(normalized.escapeLike(), courseId, limit = 5)
        }
    }

    suspend fun pickerItems(
        scope: HomeAiAttachmentScope = HomeAiAttachmentScope.Notes,
        courseId: String? = null,
    ): List<HomeAiAttachableItem> = withContext(Dispatchers.IO) {
        when (scope) {
            HomeAiAttachmentScope.Notes -> (dao.recentStudyItems(18) + dao.recentCourseItems(12) + dao.recentConceptItems(12))
                .distinctBy { it.type to it.id }
                .sortedByDescending { it.updatedAt }
                .take(36)
            HomeAiAttachmentScope.LibraryPdfs -> dao.recentPdfItems(36)
            HomeAiAttachmentScope.Course -> dao.recentCourseScopeItems(courseId, 36)
        }
    }

    suspend fun loadContexts(items: List<HomeAiAttachableItem>): List<HomeAiContextItem> = withContext(Dispatchers.IO) {
        val studyIds = items.filter { it.type == HomeAiAttachableType.Study }.map { it.id }
        val courseIds = items.filter { it.type == HomeAiAttachableType.Course }.map { it.id }
        val conceptIds = items.filter { it.type == HomeAiAttachableType.ConceptCard }.map { it.id }
        val pdfIds = items.filter { it.type == HomeAiAttachableType.Pdf }.map { it.id }

        val rows = buildList {
            if (studyIds.isNotEmpty()) addAll(dao.studyContexts(studyIds))
            if (courseIds.isNotEmpty()) addAll(dao.courseContexts(courseIds))
            if (conceptIds.isNotEmpty()) addAll(dao.conceptContexts(conceptIds))
            if (pdfIds.isNotEmpty()) addAll(dao.pdfContexts(pdfIds))
        }
        items.mapNotNull { item ->
            val row = rows.firstOrNull { it.id == item.id && it.type == item.type.name } ?: return@mapNotNull null
            HomeAiContextItem(item = item, body = row.body)
        }
    }


    suspend fun loadScreenContexts(
        scope: HomeAiAttachmentScope,
        courseId: String?,
    ): List<HomeAiContextItem> = withContext(Dispatchers.IO) {
        if (scope != HomeAiAttachmentScope.Course || courseId.isNullOrBlank()) return@withContext emptyList()
        dao.courseScreenContexts(courseId)
            .filter { it.body.isNotBlank() }
            .map { row ->
            HomeAiContextItem(
                item = HomeAiAttachableItem(
                    id = row.id,
                    title = row.title,
                    type = HomeAiAttachableType.CourseContext,
                    updatedAt = row.updatedAt,
                ),
                body = row.body,
            )
        }
    }

    fun streamAnswer(
        question: String,
        contexts: List<HomeAiContextItem>,
        provider: HomeAiProvider,
        modelMode: HomeAiModelMode,
        webSearchEnabled: Boolean = false,
        files: List<GeminiFileReference> = emptyList(),
        conversationMessages: List<HomeInlineAiMessage> = emptyList(),
    ): Flow<String> {
        val prompt = HomeInlineAiPromptBuilder.buildUserPrompt(
            question = question,
            contexts = contexts,
            conversationMessages = conversationMessages,
        )
        val system = HomeInlineAiPromptBuilder.buildSystemInstruction(hasContext = contexts.isNotEmpty())
        return client.streamText(
            provider = provider,
            modelMode = modelMode,
            systemInstruction = system,
            prompt = prompt,
            webSearchEnabled = webSearchEnabled,
            geminiFiles = if (provider == HomeAiProvider.GEMINI) files else emptyList(),
        )
    }

    suspend fun prepareGeminiPdfFiles(
        items: List<HomeAiAttachableItem>,
        forceUpload: Boolean = false,
    ): List<GeminiFileReference> = withContext(Dispatchers.IO) {
        val pdfIds = items.filter { it.type == HomeAiAttachableType.Pdf }.map { it.id }
        if (pdfIds.isEmpty()) return@withContext emptyList()

        val now = System.currentTimeMillis()
        val attachments = dao.pdfAttachments(pdfIds).associateBy { it.id }
        val cached = dao.cachedLibraryAiFiles(pdfIds)
            .associateBy { it.attachmentId }
        val safeReuseUntil = now + CacheExpirySafetyWindowMs

        pdfIds.mapNotNull { attachmentId ->
            val attachment = attachments[attachmentId] ?: return@mapNotNull null
            val cachedFile = cached[attachmentId]
            if (
                !forceUpload &&
                cachedFile != null &&
                cachedFile.expiresAt > safeReuseUntil &&
                cachedFile.localPath == attachment.localPath &&
                cachedFile.sizeBytes == attachment.sizeBytes &&
                cachedFile.fileUri.isNotBlank()
            ) {
                return@mapNotNull GeminiFileReference(
                    fileUri = cachedFile.fileUri,
                    mimeType = cachedFile.mimeType.ifBlank { attachment.mimeType },
                )
            }

            dao.deleteLibraryAiFileCache(attachmentId)
            val localFile = File(attachment.localPath)
            val uploaded = client.uploadGeminiFile(
                file = localFile,
                displayName = attachment.fileName,
                mimeType = attachment.mimeType.ifBlank { "application/pdf" },
            )
            if (uploaded.uri.isBlank()) {
                throw HomeInlineAiException(HomeInlineAiError.Unknown("Gemini did not return a usable PDF file link."))
            }
            dao.upsertLibraryAiFileCache(
                LibraryAiFileCacheEntity(
                    attachmentId = attachment.id,
                    provider = HomeAiProvider.GEMINI.name,
                    fileResourceName = uploaded.name,
                    fileUri = uploaded.uri,
                    mimeType = uploaded.mimeType.ifBlank { attachment.mimeType },
                    displayName = uploaded.displayName.ifBlank { attachment.fileName },
                    localPath = attachment.localPath,
                    sizeBytes = attachment.sizeBytes,
                    uploadedAt = now,
                    lastVerifiedAt = now,
                    expiresAt = uploaded.expirationTimeMs,
                ),
            )
            GeminiFileReference(
                fileUri = uploaded.uri,
                mimeType = uploaded.mimeType.ifBlank { attachment.mimeType },
            )
        }
    }

    suspend fun clearGeminiPdfFileCache(items: List<HomeAiAttachableItem>) = withContext(Dispatchers.IO) {
        val pdfIds = items.filter { it.type == HomeAiAttachableType.Pdf }.map { it.id }
        if (pdfIds.isNotEmpty()) dao.deleteLibraryAiFileCaches(pdfIds)
    }

    suspend fun saveThread(
        threadId: String?,
        messages: List<HomeInlineAiMessage>,
        modelId: String,
    ): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = threadId ?: UUID.randomUUID().toString()
        val firstUserMessage = messages.firstOrNull { it.role == HomeInlineAiRole.User }
        val lastAssistantMessage = messages.lastOrNull { it.role == HomeInlineAiRole.Assistant }
        val attachedTitles = messages
            .flatMap { it.attachedTitles }
            .distinct()
        dao.insertHistory(
            HomeChatHistoryEntity(
                id = id,
                userQuery = firstUserMessage?.text.orEmpty(),
                assistantAnswer = lastAssistantMessage?.text.orEmpty(),
                attachedTitles = JSONArray(attachedTitles).toString(),
                modelId = modelId,
                createdAt = firstUserMessage?.timestamp ?: now,
                updatedAt = now,
                messagesJson = messages.toMessagesJson(),
            ),
        )
        dao.pruneHistory()
        id
    }

    fun estimateContextChars(contexts: List<HomeAiContextItem>): Int =
        HomeInlineAiPromptBuilder.estimatePayloadChars(contexts)

    fun maskedKeyLabel(provider: HomeAiProvider): String = client.maskedKeyLabel(provider)

    fun providerStatuses(): List<HomeAiProviderStatus> = client.providerStatuses()

    fun resolvedModelId(provider: HomeAiProvider, modelMode: HomeAiModelMode): String =
        client.resolveModelId(provider, modelMode)

    fun isLikelyStaleGeminiFileError(error: HomeInlineAiError): Boolean =
        client.isLikelyStaleGeminiFileError(error)

    suspend fun recentHistory(): List<HomeInlineAiHistoryItem> = withContext(Dispatchers.IO) {
        dao.recentHistory().map { it.toHistoryItem() }
    }

    suspend fun historyById(id: String): HomeChatHistoryEntity? = withContext(Dispatchers.IO) {
        dao.historyById(id)
    }

    fun messagesForHistory(entity: HomeChatHistoryEntity): List<HomeInlineAiMessage> =
        parseMessages(entity.messagesJson).ifEmpty {
            val attachedTitles = parseAttachedTitles(entity.attachedTitles)
            listOf(
                HomeInlineAiMessage(
                    id = "${entity.id}-user",
                    role = HomeInlineAiRole.User,
                    text = entity.userQuery,
                    attachedTitles = attachedTitles,
                    timestamp = entity.createdAt,
                ),
                HomeInlineAiMessage(
                    id = "${entity.id}-assistant",
                    role = HomeInlineAiRole.Assistant,
                    text = entity.assistantAnswer,
                    attachedTitles = attachedTitles,
                    timestamp = entity.updatedAt,
                ),
            ).filter { it.text.isNotBlank() }
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
        createdAt = updatedAt,
    )

    private fun parseAttachedTitles(raw: String): List<String> = runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index -> array.optString(index) }.filter { it.isNotBlank() }
    }.getOrDefault(emptyList())

    private fun String.toPreview(max: Int): String {
        val singleLine = replace(Regex("\\s+"), " ").trim()
        return if (singleLine.length <= max) singleLine else singleLine.take(max - 1).trimEnd() + "…"
    }

    private fun List<HomeInlineAiMessage>.toMessagesJson(): String =
        JSONArray(
            map { message ->
                org.json.JSONObject()
                    .put("id", message.id)
                    .put("role", message.role.name)
                    .put("text", message.text)
                    .put("attachedTitles", JSONArray(message.attachedTitles))
                    .put("timestamp", message.timestamp)
            },
        ).toString()

    private fun parseMessages(raw: String): List<HomeInlineAiMessage> = runCatching {
        val array = JSONArray(raw)
        List(array.length()) { index ->
            val item = array.optJSONObject(index) ?: return@List null
            val role = runCatching { HomeInlineAiRole.valueOf(item.optString("role")) }.getOrNull()
                ?: return@List null
            HomeInlineAiMessage(
                id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                role = role,
                text = item.optString("text"),
                attachedTitles = parseAttachedTitles(item.optJSONArray("attachedTitles")?.toString().orEmpty()),
                timestamp = item.optLong("timestamp", System.currentTimeMillis()),
            )
        }.filterNotNull()
    }.getOrDefault(emptyList())

    private fun String.escapeLike(): String =
        replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

    private companion object {
        const val CacheExpirySafetyWindowMs = 10L * 60L * 1000L
    }
}
