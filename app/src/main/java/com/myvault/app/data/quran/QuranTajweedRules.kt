package com.myvault.app.data.quran

import java.lang.Character.getType

internal fun quranTajweedColorArgb(rule: String, isDark: Boolean): Int? = when (rule) {
    "ghunnah" -> if (isDark) 0xFFF08A12.toInt() else 0xFFC77600.toInt()

    "ikhfa",
    "ikhafa",
    "ikhfaa",
    "ikhafa_shafawi",
    "ikhfa_shafawi",
    "ikhfaa_shafawi" -> if (isDark) 0xFFFF4747.toInt() else 0xFFD62F2F.toInt()

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
    "idgham_mutaqaribayn" -> if (isDark) 0xFFD676FF.toInt() else 0xFF9448CC.toInt()

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
    "lam_shamsiyyah" -> if (isDark) 0xFFD5D8E0.toInt() else 0xFF7A7F8A.toInt()

    "iqlab" -> if (isDark) 0xFF67A2FF.toInt() else 0xFF3C74E0.toInt()

    "qalqalah",
    "qalaqah" -> if (isDark) 0xFF59C52E.toInt() else 0xFF3E9D1F.toInt()

    else -> null
}

internal fun adjustedQuranTajweedRange(text: String, start: Int, end: Int): Pair<Int, Int> {
    if (text.isEmpty()) return 0 to 0
    var adjustedStart = start.coerceIn(0, text.lastIndex)
    var adjustedEnd = end.coerceAtLeast(start).coerceIn(0, text.lastIndex)

    while (adjustedStart > 0 && isCombiningMark(text[adjustedStart])) adjustedStart--
    while (adjustedEnd + 1 < text.length && isCombiningMark(text[adjustedEnd + 1])) adjustedEnd++

    return adjustedStart to (adjustedEnd + 1)
}

private fun isCombiningMark(char: Char): Boolean = when (getType(char)) {
    Character.NON_SPACING_MARK.toInt(),
    Character.COMBINING_SPACING_MARK.toInt(),
    Character.ENCLOSING_MARK.toInt() -> true
    else -> false
}
