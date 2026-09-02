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

enum class ResearchProvenance(val label: String) {
    AuthorBody("Author text"),
    Footnote("Footnote / editor text"),
    Comment("Commentary"),
    Report("Reported attribution"),
    Primary("Primary source"),
    Unknown("Provenance unavailable"),
}

class ResearchProviderException(message: String, cause: Throwable? = null) : Exception(message, cause)
