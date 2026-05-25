package com.myvault.app.data.repository

data class AiPromptRequest(
    val systemInstruction: String,
    val prompt: String,
    val temperature: Float,
    val maxOutputTokens: Int,
)

private enum class AiOutputType {
    ChatAnswerPlainText,
    EditorOutputHtml,
}

/**
 * MyVault AI prompt builder.
 *
 * Design goal:
 * - Normal chat must feel fast, natural, and adaptive.
 * - Deep theological/academic prompting must only be used for genuinely deep modes.
 * - Editor actions must return clean HTML only.
 * - StructureOnly is AI-powered and must preserve exact wording while improving structure.
 */
object AiPromptBuilder {
    private val NormalChatSystemInstruction = """
        You are MyVault AI, an intelligent Islamic study assistant inside a private notes app.

        Answer naturally, clearly, and helpfully.
        Use the current note context when relevant, but do not force every answer to become a long academic essay.
        Adapt the length to the user's request: brief questions get brief answers, deep requests get deeper answers.
        Be academically honest: do not invent quotations, references, book citations, or scholar attributions.
        When unsure, say so plainly.
        Default language is English unless the user clearly asks otherwise.
    """.trimIndent()

    private val FastNoteSystemInstruction = """
        You are MyVault AI, a fast note assistant for a private Islamic study vault.

        Help the user understand, summarise, explain, simplify, or study the current note.
        Be clear and useful without unnecessary rigidity.
        Preserve Arabic terms when they matter and explain them in English.
        Do not fabricate quotations or references.
        Do not over-structure the answer unless structure improves clarity.
    """.trimIndent()

    private val DeepAnalysisSystemInstruction = """
        You are MyVault AI, a careful Islamic studies and theology analysis assistant.

        Use this deeper mode for advanced analysis, theological comparison, objections/responses, and serious study.
        Represent Athari, Ashari, Maturidi, Mu'tazili, Jahmi, Karrami, and other positions accurately when relevant.
        Do not strawman opposing views.
        Distinguish between:
        - what the note directly says
        - wider explanation
        - inference
        - possible objection
        - possible response
        - uncertainty

        Do not invent quotations, references, page numbers, or attributions.
        Preserve Arabic technical terms when precision matters, and explain them clearly in English.
    """.trimIndent()

    private val EditorHtmlSystemInstruction = """
        You are MyVault AI, an editor inside a private notes app.

        Return simple clean HTML only.
        Do not include commentary, markdown, code fences, explanations, or prefaces.
        Build polished study-note structure with clear hierarchy, semantic grouping, readable paragraph flow, consistent lists, and useful blockquotes.
        Preserve the user's meaning and wording unless the selected mode explicitly asks you to improve clarity.
        Allowed tags: <h1>, <h2>, <h3>, <p>, <ul>, <ol>, <li>, <blockquote>, <strong>, <em>, <br>, <span data-color="red">, <span data-color="blue">.
        Prefer compact semantic lists over repeated short standalone paragraphs when the content is grouped, sequential, comparative, evidential, or revision-oriented.
        Avoid unsupported tags, CSS, deeply nested spans, inline styling inside headings, malformed HTML, markdown syntax, giant dense paragraphs, and stretched line/blank-line/line formatting.
        Use red only for Qur'anic verses when clearly identifiable.
        Use blue only for scholar quotations when clearly identifiable.
    """.trimIndent()

    private val SelectedTextSystemInstruction = """
        You are MyVault AI, helping with selected text from a private note.

        Focus primarily on the selected text.
        Use surrounding note context only when it helps.
        Answer naturally and directly.
        Preserve Arabic terms where relevant.
        Do not invent quotations, references, or scholar attributions.
    """.trimIndent()

