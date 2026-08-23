package com.myvault.app.data.formatting

/**
 * Prompts used only by the retained note-formatting tools.
 * Conversational and tutoring prompt modes intentionally do not exist here.
 */
internal object NoteFormattingPromptBuilder {
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


    private val EditorOutputInstructions = """
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

    fun build(request: NoteFormattingRequest, question: String = ""): NoteFormattingPrompt {
        val safeTitle = request.title.ifBlank { "Untitled note" }
        val safeQuestion = question.ifBlank { request.action.defaultFormattingRequest() }
        val scopedBody = request.body.scopedForFormattingPrompt(request.action)
        val prompt = """
            Mode: ${request.action.displayName}
            Output type: EditorOutputHtml

            $EditorOutputInstructions

            ${modeInstructions(request.action)}

            Current note:
            <note>
            <title>$safeTitle</title>
            <body>
            $scopedBody
            </body>
            </note>

            User request:
            <request>
            $safeQuestion
            </request>
        """.trimIndent()

        return NoteFormattingPrompt(
            systemInstruction = systemInstructionFor(request.action),
            prompt = prompt,
            temperature = temperatureFor(request.action),
            maxOutputTokens = maxOutputTokensFor(request),
        )
    }

    fun buildPlan(request: NoteFormattingRequest): NoteFormattingPrompt {
        val safeTitle = request.title.ifBlank { "Untitled note" }
        val prompt = """
            Create a concise internal structure plan for this note.
            This plan is not shown to the user.

            Rules:
            - Do not rewrite the note.
            - Identify the topic, major sections, and natural heading hierarchy.
            - Mention any Qur'anic verses or scholar quotes that need careful preservation.
            - Keep it short.
            - Plain text only.

            <note>
            <title>$safeTitle</title>
            <body>
            ${request.body}
            </body>
            </note>
        """.trimIndent()

        return NoteFormattingPrompt(
            systemInstruction = StructurePlanSystemInstruction,
            prompt = prompt,
            temperature = 0.05f,
            maxOutputTokens = 900,
        )
    }

    private fun systemInstructionFor(action: NoteFormattingAction): String =
        if (action == NoteFormattingAction.StructureOnly || action == NoteFormattingAction.IntelligentStructure) {
            LosslessEditorHtmlSystemInstruction
        } else {
            EditorHtmlSystemInstruction
        }

    private fun modeInstructions(action: NoteFormattingAction): String =
        when (action) {
            NoteFormattingAction.IntelligentStructure -> """
                Intelligently organise this note into polished, editor-safe HTML without editing the user's writing.

                Absolute lossless rule:
                Every original word and every occurrence of that word must remain present exactly as written. Preserve every sentence, paragraph, phrase, example, quotation, Arabic phrase, transliteration, definition, evidence, reference, citation, URL, code-like line, and repeated point. Never delete, summarise, shorten, paraphrase, rewrite, simplify, merge away, deduplicate, replace, or correct the user's wording.

                You may improve only the presentation and organisation:
                - add concise, neutral headings and subheadings
                - group intact related passages into coherent sections
                - convert intact grouped points into lists or tables represented with supported HTML
                - use blockquotes for quotations already present
                - add only very small connective labels when genuinely necessary, and never use them to replace source text

                The original text is the source of truth. Added structure must be additive. Before returning the HTML, silently compare it with the source and verify that every original line and word is still present.
            """.trimIndent()
            NoteFormattingAction.CleanFormat,
            NoteFormattingAction.FormatNote,
            -> """
                Clean and format this note into readable HTML.
                Preserve meaning.
                Remove obvious clutter only if it is formatting noise.
            """.trimIndent()
            NoteFormattingAction.StructureOnly -> """
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

    private fun NoteFormattingAction.defaultFormattingRequest(): String = when (this) {
        NoteFormattingAction.StructureOnly -> "Format this note into polished editor-safe HTML like a professional document formatter. Preserve every original word, sentence, paragraph, quote, Arabic phrase, citation, reference, code line, and repeated wording exactly. Improve headings, spacing, hierarchy, bullet formatting, sectioning, blockquotes, and readability only. Do not delete, summarise, paraphrase, rewrite, simplify, merge away, expand, infer, or add content."
        NoteFormattingAction.IntelligentStructure -> "Intelligently structure this note as a lossless document formatter. Preserve every original word and every repeated occurrence exactly as written. Add hierarchy, headings, grouping, lists, and other presentation improvements only. Do not delete, summarise, shorten, paraphrase, rewrite, simplify, merge away, deduplicate, replace, or correct any source wording."
        NoteFormattingAction.CleanFormat -> "Clean and organise this note."
        NoteFormattingAction.FormatNote -> "Format this note."
    }

    private fun temperatureFor(action: NoteFormattingAction): Float = when (action) {
        NoteFormattingAction.StructureOnly -> 0.05f
        NoteFormattingAction.CleanFormat,
        NoteFormattingAction.FormatNote,
        -> 0.18f
        NoteFormattingAction.IntelligentStructure -> 0.08f
    }

    private fun maxOutputTokensFor(request: NoteFormattingRequest): Int {
        val base = when (request.action) {
            NoteFormattingAction.IntelligentStructure -> 16_000
            NoteFormattingAction.CleanFormat -> 3_000
            NoteFormattingAction.FormatNote -> 2_400
            NoteFormattingAction.StructureOnly -> 16_000
        }
        val multiplier = when {
            request.model == NoteFormattingModel.Smart -> 1.25f
            request.provider == NoteFormattingProvider.ChatGPT || request.provider == NoteFormattingProvider.Kimi -> 1.1f
            else -> 1.0f
        }
        return (base * multiplier).toInt()
    }

    private fun String.scopedForFormattingPrompt(action: NoteFormattingAction): String =
        if (action == NoteFormattingAction.StructureOnly || action == NoteFormattingAction.IntelligentStructure) {
            this
        } else {
            takeMiddleAware(32_000)
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
