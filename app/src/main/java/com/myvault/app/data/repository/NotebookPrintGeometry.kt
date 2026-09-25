package com.myvault.app.data.repository

internal const val PDF_POINTS_PER_MM = 72f / 25.4f

internal fun mmToPdfPoints(mm: Float): Float = mm * PDF_POINTS_PER_MM

enum class NotebookPrintDensity(val autoBodySizePt: Float) {
    Comfortable(13f),
    Compact(11.5f),
    Dense(10f),
}

internal fun resolveNotebookDensity(storedValue: String?, hasExistingSettings: Boolean): NotebookPrintDensity =
    NotebookPrintDensity.entries.firstOrNull { it.name == storedValue }
        ?: if (hasExistingSettings) NotebookPrintDensity.Comfortable else NotebookPrintDensity.Compact

internal data class NotebookTypography(
    val bodySizePt: Float,
    val lineExtraPt: Float,
    val paragraphSpacingPt: Float,
    val headingSpacingPt: Float,
    val blankLineSpacingPt: Float,
    val titleSpacingPt: Float,
) {
    fun bodySizeFor(text: String): Float = if (text.any {
        Character.UnicodeScript.of(it.code) == Character.UnicodeScript.ARABIC
    }) bodySizePt.coerceAtLeast(11f) else bodySizePt

    fun headingSizeFor(level: Int, text: String): Float {
        val scale = when (level) {
            1 -> 1.6f
            2 -> 1.4f
            3 -> 1.25f
            4 -> 1.12f
            else -> 1f
        }
        return bodySizeFor(text) * scale
    }
}

data class NotebookExportConfig(
    val topInsetMm: Float = 10f,
    val rightInsetMm: Float = 10f,
    val leftMarginMm: Float = 5f,
    val bottomMarginMm: Float = 5f,
    val drawCenterCutGuide: Boolean = true,
    val drawPageNumbers: Boolean = true,
    val density: NotebookPrintDensity = NotebookPrintDensity.Compact,
    val bodyFontSizePt: Int? = null,
) {
    init {
        require(listOf(topInsetMm, rightInsetMm, leftMarginMm, bottomMarginMm).all { it.isFinite() && it >= 0f && it <= 30f })
        require(leftMarginMm + rightInsetMm < SLOT_WIDTH_MM - 30f)
        require(topInsetMm + bottomMarginMm < A4_HEIGHT_MM - 40f)
        require(bodyFontSizePt == null || bodyFontSizePt in 9..14)
    }

    val contentWidthMm: Float get() = SLOT_WIDTH_MM - leftMarginMm - rightInsetMm
    val contentHeightMm: Float get() = A4_HEIGHT_MM - topInsetMm - bottomMarginMm

    internal fun typography(): NotebookTypography {
        val body = bodyFontSizePt?.toFloat() ?: density.autoBodySizePt
        return when (density) {
            NotebookPrintDensity.Comfortable -> NotebookTypography(body, 3f, 5f, 8f, 13f, 18f)
            NotebookPrintDensity.Compact -> NotebookTypography(body, 2f, 3.75f, 6f, 10f, 14f)
            NotebookPrintDensity.Dense -> NotebookTypography(body, 1.25f, 2.75f, 4.5f, 8f, 11f)
        }
    }

    companion object {
        const val A4_WIDTH_MM = 297f
        const val A4_HEIGHT_MM = 210f
        const val SLOT_WIDTH_MM = A4_WIDTH_MM / 2f
    }
}

internal data class NotebookSlot(val leftMm: Float, val contentLeftMm: Float, val contentRightMm: Float)

internal fun NotebookExportConfig.slot(index: Int): NotebookSlot {
    require(index in 0..1)
    val left = index * NotebookExportConfig.SLOT_WIDTH_MM
    return NotebookSlot(left, left + leftMarginMm, left + NotebookExportConfig.SLOT_WIDTH_MM - rightInsetMm)
}

internal fun notebookSheetPairs(logicalPageCount: Int): List<Pair<Int, Int?>> {
    require(logicalPageCount > 0)
    return (0 until logicalPageCount step 2).map { it to (it + 1).takeIf { page -> page < logicalPageCount } }
}
