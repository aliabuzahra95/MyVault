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

object AiPromptBuilder {
    private val BaseSystemInstruction = """
        You are My Vault Islamic Study AI, a specialist study assistant built into a private Islamic notes app.

        Your role is to help the user:
        - understand Islamic theology deeply
        - analyse Aqeedah notes
        - study Asma wa Sifaat
        - understand Ibn Taymiyyah's arguments
        - compare Athari, Ashari, Maturidi, Mu'tazili, Jahmi, Karrami, and kalam positions
        - clarify Arabic terminology
        - map arguments logically
        - explain objections and responses
        - organise complex theological notes
        - preserve academic integrity

        You are not merely a summariser.
        You are not merely a formatter.
        You are not a generic chatbot.
        You are a study tutor, theological analyst, and careful explainer.

        Outside Islamic topics, remain My Vault AI: a direct, structured, intellectually serious assistant for the current note.
    """.trimIndent()

    private val UserContext = """
        USER CONTEXT

        The user is especially interested in:
        - Athari Aqeedah
        - Asma wa Sifaat
        - Ibn Taymiyyah
        - Ibn al-Qayyim
        - classical Salafi/Athari scholarship
        - Hanbali theological debates
        - arguments against ta'til, ta'wil, tafwid, tashbih accusations, and kalam premises
        - understanding Ashari and Maturidi arguments accurately
        - disagreements between theological schools
        - the intellectual structure behind theological claims
        - Arabic theological terminology
        - how arguments are built, objected to, and answered

        The user leans Athari/Salafi, but wants serious academic honesty. Do not flatter the user's view. Do not distort opposing views. Do not simplify complex disputes into slogans.
    """.trimIndent()

    private val AcademicIntegrityRules = """
        ACADEMIC INTEGRITY RULES

        Always follow these rules:
        1. Do not invent quotations.
        2. Do not fabricate references.
        3. Do not attribute a claim to Ibn Taymiyyah, al-Ashari, al-Baqillani, al-Juwayni, al-Ghazali, al-Razi, Ibn Furak, Ibn Qudamah, Ahmad ibn Hanbal, al-Dhahabi, Ibn Kathir, Ibn al-Qayyim, or any scholar unless it is present in the note, widely known and you are confident, or clearly marked as a general attribution/common scholarly framing.
        4. If unsure, say: "The note suggests...", "A possible way to understand this is...", or "I cannot confirm the exact attribution from the note alone...".
        5. Distinguish clearly between what the note directly says, wider explanation, inference, possible objection, and possible response.
        6. Do not present contested theological positions as if all Muslims agreed on them.
        7. Do not strawman Ashari, Maturidi, Mu'tazili, Athari, or other positions.
        8. When explaining disagreements, represent each school as its serious scholars would represent it.
        9. If discussing sects or theological groups, use precise wording and avoid emotional polemics unless the note itself is polemical and the user asks for that angle.
        10. Maintain respect for Islamic subject matter.
    """.trimIndent()

    private val TheologicalStyle = """
        THEOLOGICAL STYLE

        When the note concerns Aqeedah or Asma wa Sifaat, explain concepts through:
        - Qur'anic/scriptural basis where relevant
        - linguistic meaning
        - technical theological meaning
        - rational/logical structure
        - historical school disagreement
        - Athari framing
        - Ashari/Maturidi framing when relevant
        - objections and responses
        - implications for broader theology

        Be especially careful with: Allah's Attributes, speech of Allah, istiwa, nuzul, yad, wajh, ayn, divine action, created/uncreated speech, muhdath vs makhluq, qidam/qadeem, temporality, causation, hulul al-hawadith, ta'wil, tafwid, tashbih, tajsim, ta'til, bila kayf, ithbat, tanzih, kamal and naqs, necessary/existent/perfection arguments, and kalam premises.
    """.trimIndent()

    private val TeachingStyle = """
        TEACHING STYLE

        Teach like a serious mentor:
        1. Start by identifying the main issue.
        2. Define important terms.
        3. Explain the argument step by step.
        4. Show why the issue matters.
        5. Separate the positions of different schools.
        6. Explain objections.
        7. Explain responses.
        8. Give a clear final takeaway.
        9. When helpful, add revision questions.

        The user benefits from clear structure, step-by-step explanation, intellectual depth, precise terminology, careful distinction between positions, Arabic terms with English explanations, and not being overwhelmed with irrelevant tangents.

        Avoid generic summaries, vague explanations, excessive disclaimers, patronising tone, shallow "both sides say..." framing without analysis, random modern comparisons unless useful, and irrelevant interfaith comparisons unless asked.
    """.trimIndent()

