package com.myvault.app.data.ai

interface ResearchProvider {
    suspend fun search(request: ResearchSearchRequest): ResearchSearchResult
}

data class ResearchSearchRequest(
    val query: String,
    val limit: Int = 6,
    val offset: Int = 0,
)

data class ResearchSearchResult(
    val query: String,
    val totalHits: Int?,
    val sources: List<ResearchSource>,
    val hasMore: Boolean,
    val nextOffset: Int?,
    val caveats: List<String>,
    val elapsedMillis: Long,
)

data class ResearchSource(
    val provider: String = "Shamela",
    val sourceId: String,
    val bookId: Int,
    val pageId: Int,
    val bookTitle: String,
    val authorId: Int?,
    val authorName: String?,
    val arabicPassage: String,
    val provenanceType: ResearchProvenance,
    val part: String?,
    val printedPage: String?,
    val citationText: String?,
    val retrievedAtEpochMillis: Long,
)

data class ResearchSourceContext(
    val source: ResearchSource,
    val pages: List<ResearchContextPage>,
    val citationText: String?,
)

data class ResearchContextPage(
    val pageId: Int,
    val printedPage: String?,
    val part: String?,
    val body: String,
    val footnote: String,
    val comment: String,
    val isCurrent: Boolean,
)

data class QuoteVerificationResult(
    val quote: String,
    val classification: QuoteVerificationClassification,
    val sources: List<ResearchSource>,
    val totalHits: Int?,
)

enum class QuoteVerificationClassification(val label: String) {
    Exact("Exact quotation found"),
    Similar("Similar wording found"),
    NotLocated("Not located"),
}

data class ScholarResearchEvidence(
    val requestedScholar: String,
    val resolvedScholar: String?,
    val authorId: Int?,
    val sources: List<ResearchSource>,
)

enum class ResearchProvenance(val label: String) {
    AuthorBody("Author text"),
    Footnote("Footnote / editor text"),
    Comment("Commentary"),
    Report("Reported attribution"),
    Primary("Primary source"),
    Unknown("Provenance unavailable"),
}

class ResearchProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
