package com.myvault.app.data.repository

internal const val PDF_POINTS_PER_MM = 72f / 25.4f

internal fun mmToPdfPoints(mm: Float): Float = mm * PDF_POINTS_PER_MM

data class NotebookExportConfig(
    val topInsetMm: Float = 10f,
    val rightInsetMm: Float = 10f,
    val leftMarginMm: Float = 5f,
    val bottomMarginMm: Float = 5f,
    val drawCenterCutGuide: Boolean = true,
    val drawPageNumbers: Boolean = true,
) {
    init {
        require(listOf(topInsetMm, rightInsetMm, leftMarginMm, bottomMarginMm).all { it.isFinite() && it >= 0f && it <= 30f })
        require(leftMarginMm + rightInsetMm < SLOT_WIDTH_MM - 30f)
        require(topInsetMm + bottomMarginMm < A4_HEIGHT_MM - 40f)
    }

    val contentWidthMm: Float get() = SLOT_WIDTH_MM - leftMarginMm - rightInsetMm
    val contentHeightMm: Float get() = A4_HEIGHT_MM - topInsetMm - bottomMarginMm

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