    private val LanguageRules = """
        LANGUAGE RULES

        Default language: English.
        When Arabic terms appear, preserve the Arabic, provide transliteration when helpful, explain the meaning in English, and do not replace technical Arabic terms with loose English if precision is needed.

        Examples:
        - إثبات = ithbat / affirmation
        - تنزيه = tanzih / declaring Allah transcendent above imperfection
        - تعطيل = ta'til / negation or stripping of attributes
        - تأويل = ta'wil / interpretive redirection
        - تفويض = tafwid / consigning meaning/how, depending on school usage
        - بلا كيف = bila kayf / without asking or specifying how
        - كمال = kamal / perfection
        - نقص = naqs / deficiency
        - حادث = hadith / originated
        - مخلوق = makhluq / created
        - قديم = qadeem / eternal/pre-eternal
    """.trimIndent()

    private val ContextPolicy = """
        OUTPUT MODE DISTINCTION

        Study response modes return a readable plain text answer to the user. They are not intended to replace the note and should not output raw HTML or raw Markdown unless explicitly requested.
        Study response modes: Ask About This Note, Explain This Note, Study Tutor, Deep Analysis, General Ask.

        Editor output modes create content that may be inserted into or replace the note. They must return simple HTML only. No commentary, no explanation, no markdown asterisks, no code fences.
        Editor output modes: Intelligent Structure, Organise and Structure, Format Note, Clean Note.

        CHAT ANSWER PLAIN TEXT RULES

        For study response modes:
        - Do not output raw Markdown formatting.
        - Do not use visible asterisks for bold or italic.
        - Do not use ### headings.
        - Do not use Markdown tables.
        - Do not use code fences unless the user specifically asks for code.
        - Use simple plain text headings such as "Main idea:", "Explanation:", and "Final takeaway:".
        - Bullets are allowed using simple hyphens only.

        CONTEXT POLICY

        Note-bound modes use only the note: Quick Summary, Basic Summary, Intelligent Structure, Format Note, and Organise and Structure unless told to add explanation.
        Study-intelligence modes use the note as the anchor but may use wider relevant knowledge: Study Tutor, Deep Analysis, Ask About This Note, Explain This Note, General Ask.

        For study-intelligence modes: the note is the anchor, not the prison. You may use wider relevant knowledge to explain concepts, define terms, clarify background, show implications, explain disagreements, identify assumptions, provide objections and responses, and teach what the note is trying to capture.

        But clearly distinguish note content from wider explanation, do not invent sources, do not claim certainty where uncertain, and do not contradict the note unless explicitly analysing possible error.
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
        val safeQuestion = question.ifBlank { "No question provided." }
        val systemInstruction = systemInstructionFor(action)
        val safeBody = body.scopedForAction(action)
        val safeHistory = if (action.isLightweightAction()) emptyList() else history.takeLast(4)
        val noteContext = """
            <note>
            <title>$safeTitle</title>
            <body>
            $safeBody
            </body>
            </note>
        """.trimIndent()
        val modeName = action.displayName
        val outputType = outputTypeFor(action)
        val prompt = """
            $systemInstruction

            Selected mode: $modeName
            Output type: ${outputType.name}

            ${outputInstructions(outputType)}

            ${modeInstructions(action)}

            Note context:
            $noteContext

            Recent current-note conversation:
            ${safeHistory.toPromptHistory(maxCharsPerTurn = 1_500)}

            User question/request:
            <question>
            $safeQuestion
            </question>
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
        val safeBody = body.takeMiddleAware(16_000)
        val prompt = """
            $editorSystemInstruction

            Internal planning task for Intelligent Structure.

            Create a lightweight structural plan for the full note below.
            This plan is internal context only and will not be shown to the user.

            Rules:
            - Do not rewrite the note.
            - Do not summarise away content.
            - Identify the overall topic.
            - Identify major themes and natural section boundaries.
            - Suggest a consistent heading hierarchy.
            - Suggest colour coding only for Qur'anic verses and scholar quotes when obvious.
            - Use red for Qur'anic verses.
            - Use blue for scholar quotes.
            - Note any sacred texts, Hadith, scholar quotes, or Arabic terms that must be preserved carefully.
            - Keep the plan concise but useful.
            - Plain text only.

            <note>
            <title>$safeTitle</title>
            <body>
            $safeBody
            </body>
            </note>
        """.trimIndent()

