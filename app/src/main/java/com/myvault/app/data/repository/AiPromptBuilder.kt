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
    """.trimIndent()

    private val DeepAnalysisSystemInstruction = """
        You are MyVault AI, a careful Islamic studies and theology analysis assistant.

        Use this deeper mode for advanced analysis, theological comparison, objections/responses, and serious study.
        Represent Athari, Ashari, Maturidi, Mu'tazili, Jahmi, Karrami, and other positions accurately when relevant.
        Do not strawman opposing views.
        Do not invent quotations, references, page numbers, or attributions.
        Preserve Arabic technical terms when precision matters, and explain them clearly in English.
        Write naturally and fluidly. Do not force robotic templates or repetitive bullet points unless the content demands it.
    """.trimIndent()


    private val StructurePlanSystemInstruction = """
        You are an internal structural planning assistant for MyVault, a private study-note app.

        Produce a concise plain-text planning note only.
        Do not output HTML, markdown tables, code fences, or user-facing formatted content.
        Do not rewrite, summarise, delete, or replace the user's note text.
        Identify only the natural topic flow, major sections, and hierarchy so the later formatter can preserve all content.
    """.trimIndent()

    private val EditorHtmlSystemInstruction = """
        You are MyVault AI, an editor inside a private notes app.

        Return simple clean HTML only.
        Do not include commentary, markdown, code fences, explanations, or prefaces.
        Build polished study-note structure with clear hierarchy, semantic grouping, readable paragraph flow, consistent lists, and useful blockquotes.
        Preserve the user's meaning and wording unless the selected mode explicitly asks you to improve clarity.
        Allowed tags: <h1>, <h2>, <h3>, <p>, <ul>, <ol>, <li>, <blockquote>, <strong>, <em>, <br>, <span data-color="red">, <span data-color="blue">, <span dir="rtl">, <span dir="ltr">.
        Prefer compact <ul> bullet lists over repeated short standalone paragraphs when the content is grouped, comparative, evidential, categorical, explanatory, or revision-oriented.
        Use <ol> only for true ordered sequences: steps, chronology, explicit First/Second/Third structures, syllogisms, or premise-to-conclusion chains.
        Avoid unsupported tags, CSS, deeply nested spans, inline styling inside headings, malformed HTML, markdown syntax, giant dense paragraphs, and stretched line/blank-line/line formatting.
        Preserve Arabic, Qur'anic text, transliterations, names, and technical terms exactly. Never translate, remove, or paraphrase Arabic text.
        Use red only for Qur'anic verses when clearly identifiable.
        Use blue only for scholar quotations when clearly identifiable.
    """.trimIndent()

    private val LosslessEditorHtmlSystemInstruction = """
        You are MyVault AI, acting as a professional document formatter inside a private notes app.

        Return simple clean HTML only.
        Do not include commentary, markdown, code fences, explanations, or prefaces.
        Your task is presentation, not editing.

        Core law:
        Preserve the user's original content losslessly. Every original word, sentence, paragraph, quotation, Arabic phrase, reference, citation, code line, and idea must remain present exactly as written. You may move content into cleaner HTML blocks, headings, lists, and blockquotes, but you must not rewrite the content itself.

        You may add short structural headings or subheadings when they improve navigation, but added headings must not introduce new claims, conclusions, explanations, references, or wording that changes the note's meaning.

        Allowed tags: <h1>, <h2>, <h3>, <p>, <ul>, <ol>, <li>, <blockquote>, <strong>, <em>, <br>, <span data-color="red">, <span data-color="blue">, <span dir="rtl">, <span dir="ltr">.
        Build polished study-note structure with clear hierarchy, semantic grouping, readable paragraph flow, consistent lists, and useful blockquotes.
        Prefer compact <ul> bullet lists over repeated short standalone paragraphs when the original content is naturally grouped.
        Use <ol> only for true ordered sequences already present in the source: steps, chronology, explicit First/Second/Third structures, syllogisms, or premise-to-conclusion chains.
        Preserve Arabic, Qur'anic text, transliterations, names, quotations, evidences, technical terms, spelling, punctuation, diacritics, references, citations, markdown/code meaning, and word order exactly.
        Never translate, remove, normalize, simplify, summarise, paraphrase, merge away, deduplicate, or "improve" the user's wording.
        Avoid unsupported tags, CSS, malformed HTML, markdown syntax, giant dense paragraphs, excessive blank space, and inline spans inside headings.
        Use red only for Qur'anic verses when clearly identifiable.
        Use blue only for scholar quotations when clearly identifiable.

        Before returning the final HTML, silently verify that each original sentence or line still appears verbatim in the output text after HTML tags are removed.
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
        val noteContext = noteContextFor(safeTitle, body.scopedForAction(action, safeQuestion))
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

            ${noteContextFor(safeTitle, body)}
        """.trimIndent()

        return AiPromptRequest(
            systemInstruction = StructurePlanSystemInstruction,
            prompt = prompt,
            temperature = 0.05f,
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
            ${history.takeLast(2).toPromptHistory(maxCharsPerTurn = 4_000)}
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
        if (action == NoteAiAction.StructureOnly) {
            LosslessEditorHtmlSystemInstruction
        } else when (action.promptMode()) {
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
                Infer the format from the content itself: bullets for grouped concepts, subheadings for topic transitions, and blockquotes for definitions, quoted passages, or important conclusions.
                Use <ul> as the default list type for grouped study-note content.
                Use <ol> only for actual steps, chronology, explicit First/Second/Third structures, syllogisms, procedures, or premise-to-conclusion chains.
                If a paragraph introduces points with words like includes, such as, particularly, assumes, examples, reasons, consequences, consists of, or breaks down into, format the following related points as a compact list.
                Keep related sentences together when they form one idea; do not mechanically turn every sentence into its own paragraph.
                Preserve Arabic text exactly as written. Do not translate, transliterate, normalize, remove diacritics, or rewrite Arabic.
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
                Structure this note into polished, editor-safe HTML for premium study-note readability while preserving the source text losslessly.

                Absolute preservation rule:
                Every original word, sentence, paragraph, phrase, point, example, quote, Arabic phrase, transliteration, definition, evidence, reference, citation, code line, markdown meaning, and repeated wording must remain present in the output exactly as written. Do not delete, summarise, shorten, merge away, deduplicate, paraphrase, simplify, expand, infer, replace, or compress away content. Your job is to wrap and organise the existing content, not to rewrite it.

                Formatting goal:
                Format the note as if a professional document formatter copied the original text into a clean document and spent time improving the presentation without editing a single sentence.
                Aim for the same visual organisation quality as Intelligent Structure: excellent hierarchy, clean sectioning, clear grouping, compact lists, readable paragraphs, blockquotes for obvious quotations, and premium study-note presentation.

                You should:
                - create short headings/subheadings from existing phrases, terms, or concepts already present in the note, or neutral labels such as Definition, Evidence, Objection, Response, Example, Notes, Key Point, Comparison, or Conclusion when appropriate
                - group related paragraphs into coherent sections
                - use compact <ul> lists as the default for grouped concepts, assumptions, distinctions, categories, objections, evidences, consequences, examples, and related study points
                - use <ol> only when the original content is genuinely ordered: explicit steps, chronology, first/second/third structures, procedures, or clear premise-to-conclusion chains
                - prefer <ul> over <ol> when unsure
                - use nested <ul> lists for sub-points when a point branches into smaller related points
                - treat labels like Example, Definition, Assumption, Critique, Response, Implication, Observation, Evidence, and Key Point as subheadings or strong labels, not numbered items
                - keep natural prose as prose when sentences clearly flow together
                - convert obvious grouped short points into compact bullet lists
                - keep related list items directly stacked without blank paragraphs between them
                - use <strong> for important existing terms only where helpful
                - use <em> sparingly
                - use <blockquote> only for obvious quotations, cited passages, or important statements already present in the note
                - preserve Arabic, Qur'anic text, transliteration, names, quotations, evidences, technical terms, spelling, diacritics, punctuation, and word order exactly
                - preserve mixed Arabic/English text without translating or transliterating it
                - split extremely long paragraphs into smaller paragraphs only at natural sentence boundaries and only without changing any wording
                - keep code-like lines, citations, references, page numbers, URLs, and quoted text exactly intact

                Do not:
                - remove content under any circumstance
                - paraphrase, summarise, simplify, or rewrite theology/arguments
                - add new arguments, explanations, examples, references, conclusions, names, schools, labels, or framing
                - replace the user's wording with your own wording
                - delete repeated wording merely because it seems redundant
                - create numbered lists for unrelated concepts, categories, definitions, examples, objections, explanations, or normal grouped study points
                - leave obvious grouped items as line / blank space / line / blank space paragraphs
                - wrap every short sentence as a separate paragraph when it belongs in a compact list
                - change quotations, Arabic text, citations, references, markdown/code meaning, or technical wording

                Output rules:
                - Return only clean editor-safe HTML.
                - No markdown.
                - No code fences.
                - No explanation.
                - Do not include <html>, <body>, <section>, <article>, CSS, or unsupported styles.
                - Before finalising, silently check that every original sentence or line can still be found verbatim after removing HTML tags.
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
            NoteAiAction.StructureOnly -> "Format this note into polished editor-safe HTML like a professional document formatter. Preserve every original word, sentence, paragraph, quote, Arabic phrase, citation, reference, code line, and repeated wording exactly. Improve headings, spacing, hierarchy, bullet formatting, sectioning, blockquotes, and readability only. Do not delete, summarise, paraphrase, rewrite, simplify, merge away, expand, infer, or add content."
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
            PromptMode.DeepAnalysis -> history.takeLast(5)
            PromptMode.FastNoteAction -> history.takeLast(3)
            PromptMode.EditorHtml,
            PromptMode.LocalOnly,
            -> emptyList()
        }

    private fun historyCharBudgetFor(action: NoteAiAction): Int =
        when (action.promptMode()) {
            PromptMode.NormalChat -> 2_500
            PromptMode.DeepAnalysis -> 4_000
            else -> 2_000
        }

    private fun temperatureFor(action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel): Float =
        when (action) {
            NoteAiAction.StructureOnly -> 0.05f
            NoteAiAction.CleanFormat,
            NoteAiAction.FormatNote,
            -> 0.18f
            NoteAiAction.IntelligentStructure -> 0.22f
            else -> when (action.promptMode()) {
                PromptMode.NormalChat -> 0.45f
                PromptMode.FastNoteAction -> 0.28f
                PromptMode.DeepAnalysis -> if (model.isDeepModel || provider == NoteAiProvider.ChatGPT || provider == NoteAiProvider.Kimi) 0.55f else 0.42f
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
            NoteAiAction.StructureOnly -> 16_000
        }
        val multiplier = when {
            model.isDeepModel -> 1.25f
            provider == NoteAiProvider.ChatGPT || provider == NoteAiProvider.Kimi -> 1.1f
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
            provider == NoteAiProvider.ChatGPT || provider == NoteAiProvider.Kimi -> 1.1f
            else -> 1.0f
        }
        return (base * multiplier).toInt()
    }

    private fun String.scopedForAction(action: NoteAiAction, question: String): String =
        when (action.promptMode()) {
            PromptMode.NormalChat -> scopedAroundQuestion(question, maxChars = 22_000, fallbackChars = 18_000)
            PromptMode.FastNoteAction -> scopedAroundQuestion(question, maxChars = 24_000, fallbackChars = 20_000)
            PromptMode.DeepAnalysis -> scopedAroundQuestion(question, maxChars = 42_000, fallbackChars = 36_000)
            PromptMode.EditorHtml -> if (action == NoteAiAction.StructureOnly) this else takeMiddleAware(32_000)
            PromptMode.LocalOnly -> takeMiddleAware(12_000)
        }

    private fun String.scopedAroundQuestion(question: String, maxChars: Int, fallbackChars: Int): String {
        val clean = trim()
        if (clean.length <= maxChars) return clean
        val terms = question.keyContextTerms()
        if (terms.isEmpty()) return clean.takeMiddleAware(fallbackChars)

        val paragraphs = clean.split(Regex("\\n{2,}"))
        var bestStart = -1
        var bestScore = 0
        var cursor = 0
        paragraphs.forEach { paragraph ->
            val lower = paragraph.lowercase()
            val score = terms.count { term -> lower.contains(term) }
            if (score > bestScore) {
                bestScore = score
                bestStart = cursor
            }
            cursor += paragraph.length + 2
        }

        if (bestStart < 0 || bestScore == 0) return clean.takeMiddleAware(fallbackChars)

        val sideBudget = maxChars / 2
        val start = (bestStart - sideBudget).coerceAtLeast(0)
        val end = (bestStart + sideBudget).coerceAtMost(clean.length)
        return buildString {
            if (start > 0) append("[Earlier note context trimmed for relevance.]\n\n")
            append(clean.substring(start, end).trim())
            if (end < clean.length) append("\n\n[Later note context trimmed for relevance.]")
        }
    }

    private fun String.keyContextTerms(): List<String> {
        val stopWords = setOf(
            "about", "after", "again", "also", "answer", "before", "being", "could", "does",
            "explain", "from", "have", "into", "note", "question", "should", "study", "that",
            "this", "what", "when", "where", "which", "with", "would",
        )
        return lowercase()
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 4 && it !in stopWords }
            .distinct()
            .take(8)
    }

    private fun String.takeMiddleAware(maxChars: Int): String {
        val clean = trim()
        if (clean.length <= maxChars) return clean
        val headLength = (maxChars * 65) / 100
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
        -> 10_000
        SelectedTextAiAction.Ask,
        SelectedTextAiAction.Explain,
        -> 15_000
        SelectedTextAiAction.Expand,
        SelectedTextAiAction.RelatedConcepts,
        SelectedTextAiAction.ComparePositions,
        SelectedTextAiAction.ObjectionResponse,
        -> 20_000
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
    val headLength = (maxChars * 65) / 100
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
