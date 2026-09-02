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
    val matchedExcerpt: String? = null,
    val surroundingContext: String? = null,
    val targetScholar: String? = null,
    val retrievalPass: ResearchRetrievalPass = ResearchRetrievalPass.General,
    val evidenceClass: ResearchEvidenceClass = ResearchEvidenceClass.Unclassified,
    val passageRole: ResearchPassageRole = ResearchPassageRole.Ambiguous,
    val evidenceConfidence: ResearchEvidenceConfidence = ResearchEvidenceConfidence.Low,
)

enum class ResearchRetrievalPass {
    General,
    Primary,
    AlternatePrimary,
    SecondaryAttribution,
    Contradiction,
    DisagreementDiscovery,
}

enum class ResearchEvidenceClass(val label: String, val rank: Int) {
    DirectPrimary("Direct source", 700),
    DirectPrimaryContextual("Direct contextual source", 600),
    SecondaryDirectQuote("Quoted by another scholar", 500),
    SecondaryExplicitAttribution("Explicit attribution", 450),
    SecondaryPositionReport("Reported position", 400),
    LaterSecondaryDiscussion("Later discussion", 300),
    EditorialApparatus("Editorial material", 100),
    Unclassified("Source under review", 0),
}

enum class ResearchPassageRole(val label: String) {
    DirectExplicitView("Explicit preferred view"),
    DirectContextualView("Contextual preferred view"),
    ReportOfMadhhab("Madhhab report"),
    ReportOfOtherScholar("Report of another scholar"),
    SecondaryDirectQuote("Direct quotation in another work"),
    SecondaryExplicitAttribution("Explicit attribution in another work"),
    SecondaryPositionReport("Reported position in another work"),
    Objection("Objection"),
    RejectedView("Rejected view"),
    Counterargument("Counterargument"),
    HadithQuotation("Hadith quotation"),
    QuranQuotation("Qur'an quotation"),
    EditorFootnote("Editor footnote"),
    EditorComment("Editor comment"),
    IndexMetadata("Index metadata"),
    Ambiguous("Ambiguous"),
}

enum class ResearchEvidenceConfidence(val label: String) {
    High("High confidence"),
    MediumHigh("Medium-high confidence"),
    Medium("Medium confidence"),
    Low("Low confidence"),
}

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

data class ResearchQuoteCheck(
    val status: String,
    val verified: Boolean,
    val provenance: ResearchProvenance?,
)

enum class QuoteVerificationClassification(val label: String) {
    Exact("Exact quotation found"),
    NearExact("Quotation found with orthographic differences"),
    Partial("Only part of the quotation was located"),
    Similar("Similar wording found"),
    NotLocated("Not located"),
    Unverifiable("Not verifiable in the downloaded Shamela corpus"),
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