    fun build(
        action: NoteAiAction,
        title: String,
        body: String,
        question: String,
        history: List<NoteAiConversationTurn> = emptyList(),
        provider: NoteAiProvider = NoteAiProvider.Gemini,
        model: NoteAiModel = NoteAiModel.Gemini25Flash,
    ): AiPromptRequest {
        val safeTitle = title.ifBlank { "Untitled note" }
        val safeQuestion = question.ifBlank { action.defaultUserRequest() }
        val outputType = outputTypeFor(action)
        val systemInstruction = systemInstructionFor(action)
        val noteContext = noteContextFor(safeTitle, body.scopedForAction(action))
        val safeHistory = historyFor(action, history)

        val prompt = """
            Mode: ${action.displayName}
            Output type: ${outputType.name}

            ${outputInstructions(outputType)}

            ${modeInstructions(action)}

            Current note:
            $noteContext

            Recent conversation:
            ${safeHistory.toPromptHistory(maxCharsPerTurn = historyCharBudgetFor(action))}

            User request:
            <request>
            $safeQuestion
            </request>
        """.trimIndent()

        return AiPromptRequest(
            systemInstruction = systemInstruction,
            prompt = prompt,
            temperature = temperatureFor(action, provider, model),
            maxOutputTokens = maxOutputTokensFor(action, provider, model),
        )
    }

    fun buildIntelligentStructurePlan(
        title: String,
        body: String,
        provider: NoteAiProvider = NoteAiProvider.Gemini,
        model: NoteAiModel = NoteAiModel.Gemini25Flash,
    ): AiPromptRequest {
        val safeTitle = title.ifBlank { "Untitled note" }
        val prompt = """
            Create a concise internal structure plan for this note.
            This plan is not shown to the user.

            Rules:
            - Do not rewrite the note.
            - Identify the topic, major sections, and natural heading hierarchy.
            - Mention any Qur'anic verses or scholar quotes that need careful preservation.
            - Keep it short.
            - Plain text only.

            ${noteContextFor(safeTitle, body.takeMiddleAware(10_000))}
        """.trimIndent()

        return AiPromptRequest(
            systemInstruction = EditorHtmlSystemInstruction,
            prompt = prompt,
            temperature = 0.15f,
            maxOutputTokens = 900,
        )
    }

    fun buildSelectedText(
        action: SelectedTextAiAction,
        title: String,
        body: String,
        selectedText: String,
        question: String = "",
        history: List<NoteAiConversationTurn> = emptyList(),
        provider: NoteAiProvider = NoteAiProvider.Gemini,
        model: NoteAiModel = NoteAiModel.Gemini25Flash,
    ): AiPromptRequest {
        val safeTitle = title.ifBlank { "Untitled note" }
        val safeQuestion = question.ifBlank { action.defaultSelectedTextRequest() }
        val prompt = """
            Selected text mode: ${action.displayName}
            Output type: ${AiOutputType.ChatAnswerPlainText.name}

            ${outputInstructions(AiOutputType.ChatAnswerPlainText)}

            ${selectedTextInstructions(action)}

            Current note context:
            <note>
            <title>$safeTitle</title>
            <body>
            ${body.scopedAroundSelection(selectedText, action.selectedTextContextBudget())}
            </body>
            </note>

            Selected text:
            <selected_text>
            $selectedText
            </selected_text>

            User request:
            <request>
            $safeQuestion
            </request>

            Recent conversation:
            ${history.takeLast(2).toPromptHistory(maxCharsPerTurn = 900)}
        """.trimIndent()

        return AiPromptRequest(
            systemInstruction = SelectedTextSystemInstruction,
            prompt = prompt,
            temperature = selectedTextTemperatureFor(action, provider, model),
            maxOutputTokens = selectedTextMaxOutputTokensFor(action, provider, model),
        )
    }

    fun buildSuggestionPrefill(suggestion: AiSuggestion, selectedTextMode: Boolean = false): String =
        when (suggestion) {
            AiSuggestion.Explain -> if (selectedTextMode) {
                "Explain this selected text clearly."
            } else {
                "Explain this note clearly."
            }
            AiSuggestion.Simplify -> if (selectedTextMode) {
                "Simplify this selected text without losing the technical meaning."
            } else {
                "Simplify this note without losing the important meaning."
            }
            AiSuggestion.Terminology -> if (selectedTextMode) {
                "Explain the terminology in this selected text."
            } else {
                "Explain the terminology in this note."
            }
            AiSuggestion.Compare -> "Compare this position with..."
            AiSuggestion.RelatedConcepts -> if (selectedTextMode) {
                "What related concepts help explain this selected text?"
            } else {
                "What related concepts help explain this note?"
            }
            AiSuggestion.ObjectionResponse -> if (selectedTextMode) {
                "What is a strong objection to this selected idea, and how could it be answered?"
            } else {
                "What is a strong objection to the main idea in this note, and how could it be answered?"
            }
            AiSuggestion.StudyQuestions -> if (selectedTextMode) {
                "Generate study questions from this selected text."
            } else {
                "Generate study questions from this note."
            }
        }

