package com.myvault.app.data.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class ShamelaResearchProvider @Inject constructor(
    private val mcpClient: ShamelaMcpClient,
) : ResearchProvider {
    override suspend fun search(request: ResearchSearchRequest): ResearchSearchResult = withContext(Dispatchers.Default) {
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
        ResearchSearchResult(
            query = structured.optString("query").ifBlank { query },
            totalHits = structured.numberOrNull("total_hits")?.toInt(),
            sources = sources,
            hasMore = structured.optBoolean("has_more", false),
            nextOffset = structured.numberOrNull("next_offset")?.toInt(),
            caveats = structured.optJSONArray("caveats").orEmptyStrings(),
            elapsedMillis = elapsedMillis,
        )
    }

    suspend fun sourceContext(source: ResearchSource): ResearchSourceContext = withContext(Dispatchers.Default) {
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
            add(
                parseContextPage(current, isCurrent = true).copy(
                    body = bodyParts.joinToString("\n").cleanShamelaText().take(MaxSourceContextCharacters),
                ),
            )
            current.positiveInt("prev_page_id")?.let { previousId ->
                add(parseContextPage(getPage(source.bookId, previousId).structuredContent(), isCurrent = false))
            }
            current.positiveInt("next_page_id")?.let { nextId ->
                add(parseContextPage(getPage(source.bookId, nextId).structuredContent(), isCurrent = false))
            }
        }
        ResearchSourceContext(
            source = source,
            pages = pages,
            citationText = current.firstText("citation") ?: source.citationText,
        )
    }

    suspend fun groundingSource(
        source: ResearchSource,
        includeAdjacentContext: Boolean = false,
    ): ResearchSource = withContext(Dispatchers.Default) {
        require(source.bookId > 0 && source.pageId > 0) { "Source location is unavailable." }
        val current = getPage(source.bookId, source.pageId, bodyPart = 1).structuredContent()
        val bodyParts = mutableListOf(current.optString("body"))
        val totalParts = current.numberOrNull("body_total_parts")?.toInt()?.coerceIn(1, MaxCurrentPageParts) ?: 1
        for (part in 2..totalParts) {
            bodyParts += getPage(source.bookId, source.pageId, bodyPart = part)
                .structuredContent()
                .optString("body")
        }
        val fullPassage = when (source.provenanceType) {
            ResearchProvenance.Footnote -> current.optString("foot")
            ResearchProvenance.Comment -> current.optString("comment")
            else -> bodyParts.joinToString("\n")
        }.cleanShamelaText().take(MaxGroundingPageCharacters)
        val surroundingContext = if (includeAdjacentContext) {
            buildList {
                current.positiveInt("prev_page_id")?.let { previousId ->
                    getPage(source.bookId, previousId).structuredContent().optString("body")
                        .cleanShamelaText().take(MaxAdjacentPageCharacters).takeIf(String::isNotBlank)?.let(::add)
                }
                current.positiveInt("next_page_id")?.let { nextId ->
                    getPage(source.bookId, nextId).structuredContent().optString("body")
                        .cleanShamelaText().take(MaxAdjacentPageCharacters).takeIf(String::isNotBlank)?.let(::add)
                }
            }.joinToString("\n\n").takeIf(String::isNotBlank)
        } else {
            source.surroundingContext
        }
        source.copy(
            arabicPassage = fullPassage.ifBlank { source.arabicPassage },
            part = current.firstText("part") ?: source.part,
            printedPage = current.firstText("printed_page") ?: source.printedPage,
            citationText = current.firstText("citation") ?: source.citationText,
            matchedExcerpt = source.matchedExcerpt ?: source.arabicPassage,
            surroundingContext = surroundingContext,
        )
    }

    suspend fun verifyQuote(rawQuote: String): QuoteVerificationResult = withContext(Dispatchers.Default) {
        val quote = rawQuote.trim().trim('"', '\'', '«', '»').trim()
        require(quote.length >= MinQuoteCharacters) { "Enter an Arabic quotation to verify." }
        require(quote.length <= MaxQueryCharacters) { "Quotations are limited to $MaxQueryCharacters characters." }
        val verification = mcpClient.callTool(
            name = VerifyQuoteTool,
            arguments = JSONObject()
                .put("quote", quote)
                .put("limit", MaxVerificationResults)
                .put("response_format", "json"),
        ).structuredContent()
        val status = verification.optString("status").trim().lowercase()
        val verifiedSources = parseNestedResearchSources(verification, System.currentTimeMillis())
            .distinctBy(ResearchSource::sourceId)
            .take(MaxVerificationResults)
            .map { source -> runCatching { groundingSource(source, includeAdjacentContext = true) }.getOrElse { source } }
        if (verifiedSources.isNotEmpty() || status in setOf("partial", "unverifiable")) {
            return@withContext QuoteVerificationResult(
                quote = quote,
                classification = parseQuoteVerificationClassification(status),
                sources = verifiedSources,
                totalHits = verification.numberOrNull("total_count")?.toInt(),
            )
        }
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
            return@withContext QuoteVerificationResult(
                quote = quote,
                classification = QuoteVerificationClassification.Exact,
                sources = exactSources,
                totalHits = exact.numberOrNull("total_hits")?.toInt(),
            )
        }
        val similar = search(ResearchSearchRequest(query = quote, limit = MaxVerificationResults))
        QuoteVerificationResult(
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
    ): ScholarResearchEvidence = withContext(Dispatchers.Default) {
        val cleanQuery = query.trim().take(MaxQueryCharacters)
        val cleanScholar = scholarName.trim().take(MaxScholarNameCharacters)
        require(cleanQuery.isNotBlank()) { "A comparison topic is required." }
        require(cleanScholar.isNotBlank()) { "A scholar name is required." }
        val author = resolveAuthor(cleanScholar)
            ?: return@withContext ScholarResearchEvidence(cleanScholar, null, null, emptyList())
        val authorId = author.positiveInt("author_id")
            ?: return@withContext ScholarResearchEvidence(cleanScholar, author.firstText("author_name", "name"), null, emptyList())
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
        ScholarResearchEvidence(
            requestedScholar = cleanScholar,
            resolvedScholar = resolvedName,
            authorId = authorId,
            sources = parseSearchSources(result, limit.coerceIn(1, MaxScholarResults)),
        )
    }

    suspend fun searchScholarCorpus(
        queries: List<String>,
        scholarName: String,
        retrievalPass: ResearchRetrievalPass = ResearchRetrievalPass.Primary,
        limit: Int = MaxResearchCandidates,
    ): ScholarResearchEvidence = withContext(Dispatchers.Default) {
        val cleanScholar = scholarName.trim().take(MaxScholarNameCharacters)
        require(cleanScholar.isNotBlank()) { "A scholar name is required." }
        val author = resolveAuthor(cleanScholar)
            ?: return@withContext ScholarResearchEvidence(cleanScholar, null, null, emptyList())
        val authorId = author.positiveInt("author_id")
            ?: return@withContext ScholarResearchEvidence(
                cleanScholar,
                author.firstText("author_name", "name"),
                null,
                emptyList(),
            )
        val resolvedName = author.firstText("author_name", "name") ?: cleanScholar
        val retrievedAt = System.currentTimeMillis()
        val sources = buildList {
            queries.map(String::trim).filter(String::isNotBlank).distinct().take(MaxResearchQueries).forEach { query ->
                val pageMatches = searchAuthorPages(query.take(MaxQueryCharacters), authorId, retrievedAt)
                addAll(pageMatches)
                val proximityQuery = query
                    .split(Regex("\\s+"))
                    .filter(String::isNotBlank)
                    .take(MaxProximityTerms)
                    .joinToString(" ")
                if (pageMatches.isEmpty() && proximityQuery.split(' ').size >= 2) {
                    addAll(searchAuthorProximity(proximityQuery, authorId, retrievedAt))
                }
            }
        }
            .filter { it.provenanceType == ResearchProvenance.AuthorBody }
            .distinctBy(ResearchSource::sourceId)
            .map { source ->
                source.copy(
                    targetScholar = resolvedName,
                    retrievalPass = retrievalPass,
                )
            }
            .take(limit.coerceIn(1, MaxResearchCandidates))
        ScholarResearchEvidence(cleanScholar, resolvedName, authorId, sources)
    }

    suspend fun searchSecondaryAttributions(
        queries: List<String>,
        targetScholar: String,
        limit: Int = MaxSecondaryCandidates,
    ): List<ResearchSource> = withContext(Dispatchers.Default) {
        val cleanScholar = targetScholar.trim().take(MaxScholarNameCharacters)
        require(cleanScholar.isNotBlank()) { "A target scholar is required." }
        val retrievedAt = System.currentTimeMillis()
        buildList {
            queries.map(String::trim).filter(String::isNotBlank).distinct().take(MaxSecondaryQueries).forEach { query ->
                val cleanQuery = query.take(MaxQueryCharacters)
                val search = mcpClient.callTool(
                    name = SearchTool,
                    arguments = JSONObject()
                        .put("query", cleanQuery)
                        .put("limit", MaxResultsPerResearchQuery)
                        .put("offset", 0)
                        .put("options", JSONObject().put("search_in", JSONArray().put("body")))
                        .put("response_format", "json"),
                ).structuredContent()
                val pageMatches = search.optJSONArray("results").orEmptyObjects()
                    .flatMap { parseShamelaSearchResult(it, retrievedAt) }
                addAll(pageMatches)
                val proximityQuery = cleanQuery.split(Regex("\\s+"))
                    .filter(String::isNotBlank)
                    .take(MaxSecondaryProximityTerms)
                    .joinToString(" ")
                if (pageMatches.isEmpty() && proximityQuery.split(' ').size >= 2) {
                    val near = mcpClient.callTool(
                        name = ExactPhraseTool,
                        arguments = JSONObject()
                            .put("query", proximityQuery)
                            .put("mode", "near")
                            .put("distance", SecondaryProximityDistance)
                            .put("search_in", JSONArray().put("body"))
                            .put("limit", MaxResultsPerResearchQuery)
                            .put("offset", 0)
                            .put("response_format", "json"),
                    ).structuredContent()
                    addAll(
                        near.optJSONArray("results").orEmptyObjects()
                            .flatMap { parseShamelaSearchResult(it, retrievedAt) },
                    )
                }
            }
        }
            .filter { it.provenanceType == ResearchProvenance.AuthorBody }
            .distinctBy(ResearchSource::sourceId)
            .map { source ->
                source.copy(
                    targetScholar = cleanScholar,
                    retrievalPass = ResearchRetrievalPass.SecondaryAttribution,
                )
            }
            .take(limit.coerceIn(1, MaxSecondaryCandidates))
    }

    suspend fun verifyQuoteAtSource(source: ResearchSource, quote: String): ResearchQuoteCheck =
        withContext(Dispatchers.Default) {
            val result = mcpClient.callTool(
                name = VerifyQuoteTool,
                arguments = JSONObject()
                    .put("quote", quote.take(MaxQueryCharacters))
                    .put("book_id", source.bookId)
                    .put("page_id", source.pageId)
                    .put("limit", 3)
                    .put("response_format", "json"),
            ).structuredContent()
            parseQuoteCheckAtSource(result, source)
        }

    suspend fun findExactQuoteInScholarCorpus(
        quote: String,
        scholarName: String,
        limit: Int = MaxPrimaryQuoteMatches,
    ): ScholarResearchEvidence = withContext(Dispatchers.Default) {
        val cleanScholar = scholarName.trim().take(MaxScholarNameCharacters)
        val author = resolveAuthor(cleanScholar)
            ?: return@withContext ScholarResearchEvidence(cleanScholar, null, null, emptyList())
        val authorId = author.positiveInt("author_id")
            ?: return@withContext ScholarResearchEvidence(
                cleanScholar,
                author.firstText("author_name", "name"),
                null,
                emptyList(),
            )
        val resolvedName = author.firstText("author_name", "name") ?: cleanScholar
        val result = mcpClient.callTool(
            name = ExactPhraseTool,
            arguments = JSONObject()
                .put("query", quote.take(MaxQueryCharacters))
                .put("mode", "phrase")
                .put("search_in", JSONArray().put("body"))
                .put("scope", JSONObject().put("author_ids", JSONArray().put(authorId)))
                .put("limit", limit.coerceIn(1, MaxPrimaryQuoteMatches))
                .put("offset", 0)
                .put("response_format", "json"),
        ).structuredContent()
        val sources = parseSearchSources(result, limit.coerceIn(1, MaxPrimaryQuoteMatches)).map { source ->
            source.copy(
                targetScholar = resolvedName,
                retrievalPass = ResearchRetrievalPass.Primary,
            )
        }
        ScholarResearchEvidence(cleanScholar, resolvedName, authorId, sources)
    }

    suspend fun discoverDisagreement(
        topic: String,
        targetScholar: String?,
        limit: Int = MaxDisagreementCandidates,
    ): List<ResearchSource> = withContext(Dispatchers.Default) {
        val boundedTopic = topic.trim().split(Regex("\\s+")).filter(String::isNotBlank).take(3).joinToString(" ")
        if (boundedTopic.isBlank()) return@withContext emptyList()
        val result = mcpClient.callTool(
            name = ConsensusScanTool,
            arguments = JSONObject()
                .put("question", boundedTopic)
                .put("families", JSONArray().put("ijmaa").put("khilaf"))
                .put("distance", 15)
                .put("search_in", JSONArray().put("body"))
                .put("witnesses", 2)
                .put("response_format", "json"),
        ).structuredContent()
        parseNestedResearchSources(result, System.currentTimeMillis())
            .filter { it.provenanceType == ResearchProvenance.AuthorBody }
            .distinctBy(ResearchSource::sourceId)
            .map { source ->
                source.copy(
                    targetScholar = targetScholar,
                    retrievalPass = ResearchRetrievalPass.DisagreementDiscovery,
                )
            }
            .take(limit.coerceIn(1, MaxDisagreementCandidates))
    }

    private suspend fun resolveAuthor(scholarName: String): JSONObject? {
        val candidates = mcpClient.callTool(
            name = ResolveTool,
            arguments = JSONObject()
                .put("query", scholarName)
                .put("type", "author")
                .put("limit", MaxResolvedAuthorCandidates)
                .put("response_format", "json"),
        ).structuredContent().optJSONArray("authors").orEmptyObjects()
        return selectResolvedAuthor(candidates, scholarName)
    }

    private suspend fun searchAuthorPages(
        query: String,
        authorId: Int,
        retrievedAt: Long,
    ): List<ResearchSource> {
        val result = mcpClient.callTool(
            name = SearchTool,
            arguments = JSONObject()
                .put("query", query)
                .put("limit", MaxResultsPerResearchQuery)
                .put("offset", 0)
                .put("scope", JSONObject().put("author_ids", JSONArray().put(authorId)))
                .put("options", JSONObject().put("search_in", JSONArray().put("body")))
                .put("response_format", "json"),
        ).structuredContent()
        return result.optJSONArray("results")
            .orEmptyObjects()
            .flatMap { parseShamelaSearchResult(it, retrievedAt) }
    }

    private suspend fun searchAuthorProximity(
        query: String,
        authorId: Int,
        retrievedAt: Long,
    ): List<ResearchSource> {
        val result = mcpClient.callTool(
            name = ExactPhraseTool,
            arguments = JSONObject()
                .put("query", query)
                .put("mode", "near")
                .put("distance", ResearchProximityDistance)
                .put("search_in", JSONArray().put("body"))
                .put("scope", JSONObject().put("author_ids", JSONArray().put(authorId)))
                .put("limit", MaxResultsPerResearchQuery)
                .put("offset", 0)
                .put("response_format", "json"),
        ).structuredContent()
        return result.optJSONArray("results")
            .orEmptyObjects()
            .flatMap { parseShamelaSearchResult(it, retrievedAt) }
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
        const val VerifyQuoteTool = "shamela_verify_quote"
        const val ConsensusScanTool = "shamela_scan_consensus"
        const val MaxQueryCharacters = 500
        const val MaxSearchResults = 8
        const val MaxVerificationResults = 6
        const val MinQuoteCharacters = 2
        const val MaxScholarResults = 3
        const val MaxScholarNameCharacters = 100
        const val MaxResolvedAuthorCandidates = 3
        const val MaxCurrentPageParts = 3
        const val MaxSourceContextCharacters = 12_000
        const val MaxGroundingPageCharacters = 12_000
        const val MaxAdjacentPageCharacters = 2_000
        const val MaxResearchCandidates = 18
        const val MaxResearchQueries = 8
        const val MaxResultsPerResearchQuery = 5
        const val MaxProximityTerms = 5
        const val ResearchProximityDistance = 12
        const val MaxSecondaryCandidates = 12
        const val MaxSecondaryQueries = 5
        const val MaxSecondaryProximityTerms = 7
        const val SecondaryProximityDistance = 18
        const val MaxPrimaryQuoteMatches = 3
        const val MaxDisagreementCandidates = 4
    }
}

