package com.myvault.app.ui.screens

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal data class VisibleReadingPage(val index: Int, val top: Float, val bottom: Float)

/** Reading progress follows the main visible page, not a sliver of its predecessor. */
internal fun selectPdfReadingPage(
    pages: List<VisibleReadingPage>,
    viewportHeight: Float,
    fallback: Int,
): Int {
    if (!viewportHeight.isFinite() || viewportHeight <= 0f) return fallback
    return pages.asSequence()
        .filter { it.top.isFinite() && it.bottom.isFinite() && it.bottom > it.top }
        .filter { it.bottom > 0f && it.top < viewportHeight }
        .maxWithOrNull(compareBy<VisibleReadingPage> {
            min(it.bottom, viewportHeight) - max(it.top, 0f)
        }.thenBy { -abs((it.top + it.bottom) / 2f - viewportHeight / 2f) })
        ?.index ?: fallback
}
