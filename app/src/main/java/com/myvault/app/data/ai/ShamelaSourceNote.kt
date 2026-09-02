package com.myvault.app.data.ai

data class ShamelaSourceNote(
    val title: String,
    val body: String,
    val citation: String,
)

fun ResearchSource.toShamelaSourceNote(): ShamelaSourceNote {
    val location = listOfNotNull(
        part?.takeIf(String::isNotBlank)?.let { "Part $it" },
        printedPage?.takeIf(String::isNotBlank)?.let { "Page $it" },
    ).joinToString(" · ")
    val citation = citationText?.trim()?.takeIf(String::isNotBlank)
        ?: buildList {
            add(bookTitle)
            authorName?.takeIf(String::isNotBlank)?.let(::add)
            if (location.isNotBlank()) add(location)
        }.joinToString(" · ")
    val metadata = buildList {
        add("Source: $bookTitle")
        authorName?.takeIf(String::isNotBlank)?.let { add("Author: $it") }
        if (location.isNotBlank()) add("Location: $location")
        add("Provenance: ${provenanceType.label}")
        if (citation.isNotBlank()) add("Citation: $citation")
    }
    return ShamelaSourceNote(
        title = "Shamela - ${bookTitle.trim()}".take(MaxSourceNoteTitleCharacters),
        body = buildString {
            appendLine(arabicPassage.trim())
            appendLine()
            append(metadata.joinToString("\n"))
        }.trim(),
        citation = citation,
    )
}

private const val MaxSourceNoteTitleCharacters = 180
