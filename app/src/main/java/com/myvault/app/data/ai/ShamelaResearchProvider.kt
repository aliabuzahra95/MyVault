package com.myvault.app.data.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ShamelaResearchProvider @Inject constructor(
    private val mcpClient: ShamelaMcpClient,
) : ResearchProvider {
    override suspend fun search(request: ResearchSearchRequest): ResearchSearchResult {
        val query = request.query.trim()
        require(query.isNotEmpty()) { "Enter a Shamela search query." }
        require(query.length <= MaxQueryCharacters) { "Shamela search is limited to $MaxQueryCharacters characters." }
        val limit = request.limit.coerceIn(1, MaxSearchResults)
        val offset = request.offset.coerceAtLeast(0)
        val startedAt = System.nanoTime()
        val toolResult = mcpClient.callTool(
            name = SearchTool,
            arguments = JSONObject()
                .put("query", query)
                .put("limit", limit)
                .put("offset", offset)
                .put(
                    "options",
                    JSONObject().put("search_in", JSONArray().put("body").put("foot")),
                )
                .put("response_format", "json"),
        )
        val structured = toolResult.structuredContent()
        val retrievedAt = System.currentTimeMillis()
        val sources = structured.optJSONArray("results")
            .orEmptyObjects()
            .flatMap { result -> parseShamelaSearchResult(result, retrievedAt) }
            .take(limit)
        val elapsedMillis = ((System.nanoTime() - startedAt) / 1_000_000.0).roundToInt().toLong()
        return ResearchSearchResult(
            query = structured.optString("query").ifBlank { query },
            totalHits = structured.numberOrNull("total_hits")?.toInt(),
            sources = sources,
            hasMore = structured.optBoolean("has_more", false),
            nextOffset = structured.numberOrNull("next_offset")?.toInt(),
            caveats = structured.optJSONArray("caveats").orEmptyStrings(),
            elapsedMillis = elapsedMillis,
        )
    }

    suspend fun sourceContext(source: ResearchSource): ResearchSourceContext {
        require(source.bookId > 0 && source.pageId > 0) { "Source location is unavailable." }
        val currentResult = getPage(source.bookId, source.pageId, bodyPart = 1)
        val current = currentResult.structuredContent()
        val bodyParts = mutableListOf(current.optString("body"))
        val totalParts = current.numberOrNull("body_total_parts")?.toInt()?.coerceIn(1, MaxCurrentPageParts) ?: 1
        for (part in 2..totalParts) {
            bodyParts += getPage(source.bookId, source.pageId, bodyPart = part)
                .structuredContent()
                .optString("body")
        }
        val pages = buildList {
            current.positiveInt("prev_page_id")?.let { previousId ->
                add(parseContextPage(getPage(source.bookId, previousId).structuredContent(), isCurrent = false))
            }
            add(
                parseContextPage(current, isCurrent = true).copy(
                    body = bodyParts.joinToString("\n").cleanShamelaText().take(MaxSourceContextCharacters),
                ),
            )
            current.positiveInt("next_page_id")?.let { nextId ->
                add(parseContextPage(getPage(source.bookId, nextId).structuredContent(), isCurrent = false))
            }
        }
        return ResearchSourceContext(
            source = source,
            pages = pages,
            citationText = current.firstText("citation") ?: source.citationText,
        )
    }

    suspend fun verifyQuote(rawQuote: String): QuoteVerificationResult {
        val quote = rawQuote.trim().trim('"', '\'', '«', '»').trim()
        require(quote.length >= MinQuoteCharacters) { "Enter an Arabic quotation to verify." }
        require(quote.length <= MaxQueryCharacters) { "Quotations are limited to $MaxQueryCharacters characters." }
        val exact = mcpClient.callTool(
            name = ExactPhraseTool,
            arguments = JSONObject()
                .put("query", quote)
                .put("mode", "phrase")
                .put("search_in", JSONArray().put("body").put("foot"))
                .put("limit", MaxVerificationResults)
                .put("offset", 0)
                .put("response_format", "json"),
        ).structuredContent()
        val exactSources = parseSearchSources(exact, MaxVerificationResults)
        if (exactSources.isNotEmpty()) {
            return QuoteVerificationResult(
                quote = quote,
                classification = QuoteVerificationClassification.Exact,
                sources = exactSources,
                totalHits = exact.numberOrNull("total_hits")?.toInt(),
            )
        }
        val similar = search(ResearchSearchRequest(query = quote, limit = MaxVerificationResults))
        return QuoteVerificationResult(
            quote = quote,
            classification = if (similar.sources.isEmpty()) {
                QuoteVerificationClassification.NotLocated
            } else {
                QuoteVerificationClassification.Similar
            },
            sources = similar.sources,
            totalHits = similar.totalHits,
        )
    }

    suspend fun searchScholar(
        query: String,
        scholarName: String,
        limit: Int = MaxScholarResults,
    ): ScholarResearchEvidence {
        val cleanQuery = query.trim().take(MaxQueryCharacters)
        val cleanScholar = scholarName.trim().take(MaxScholarNameCharacters)
        require(cleanQuery.isNotBlank()) { "A comparison topic is required." }
        require(cleanScholar.isNotBlank()) { "A scholar name is required." }
        val resolved = mcpClient.callTool(
            name = ResolveTool,
            arguments = JSONObject()
                .put("query", cleanScholar)
                .put("type", "author")
                .put("limit", MaxResolvedAuthorCandidates)
                .put("response_format", "json"),
        ).structuredContent()
        val author = resolved.optJSONArray("authors").orEmptyObjects().firstOrNull()
            ?: return ScholarResearchEvidence(cleanScholar, null, null, emptyList())
        val authorId = author.positiveInt("author_id")
            ?: return ScholarResearchEvidence(cleanScholar, author.firstText("author_name", "name"), null, emptyList())
        val resolvedName = author.firstText("author_name", "name") ?: cleanScholar
        val result = mcpClient.callTool(
            name = SearchTool,
            arguments = JSONObject()
                .put("query", cleanQuery)
                .put("limit", limit.coerceIn(1, MaxScholarResults))
                .put("offset", 0)
                .put("scope", JSONObject().put("author_ids", JSONArray().put(authorId)))
                .put("options", JSONObject().put("search_in", JSONArray().put("body").put("foot")))
                .put("response_format", "json"),
        ).structuredContent()
        return ScholarResearchEvidence(
            requestedScholar = cleanScholar,
            resolvedScholar = resolvedName,
            authorId = authorId,
            sources = parseSearchSources(result, limit.coerceIn(1, MaxScholarResults)),
        )
    }

    private suspend fun getPage(bookId: Int, pageId: Int, bodyPart: Int = 1): JSONObject =
        mcpClient.callTool(
            name = SourcePageTool,
            arguments = JSONObject()
                .put("book_id", bookId)
                .put("page_id", pageId)
                .put("body_part", bodyPart)
                .put("keep_html", false)
                .put("response_format", "json"),
        )

    private companion object {
        const val SearchTool = "shamela_search_pages"
        const val SourcePageTool = "shamela_get_page"
        const val ExactPhraseTool = "shamela_search_phrase"
        const val ResolveTool = "shamela_resolve"
        const val MaxQueryCharacters = 500
        const val MaxSearchResults = 8
        const val MaxVerificationResults = 6
        const val MinQuoteCharacters = 2
        const val MaxScholarResults = 3
        const val MaxScholarNameCharacters = 100
        const val MaxResolvedAuthorCandidates = 3
        const val MaxCurrentPageParts = 3
        const val MaxSourceContextCharacters = 12_000
    }
}