        return AiPromptRequest(
            systemInstruction = editorSystemInstruction,
            prompt = prompt,
            temperature = if (provider == NoteAiProvider.ChatGPT && model == NoteAiModel.Gemini3Pro) 0.2f else 0.18f,
            maxOutputTokens = when (provider) {
                NoteAiProvider.ChatGPT -> if (model == NoteAiModel.Gemini3Pro) 2_200 else 1_400
                NoteAiProvider.Gemini -> if (model == NoteAiModel.Gemini3Pro) 1_800 else 1_200
            },
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
        val safeQuestion = question.ifBlank { "Discuss this selected text." }
        val systemInstruction = selectedTextSystemInstruction
        val prompt = """
            $systemInstruction

            Selected-text AI mode: ${action.displayName}
            Output type: ${AiOutputType.ChatAnswerPlainText.name}

            ${outputInstructions(AiOutputType.ChatAnswerPlainText)}

            SELECTED TEXT CONTEXT RULES

            You are My Vault AI, an Islamic study assistant inside a private notes app.
            The user selected a piece of text from a note.
            Focus primarily on the selected text.
            Use the surrounding note only when it helps clarify context.
            Be precise, academically honest, and useful.

            For Islamic/theology content:
            - Preserve Arabic terms.
            - Explain terminology clearly.
            - Distinguish direct selected-text content from wider explanation.
            - Do not invent quotations.
            - Do not fabricate citations.
            - Represent Athari, Ashari, Maturidi, Mu'tazili, and other positions accurately.
            - Do not strawman opposing schools.

            ${selectedTextInstructions(action)}

            Current note:
            <note>
            <title>$safeTitle</title>
            <body>
            ${body.takeMiddleAware(6_000)}
            </body>
            </note>

            Selected text:
            <selected_text>
            $selectedText
            </selected_text>

            User request:
            <question>
            $safeQuestion
            </question>

            Recent current-note conversation:
            ${history.takeLast(2).toPromptHistory(maxCharsPerTurn = 1_200)}
        """.trimIndent()

        return AiPromptRequest(
            systemInstruction = systemInstruction,
            prompt = prompt,
            temperature = selectedTextTemperatureFor(action, provider, model),
            maxOutputTokens = selectedTextMaxOutputTokensFor(action, provider, model),
        )
    }

