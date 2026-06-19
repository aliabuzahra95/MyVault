package com.myvault.app.ai.home

object HomeInlineAiPromptBuilder {
    fun buildSystemInstruction(hasContext: Boolean): String =
        if (hasContext) {
            """
            You are Ask AI inside the user's private MyVault app.
            Analyze the attached MyVault notes and context first.
            Do not claim global vault access.
            If the answer is not inside the attached context, state that clearly.
            Do not invent sources, titles, quotes, page numbers, or attributions.
            Synthesize across attached notes when useful.
            Keep answers direct, specific, and useful for serious study.
            """.trimIndent()
        } else {
            """
            You are Ask AI inside the user's private MyVault app.
            Act as a direct parametric study assistant.
            You currently have no note references attached.
            Answer normally, but do not pretend you can see the user's vault.
            Keep answers direct, specific, and useful for serious study.
            """.trimIndent()
        }

    fun buildUserPrompt(
        question: String,
        contexts: List<HomeAiContextItem>,
    ): String {
        val attachedContext = if (contexts.isEmpty()) {
            "<attached_context empty='true'></attached_context>"
        } else {
            buildString {
                appendLine("<attached_context>")
                contexts.forEachIndexed { index, context ->
                    appendLine("<note index='${index + 1}' type='${context.item.type.label}'>")
                    appendLine("<title>${context.item.title.escapeXml()}</title>")
                    appendLine("<body>")
                    appendLine(context.body.trim().take(MaxBodyCharsPerAttachment))
                    appendLine("</body>")
                    appendLine("</note>")
                }
                appendLine("</attached_context>")
            }
        }

        return """
            $attachedContext

            <user_question>
            ${question.trim()}
            </user_question>
        """.trimIndent()
    }

    fun estimatePayloadChars(contexts: List<HomeAiContextItem>): Int =
        contexts.sumOf { it.body.length + it.item.title.length }

    private fun String.escapeXml(): String =
        replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")

    private const val MaxBodyCharsPerAttachment = 16_000
}