private fun parseSearchSources(value: JSONObject, limit: Int): List<ResearchSource> {
    val retrievedAt = System.currentTimeMillis()
    return value.optJSONArray("results")
        .orEmptyObjects()
        .flatMap { result -> parseShamelaSearchResult(result, retrievedAt) }
        .take(limit)
}

internal fun parseContextPage(value: JSONObject, isCurrent: Boolean): ResearchContextPage = ResearchContextPage(
    pageId = value.positiveInt("page_id") ?: throw ResearchProviderException("Shamela page identity is missing."),
    printedPage = value.firstText("printed_page"),
    part = value.firstText("part"),
    body = value.optString("body").cleanShamelaText().take(MaxContextPageSectionCharacters),
    footnote = value.optString("foot").cleanShamelaText().take(MaxContextPageSectionCharacters),
    comment = value.optString("comment").cleanShamelaText().take(MaxContextPageSectionCharacters),
    isCurrent = isCurrent,
)

internal fun parseShamelaSearchResult(result: JSONObject, retrievedAt: Long): List<ResearchSource> {
    val bookId = result.positiveInt("book_id") ?: return emptyList()
    val pageId = result.positiveInt("page_id") ?: return emptyList()
    val bookTitle = result.firstText("book_name", "book_title", "title") ?: return emptyList()
    val sections = listOf(
        "snippet_body" to ResearchProvenance.AuthorBody,
        "snippet_foot" to ResearchProvenance.Footnote,
        "snippet_comment" to ResearchProvenance.Comment,
    ).mapNotNull { (field, provenance) ->
        result.firstText(field)?.let { passage -> provenance to passage }
    }.ifEmpty {
        result.firstText("snippet", "excerpt", "text", "body", "foot")
            ?.let { passage -> listOf(result.provenanceFallback() to passage) }
            .orEmpty()
    }
    return sections.mapNotNull { (provenance, rawPassage) ->
        val passage = rawPassage.cleanShamelaText().take(MaxResearchPassageCharacters)
        if (passage.isBlank()) return@mapNotNull null
        ResearchSource(
            sourceId = "shamela:$bookId:$pageId:${provenance.name.lowercase()}",
            bookId = bookId,
            pageId = pageId,
            bookTitle = bookTitle,
            authorId = result.positiveInt("author_id"),
            authorName = result.firstText("author_name", "author"),
            arabicPassage = passage,
            provenanceType = provenance,
            part = result.firstText("part", "volume"),
            printedPage = result.firstText("printed_page", "page"),
            citationText = result.firstText("citation"),
            retrievedAtEpochMillis = retrievedAt,
        )
    }
}