    fun wrapSelectedTextQuestion(question: String, selectedText: String?): String {
        val selected = selectedText?.trim().orEmpty()
        val safeQuestion = question.trim()
        if (selected.isBlank()) return safeQuestion
        return """
            Selected text:
            <selected_text>
            $selected
            </selected_text>

            User request:
            $safeQuestion
        """.trimIndent()
    }

    private fun systemInstructionFor(action: NoteAiAction): String =
        when (action.promptMode()) {
            PromptMode.NormalChat -> NormalChatSystemInstruction
            PromptMode.FastNoteAction -> FastNoteSystemInstruction
            PromptMode.DeepAnalysis -> DeepAnalysisSystemInstruction
            PromptMode.EditorHtml -> EditorHtmlSystemInstruction
            PromptMode.LocalOnly -> FastNoteSystemInstruction
        }

    private fun outputTypeFor(action: NoteAiAction): AiOutputType =
        when (action) {
            NoteAiAction.IntelligentStructure,
            NoteAiAction.CleanFormat,
            NoteAiAction.FormatNote,
            NoteAiAction.StructureOnly,
            -> AiOutputType.EditorOutputHtml
            else -> AiOutputType.ChatAnswerPlainText
        }

    private fun outputInstructions(type: AiOutputType): String =
        when (type) {
            AiOutputType.ChatAnswerPlainText -> """
                Answer in readable plain text.
                Do not use markdown tables or code fences unless the user asks for code.
                Use headings and bullets only when they genuinely improve clarity.
                Keep the answer as short or detailed as the request deserves.
            """.trimIndent()
            AiOutputType.EditorOutputHtml -> """
                Return HTML only.
                No markdown.
                No explanation before or after.
                No code fences.
                Use only editor-safe HTML tags.
                Use a clean heading hierarchy, readable paragraphs, proper lists, and blockquotes for obvious quotations.
                Infer the format from the content itself: bullets for grouped concepts, numbered lists for sequences/stages, subheadings for topic transitions, and blockquotes for definitions, quoted passages, or important conclusions.
                Prefer <ul> or <ol> whenever related points would otherwise become several short paragraphs.
                If a paragraph introduces points with words like includes, such as, particularly, assumes, examples, reasons, consequences, consists of, or breaks down into, format the following related points as a compact list.
                Keep related sentences together when they form one idea; do not mechanically turn every sentence into its own paragraph.
                Avoid unsupported tags, CSS, markdown remnants, malformed nesting, inline spans inside headings, inconsistent heading jumps, and excessive blank vertical space.
            """.trimIndent()
        }

