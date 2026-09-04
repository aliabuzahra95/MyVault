package com.myvault.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import com.myvault.app.data.quran.TajweedAnnotation
import com.myvault.app.data.quran.adjustedQuranTajweedRange
import com.myvault.app.data.quran.quranTajweedColorArgb

fun buildQuranArabicText(
    text: String,
    annotations: List<TajweedAnnotation>,
    tajweedEnabled: Boolean,
    isDark: Boolean,
): AnnotatedString {
    if (!tajweedEnabled || annotations.isEmpty()) {
        return buildBaseArabicText(text)
    }
    return buildAnnotatedString {
        append(text)
        addStyle(ParagraphStyle(textDirection = TextDirection.Rtl), 0, text.length)
        annotations.forEach { annotation ->
            val color = quranTajweedColorArgb(annotation.rule, isDark)?.let(::Color) ?: return@forEach
            val (start, end) = adjustedQuranTajweedRange(text, annotation.start, annotation.end)
            if (start < end) {
                addStyle(SpanStyle(color = color), start, end)
            }
        }
    }
}

private fun buildBaseArabicText(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    addStyle(ParagraphStyle(textDirection = TextDirection.Rtl), 0, text.length)
}