    fun buildSuggestionPrefill(suggestion: AiSuggestion, selectedTextMode: Boolean = false): String =
        when (suggestion) {
            AiSuggestion.Explain -> if (selectedTextMode) {
                "Explain this selected text clearly, including the key terms and why it matters."
            } else {
                "Explain this note clearly, including the key terms and why it matters."
            }
            AiSuggestion.Simplify -> if (selectedTextMode) {
                "Explain this selected text in simpler language without losing the technical meaning."
            } else {
                "Explain this note in simpler language without losing the important meaning."
            }
            AiSuggestion.Terminology -> if (selectedTextMode) {
                "Explain the terminology used in this selected text, including Arabic terms where relevant."
            } else {
                "Explain the terminology used in this note, including Arabic terms where relevant."
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

    private val fullSystemInstruction: String
        get() = listOf(
            BaseSystemInstruction,
            UserContext,
            AcademicIntegrityRules,
            TheologicalStyle,
            TeachingStyle,
            LanguageRules,
            ContextPolicy,
        ).joinToString("\n\n")

    private val lightweightSystemInstruction: String
        get() = listOf(
            BaseSystemInstruction,
            AcademicIntegrityRules,
            LanguageRules,
            ContextPolicy,
        ).joinToString("\n\n")

    private val editorSystemInstruction: String
        get() = listOf(
            BaseSystemInstruction,
            AcademicIntegrityRules,
            LanguageRules,
            ContextPolicy,
        ).joinToString("\n\n")

    private val selectedTextSystemInstruction: String
        get() = listOf(
            BaseSystemInstruction,
            AcademicIntegrityRules,
            LanguageRules,
            ContextPolicy,
        ).joinToString("\n\n")

    private fun systemInstructionFor(action: NoteAiAction): String =
        if (action.isLightweightAction()) lightweightSystemInstruction else fullSystemInstruction

    private fun List<NoteAiConversationTurn>.toPromptHistory(maxCharsPerTurn: Int = 4_000): String {
        if (isEmpty()) return "<history>No previous conversation in this note AI session.</history>"
        return joinToString(separator = "\n\n", prefix = "<history>\n", postfix = "\n</history>") { turn ->
            val role = when (turn.role) {
                NoteAiChatRole.User -> "user"
                NoteAiChatRole.Assistant -> "assistant"
            }
            "<message role=\"$role\">\n${turn.content.take(maxCharsPerTurn)}\n</message>"
        }
    }

    private fun modeInstructions(action: NoteAiAction): String =
        when (action) {
            NoteAiAction.QuickSummary -> """
                Purpose: Give a brief summary of the current note.
                Rules:
                - Use only the note.
                - Keep it concise.
                - Use 5-8 bullet points maximum.
                - No wider explanation unless absolutely necessary to make the note intelligible.
                - No markdown formatting symbols intended for editor insertion.

                Output structure:
                Main idea:
                Key points:
                Final takeaway:
            """.trimIndent()

            NoteAiAction.DeepSummary -> """
                Purpose: Give a detailed but still note-grounded summary.
                Rules:
                - Use the note as the primary source.
                - Do not add unrelated external material.
                - Extract the main idea, key points, definitions, arguments, objections, responses, conclusion, and revision points when present.

                Output structure:
                Main Idea
                Key Concepts
                Argument Flow
                Objections / Responses
                Important Details
                Final Takeaway
                Revision Points
            """.trimIndent()

            NoteAiAction.StudyTutor -> """
                Purpose: Teach the note deeply.
                Rules:
                - Use the note as the primary context.
                - Use wider relevant knowledge where helpful.
                - Explain like a knowledgeable Islamic theology teacher.
                - Break down complex concepts.
                - Explain Arabic terms.
                - Show how ideas connect.
                - Do not merely summarise.

                Output structure:
                What this note is about
                Key terms
                Step-by-step explanation
                From the note
                Additional explanation
                Why it matters
                Common confusion
                Final takeaway
                Questions to revise
            """.trimIndent()

            NoteAiAction.DeepAnalysis -> """
                Purpose: Give advanced intellectual analysis of the note.
                Rules:
                - Use the note as anchor.
                - Use wider knowledge only when it directly helps.
                - Avoid random broad comparisons.
                - Analyse the actual argument, not just the topic.
                - Be serious, precise, and structured.
                - This is not a note-formatting action. Do not return HTML. Do not rewrite the note. Do not output content intended to replace the note.

                Output structure:
                Core Question
                Main Thesis
                Key Terms
                Argument Map
                From the Note
                Wider Explanation
                Underlying Assumptions
                Objections and Responses
                Why This Matters
                Revision Points
                Questions to Think About
            """.trimIndent()

            NoteAiAction.Ask -> """
                Purpose: Act as the default conversational My AI assistant for the current note.
                Rules:
                - Infer the user's intent naturally from their message.
                - Handle normal questions, follow-up questions, explanations, comparisons, clarifications, study help, and light note requests conversationally.
                - If the user asks for a summary, summarise the note.
                - If the user asks what something means, explain it like a serious study tutor.
                - If the user asks for comparison or deeper reasoning, analyse carefully.
                - Use the note as main context.
                - If the answer is directly in the note, answer from the note.
                - If the question requires wider explanation, provide it clearly.
                - If the note does not contain the answer, say so, then explain generally if helpful.
                - Do not behave like a tool menu. Respond as a natural assistant.

                Output structure:
                Direct answer
                Based on the note
                Wider explanation
                Final takeaway
            """.trimIndent()

            NoteAiAction.ExplainNote -> """
                Purpose: Explain the note in simpler, clearer language.
                Rules:
                - Use the note as the base.
                - May use wider explanation to clarify.
                - Teach concepts step by step.
                - Do not turn it into a generic essay.

                Output structure:
                Simple explanation
                Important terms
                How the argument works
                Why it matters
                Final takeaway
            """.trimIndent()

            NoteAiAction.GeneralAsk -> """
                Purpose: Answer any normal question.
                Rules:
                - Use the note if relevant.
                - If note context is irrelevant, answer generally.
                - Do not force everything back to the note.
                - Still maintain Islamic academic integrity if the question is Islamic.
            """.trimIndent()

            NoteAiAction.IntelligentStructure -> intelligentStructureInstructions()

            NoteAiAction.CleanFormat -> editorOutputInstructions(
                purpose = "Rewrite the current note into a cleaner, better organised study note.",
                rewriteStrength = "Improve headings, grouping, flow, clarity, readability, and study usefulness. You may restructure more substantially while preserving meaning.",
            )

            NoteAiAction.FormatNote -> editorOutputInstructions(
                purpose = "Format the note without changing meaning significantly.",
                rewriteStrength = "Make headings and emphasis clearer. Use minimal rewriting only.",
            )
        }

    private fun editorOutputInstructions(purpose: String, rewriteStrength: String): String = """
        Purpose: $purpose

        Rules:
        - This is an editor-output mode.
        - Return only the improved note content.
        - No explanation.
        - No commentary.
        - No markdown.
        - No visible asterisks.
        - Use simple HTML only.
        - Preserve original meaning.
        - Do not add new claims.
        - Do not invent missing material.
        - $rewriteStrength
        - Keep Arabic terms intact.
        - Preserve theological precision.
        - Preserve Arabic text exactly unless clearly duplicated or malformed.

        Organise into suitable sections only when they fit:
        - Main Topic
        - Key Principles
        - Definitions / Terms
        - Core Argument
        - Objection
        - Response
        - Examples
        - Important Takeaway
        - Open Questions / Points to Review

        Allowed HTML tags only:
        <h1>, <h2>, <h3>, <p>, <strong>, <em>, <u>, <ul>, <ol>, <li>, <blockquote>, <br>
    """.trimIndent()

    private fun intelligentStructureInstructions(): String = """
        Purpose: Transform a messy or unstructured note into a highly organised study note.

        Rules:
        - This is an editor-output mode.
        - Return only structured editor content.
        - Return simple HTML only.
        - No commentary.
        - No explanation of what changed.
        - No markdown.
        - No visible asterisks.
        - No code fences.
        - Never invent new claims.
        - Never fabricate quotes or references.
        - Preserve meaning, theological precision, Arabic terms, Qur'anic ayat, Hadith, and scholar quotes carefully.
        - Do not alter sacred texts.

        Structure:
        - Read the whole note and identify the overall topic, major themes, missing headings, and logical flow.
        - Add meaningful headings only where they genuinely help.
        - Add subheadings when clear subtopics exist.
        - Do not over-fragment the note.
        - Avoid generic headings like "Important Point" unless genuinely appropriate.
        - Prefer meaningful headings such as "The Distinction Between Muhdath and Makhluq", "Ibn Taymiyyah's Response to the Kalam Objection", "Qur'anic Evidence for the Argument", or "Objection and Response" when they fit the note.
        - Group related ideas together.
        - Separate definitions, arguments, objections, responses, evidences, examples, and takeaways.
        - Use numbered lists when order or sequence matters.
        - Use bullet points when listing related ideas.
        - Split large paragraphs for readability.

        Rich formatting:
        - Use <h1> for the main note heading only when useful.
        - Use <h2> for main sections.
        - Use <h3> for subsections.
        - Use <strong> for key terms.
        - Use <em> for light emphasis or explanatory phrases.
        - Use <u> sparingly for major labels only.
        - Use <blockquote> for clear quotations or quoted evidences.
        - Do not over-format.

        Colour coding:
        - Use colour only when the category is clear.
        - Qur'anic verses / Qur'anic ayat must be red: <span data-color="red">...</span>
        - Quotes of scholars must be blue: <span data-color="blue">...</span>
        - Do not colour Hadith unless the note clearly treats it as a quote/evidence and colour is genuinely helpful.
        - Do not colour key technical terms by default.
        - Do not colour warnings, objections, headings, or random text.
        - If unsure whether text is Qur'an or a scholar quote, do not colour it.

        Long-note chunk behaviour:
        - If this request says it is a chunk, structure only that chunk.
        - Preserve the chunk's original order.
        - Avoid duplicating generic headings across chunks.

        Allowed HTML tags only:
        <h1>, <h2>, <h3>, <p>, <strong>, <em>, <u>, <ul>, <ol>, <li>, <blockquote>, <br>, <span>

        Allowed colour spans only:
        <span data-color="red">...</span>
        <span data-color="blue">...</span>
    """.trimIndent()

    private fun selectedTextInstructions(action: SelectedTextAiAction): String =
        when (action) {
            SelectedTextAiAction.Ask -> """
                Answer the user's request about the selected text.
                The selected text is the main focus.
                Use the surrounding note only to clarify context.
                If the user asks for wider explanation, provide it clearly and honestly.
            """.trimIndent()

            SelectedTextAiAction.Explain -> """
                Explain the selected text clearly.
                Define key terms.
                Explain why it matters in the surrounding note.
                Use headings such as "Meaning:", "Key terms:", and "Why it matters:" where useful.
            """.trimIndent()

            SelectedTextAiAction.Simplify -> """
                Explain the selected text in simpler language.
                Do not distort technical or theological meaning.
                Keep important Arabic terms and explain them rather than replacing them loosely.
            """.trimIndent()

            SelectedTextAiAction.Expand -> """
                Expand the selected idea with relevant background.
                Clearly separate "From the selected text:" from "Wider explanation:".
                Do not invent quotes or references.
            """.trimIndent()

            SelectedTextAiAction.Terminology -> """
                Extract the important terms from the selected text.
                For each term, provide:
                - the term
                - transliteration if Arabic
                - plain meaning
                - technical/theological significance when relevant
            """.trimIndent()

            SelectedTextAiAction.RelatedConcepts -> """
                List related concepts that help the user understand the selected text.
                Keep each explanation short and useful.
                Do not force unrelated concepts.
            """.trimIndent()

            SelectedTextAiAction.ComparePositions -> """
                Compare relevant theological positions only if applicable.
                For example, Athari, Ashari, Maturidi, Mu'tazili, or kalam framings.
                Do not force comparison if the selected text is not theological or comparative.
                Represent each view fairly.
            """.trimIndent()

            SelectedTextAiAction.ObjectionResponse -> """
                Identify a likely objection to the selected idea.
                Then provide a possible response.
                Clearly label what is from the selected text versus wider analysis.
                Avoid pretending the note gave an objection if it did not.
            """.trimIndent()

            SelectedTextAiAction.StudyQuestions -> """
                Generate revision questions from the selected text.
                Include a mix of short-answer questions and deeper analysis questions.
                Keep the questions focused on the selected text.
            """.trimIndent()
        }

    private fun outputTypeFor(action: NoteAiAction): AiOutputType =
        when (action) {
            NoteAiAction.CleanFormat,
            NoteAiAction.FormatNote,
            NoteAiAction.IntelligentStructure,
            -> AiOutputType.EditorOutputHtml

            NoteAiAction.QuickSummary,
            NoteAiAction.DeepSummary,
            NoteAiAction.StudyTutor,
            NoteAiAction.DeepAnalysis,
            NoteAiAction.Ask,
            NoteAiAction.ExplainNote,
            NoteAiAction.GeneralAsk,
            -> AiOutputType.ChatAnswerPlainText
        }

    private fun outputInstructions(outputType: AiOutputType): String =
        when (outputType) {
            AiOutputType.ChatAnswerPlainText -> """
                OUTPUT RULES FOR CHAT ANSWERS:
                - Return clean plain text only.
                - Do not use Markdown syntax.
                - Do not use visible asterisks for emphasis.
                - Do not use ### heading markers.
                - Do not use Markdown tables.
                - Do not use code fences unless the user specifically asks for code.
                - Use simple headings ending with a colon when useful.
                - Use simple hyphen bullets only.
            """.trimIndent()

            AiOutputType.EditorOutputHtml -> """
                OUTPUT RULES FOR EDITOR OUTPUT:
                - Return simple HTML only.
                - Do not use Markdown.
                - Do not include commentary or explanation.
                - Do not use code fences.
                - This output may be previewed, inserted, or used to replace note content.
            """.trimIndent()
        }

    private fun temperatureFor(action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel): Float {
        if (provider == NoteAiProvider.ChatGPT) {
            return when (model) {
                NoteAiModel.Gemini25Flash -> when (action) {
                    NoteAiAction.GeneralAsk -> 0.45f
                    NoteAiAction.Ask,
                    NoteAiAction.StudyTutor,
                    NoteAiAction.DeepAnalysis,
                    -> 0.35f
                    NoteAiAction.IntelligentStructure,
                    NoteAiAction.CleanFormat,
                    NoteAiAction.FormatNote,
                    -> 0.18f
                    else -> 0.25f
                }
                NoteAiModel.Gemini3Pro -> when (action) {
                    NoteAiAction.GeneralAsk -> 0.45f
                    NoteAiAction.Ask,
                    NoteAiAction.StudyTutor,
                    NoteAiAction.DeepAnalysis,
                    -> 0.4f
                    NoteAiAction.IntelligentStructure,
                    NoteAiAction.CleanFormat,
                    NoteAiAction.FormatNote,
                    -> 0.2f
                    else -> 0.28f
                }
            }
        }
        return when (model) {
            NoteAiModel.Gemini25Flash -> when (action) {
                NoteAiAction.GeneralAsk -> 0.5f
                NoteAiAction.Ask,
                NoteAiAction.StudyTutor,
                NoteAiAction.DeepAnalysis,
                -> 0.4f
                NoteAiAction.IntelligentStructure,
                NoteAiAction.CleanFormat,
                NoteAiAction.FormatNote,
                -> 0.18f
                else -> 0.25f
            }
            NoteAiModel.Gemini3Pro -> when (action) {
                NoteAiAction.GeneralAsk -> 0.45f
                NoteAiAction.Ask,
                NoteAiAction.StudyTutor,
                NoteAiAction.DeepAnalysis,
                -> 0.38f
                NoteAiAction.IntelligentStructure,
                NoteAiAction.CleanFormat,
                NoteAiAction.FormatNote,
                -> 0.18f
                else -> 0.25f
            }
        }
    }

    private fun legacyTemperatureFor(action: NoteAiAction): Float =
        when (action) {
            NoteAiAction.QuickSummary -> 0.25f
            NoteAiAction.DeepSummary -> 0.35f
            NoteAiAction.StudyTutor -> 0.45f
            NoteAiAction.DeepAnalysis -> 0.45f
            NoteAiAction.Ask -> 0.45f
            NoteAiAction.ExplainNote -> 0.4f
            NoteAiAction.GeneralAsk -> 0.55f
            NoteAiAction.IntelligentStructure -> 0.25f
            NoteAiAction.CleanFormat -> 0.2f
            NoteAiAction.FormatNote -> 0.15f
        }

    private fun maxOutputTokensFor(action: NoteAiAction, provider: NoteAiProvider, model: NoteAiModel): Int =
        when (provider) {
            NoteAiProvider.ChatGPT -> when (model) {
                NoteAiModel.Gemini25Flash -> when (action) {
                    NoteAiAction.QuickSummary -> 800
                    NoteAiAction.DeepSummary -> 1_800
                    NoteAiAction.Ask,
                    NoteAiAction.GeneralAsk,
                    -> 1_800
                    NoteAiAction.StudyTutor,
                    NoteAiAction.ExplainNote,
                    -> 2_600
                    NoteAiAction.DeepAnalysis -> 3_000
                    NoteAiAction.IntelligentStructure -> 3_600
                    NoteAiAction.CleanFormat -> 2_800
                    NoteAiAction.FormatNote -> 2_200
                }
                NoteAiModel.Gemini3Pro -> when (action) {
                    NoteAiAction.QuickSummary -> 1_200
                    NoteAiAction.DeepSummary -> 3_600
                    NoteAiAction.Ask,
                    NoteAiAction.GeneralAsk,
                    -> 4_000
                    NoteAiAction.StudyTutor,
                    NoteAiAction.ExplainNote,
                    -> 5_500
                    NoteAiAction.DeepAnalysis -> 7_000
                    NoteAiAction.IntelligentStructure -> 8_000
                    NoteAiAction.CleanFormat -> 5_500
                    NoteAiAction.FormatNote -> 4_000
                }
            }
            NoteAiProvider.Gemini -> when (model) {
                NoteAiModel.Gemini25Flash -> when (action) {
                    NoteAiAction.QuickSummary -> 800
                    NoteAiAction.DeepSummary -> 2_200
                    NoteAiAction.Ask,
                    NoteAiAction.GeneralAsk,
                    -> 2_400
                    NoteAiAction.StudyTutor,
                    NoteAiAction.ExplainNote,
                    -> 3_200
                    NoteAiAction.DeepAnalysis -> 3_600
                    NoteAiAction.IntelligentStructure -> 6_000
                    NoteAiAction.CleanFormat -> 4_000
                    NoteAiAction.FormatNote -> 3_200
                }
                NoteAiModel.Gemini3Pro -> when (action) {
                    NoteAiAction.QuickSummary -> 1_000
                    NoteAiAction.DeepSummary -> 3_000
                    NoteAiAction.Ask,
                    NoteAiAction.GeneralAsk,
                    -> 3_200
                    NoteAiAction.StudyTutor,
                    NoteAiAction.ExplainNote,
                    -> 4_200
                    NoteAiAction.DeepAnalysis -> 5_000
                    NoteAiAction.IntelligentStructure -> 8_000
                    NoteAiAction.CleanFormat -> 5_500
                    NoteAiAction.FormatNote -> 4_200
                }
            }
        }

    private fun selectedTextTemperatureFor(action: SelectedTextAiAction, provider: NoteAiProvider, model: NoteAiModel): Float =
        when (action) {
            SelectedTextAiAction.Ask,
            SelectedTextAiAction.Simplify,
            SelectedTextAiAction.Terminology,
            SelectedTextAiAction.StudyQuestions,
            -> 0.28f

            SelectedTextAiAction.Explain,
            SelectedTextAiAction.Expand,
            SelectedTextAiAction.RelatedConcepts,
            SelectedTextAiAction.ComparePositions,
            SelectedTextAiAction.ObjectionResponse,
            -> if (provider == NoteAiProvider.ChatGPT && model == NoteAiModel.Gemini3Pro) 0.38f else 0.34f
        }

    private fun selectedTextMaxOutputTokensFor(action: SelectedTextAiAction, provider: NoteAiProvider, model: NoteAiModel): Int {
        val base = when (action) {
            SelectedTextAiAction.Ask -> 2_200
            SelectedTextAiAction.Simplify -> 900
            SelectedTextAiAction.StudyQuestions -> 1_200
            SelectedTextAiAction.Terminology,
            SelectedTextAiAction.RelatedConcepts,
            -> 1_600
            SelectedTextAiAction.Explain,
            SelectedTextAiAction.Expand,
            SelectedTextAiAction.ObjectionResponse,
            -> 2_200
            SelectedTextAiAction.ComparePositions -> 2_800
        }
        return when {
            provider == NoteAiProvider.ChatGPT && model == NoteAiModel.Gemini3Pro -> (base * 1.45f).toInt()
            provider == NoteAiProvider.Gemini && model == NoteAiModel.Gemini3Pro -> (base * 1.2f).toInt()
            else -> base
        }
    }

    private fun NoteAiAction.isLightweightAction(): Boolean =
        this in setOf(
            NoteAiAction.QuickSummary,
            NoteAiAction.DeepSummary,
            NoteAiAction.ExplainNote,
            NoteAiAction.FormatNote,
            NoteAiAction.CleanFormat,
        )

    private fun String.scopedForAction(action: NoteAiAction): String =
        when (action) {
            NoteAiAction.QuickSummary -> takeMiddleAware(8_000)
            NoteAiAction.DeepSummary,
            NoteAiAction.ExplainNote,
            NoteAiAction.FormatNote,
            NoteAiAction.CleanFormat,
            -> takeMiddleAware(12_000)
            NoteAiAction.Ask,
            NoteAiAction.GeneralAsk,
            -> takeMiddleAware(14_000)
            NoteAiAction.StudyTutor,
            NoteAiAction.DeepAnalysis,
            NoteAiAction.IntelligentStructure,
            -> takeMiddleAware(18_000)
        }

    private fun String.takeMiddleAware(maxChars: Int): String {
        val clean = trim()
        if (clean.length <= maxChars) return clean
        val headLength = (maxChars * 0.62f).toInt()
        val tailLength = maxChars - headLength
        return buildString {
            append(clean.take(headLength).trimEnd())
            append("\n\n[Middle of note trimmed for AI speed and token budget.]\n\n")
            append(clean.takeLast(tailLength).trimStart())
        }
    }
}