    private fun modeInstructions(action: NoteAiAction): String =
        when (action) {
            NoteAiAction.QuickSummary -> """
                Give a quick summary of the note.
                Focus on the main point and key takeaways.
            """.trimIndent()
            NoteAiAction.DeepSummary -> """
                Give a useful structured summary.
                Include the main argument, key terms, and important distinctions.
                Do not turn it into a full deep analysis unless needed.
            """.trimIndent()
            NoteAiAction.StudyTutor -> """
                Teach the note step by step.
                Explain terms, structure the idea clearly, and include a short final takeaway.
            """.trimIndent()
            NoteAiAction.DeepAnalysis -> """
                Analyse deeply and carefully.
                Separate direct note content from wider explanation.
                Include objections/responses where useful.
                Be fair to all schools or views discussed.
            """.trimIndent()
            NoteAiAction.Ask,
            NoteAiAction.GeneralAsk,
            -> """
                Answer the user's question naturally.
                Use the note as context when relevant.
                Do not force a long answer unless the question asks for one.
            """.trimIndent()
            NoteAiAction.ExplainNote -> """
                Explain the note clearly.
                Define key terms and make the idea easier to understand.
            """.trimIndent()
            NoteAiAction.IntelligentStructure -> """
                Intelligently organise this note into clean HTML.
                Preserve meaning and important wording, but you may improve structure, headings, grouping, and readability.
                Use headings, paragraphs, lists, and blockquotes where helpful.
            """.trimIndent()
            NoteAiAction.CleanFormat,
            NoteAiAction.FormatNote,
            -> """
                Clean and format this note into readable HTML.
                Preserve meaning.
                Remove obvious clutter only if it is formatting noise.
            """.trimIndent()
            NoteAiAction.StructureOnly -> """
                Structure this note into polished, editor-safe HTML for premium study-note readability.

                Core rule:
                Preserve the wording and meaning of the body content as exactly as possible while aggressively improving organisation, hierarchy, spacing, scanability, academic presentation, and efficient visual density.

                You should:
                - create strong headings/subheadings from existing phrases, terms, or concepts already present in the note
                - group related paragraphs into coherent sections
                - strongly prefer compact <ul> lists for grouped concepts, assumptions, distinctions, categories, objections, evidences, consequences, examples, and repeated points
                - strongly prefer compact <ol> lists for sequences, logical progressions, stages, syllogisms, arguments, premises/conclusions, methods, or ordered flows
                - use nested <ul> lists for sub-points when a point branches into smaller related points
                - convert obvious label groups into lists, e.g. Universal / Particular / Conclusion, Claim / Evidence / Response, Objection / Answer
                - treat short standalone lines under one topic as likely list items, not separate paragraphs
                - create definition-style blocks where the note defines a term or principle
                - create example/evidence sections where the note contains examples or proofs
                - use <strong> for important existing terms
                - use <em> sparingly for emphasis
                - use <blockquote> for obvious quotations, cited passages, definitions, or important conclusions already present in the note
                - split giant dense prose into readable paragraphs only where actual prose is needed
                - keep related list items close together and compress formatting where isolated paragraphs would make the note unnecessarily long
                - preserve Arabic, transliteration, names, quotations, evidences, and technical terms exactly

                Do not:
                - paraphrase, summarise, simplify, or rewrite theology/arguments
                - add new arguments, explanations, examples, references, conclusions, names, schools, labels, or framing
                - remove meaningful content
                - write in second person unless the note already does
                - leave obvious grouped items as line / blank space / line / blank space paragraphs
                - create one paragraph per short line unless it is genuinely connected prose

                Return only clean HTML. No explanation.
            """.trimIndent()
        }

    private fun selectedTextInstructions(action: SelectedTextAiAction): String =
        when (action) {
            SelectedTextAiAction.Ask -> "Answer the user's question about the selected text."
            SelectedTextAiAction.Explain -> "Explain the selected text clearly."
            SelectedTextAiAction.Simplify -> "Simplify the selected text without losing the technical meaning."
            SelectedTextAiAction.Expand -> "Expand on the selected idea with useful context."
            SelectedTextAiAction.Terminology -> "Explain the terminology in the selected text."
            SelectedTextAiAction.RelatedConcepts -> "List and explain related concepts that clarify the selected text."
            SelectedTextAiAction.ComparePositions -> "Compare the selected idea with another relevant position, fairly and clearly."
            SelectedTextAiAction.ObjectionResponse -> "Give a strong objection to the selected idea and a possible response."
            SelectedTextAiAction.StudyQuestions -> "Generate useful study questions from the selected text."
        }

    private fun noteContextFor(title: String, body: String): String = """
        <note>
        <title>$title</title>
        <body>
        $body
        </body>
        </note>
    """.trimIndent()

    private enum class PromptMode {
        NormalChat,
        FastNoteAction,
        DeepAnalysis,
        EditorHtml,
        LocalOnly,
    }

    private fun NoteAiAction.promptMode(): PromptMode =
        when (this) {
            NoteAiAction.GeneralAsk,
            NoteAiAction.Ask,
            -> PromptMode.NormalChat
            NoteAiAction.QuickSummary,
            NoteAiAction.DeepSummary,
            NoteAiAction.ExplainNote,
            -> PromptMode.FastNoteAction
            NoteAiAction.StudyTutor,
            NoteAiAction.DeepAnalysis,
            -> PromptMode.DeepAnalysis
            NoteAiAction.IntelligentStructure,
            NoteAiAction.CleanFormat,
            NoteAiAction.FormatNote,
            -> PromptMode.EditorHtml
            NoteAiAction.StructureOnly -> PromptMode.EditorHtml
        }

