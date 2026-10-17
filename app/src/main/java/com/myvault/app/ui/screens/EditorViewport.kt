package com.myvault.app.ui.screens

internal fun editorCaretTarget(top: Float, bottom: Float, scroll: Int, viewport: Int,
    maxScroll: Int, center: Boolean): Int {
    if (viewport <= 0 || !top.isFinite() || !bottom.isFinite()) return scroll
    return if (center) ((top + bottom - viewport) / 2f).toInt().coerceIn(0, maxScroll)
    else (scroll + noteCaretScrollDelta(top - 24f, bottom + 40f, scroll, viewport)).coerceIn(0, maxScroll)
}

internal fun editorWordCount(text: String): Int {
    var count = 0
    var inWord = false
    for (character in text) {
        val word = !character.isWhitespace()
        if (word && !inWord) count++
        inWord = word
    }
    return count
}
