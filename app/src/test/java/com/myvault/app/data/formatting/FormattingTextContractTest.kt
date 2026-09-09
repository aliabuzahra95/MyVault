package com.myvault.app.data.formatting

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class FormattingTextContractTest {
    private val fixtures = listOf(
        "Water\nWater remains pure unless its properties change.",
        List(800) { "Research point $it remains exactly as written [${it + 1}]." }.joinToString("\n\n"),
        "Evidence\nقال: «إِنَّمَا الْأَعْمَالُ بِالنِّيَّاتِ».\nThe quotation remains exact.",
        "- First point\n- Second point\n- Third point",
        " Pasted    text\n\nwith uneven   spacing. ",
        "https://example.org/page?q=one&n=12#section\nReference [14], vol. 2, p. 39.",
        "He wrote: \"Do not change this quotation.\"\nIbn Taymiyyah (728 AH).",
        "Existing heading\nFirst section.\nAnother heading\nSecond section.",
        "Numbers\n1. A point.\n2. Another point.\nThe value is -4.25, not 4.25.",
        "Literal <text> & punctuation: ; ! ? / ' \" remain.",
    )
    private fun html(text: String) = text.split("\n").joinToString("") {
        "<p>" + it.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;") + "</p>"
    }

    @Test fun representativeFixturesSurviveBothModesAndEveryProviderBoundary() = runBlocking {
        val repository = NoteFormattingRepository(NoteFormattingGenerator { request, _ -> html(request.body) })
        for (source in fixtures) for (action in listOf(NoteFormattingAction.StructureOnly, NoteFormattingAction.IntelligentStructure)) {
            for (provider in NoteFormattingProvider.entries) {
                val result = repository.format(NoteFormattingRequest(action, provider, NoteFormattingModel.Smart, "Title", source))
                FormattingTextContract.requirePreserved(source, result.editorHtml)
            }
        }
    }

    @Test fun alterationsToArabicQuotesNumbersUrlsOrOrderAreRejected() {
        for (source in fixtures) {
            assertThrows(NoteFormattingException::class.java) { FormattingTextContract.requirePreserved(source, html(source + " Added claim.")) }
            assertThrows(NoteFormattingException::class.java) { FormattingTextContract.requirePreserved(source, html(source.dropLast(2))) }
        }
        for ((source, changed) in listOf(
            "[14]" to "[15]", "not required" to "required", "إِنَّمَا" to "إنما",
            "https://x.org/?a=1&b=2" to "https://x.org/?a=2&b=1",
            "First. Second." to "Second. First.", "\"Exact.\"" to "\"Exact!\"",
        )) assertThrows(NoteFormattingException::class.java) { FormattingTextContract.requirePreserved(source, html(changed)) }
    }

    @Test fun markupEntitiesAndListBulletsArePresentationOnly() {
        FormattingTextContract.requirePreserved("Heading\n- A & B\n- جَمِيل", "<h2>Heading</h2><ul><li>A &amp; B</li><li><strong>جَمِيل</strong></li></ul>")
        FormattingTextContract.requirePreserved("A B", "<p>A&#160;B</p>")
        FormattingTextContract.requirePreserved("1. A\n2. B", "<ol><li>A</li><li>B</li></ol>")
        assertThrows(NoteFormattingException::class.java) { FormattingTextContract.requirePreserved("12. A\n14. B", "<ol><li>A</li><li>B</li></ol>") }
        assertFalse(FormattingTextContract.preservesText("ice cream", "icecream"))
    }

    @Test fun unsafeOrMalformedMarkupCannotReachPreview() {
        for (output in listOf("<p>Text", "<script>Text</script>", "<p onclick='run()'>Text</p>", "<span data-color='red'>Text</span>", "<!DOCTYPE a [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><p>&x;</p>")) {
            assertThrows(NoteFormattingException::class.java) { FormattingTextContract.requirePreserved("Text", output) }
        }
    }

    @Test fun cancellationIsNotTurnedIntoUserFacingFailure() {
        val repository = NoteFormattingRepository(NoteFormattingGenerator { _, _ -> throw CancellationException() })
        assertThrows(CancellationException::class.java) {
            runBlocking { repository.format(NoteFormattingRequest(NoteFormattingAction.StructureOnly, NoteFormattingProvider.Gemini, NoteFormattingModel.Fast, "", "Text")) }
        }
    }
}
