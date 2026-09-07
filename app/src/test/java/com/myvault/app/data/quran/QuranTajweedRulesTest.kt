package com.myvault.app.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class QuranTajweedRulesTest {
    @Test fun `shared Tajweed palette includes every production category in both themes`() {
        listOf("ghunnah", "ikhfa", "idghaam_ghunnah", "iqlab", "qalqalah").forEach { rule ->
            assertNotNull(quranTajweedColorArgb(rule, isDark = false))
            assertNotNull(quranTajweedColorArgb(rule, isDark = true))
        }
    }

    @Test fun `grey Tajweed rules share the exact production grey mapping`() {
        listOf("madda_normal", "madd_246", "ham_wasl", "slnt", "laam_shamsiyah").forEach { rule ->
            assertEquals(0xFF7A7F8A.toInt(), quranTajweedColorArgb(rule, isDark = false))
            assertEquals(0xFFD5D8E0.toInt(), quranTajweedColorArgb(rule, isDark = true))
        }
    }
}
