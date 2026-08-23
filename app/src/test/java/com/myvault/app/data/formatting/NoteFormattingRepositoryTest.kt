package com.myvault.app.data.formatting

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteFormattingRepositoryTest {
    @Test
    fun formattingRepositoryPassesRequestAndProgressWithoutChatState() = runBlocking {
        var received: NoteFormattingRequest? = null
        val progress = mutableListOf<String>()
        val repository = NoteFormattingRepository(
            generator = NoteFormattingGenerator { request, onProgress ->
                received = request
                onProgress("Planning structure...")
                onProgress("Formatting note...")
                "  <h2>Purification</h2><p>Original wording.</p>  "
            },
        )
        val request = request()

        val result = repository.format(request, progress::add)

        assertEquals(request, received)
        assertEquals(listOf("Planning structure...", "Formatting note..."), progress)
        assertEquals("<h2>Purification</h2><p>Original wording.</p>", result.editorHtml)
    }

    @Test
    fun allRetainedActionsAndProvidersCrossTheProviderNeutralBoundary() = runBlocking {
        val received = mutableListOf<NoteFormattingRequest>()
        val repository = NoteFormattingRepository(
            generator = NoteFormattingGenerator { request, _ ->
                received += request
                "<p>${request.action}:${request.provider}</p>"
            },
        )

        NoteFormattingAction.entries.forEach { action ->
            NoteFormattingProvider.entries.forEach { provider ->
                repository.format(request(action = action, provider = provider))
            }
        }

        assertEquals(NoteFormattingAction.entries.size * NoteFormattingProvider.entries.size, received.size)
        assertTrue(received.any { it.action == NoteFormattingAction.StructureOnly && it.provider == NoteFormattingProvider.ChatGPT })
        assertTrue(received.any { it.action == NoteFormattingAction.IntelligentStructure && it.provider == NoteFormattingProvider.Kimi })
    }

    @Test
    fun emptyNoteIsRejectedBeforeProviderInvocation() {
        var invoked = false
        val repository = NoteFormattingRepository(
            generator = NoteFormattingGenerator { _, _ -> invoked = true; "<p>Unexpected</p>" },
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking { repository.format(request(title = "", body = "")) }
        }

        assertTrue(error.message.orEmpty().contains("empty", ignoreCase = true))
        assertEquals(false, invoked)
    }

    @Test
    fun emptyProviderOutputBecomesFormattingSpecificFailure() {
        val repository = NoteFormattingRepository(
            generator = NoteFormattingGenerator { _, _ -> "   " },
        )

        val error = assertThrows(NoteFormattingException::class.java) {
            runBlocking { repository.format(request()) }
        }

        assertTrue(error.message.orEmpty().contains("empty note", ignoreCase = true))
    }

    @Test
    fun providerFailureIsWrappedWithoutLosingUsefulMessage() {
        val repository = NoteFormattingRepository(
            generator = NoteFormattingGenerator { _, _ -> error("Provider unavailable") },
        )

        val error = assertThrows(NoteFormattingException::class.java) {
            runBlocking { repository.format(request()) }
        }

        assertEquals("Provider unavailable", error.message)
        assertTrue(error.cause is IllegalStateException)
    }

    private fun request(
        action: NoteFormattingAction = NoteFormattingAction.StructureOnly,
        provider: NoteFormattingProvider = NoteFormattingProvider.Gemini,
        model: NoteFormattingModel = NoteFormattingModel.Fast,
        title: String = "Purification",
        body: String = "Original wording.",
    ) = NoteFormattingRequest(
        action = action,
        provider = provider,
        model = model,
        title = title,
        body = body,
    )
}
