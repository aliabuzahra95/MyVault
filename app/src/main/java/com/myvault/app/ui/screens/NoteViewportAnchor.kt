package com.myvault.app.ui.screens

import java.io.Serializable

/** Navigation-only reading position, never part of note content or backups. */
data class NoteViewportAnchor(
    val noteId: String,
    val textLength: Int,
    val textHash: Int,
    val offset: Int,
    val lineFraction: Float,
) : Serializable {
    fun matches(id: String?, text: String): Boolean =
        id == noteId && text.length == textLength && text.hashCode() == textHash &&
            offset in 0..text.length && lineFraction.isFinite() && lineFraction in 0f..1f
}

internal fun noteAnchorScroll(lineTop: Float, lineBottom: Float, fraction: Float): Int =
    (lineTop + (lineBottom - lineTop).coerceAtLeast(0f) * fraction.coerceIn(0f, 1f))
        .toInt().coerceAtLeast(0)

internal fun noteCaretScrollDelta(top: Float, bottom: Float, scroll: Int, viewport: Int): Int {
    if (viewport <= 0 || !top.isFinite() || !bottom.isFinite()) return 0
    return when {
        top < scroll -> kotlin.math.floor(top - scroll).toInt()
        bottom > scroll + viewport -> kotlin.math.ceil(bottom - scroll - viewport).toInt()
        else -> 0
    }
}
