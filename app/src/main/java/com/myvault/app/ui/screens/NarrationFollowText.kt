package com.myvault.app.ui.screens

import kotlin.math.abs

// Narrated text has normalized whitespace; map it back to the displayed source offsets.
internal fun narrationTextRange(text: String, sentence: String, hint: Int = 0): IntRange? {
    if (text.isEmpty() || sentence.isBlank()) return null
    val words = sentence.trim().split(Regex("[\\s\\u00A0]+"))
    val pattern = words.joinToString("[\\s\\u00A0\\u200E\\u200F\\u2066-\\u2069]+") { Regex.escape(it) }
    return Regex(pattern).findAll(text).minByOrNull { abs(it.range.first - hint) }?.range
}

internal fun narrationCenteredScroll(top: Float, bottom: Float, viewport: Float, maxScroll: Int): Int =
    ((top + bottom) / 2f - viewport.coerceAtLeast(1f) / 2f).toInt().coerceIn(0, maxScroll)

internal fun narrationToolbarVisible(previous: Boolean, deltaY: Float, userInput: Boolean): Boolean =
    if (!userInput || deltaY == 0f) previous else deltaY > 0f
