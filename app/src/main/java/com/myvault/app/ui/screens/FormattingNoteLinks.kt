package com.myvault.app.ui.screens

import com.myvault.app.data.formatting.FormattingTextContract

internal fun remapFormattingNoteLinks(source: String, output: String, links: List<VaultNoteLink>): List<VaultNoteLink> {
    require(FormattingTextContract.preservesText(source, output))
    if (links.isEmpty()) return emptyList()
    fun offsets(text: String): List<Int> {
        val marker = BooleanArray(text.length)
        Regex("(?m)^[\\t ]*[•*\\-]\\s+").findAll(text).forEach { match -> match.range.forEach { marker[it] = true } }
        return text.indices.filter { index -> !text[index].isWhitespace() && !marker[index] }
    }
    val before = offsets(source)
    val after = offsets(output)
    check(before.size == after.size)
    return links.map { link ->
        val first = before.indexOfFirst { it >= link.start && it < link.end }
        val last = before.indexOfLast { it >= link.start && it < link.end }
        check(first >= 0 && last >= first) { "A note link could not be preserved. Nothing was applied." }
        link.copy(start = after[first], end = after[last] + 1)
    }
}
