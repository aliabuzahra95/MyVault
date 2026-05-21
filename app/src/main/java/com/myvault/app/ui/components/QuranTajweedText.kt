package com.myvault.app.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import com.myvault.app.data.quran.TajweedAnnotation
import java.lang.Character.getType

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
            val color = annotation.rule.toTajweedColor(isDark) ?: return@forEach
            val (start, end) = adjustArabicRange(
                text = text,
                start = annotation.start.coerceIn(0, text.length),
                end = annotation.end.coerceIn(0, text.length),
            )
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

private fun adjustArabicRange(text: String, start: Int, end: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0
    var adjustedStart = start.coerceAtMost(text.lastIndex)
    var adjustedEnd = end.coerceAtLeast(start).coerceAtMost(text.lastIndex)

    while (adjustedStart > 0 && adjustedStart < text.length && isCombiningMark(text[adjustedStart])) {
        adjustedStart--
    }
    if (adjustedStart > 0 && adjustedStart < text.length && isCombiningMark(text[adjustedStart])) {
        adjustedStart--
    }

    while (adjustedEnd + 1 < text.length && isCombiningMark(text[adjustedEnd + 1])) {
        adjustedEnd++
    }

    return adjustedStart to (adjustedEnd + 1)
}

private fun isCombiningMark(char: Char): Boolean = when (getType(char)) {
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt() -> true
    else -> false
}

private fun String.toTajweedColor(isDark: Boolean): Color? = when (this) {
    "ghunnah" -> if (isDark) Color(0xFFF08A12) else Color(0xFFC77600)

    "ikhfa",
    "ikhafa",
    "ikhfaa",
    "ikhafa_shafawi",
    "ikhfa_shafawi",
    "ikhfaa_shafawi" -> if (isDark) Color(0xFFFF4747) else Color(0xFFD62F2F)

    "idghaam_ghunnah",
    "idghaam_shafawi",
    "idgham_ghunnah",
    "idgham_shafawi",
    "idgham_wo_ghunnah",
    "idghaam_wo_ghunnah",
    "idghaam_no_ghunnah",
    "idgham_no_ghunnah",
    "idgham_without_ghunnah",
    "idghaam_without_ghunnah",
    "idgham_bila_ghunnah",
    "idghaam_bila_ghunnah",
    "idgham_mutajanisayn",
    "idgham_mutaqaribayn" -> if (isDark) Color(0xFFD676FF) else Color(0xFF9448CC)

    "madda_normal",
    "madda_obligatory",
    "madda_permissible",
    "madda_necessary",
    "madd_2",
    "madd_246",
    "ham_wasl",
    "hamzat_wasl",
    "slnt",
    "laam_shamsiyah",
    "lam_shamsiyyah" -> if (isDark) Color(0xFFD5D8E0) else Color(0xFF7A7F8A)

    "iqlab" -> if (isDark) Color(0xFF67A2FF) else Color(0xFF3C74E0)

    "qalqalah",
    "qalaqah" -> if (isDark) Color(0xFF59C52E) else Color(0xFF3E9D1F)

    else -> null
}