private fun JSONObject.provenanceFallback(): ResearchProvenance {
    val values = buildList {
        optJSONArray("matched_in")?.let { array ->
            for (index in 0 until array.length()) add(array.optString(index).lowercase())
        }
        firstText("matched_in", "section", "source_type", "provenance")?.lowercase()?.let(::add)
    }
    return when {
        values.any { it == "body" || it.contains("matn") } -> ResearchProvenance.AuthorBody
        values.any { it == "foot" || it.contains("foot") } -> ResearchProvenance.Footnote
        values.any { it == "comment" || it.contains("comment") } -> ResearchProvenance.Comment
        values.any { it == "primary" } -> ResearchProvenance.Primary
        values.any { it == "report" } -> ResearchProvenance.Report
        else -> ResearchProvenance.Unknown
    }
}

private const val MaxResearchPassageCharacters = 1_500
private const val MaxContextPageSectionCharacters = 4_500

internal fun JSONObject.structuredContent(): JSONObject {
    optJSONObject("structuredContent")?.let { return it }
    val content = optJSONArray("content")
    for (index in 0 until (content?.length() ?: 0)) {
        val text = content?.optJSONObject(index)?.optString("text").orEmpty().trim()
        val candidate = text.removePrefix("```json").removeSuffix("```").trim()
        runCatching { JSONObject(candidate) }.getOrNull()?.let { return it }
    }
    throw ResearchProviderException("Shamela returned no structured research result.")
}

private fun JSONObject.firstText(vararg names: String): String? = names.firstNotNullOfOrNull { name ->
    if (!has(name) || isNull(name)) return@firstNotNullOfOrNull null
    when (val value = opt(name)) {
        is String -> value.trim().takeIf(String::isNotEmpty)
        is Number -> value.toString()
        else -> null
    }
}

private fun JSONObject.positiveInt(name: String): Int? = numberOrNull(name)?.toInt()?.takeIf { it > 0 }

private fun JSONObject.numberOrNull(name: String): Number? = when (val value = opt(name)) {
    is Number -> value
    is String -> value.toDoubleOrNull()
    else -> null
}

private fun JSONArray?.orEmptyObjects(): List<JSONObject> = buildList {
    for (index in 0 until (this@orEmptyObjects?.length() ?: 0)) {
        this@orEmptyObjects?.optJSONObject(index)?.let(::add)
    }
}

private fun JSONArray?.orEmptyStrings(): List<String> = buildList {
    for (index in 0 until (this@orEmptyStrings?.length() ?: 0)) {
        this@orEmptyStrings?.optString(index)?.takeIf(String::isNotBlank)?.let(::add)
    }
}

private fun String.cleanShamelaText(): String = this
    .replace(Regex("<mark[^>]*>"), "")
    .replace("</mark>", "")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ")
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace(Regex("[ \\t]+"), " ")
    .replace(Regex("\\n{3,}"), "\n\n")
    .trim()
