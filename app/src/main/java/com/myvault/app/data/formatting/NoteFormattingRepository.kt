package com.myvault.app.data.formatting

import javax.inject.Inject
import javax.inject.Singleton

/** Provider-neutral generation boundary for retained editor formatting. */
fun interface NoteFormattingGenerator {
    suspend fun generate(
        request: NoteFormattingRequest,
        onProgress: (String) -> Unit,
    ): String
}

/**
 * Formatting-only application service. It deliberately knows nothing about chat
 * messages, conversation history, selected-text AI, tutoring, or continuation.
 */
@Singleton
class NoteFormattingRepository @Inject constructor(
    private val generator: NoteFormattingGenerator,
) {
    suspend fun format(
        request: NoteFormattingRequest,
        onProgress: (String) -> Unit = {},
    ): NoteFormattingResult {
        require(request.body.isNotBlank() || request.title.isNotBlank()) {
            "This note is empty, so there is nothing to format yet."
        }
        return try {
            val editorHtml = generator.generate(request, onProgress).trim()
            if (editorHtml.isBlank()) {
                throw NoteFormattingException("The formatting provider returned an empty note.")
            }
            NoteFormattingResult(editorHtml = editorHtml)
        } catch (error: NoteFormattingException) {
            throw error
        } catch (error: Throwable) {
            throw NoteFormattingException(
                message = error.message?.takeIf { it.isNotBlank() } ?: "Note formatting failed.",
                cause = error,
            )
        }
    }
}