internal fun parseQuoteVerificationClassification(status: String): QuoteVerificationClassification = when (
    status.trim().lowercase()
) {
    "verbatim" -> QuoteVerificationClassification.Exact
    "differs" -> QuoteVerificationClassification.NearExact
    "partial" -> QuoteVerificationClassification.Partial
    "unverifiable" -> QuoteVerificationClassification.Unverifiable
    else -> QuoteVerificationClassification.NotLocated
}

internal fun selectResolvedAuthor(candidates: List<JSONObject>, requestedName: String): JSONObject? {
    val requested = requestedName.normalizedArabicName()
    return candidates.minByOrNull { candidate ->
        val candidateName = candidate.firstText("author_name", "name").orEmpty().normalizedArabicName()
        when {
            candidateName == requested -> 0
            candidateName.endsWith(requested) || requested.endsWith(candidateName) -> 1
            candidateName.contains(requested) || requested.contains(candidateName) -> 2
            else -> 3
        }
    }
}

internal fun parseNestedResearchSources(value: Any?, retrievedAt: Long): List<ResearchSource> = buildList {
    fun visit(node: Any?) {
        when (node) {
            is JSONObject -> {
                if (node.positiveInt("book_id") != null && node.positiveInt("page_id") != null) {
                    addAll(parseShamelaSearchResult(node, retrievedAt))
                }
                node.keys().forEach { key -> visit(node.opt(key)) }
            }
            is JSONArray -> for (index in 0 until node.length()) visit(node.opt(index))
        }
    }
    visit(value)
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

internal fun parseQuoteCheckAtSource(result: JSONObject, source: ResearchSource): ResearchQuoteCheck {
    val status = result.optString("status").trim().lowercase()
    val location = result.optJSONArray("locations").orEmptyObjects().firstOrNull { candidate ->
        candidate.positiveInt("book_id") == source.bookId &&
            candidate.positiveInt("page_id") == source.pageId
    }
    val provenance = location?.provenanceFallback()
    return ResearchQuoteCheck(
        status = status,
        verified = location != null &&
            status in setOf("verbatim", "differs") &&
            provenance == ResearchProvenance.AuthorBody,
        provenance = provenance,
    )
}

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
        result.firstText("snippet", "excerpt", "matched_text", "match", "context", "text", "body", "foot")
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
            matchedExcerpt = passage,
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

private fun String.normalizedArabicName(): String = trim()
    .replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
    .replace('أ', 'ا')
    .replace('إ', 'ا')
    .replace('آ', 'ا')
    .replace('ٱ', 'ا')
    .replace('ى', 'ي')
    .replace(Regex("[^\\p{L}\\p{N}]+"), "")
