package com.myvault.app.data.ai

import android.util.Log
import com.myvault.app.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

internal class ResearchTraceRecorder(private val question: String) {
    fun intent(plan: ResearchSearchPlan) = record(
        "intent",
        JSONObject()
            .put("question", question.take(MaxLoggedText))
            .put("topic", plan.topic)
            .put("domain", plan.domain)
            .put("answerType", plan.answerType)
            .put("scholars", JSONArray(plan.scholars))
            .put("primaryQueries", JSONArray(plan.queries))
            .put("alternateQueries", JSONArray(plan.alternateQueries))
            .put("attributionQueries", JSONArray(plan.attributionQueries))
            .put("contradictionQueries", JSONArray(plan.contradictionQueries))
            .put(
                "tools",
                JSONArray(
                    listOf(
                        "shamela_resolve",
                        "shamela_search_pages",
                        "shamela_search_phrase",
                        "shamela_get_page",
                        "shamela_verify_quote",
                        "shamela_scan_consensus",
                    ),
                ),
            ),
    )

    fun candidates(sources: List<ResearchSource>) = record(
        "candidates",
        JSONArray().apply {
            sources.forEach { source ->
                put(
                    JSONObject()
                        .put("id", source.sourceId)
                        .put("book", source.bookTitle)
                        .put("author", source.authorName)
                        .put("pass", source.retrievalPass.name)
                        .put("provenance", source.provenanceType.name),
                )
            }
        },
    )

    fun cacheHit(sources: List<ResearchSource>) = record(
        "packet_cache_hit",
        JSONObject().put("sourceIds", JSONArray(sources.map(ResearchSource::sourceId))),
    )

    fun comparisonPlan(plan: ScholarComparisonPlan) = record(
        "comparison_plan",
        JSONObject()
            .put("question", question.take(MaxLoggedText))
            .put("topic", plan.topic)
            .put("scholars", JSONArray(plan.scholars)),
    )

    fun comparisonEvidence(
        scholar: String,
        sources: List<ResearchSource>,
        findings: List<VerifiedResearchFinding>,
    ) = record(
        "comparison_evidence",
        JSONObject()
            .put("scholar", scholar)
            .put("sourceIds", JSONArray(sources.map(ResearchSource::sourceId)))
            .put(
                "findings",
                JSONArray().apply {
                    findings.forEach { finding ->
                        put(
                            JSONObject()
                                .put("sourceId", finding.sourceId)
                                .put("class", finding.evidenceClass.name)
                                .put("role", finding.passageRole.name)
                                .put("relation", finding.relation.name)
                                .put("confidence", finding.confidence.name),
                        )
                    }
                },
            ),
    )

    fun evidence(candidates: List<ResearchSource>, set: ResearchTraceEvidenceSet) = record(
        "evidence",
        JSONObject()
            .put(
                "selected",
                JSONArray().apply {
                    set.findings.forEach { finding ->
                        put(
                            JSONObject()
                                .put("sourceId", finding.sourceId)
                                .put("class", finding.evidenceClass.name)
                                .put("role", finding.passageRole.name)
                                .put("relation", finding.relation.name)
                                .put("confidence", finding.confidence.name)
                                .put("quote", finding.exactQuote.take(MaxLoggedText)),
                        )
                    }
                },
            )
            .put(
                "rejected",
                JSONArray().apply {
                    val selectedIds = set.findings.map(VerifiedResearchFinding::sourceId).toSet()
                    candidates.filterNot { it.sourceId in selectedIds }.forEach { source ->
                        put(
                            JSONObject()
                                .put("sourceId", source.sourceId)
                                .put(
                                    "reason",
                                    "No directly relevant exact quotation survived provenance classification and source verification.",
                                ),
                        )
                    }
                },
            ),
    )

    fun packet(sources: List<ResearchSource>, findings: List<VerifiedResearchFinding>) = record(
        "evidence_packet",
        JSONObject()
            .put("sourceIds", JSONArray(sources.map(ResearchSource::sourceId)))
            .put(
                "claims",
                JSONArray().apply {
                    findings.forEach { finding ->
                        put(
                            JSONObject()
                                .put("sourceId", finding.sourceId)
                                .put("meaning", finding.meaning.take(MaxLoggedText))
                                .put("relation", finding.relation.name),
                        )
                    }
                },
            ),
    )

    fun synthesis(answer: String, citedEvidenceIds: Set<String>) = record(
        "synthesis",
        JSONObject()
            .put("citedEvidenceIds", JSONArray(citedEvidenceIds.toList()))
            .put("answerPreview", answer.take(MaxLoggedText)),
    )

    fun final(verdict: String, sourceIds: List<String>) = record(
        "final",
        JSONObject().put("verdict", verdict).put("sourceIds", JSONArray(sourceIds)),
    )

    private fun record(event: String, payload: Any) {
        if (!BuildConfig.DEBUG) return
        Log.d(Tag, JSONObject().put("event", event).put("payload", payload).toString())
    }

    companion object {
        const val Tag = "MyVaultResearchTrace"
        const val MaxLoggedText = 800

        fun toolCall(name: String, arguments: JSONObject) {
            if (!BuildConfig.DEBUG) return
            val safeArguments = JSONObject().apply {
                arguments.keys().forEach { key ->
                    val value = arguments.opt(key)
                    put(
                        key,
                        when (value) {
                            is String -> value.take(MaxLoggedText)
                            is JSONObject, is JSONArray, is Number, is Boolean -> value
                            else -> value?.toString()?.take(MaxLoggedText)
                        },
                    )
                }
            }
            Log.d(
                Tag,
                JSONObject()
                    .put("event", "mcp_tool_call")
                    .put("payload", JSONObject().put("tool", name).put("arguments", safeArguments))
                    .toString(),
            )
        }
    }
}

internal data class ResearchTraceEvidenceSet(
    val findings: List<VerifiedResearchFinding>,
)
