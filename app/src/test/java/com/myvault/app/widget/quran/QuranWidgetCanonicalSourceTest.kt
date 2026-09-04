package com.myvault.app.widget.quran

import com.myvault.app.data.quran.QuranCanonicalSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QuranWidgetCanonicalSourceTest {
    private val projectRoot: Path = generateSequence(Paths.get(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("app/src/main/assets/qpc_hafs.json")) }
    private val source: JSONObject by lazy {
        JSONObject(String(Files.readAllBytes(projectRoot.resolve("app/src/main/assets/qpc_hafs.json"))))
    }

    @Test
    fun `widget Surahs are built directly from the canonical reader asset`() {
        assertEquals(7, QuranCanonicalSource.buildSurah(source, 1).size)
        assertEquals(286, QuranCanonicalSource.buildSurah(source, 2).size)
        assertEquals(30, QuranCanonicalSource.buildSurah(source, 67).size)
        assertEquals(15, QuranCanonicalSource.buildSurah(source, 91).size)
    }

    @Test
    fun `known widget references retain canonical Arabic without duplicate verse digits`() {
        listOf("1:1", "2:255", "67:1", "91:1").forEach { verseKey ->
            val surahNumber = verseKey.substringBefore(':').toInt()
            val ayahNumber = verseKey.substringAfter(':').toInt()
            val ayah = QuranCanonicalSource.buildSurah(source, surahNumber)
                .single { it.ayahNumber == ayahNumber }
            assertEquals(verseKey, ayah.verseKey)
            assertFalse(ayah.arabicText.isBlank())
            assertFalse(ayah.arabicText.last().isDigit())
        }
    }
}