    private fun NoteAiAction.defaultUserRequest(): String =
        when (this) {
            NoteAiAction.QuickSummary -> "Summarise this note briefly."
            NoteAiAction.DeepSummary -> "Summarise this note clearly."
            NoteAiAction.StudyTutor -> "Teach me this note step by step."
            NoteAiAction.DeepAnalysis -> "Analyse this note deeply."
            NoteAiAction.Ask -> "Answer my question about this note."
            NoteAiAction.ExplainNote -> "Explain this note clearly."
            NoteAiAction.GeneralAsk -> "Answer my question."
            NoteAiAction.StructureOnly -> "Structure this note into clean HTML. Preserve body wording and meaning as exactly as possible, but strongly prefer compact bullet/numbered lists for grouped points, arguments, categories, examples, and revision-friendly structure."
            NoteAiAction.IntelligentStructure -> "Intelligently structure this note."
            NoteAiAction.CleanFormat -> "Clean and organise this note."
            NoteAiAction.FormatNote -> "Format this note."
        }

    private fun SelectedTextAiAction.defaultSelectedTextRequest(): String =
        when (this) {
            SelectedTextAiAction.Ask -> "Answer my question about this selected text."
            SelectedTextAiAction.Explain -> "Explain this selected text."
            SelectedTextAiAction.Simplify -> "Simplify this selected text."
            SelectedTextAiAction.Expand -> "Expand on this selected text."
            SelectedTextAiAction.Terminology -> "Explain the terminology in this selected text."
            SelectedTextAiAction.RelatedConcepts -> "Explain related concepts."
            SelectedTextAiAction.ComparePositions -> "Compare this with another position."
            SelectedTextAiAction.ObjectionResponse -> "Give an objection and response."
            SelectedTextAiAction.StudyQuestions -> "Generate study questions."
        }

    private fun historyFor(action: NoteAiAction, history: List<NoteAiConversationTurn>): List<NoteAiConversationTurn> =
        when (action.promptMode()) {
            PromptMode.NormalChat -> history.takeLast(6)
            PromptMode.DeepAnalysis -> history.takeLast(4)
            PromptMode.FastNoteAction -> history.takeLast(2)
            PromptMode.EditorHtml,
            PromptMode.LocalOnly,
            -> emptyList()
        }

    private fun historyCharBudgetFor(action: NoteAiAction): Int =
        when (action.promptMode()) {
            PromptMode.NormalChat -> 900
            PromptMode.DeepAnalysis -> 1_200
            else -> 600
        }

    private fun temperatureFor(action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel): Float =
        when (action) {
            NoteAiAction.StructureOnly -> 0.12f
            NoteAiAction.CleanFormat,
            NoteAiAction.FormatNote,
            -> 0.18f
            NoteAiAction.IntelligentStructure -> 0.22f
            else -> when (action.promptMode()) {
                PromptMode.NormalChat -> 0.45f
                PromptMode.FastNoteAction -> 0.28f
                PromptMode.DeepAnalysis -> if (model.isDeepModel || provider == NoteAiProvider.ChatGPT) 0.55f else 0.42f
                PromptMode.EditorHtml -> 0.18f
                PromptMode.LocalOnly -> 0.0f
            }
        }.coerceIn(0.0f, 1.0f)

    private fun maxOutputTokensFor(action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel): Int {
        val base = when (action) {
            NoteAiAction.QuickSummary -> 700
            NoteAiAction.DeepSummary -> 1_400
            NoteAiAction.Ask,
            NoteAiAction.GeneralAsk,
            -> 1_600
            NoteAiAction.ExplainNote -> 1_400
            NoteAiAction.StudyTutor -> 2_200
            NoteAiAction.DeepAnalysis -> 3_200
            NoteAiAction.IntelligentStructure -> 4_500
            NoteAiAction.CleanFormat -> 3_000
            NoteAiAction.FormatNote -> 2_400
            NoteAiAction.StructureOnly -> 4_500
        }
        val multiplier = when {
            model.isDeepModel -> 1.25f
            provider == NoteAiProvider.ChatGPT -> 1.1f
            else -> 1.0f
        }
        return (base * multiplier).toInt()
    }

    private fun selectedTextTemperatureFor(action: SelectedTextAiAction, provider: NoteAiProvider, model: NoteAiModel): Float =
        when (action) {
            SelectedTextAiAction.Ask,
            SelectedTextAiAction.Simplify,
            SelectedTextAiAction.Terminology,
            SelectedTextAiAction.StudyQuestions,
            -> 0.26f
            SelectedTextAiAction.Explain,
            SelectedTextAiAction.Expand,
            SelectedTextAiAction.RelatedConcepts,
            SelectedTextAiAction.ComparePositions,
            SelectedTextAiAction.ObjectionResponse,
            -> 0.32f
        }

