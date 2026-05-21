package com.myvault.app.data.quran

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuranCatalogRepository @Inject constructor() {
    fun surahs(): List<SurahInfo> = quranCatalog

    fun surah(number: Int): SurahInfo? =
        quranCatalog.firstOrNull { it.num == number }
}