    private fun selectedTextMaxOutputTokensFor(action: SelectedTextAiAction, provider: NoteAiProvider, model: NoteAiModel): Int {
        val base = when (action) {
            SelectedTextAiAction.Ask -> 1_400
            SelectedTextAiAction.Simplify -> 800
            SelectedTextAiAction.StudyQuestions -> 1_000
            SelectedTextAiAction.Terminology,
            SelectedTextAiAction.RelatedConcepts,
            -> 1_300
            SelectedTextAiAction.Explain,
            SelectedTextAiAction.Expand,
            SelectedTextAiAction.ObjectionResponse,
            -> 1_600
            SelectedTextAiAction.ComparePositions -> 2_200
        }
        val multiplier = when {
            model.isDeepModel -> 1.2f
            provider == NoteAiProvider.ChatGPT -> 1.1f
            else -> 1.0f
        }
        return (base * multiplier).toInt()
    }

    private fun String.scopedForAction(action: NoteAiAction): String =
        when (action.promptMode()) {
            PromptMode.NormalChat -> takeMiddleAware(8_000)
            PromptMode.FastNoteAction -> takeMiddleAware(7_000)
            PromptMode.DeepAnalysis -> takeMiddleAware(12_000)
            PromptMode.EditorHtml -> if (action == NoteAiAction.StructureOnly) takeMiddleAware(18_000) else takeMiddleAware(14_000)
            PromptMode.LocalOnly -> takeMiddleAware(12_000)
        }

    private fun String.takeMiddleAware(maxChars: Int): String {
        val clean = trim()
        if (clean.length <= maxChars) return clean
        val headLength = (maxChars * 0.65f).toInt()
        val tailLength = maxChars - headLength
        return buildString {
            append(clean.take(headLength).trimEnd())
            append("\n\n[Middle of note trimmed for speed.]\n\n")
            append(clean.takeLast(tailLength).trimStart())
        }
    }
}

private fun SelectedTextAiAction.selectedTextContextBudget(): Int =
    when (this) {
        SelectedTextAiAction.Simplify,
        SelectedTextAiAction.Terminology,
        SelectedTextAiAction.StudyQuestions,
        -> 1_200
        SelectedTextAiAction.Ask,
        SelectedTextAiAction.Explain,
        -> 2_000
        SelectedTextAiAction.Expand,
        SelectedTextAiAction.RelatedConcepts,
        SelectedTextAiAction.ComparePositions,
        SelectedTextAiAction.ObjectionResponse,
        -> 3_000
    }

private fun String.scopedAroundSelection(selectedText: String, maxChars: Int): String {
    val clean = trim()
    if (clean.length <= maxChars) return clean
    val selected = selectedText.trim().take(400)
    val index = if (selected.isNotBlank()) clean.indexOf(selected, ignoreCase = true) else -1
    if (index < 0) return clean.takeMiddleAwareForSelectedText(maxChars)
    val sideBudget = ((maxChars - selected.length).coerceAtLeast(600)) / 2
    val start = (index - sideBudget).coerceAtLeast(0)
    val end = (index + selectedText.length + sideBudget).coerceAtMost(clean.length)
    return buildString {
        if (start > 0) append("[Earlier note context trimmed for speed.]\n\n")
        append(clean.substring(start, end).trim())
        if (end < clean.length) append("\n\n[Later note context trimmed for speed.]")
    }
}

private fun String.takeMiddleAwareForSelectedText(maxChars: Int): String {
    val clean = trim()
    if (clean.length <= maxChars) return clean
    val headLength = (maxChars * 0.65f).toInt()
    val tailLength = maxChars - headLength
    return buildString {
        append(clean.take(headLength).trimEnd())
        append("\n\n[Middle of note trimmed for speed.]\n\n")
        append(clean.takeLast(tailLength).trimStart())
    }
}

private fun List<NoteAiConversationTurn>.toPromptHistory(maxCharsPerTurn: Int): String {
    if (isEmpty()) return "No recent conversation."
    return joinToString("\n\n") { turn ->
        val role = when (turn.role) {
            NoteAiChatRole.User -> "User"
            NoteAiChatRole.Assistant -> "Assistant"
        }
        "<$role>\n${turn.content.trim().take(maxCharsPerTurn)}\n</$role>"
    }
}
